package com.trieur.screenshots.lan

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.provider.OpenableColumns
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.trieur.screenshots.MainActivity
import com.trieur.screenshots.R
import com.trieur.screenshots.TrieurApp
import com.trieur.screenshots.data.Screenshot
import com.trieur.screenshots.data.Source
import com.trieur.screenshots.data.foldText
import com.trieur.screenshots.data.searchTokens
import com.trieur.screenshots.data.toFtsQuery
import com.trieur.screenshots.ui.displayPath
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/**
 * Serveur du téléphone, actif seulement quand « Visible sur le Wi-Fi » est allumé : répond à la
 * découverte, à l'appairage par code, puis aux recherches et téléchargements des appareils appairés.
 * Petit serveur HTTP maison (une requête par connexion), pour n'ajouter aucune bibliothèque.
 */
class LanServer(private val context: Context, private val store: LanStore) {
    private val app = context.applicationContext as TrieurApp
    private var tcp: ServerSocket? = null
    private var udp: DatagramSocket? = null
    private val pool = Executors.newFixedThreadPool(4)
    private val fails = ConcurrentHashMap<String, MutableList<Long>>()
    private var lock: WifiManager.MulticastLock? = null

    fun start() {
        lock = context.getSystemService(WifiManager::class.java)?.createMulticastLock("trieur-visible")?.apply { setReferenceCounted(false); acquire() }
        Thread({ runUdp() }, "trieur-udp").apply { isDaemon = true }.start()
        Thread({ runTcp() }, "trieur-http").apply { isDaemon = true }.start()
    }

    fun stop() {
        runCatching { tcp?.close() }
        runCatching { udp?.close() }
        runCatching { lock?.release() }
        pool.shutdownNow()
    }

    private fun runUdp() = try {
        DatagramSocket(null).also { udp = it }.use { s ->
            s.reuseAddress = true
            s.bind(InetSocketAddress(Lan.DISCOVERY_PORT))
            val buf = ByteArray(512)
            while (!s.isClosed) {
                val p = DatagramPacket(buf, buf.size)
                s.receive(p)
                if (String(p.data, 0, p.length).trim() != Lan.DISCOVER_MESSAGE || !Lan.isPrivate(p.address)) continue
                val reply = store.hello().toString().toByteArray()
                s.send(DatagramPacket(reply, reply.size, p.address, p.port))
            }
        }
    } catch (e: Exception) {
        Log.i(TAG, "UDP arrêté : $e")
    }

    private fun runTcp() = try {
        ServerSocket().also { tcp = it }.use { server ->
            server.reuseAddress = true
            server.bind(InetSocketAddress(Lan.HTTP_PORT))
            while (!server.isClosed) {
                val socket = server.accept()
                pool.execute { socket.use { handle(it) } }
            }
        }
    } catch (e: Exception) {
        Log.i(TAG, "HTTP arrêté : $e")
    }

    private class Request(val method: String, val path: String, val query: Map<String, String>, val headers: Map<String, String>, val body: ByteArray)

    private fun read(input: InputStream): Request? {
        val line = readLine(input) ?: return null
        val parts = line.split(' ')
        if (parts.size < 2) return null
        val headers = HashMap<String, String>()
        while (true) {
            val h = readLine(input) ?: break
            if (h.isEmpty()) break
            val i = h.indexOf(':')
            if (i > 0) headers[h.substring(0, i).trim().lowercase()] = h.substring(i + 1).trim()
        }
        val length = headers["content-length"]?.toIntOrNull()?.coerceIn(0, 64 * 1024) ?: 0
        val body = ByteArray(length)
        var read = 0
        while (read < length) {
            val n = input.read(body, read, length - read)
            if (n <= 0) break
            read += n
        }
        val target = parts[1]
        val query = target.substringAfter('?', "").split('&').filter { '=' in it }
            .associate { URLDecoder.decode(it.substringBefore('='), "UTF-8") to URLDecoder.decode(it.substringAfter('='), "UTF-8") }
        return Request(parts[0], target.substringBefore('?'), query, headers, body)
    }

    private fun readLine(input: InputStream): String? {
        val sb = StringBuilder()
        while (true) {
            val c = input.read()
            if (c == -1) return if (sb.isEmpty()) null else sb.toString()
            if (c == '\n'.code) return sb.toString().trimEnd('\r')
            if (sb.length > 8192) return null
            sb.append(c.toChar())
        }
    }

    private fun handle(socket: Socket) {
        socket.soTimeout = 10_000
        val remote = socket.inetAddress
        val out = socket.getOutputStream()
        if (!Lan.isPrivate(remote)) return respond(out, 403, "réseau local seulement")
        val req = read(BufferedInputStream(socket.getInputStream())) ?: return
        try {
            when (req.path) {
                "/api/hello" -> json(out, store.hello())
                "/api/pair" -> pair(out, req, remote.hostAddress!!)
                "/api/search", "/api/file" -> {
                    val token = req.headers["authorization"]?.removePrefix("Bearer ")?.trim()
                    val peer = store.peers.firstOrNull { it.tokenIn == token } ?: return respond(out, 401, "non appairé")
                    if (peer.host != remote.hostAddress) { peer.host = remote.hostAddress; store.upsert(peer) }
                    if (req.path == "/api/search") search(out, req.query["q"].orEmpty()) else file(out, req.query["id"]?.toLongOrNull())
                }
                else -> respond(out, 404, "inconnu")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Requête ${req.path}", e)
            runCatching { respond(out, 500, "erreur") }
        }
    }

    private fun pair(out: OutputStream, req: Request, host: String) {
        if (req.method != "POST") return respond(out, 405, "POST")
        val now = System.currentTimeMillis()
        val list = fails.getOrPut(host) { mutableListOf() }.apply { removeAll { now - it > 60_000 } }
        if (list.size >= 5) return respond(out, 429, "trop d'essais")
        val body = JSONObject(req.body.decodeToString())
        if (body.optString("code") != store.pairCode) {
            list += now
            return respond(out, 403, "code incorrect")
        }
        val tokenForThem = Lan.randomHex(24)
        store.upsert(Peer(body.getString("id"), body.optString("name", "PC"), body.optString("kind", "pc"), body.getString("token"), tokenForThem, host))
        store.pairCode = Lan.newPairCode() // un code ne sert qu'une fois
        json(out, JSONObject().put("id", store.deviceId).put("name", store.name).put("kind", "phone").put("token", tokenForThem))
    }

    private fun search(out: OutputStream, q: String) {
        val fts = toFtsQuery(q)
        val shots = if (fts == null) emptyList() else runBlocking { app.database.screenshotDao().searchNow(fts, 60) }
        val tokens = searchTokens(q).map(::foldText)
        val results = JSONArray()
        for (s in shots) {
            results.put(
                JSONObject().put("id", s.mediaId.toString()).put("name", s.displayName)
                    .put("path", s.path ?: ((s.folder?.takeUnless { it.startsWith("content:") } ?: "") + s.displayName))
                    .put("location", displayPath(s)).put("size", 0).put("modified", s.dateTaken)
                    .put("kind", kindOf(s)).put("snippet", snippet(s.text, tokens)),
            )
        }
        json(out, JSONObject().put("device", store.hello()).put("results", results))
    }

    private fun file(out: OutputStream, id: Long?) {
        val shot = id?.let { runBlocking { app.database.screenshotDao().get(it) } } ?: return respond(out, 404, "introuvable")
        val uri = Uri.parse(shot.uri)
        val size = context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst() && !c.isNull(0)) c.getLong(0) else -1L
        } ?: -1L
        val input = context.contentResolver.openInputStream(uri) ?: return respond(out, 404, "introuvable")
        input.use {
            val head = StringBuilder("HTTP/1.1 200 OK\r\nContent-Type: application/octet-stream\r\nConnection: close\r\n")
            head.append("Content-Disposition: attachment; filename*=UTF-8''").append(URLEncoder.encode(shot.displayName, "UTF-8").replace("+", "%20")).append("\r\n")
            if (size >= 0) head.append("Content-Length: ").append(size).append("\r\n")
            out.write(head.append("\r\n").toString().toByteArray())
            it.copyTo(out)
            out.flush()
        }
    }

    private fun json(out: OutputStream, o: JSONObject) {
        val bytes = o.toString().toByteArray()
        out.write("HTTP/1.1 200 OK\r\nContent-Type: application/json; charset=utf-8\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray())
        out.write(bytes)
        out.flush()
    }

    private fun respond(out: OutputStream, code: Int, msg: String) {
        val bytes = msg.toByteArray()
        out.write("HTTP/1.1 $code X\r\nContent-Type: text/plain; charset=utf-8\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray())
        out.write(bytes)
        out.flush()
    }

    companion object {
        private const val TAG = "LanServer"

        fun kindOf(s: Screenshot): String = when (Source.fromName(s.source)) {
            Source.SCREENSHOT -> "screenshot"
            Source.DOWNLOAD, Source.GALLERY -> "image"
            Source.PDF -> "pdf"
            Source.DOC -> when (s.displayName.substringAfterLast('.', "").lowercase()) {
                "doc", "docx", "odt", "rtf", "txt", "md" -> "doc"
                "xls", "xlsx", "ods", "csv" -> "sheet"
                "ppt", "pptx", "odp" -> "slides"
                "zip", "rar", "7z" -> "archive"
                else -> "file"
            }
        }

        /** Extrait autour du premier mot trouvé, le mot entre crochets (mis en valeur par l'autre appareil). */
        fun snippet(text: String, foldedTokens: List<String>): String {
            if (text.isBlank()) return ""
            val folded = foldText(text)
            val hit = foldedTokens.mapNotNull { t -> folded.indexOf(t).takeIf { it >= 0 }?.let { it to t.length } }.minByOrNull { it.first }
                ?: return text.replace(Regex("\\s+"), " ").take(160)
            val (at, len) = hit
            val start = (at - 60).coerceAtLeast(0)
            val end = (at + len + 100).coerceAtMost(text.length)
            val s = (if (start > 0) "…" else "") + text.substring(start, at) + "[" + text.substring(at, at + len) + "]" + text.substring(at + len, end) + if (end < text.length) "…" else ""
            return s.replace(Regex("\\s+"), " ")
        }
    }
}

/** Service au premier plan qui garde le téléphone « visible » pour le PC, avec une notification. */
class LanService : Service() {
    private var server: LanServer? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Visible sur le Wi-Fi", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val store = LanStore(this)
        val n: Notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_lan)
            .setContentTitle("Trieur visible sur le Wi-Fi")
            .setContentText("Tes appareils appairés peuvent chercher ici · code ${store.pairCode}")
            .setContentIntent(open)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 29) startForeground(ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE) else startForeground(ID, n)
        if (server == null) server = LanServer(this, store).also { it.start() }
        return START_STICKY
    }

    override fun onDestroy() {
        server?.stop()
        server = null
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "lan"
        private const val ID = 4711

        fun setVisible(context: Context, visible: Boolean) {
            LanStore(context).visible = visible
            val i = Intent(context, LanService::class.java)
            if (visible) ContextCompat.startForegroundService(context, i) else context.stopService(i)
        }
    }
}
