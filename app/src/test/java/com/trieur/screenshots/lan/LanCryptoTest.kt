package com.trieur.screenshots.lan

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.random.Random

class LanCryptoTest {
    private fun pairKeys(): Pair<ByteArray, ByteArray> {
        val a = LanCrypto.newKeyPair()
        val b = LanCrypto.newKeyPair()
        return LanCrypto.sharedKey(a, LanCrypto.publicKey(b)) to LanCrypto.sharedKey(b, LanCrypto.publicKey(a))
    }

    @Test fun bothSidesDeriveTheSameKey() {
        val (ka, kb) = pairKeys()
        assertArrayEquals(ka, kb)
        assertEquals(32, ka.size)
    }

    @Test fun requestRoundTripAndWrongKeyRefused() {
        val (ka, kb) = pairKeys()
        val box = LanCrypto.sealRequest(ka, """{"q":"cv"}""")
        assertEquals("""{"q":"cv"}""", LanCrypto.openRequest(kb, box))
        val (other, _) = pairKeys()
        assertThrows(Exception::class.java) { LanCrypto.openRequest(other, box) }
    }

    private fun framed(key: ByteArray, data: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        val w = LanCrypto.Writer(key, out)
        w.frame("""{"name":"CV.pdf"}""".toByteArray())
        w.stream(ByteArrayInputStream(data))
        return out.toByteArray()
    }

    @Test fun fileStreamsRoundTrip() {
        val (ka, kb) = pairKeys()
        val data = Random(1).nextBytes(LanCrypto.CHUNK * 3 + 123)
        val r = LanCrypto.Reader(kb, ByteArrayInputStream(framed(ka, data)))
        assertEquals("""{"name":"CV.pdf"}""", r.next()!!.decodeToString())
        assertArrayEquals(data, r.readAll())
    }

    @Test fun truncatedOrTamperedStreamFails() {
        val (ka, kb) = pairKeys()
        val bytes = framed(ka, Random(2).nextBytes(LanCrypto.CHUNK * 2))
        // Coupé avant la trame finale.
        val cut = bytes.copyOf(bytes.size - 40)
        assertThrows(Exception::class.java) { LanCrypto.Reader(kb, ByteArrayInputStream(cut)).readAll() }
        // Un octet modifié au milieu.
        val bad = bytes.copyOf().also { it[it.size / 2] = (it[it.size / 2].toInt() xor 1).toByte() }
        assertThrows(Exception::class.java) { LanCrypto.Reader(kb, ByteArrayInputStream(bad)).readAll() }
        // Le texte en clair n'apparaît pas dans ce qui passe sur le réseau.
        val plain = framed(ka, "Curriculum vitae confidentiel".toByteArray())
        assert(!String(plain, Charsets.ISO_8859_1).contains("Curriculum") && !String(plain, Charsets.ISO_8859_1).contains("CV.pdf"))
    }
}
