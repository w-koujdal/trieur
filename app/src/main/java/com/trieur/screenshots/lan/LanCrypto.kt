package com.trieur.screenshots.lan

// Fichier identique dans Trieur PC (com.trieur.pc) : le modifier des deux côtés.

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.spec.ECGenParameterSpec
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Chiffrement des échanges entre appareils (protocole 2).
 *
 * À l'appairage, chaque appareil envoie une clé publique (ECDH P-256) : les deux en tirent la
 * même clé secrète, qui ne passe jamais sur le réseau. Ensuite, requêtes et réponses (résultats,
 * extraits, fichiers et leurs noms) sont chiffrées et authentifiées (AES-256-GCM). Quelqu'un qui
 * écoute le Wi-Fi ne voit que des données illisibles ; une requête que la clé n'ouvre pas est refusée.
 */
object LanCrypto {
    const val CHUNK = 64 * 1024
    private val rnd = SecureRandom()
    private val b64 = Base64.getEncoder()
    private val b64d = Base64.getDecoder()

    fun newKeyPair(): KeyPair = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()

    fun publicKey(kp: KeyPair): String = b64.encodeToString(kp.public.encoded)

    fun encodeKey(key: ByteArray): String = b64.encodeToString(key)

    fun decodeKey(key: String): ByteArray = b64d.decode(key)

    /** Clé partagée : ECDH, puis SHA-256 avec les deux clés publiques (dans un ordre fixe). */
    fun sharedKey(mine: KeyPair, theirPublic: String): ByteArray {
        val theirBytes = b64d.decode(theirPublic)
        val their = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(theirBytes))
        val secret = KeyAgreement.getInstance("ECDH").run { init(mine.private); doPhase(their, true); generateSecret() }
        val a = mine.public.encoded
        val (x, y) = if (compare(a, theirBytes) <= 0) a to theirBytes else theirBytes to a
        return MessageDigest.getInstance("SHA-256").run {
            update("trieur-lan-2".toByteArray()); update(secret); update(x); update(y); digest()
        }
    }

    private fun compare(a: ByteArray, b: ByteArray): Int {
        for (i in 0 until minOf(a.size, b.size)) {
            val d = (a[i].toInt() and 0xFF) - (b[i].toInt() and 0xFF)
            if (d != 0) return d
        }
        return a.size - b.size
    }

    /** nonce (12 octets) + texte chiffré + étiquette. [aad] : contexte authentifié, non chiffré. */
    fun seal(key: ByteArray, plain: ByteArray, aad: ByteArray): ByteArray {
        val nonce = ByteArray(12).also { rnd.nextBytes(it) }
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
        c.updateAAD(aad)
        return nonce + c.doFinal(plain)
    }

    /** Lève une exception si la clé ne correspond pas ou si les données ont été modifiées. */
    fun open(key: ByteArray, box: ByteArray, aad: ByteArray): ByteArray {
        require(box.size >= 28) { "trop court" }
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, box, 0, 12))
        c.updateAAD(aad)
        return c.doFinal(box, 12, box.size - 12)
    }

    /** Requête : JSON chiffré, envoyé en en-tête (« X-Trieur-Box »). */
    fun sealRequest(key: ByteArray, json: String): String = b64.encodeToString(seal(key, json.toByteArray(), "req".toByteArray()))

    fun openRequest(key: ByteArray, box: String): String = open(key, b64d.decode(box), "req".toByteArray()).decodeToString()

    private fun frameAad(index: Int, last: Boolean) = byteArrayOf(
        'r'.code.toByte(), (index ushr 24).toByte(), (index ushr 16).toByte(), (index ushr 8).toByte(), index.toByte(), if (last) 1 else 0,
    )

    /**
     * Réponse : une suite de trames [dernière ? 1 octet][longueur 4 octets][boîte], numérotées.
     * La dernière est marquée : une réponse coupée ou réordonnée est détectée.
     */
    class Writer(private val key: ByteArray, out: OutputStream) {
        private val out = DataOutputStream(out)
        private var index = 0

        fun frame(data: ByteArray, last: Boolean = false) {
            val box = seal(key, data, frameAad(index++, last))
            out.writeByte(if (last) 1 else 0)
            out.writeInt(box.size)
            out.write(box)
        }

        /** Copie [input] en trames de 64 Ko, puis la trame finale. */
        fun stream(input: InputStream) {
            val buf = ByteArray(CHUNK)
            while (true) {
                val n = input.readNBytesCompat(buf)
                if (n <= 0) break
                frame(buf.copyOf(n))
            }
            frame(ByteArray(0), last = true)
            out.flush()
        }
    }

    class Reader(private val key: ByteArray, input: InputStream) {
        private val input = DataInputStream(input)
        private var index = 0
        private var done = false

        /** Trame suivante, ou null après la dernière. */
        fun next(): ByteArray? {
            if (done) return null
            val last = input.readUnsignedByte() == 1
            val len = input.readInt()
            require(len in 28..(CHUNK + 1024)) { "trame invalide" }
            val box = ByteArray(len).also { input.readFully(it) }
            val data = open(key, box, frameAad(index++, last))
            if (last) done = true
            return data
        }

        /** Copie tout le contenu dans [out] ; échoue si la fin authentifiée manque. */
        fun copyTo(out: OutputStream) {
            while (true) {
                val d = next() ?: break
                out.write(d)
            }
        }

        fun readAll(): ByteArray = java.io.ByteArrayOutputStream().also { copyTo(it) }.toByteArray()
    }

    /** Lit jusqu'à remplir [buf] (ou la fin du flux). */
    private fun InputStream.readNBytesCompat(buf: ByteArray): Int {
        var total = 0
        while (total < buf.size) {
            val n = read(buf, total, buf.size - total)
            if (n < 0) break
            total += n
        }
        return total
    }

    /** Comparaison en temps constant (codes d'appairage). */
    fun same(a: String, b: String): Boolean = MessageDigest.isEqual(a.toByteArray(), b.toByteArray())
}
