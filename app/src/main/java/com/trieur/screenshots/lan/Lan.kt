package com.trieur.screenshots.lan

import android.content.Context
import android.net.wifi.WifiManager
import android.os.Build
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.SocketTimeoutException
import java.net.URL
import java.net.URLDecoder
import java.net.URLEncoder
import java.security.SecureRandom

/**
 * Recherche entre appareils sur le même Wi-Fi (Trieur PC, autre téléphone). Protocole commun
 * avec Trieur PC :
 * - découverte : message UDP « TRIEUR-DISCOVER 1 » diffusé sur le port 47130, réponse JSON ;
 * - appairage : POST /api/pair avec le code affiché par l'autre appareil, échange de jetons ;
 * - GET /api/search?q=… et GET /api/file?id=… avec « Authorization: Bearer <jeton> ».
 * Seules des adresses du réseau local sont contactées ou acceptées.
 */
object Lan {
    const val DISCOVERY_PORT = 47130
    const val HTTP_PORT = 47131
    const val DISCOVER_MESSAGE = "TRIEUR-DISCOVER 1"
    const val VERSION = 1

    fun randomHex(bytes: Int): String = ByteArray(bytes).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }

    fun newPairCode(): String = "%06d".format(SecureRandom().nextInt(1_000_000))

    fun isPrivate(a: InetAddress): Boolean = a.isSiteLocalAddress || a.isLinkLocalAddress || a.isLoopbackAddress

    fun isPrivateHost(host: String): Boolean = runCatching { isPrivate(InetAddress.getByName(host)) }.getOrDefault(false)

    /** Adresses IPv4 du téléphone sur le réseau local (pour l'ajout manuel depuis le PC). */
    fun localAddresses(): List<String> = runCatching {
        NetworkInterface.getNetworkInterfaces().toList().filter { it.isUp && !it.isLoopback }
            .flatMap { ni -> ni.inetAddresses.toList() }
            .filter { it is Inet4Address && it.isSiteLocalAddress }.map { it.hostAddress!! }
    }.getOrDefault(emptyList())
}

data class Peer(
    val id: String,
    var name: String,
    val kind: String,
    val tokenOut: String,
    val tokenIn: String,
    var host: String?,
    var port: Int = Lan.HTTP_PORT,
) {
    fun toJson() = JSONObject().put("id", id).put("name", name).put("kind", kind).put("tokenOut", tokenOut)
        .put("tokenIn", tokenIn).put("host", host).put("port", port)

    companion object {
        fun fromJson(o: JSONObject) = Peer(
            o.getString("id"), o.getString("name"), o.optString("kind", "pc"), o.getString("tokenOut"),
            o.getString("tokenIn"), o.optString("host").ifBlank { null }, o.optInt("port", Lan.HTTP_PORT),
        )
    }
}

data class Found(val id: String, val name: String, val kind: String, val host: String, val port: Int)

/** Un fichier trouvé chez un autre appareil. */
data class RemoteHit(
    val id: String,
    val name: String,
    val path: String,
    val location: String,
    val size: Long,
    val modified: Long,
    val kind: String,
    val snippet: String,
)

data class RemoteGroup(val peer: Peer, val hits: List<RemoteHit>, val error: String?)

/** Identité de ce téléphone, code d'appairage, appareils appairés. */
class LanStore(context: Context) {
    private val prefs = context.getSharedPreferences("lan", Context.MODE_PRIVATE)

    val deviceId: String = prefs.getString("id", null) ?: Lan.randomHex(8).also { prefs.edit().putString("id", it).apply() }

    var name: String
        get() = prefs.getString("name", null) ?: "Téléphone (${Build.MODEL})"
        set(v) = prefs.edit().putString("name", v).apply()

    var visible: Boolean
        get() = prefs.getBoolean("visible", false)
        set(v) = prefs.edit().putBoolean("visible", v).apply()

    var pairCode: String
        get() = prefs.getString("code", null) ?: Lan.newPairCode().also { pairCode = it }
        set(v) = prefs.edit().putString("code", v).apply()

    @get:Synchronized
    @set:Synchronized
    var peers: List<Peer>
        get() = runCatching {
            val a = JSONArray(prefs.getString("peers", "[]"))
            (0 until a.length()).map { Peer.fromJson(a.getJSONObject(it)) }
        }.getOrDefault(emptyList())
        set(v) = prefs.edit().putString("peers", JSONArray(v.map { it.toJson() }).toString()).apply()

    @Synchronized
    fun upsert(peer: Peer) {
        peers = peers.filterNot { it.id == peer.id } + peer
    }

    @Synchronized
    fun remove(id: String) {
        peers = peers.filterNot { it.id == id }
    }

    fun hello() = JSONObject().put("app", "trieur").put("v", Lan.VERSION).put("id", deviceId).put("name", name)
        .put("kind", "phone").put("port", Lan.HTTP_PORT)
}

/** Côté client : trouver les appareils, s'appairer, chercher chez eux, récupérer un fichier. */
class LanClient(private val context: Context, private val store: LanStore) {

    /** Diffuse une demande sur le Wi-Fi et collecte les réponses. */
    fun discover(waitMs: Long = 1800): List<Found> {
        val wifi = context.applicationContext.getSystemService(WifiManager::class.java)
        val lock = wifi?.createMulticastLock("trieur")?.apply { setReferenceCounted(false); acquire() }
        try {
            val found = LinkedHashMap<String, Found>()
            DatagramSocket().use { socket ->
                socket.broadcast = true
                socket.soTimeout = 300
                val msg = Lan.DISCOVER_MESSAGE.toByteArray()
                for (target in broadcastTargets()) runCatching { socket.send(DatagramPacket(msg, msg.size, target, Lan.DISCOVERY_PORT)) }
                val end = System.currentTimeMillis() + waitMs
                val buf = ByteArray(2048)
                while (System.currentTimeMillis() < end) {
                    val p = DatagramPacket(buf, buf.size)
                    try {
                        socket.receive(p)
                    } catch (_: SocketTimeoutException) {
                        continue
                    }
                    if (!Lan.isPrivate(p.address)) continue
                    val o = runCatching { JSONObject(String(p.data, 0, p.length)) }.getOrNull() ?: continue
                    if (o.optString("app") != "trieur") continue
                    val id = o.optString("id")
                    if (id.isBlank() || id == store.deviceId) continue
                    found[id] = Found(id, o.optString("name", "?"), o.optString("kind", "pc"), p.address.hostAddress!!, o.optInt("port", Lan.HTTP_PORT))
                }
            }
            return found.values.toList()
        } finally {
            lock?.release()
        }
    }

    /** Appareil saisi à la main (adresse IP affichée sur le PC) : on lui demande qui il est. */
    fun probe(host: String, port: Int = Lan.HTTP_PORT): Found {
        require(Lan.isPrivateHost(host)) { "Adresse hors du réseau local" }
        val o = JSONObject(get("http://$host:$port/api/hello", null).decodeToString())
        return Found(o.getString("id"), o.optString("name", host), o.optString("kind", "pc"), host, o.optInt("port", port))
    }

    fun pair(target: Found, code: String): Peer {
        require(Lan.isPrivateHost(target.host)) { "Adresse hors du réseau local" }
        val myTokenForThem = Lan.randomHex(24)
        val body = JSONObject().put("code", code).put("id", store.deviceId).put("name", store.name).put("kind", "phone").put("token", myTokenForThem)
        val c = open("http://${target.host}:${target.port}/api/pair", null).apply {
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            outputStream.use { it.write(body.toString().toByteArray()) }
        }
        when (c.responseCode) {
            200 -> Unit
            403 -> error("Code incorrect")
            429 -> error("Trop d'essais : attends une minute")
            else -> error("Refusé (${c.responseCode})")
        }
        val o = JSONObject(c.inputStream.use { it.readBytes().decodeToString() })
        val peer = Peer(o.getString("id"), o.getString("name"), o.optString("kind", target.kind), o.getString("token"), myTokenForThem, target.host, target.port)
        store.upsert(peer)
        return peer
    }

    /** Recherche chez tous les appareils appairés, en parallèle. */
    fun searchAll(query: String): List<RemoteGroup> {
        val peers = store.peers
        if (peers.isEmpty() || query.isBlank()) return emptyList()
        val results = arrayOfNulls<RemoteGroup>(peers.size)
        val threads = peers.mapIndexed { i, peer ->
            Thread { results[i] = searchWithRetry(peer, query) }.apply { start() }
        }
        threads.forEach { it.join(10_000) }
        return results.mapIndexed { i, r -> r ?: RemoteGroup(peers[i], emptyList(), "pas de réponse") }
    }

    private fun searchWithRetry(peer: Peer, query: String): RemoteGroup = try {
        RemoteGroup(peer, search(peer, query), null)
    } catch (e: Exception) {
        // L'adresse a pu changer (box redémarrée) : on le cherche sur le réseau, une fois.
        val again = runCatching { discover(1200).firstOrNull { it.id == peer.id } }.getOrNull()
        if (again != null) {
            peer.host = again.host; peer.port = again.port
            store.upsert(peer)
            runCatching { RemoteGroup(peer, search(peer, query), null) }.getOrElse { RemoteGroup(peer, emptyList(), "injoignable") }
        } else {
            RemoteGroup(peer, emptyList(), if (e.message == "401") "n'est plus appairé" else "injoignable : même Wi-Fi ? Trieur PC lancé ?")
        }
    }

    private fun search(peer: Peer, query: String): List<RemoteHit> {
        val host = peer.host ?: error("adresse inconnue")
        val o = JSONObject(get("http://$host:${peer.port}/api/search?q=${URLEncoder.encode(query, "UTF-8")}", peer.tokenOut).decodeToString())
        o.optJSONObject("device")?.optString("name")?.takeIf { it.isNotBlank() && it != peer.name }?.let { peer.name = it; store.upsert(peer) }
        val a = o.getJSONArray("results")
        return (0 until a.length()).map { i ->
            val h = a.getJSONObject(i)
            RemoteHit(
                h.getString("id"), h.getString("name"), h.optString("path"), h.optString("location"),
                h.optLong("size"), h.optLong("modified"), h.optString("kind", "file"), h.optString("snippet"),
            )
        }
    }

    /** Télécharge le fichier dans [dir] ; renvoie le fichier écrit. */
    fun download(peer: Peer, hit: RemoteHit, dir: File): File {
        val host = peer.host ?: error("adresse inconnue")
        val c = open("http://$host:${peer.port}/api/file?id=${URLEncoder.encode(hit.id, "UTF-8")}", peer.tokenOut).apply { readTimeout = 120_000 }
        if (c.responseCode != 200) error("Refusé (${c.responseCode})")
        val name = c.getHeaderField("Content-Disposition")?.substringAfter("filename*=UTF-8''", "")
            ?.let { URLDecoder.decode(it, "UTF-8") }?.ifBlank { null } ?: hit.name
        dir.mkdirs()
        val out = File(dir, name.replace(Regex("[\\\\/:*?\"<>|]"), "_"))
        c.inputStream.use { input -> out.outputStream().use { input.copyTo(it) } }
        return out
    }

    private fun open(url: String, token: String?): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 3000
            readTimeout = 8000
            if (token != null) setRequestProperty("Authorization", "Bearer $token")
        }

    private fun get(url: String, token: String?): ByteArray {
        val c = open(url, token)
        if (c.responseCode == 401) error("401")
        if (c.responseCode != 200) error("HTTP ${c.responseCode}")
        return c.inputStream.use { it.readBytes() }
    }

    private fun broadcastTargets(): List<InetAddress> {
        val out = mutableListOf<InetAddress>(InetAddress.getByName("255.255.255.255"))
        runCatching {
            for (ni in NetworkInterface.getNetworkInterfaces()) {
                if (!ni.isUp || ni.isLoopback) continue
                for (ia in ni.interfaceAddresses) {
                    val b = ia.broadcast
                    if (b is Inet4Address && ia.address.isSiteLocalAddress) out += b
                }
            }
        }
        return out.distinct()
    }
}
