package app.bungaealert.detect

object ChatMatcher {
    fun matches(mode: MatchMode, pattern: String, chatName: String): Boolean {
        val needle = pattern.trim()
        val name = chatName.trim()
        if (needle.isEmpty() || name.isEmpty()) return false
        return when (mode) {
            MatchMode.EXACT -> name == needle
            MatchMode.CONTAINS -> name.contains(needle)
            MatchMode.ENDS_WITH -> name.endsWith(needle)
        }
    }

    fun matches(rule: WatchRule, chatName: String): Boolean =
        rule.enabled && matches(rule.matchMode, rule.pattern, chatName)
}
