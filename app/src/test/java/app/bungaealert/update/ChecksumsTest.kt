package app.bungaealert.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ChecksumsTest {
    @Test
    fun sha256MatchesTheKnownVector() {
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            Checksums.sha256("abc".toByteArray()),
        )
    }

    @Test
    fun fileDigestMatchesTheByteDigest() {
        val file = File.createTempFile("sums", ".bin")
        try {
            val bytes = "bungae-alert".toByteArray()
            file.writeBytes(bytes)
            assertEquals(Checksums.sha256(bytes), Checksums.sha256(file))
        } finally {
            file.delete()
        }
    }

    @Test
    fun parsesGnuAndBinaryLinesAndIgnoresComments() {
        val text = """
            # comment
            bA7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad  bungae-alert-v1.4.0-vc42.apk
            deadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeef *other.apk

        """.trimIndent()
        val entries = Checksums.parse(text)
        assertEquals(2, entries.size)
        assertEquals("bungae-alert-v1.4.0-vc42.apk", entries[0].name)
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", entries[0].sha256)
        assertEquals("other.apk", entries[1].name)
    }

    @Test
    fun matchesOnlyTheNamedFile() {
        val hash = Checksums.sha256("apk-bytes".toByteArray())
        val text = """
            ${hash}  bungae-alert-v1.4.0-vc42.apk
            0000000000000000000000000000000000000000000000000000000000000000  other.apk
        """.trimIndent()
        assertTrue(Checksums.matches(text, "bungae-alert-v1.4.0-vc42.apk", hash.uppercase()))
        assertFalse(Checksums.matches(text, "other.apk", hash))
        assertFalse(Checksums.matches(text, "bungae-alert-v1.4.0-vc42.apk", "00"))
        assertFalse(Checksums.matches(text, "missing.apk", hash))
        assertFalse(Checksums.matches("", "bungae-alert-v1.4.0-vc42.apk", hash))
    }
}
