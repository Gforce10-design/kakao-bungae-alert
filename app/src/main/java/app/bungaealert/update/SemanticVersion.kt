package app.bungaealert.update

/**
 * Semantic version comparison for GitHub release tags.
 * A leading `v` is ignored, and build metadata after `+` is ignored.
 */
data class SemanticVersion(
    val numbers: List<Int>,
    val preRelease: List<String> = emptyList(),
) : Comparable<SemanticVersion> {
    fun normalized(): String {
        val core = numbers.joinToString(".")
        return if (preRelease.isEmpty()) core else "$core-${preRelease.joinToString(".")}"
    }

    override fun compareTo(other: SemanticVersion): Int {
        val count = maxOf(numbers.size, other.numbers.size)
        for (index in 0 until count) {
            val diff = numbers.getOrElse(index) { 0 } - other.numbers.getOrElse(index) { 0 }
            if (diff != 0) return diff
        }
        if (preRelease.isEmpty() && other.preRelease.isEmpty()) return 0
        if (preRelease.isEmpty()) return 1
        if (other.preRelease.isEmpty()) return -1
        val idCount = maxOf(preRelease.size, other.preRelease.size)
        for (index in 0 until idCount) {
            if (index >= preRelease.size) return -1
            if (index >= other.preRelease.size) return 1
            val diff = compareIdentifier(preRelease[index], other.preRelease[index])
            if (diff != 0) return diff
        }
        return 0
    }

    companion object {
        fun parse(raw: String): SemanticVersion? {
            var text = raw.trim()
            if (text.length >= 2 && (text[0] == 'v' || text[0] == 'V') && text[1].isDigit()) {
                text = text.substring(1)
            }
            text = text.substringBefore('+')
            if (text.isEmpty()) return null
            val corePart: String
            val prePart: String?
            val dash = text.indexOf('-')
            if (dash < 0) {
                corePart = text
                prePart = null
            } else {
                corePart = text.substring(0, dash)
                prePart = text.substring(dash + 1)
                if (prePart.isEmpty()) return null
            }
            if (corePart.isEmpty()) return null
            val numbers = corePart.split('.')
            if (numbers.any { segment -> segment.isEmpty() || segment.any { !it.isDigit() } }) return null
            val parsed = numbers.map { it.toIntOrNull() ?: return null }
            val pre = if (prePart == null) {
                emptyList()
            } else {
                val ids = prePart.split('.')
                if (ids.any { id -> id.isEmpty() || id.any { ch -> !(ch.isLetterOrDigit() || ch == '-') } }) return null
                ids
            }
            return SemanticVersion(parsed, pre)
        }

        fun isNewer(candidate: String, installed: String): Boolean {
            val latest = parse(candidate) ?: return false
            val current = parse(installed) ?: return false
            return latest > current
        }

        fun display(raw: String): String {
            val parsed = parse(raw) ?: return raw.trim()
            return "v${parsed.normalized()}"
        }

        private fun compareIdentifier(left: String, right: String): Int {
            val leftNumber = left.toLongOrNull()
            val rightNumber = right.toLongOrNull()
            val leftNumeric = leftNumber != null && left.all(Char::isDigit)
            val rightNumeric = rightNumber != null && right.all(Char::isDigit)
            if (leftNumeric && rightNumeric) return leftNumber!!.compareTo(rightNumber!!)
            if (leftNumeric) return -1
            if (rightNumeric) return 1
            return left.compareTo(right)
        }
    }
}
