package app.bungaealert.update

import java.io.File
import java.security.MessageDigest

object Checksums {
    data class Entry(val sha256: String, val name: String)

    private val linePattern = Regex("""^([0-9a-fA-F]{64})[ \t]+\*?(\S.*?)\s*$""")

    fun parse(text: String): List<Entry> {
        val entries = mutableListOf<Entry>()
        for (raw in text.removePrefix("\uFEFF").lineSequence()) {
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("#")) continue
            val match = linePattern.matchEntire(line) ?: continue
            entries += Entry(match.groupValues[1].lowercase(), match.groupValues[2])
        }
        return entries
    }

    fun matches(text: String, fileName: String, actualSha256: String): Boolean {
        if (fileName.isBlank()) return false
        val expected = parse(text).firstOrNull { it.name == fileName } ?: return false
        return expected.sha256.equals(actualSha256.trim(), ignoreCase = true)
    }

    fun sha256(bytes: ByteArray): String = hex(MessageDigest.getInstance("SHA-256").digest(bytes))

    fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return hex(digest.digest())
    }

    private fun hex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }
}
