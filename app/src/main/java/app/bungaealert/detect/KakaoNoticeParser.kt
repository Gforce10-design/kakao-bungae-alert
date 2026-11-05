package app.bungaealert.detect

data class RawKakaoMessage(
    val sender: String?,
    val text: String,
)

/**
 * Fields already pulled off a StatusBarNotification. Importance is recorded
 * and deliberately not used as a filter: muted KakaoTalk rooms still post
 * notifications on a low-importance channel, and those must be read.
 */
data class RawKakaoNotice(
    val packageName: String,
    val title: String?,
    val text: String?,
    val bigText: String?,
    val subText: String?,
    val conversationTitle: String?,
    val textLines: List<String> = emptyList(),
    val messages: List<RawKakaoMessage> = emptyList(),
    val isGroupSummary: Boolean = false,
    val importance: Int? = null,
    val postedAtMillis: Long = 0L,
    /**
     * Room name from the notification's conversation shortcut (API 30+
     * ranking). KakaoTalk group MessagingStyle notifications carry the sender
     * in android.title and no conversationTitle, so this wins when present.
     */
    val roomName: String? = null,
)

data class ParsedChatMessage(
    val chatName: String,
    val sender: String?,
    val text: String,
    val postedAtMillis: Long,
)

object KakaoPackages {
    const val KAKAO_TALK = "com.kakao.talk"

    fun shouldRead(packageName: String, importance: Int?): Boolean {
        // importance is accepted so callers cannot "forget" it, then ignored.
        if (importance != null && importance < -2) return false
        return packageName == KAKAO_TALK
    }
}

object KakaoNoticeParser {
    private val appTitles = setOf("카카오톡", "kakaotalk", "kakao talk")
    private val summaryCount = Regex("^\\d+개의 새 메시지$|^새 메시지$")
    private val senderSplit = Regex("^([^:：]{1,16})\\s*[:：]\\s*(.+)$")

    fun parse(raw: RawKakaoNotice): List<ParsedChatMessage> {
        if (!KakaoPackages.shouldRead(raw.packageName, raw.importance)) return emptyList()
        val chatFromConversation = raw.roomName?.trim()?.takeIf { isChatName(it) }
            ?: raw.conversationTitle?.trim()?.takeIf { isChatName(it) }
        val fromSummary = raw.isGroupSummary && raw.textLines.isNotEmpty()
        val lines = when {
            fromSummary -> messagesFromLines(raw.textLines)
            raw.messages.isNotEmpty() -> raw.messages.map { it.sender to it.text }
            raw.textLines.isNotEmpty() -> messagesFromLines(raw.textLines)
            else -> {
                val body = raw.text?.trim().orEmpty().ifEmpty { raw.bigText?.trim().orEmpty() }
                if (body.isEmpty() && raw.title.isNullOrBlank()) return emptyList()
                listOf(splitSender(body))
            }
        }.filterNot { (_, body) -> body.isBlank() || summaryCount.matches(body.trim()) }

        if (lines.isEmpty()) {
            val chat = chatFromConversation ?: inferChat(raw.title, raw.subText, "")
            if (chat == null) return emptyList()
            return listOf(ParsedChatMessage(chat, null, "", raw.postedAtMillis))
        }

        return lines.mapNotNull { (sender, body) ->
            val chat = chatFromConversation ?: inferChat(raw.title, raw.subText, body, sender)
            if (chat == null) null
            else ParsedChatMessage(
                chatName = chat,
                sender = sender?.takeIf { it.isNotBlank() && it != chat },
                text = body.trim(),
                postedAtMillis = raw.postedAtMillis,
            )
        }
    }

    private fun messagesFromLines(lines: List<String>): List<Pair<String?, String>> =
        lines.map { splitSender(it) }

    fun splitSender(line: String): Pair<String?, String> {
        val trimmed = line.trim()
        val match = senderSplit.find(trimmed) ?: return null to trimmed
        val left = match.groupValues[1].trim()
        val right = match.groupValues[2].trim()
        val nameLike = left.isNotEmpty() &&
            left.length <= 12 &&
            left.none { it.isDigit() } &&
            !left.contains("http", ignoreCase = true)
        return if (nameLike && right.isNotEmpty()) left to right else null to trimmed
    }

    private fun inferChat(
        title: String?,
        subText: String?,
        body: String,
        sender: String? = null,
    ): String? {
        subText?.trim()?.takeIf { isChatName(it) }?.let { return it }
        val titled = title?.trim()?.takeIf { isChatName(it) }
        if (titled != null && sender != null && titled == sender && body.isNotBlank()) {
            return titled
        }
        return titled
    }

    private fun isChatName(value: String): Boolean {
        val v = value.trim()
        if (v.isEmpty()) return false
        if (v.lowercase() in appTitles) return false
        if (summaryCount.matches(v)) return false
        return true
    }
}

class DedupeWindow(
    private val windowMillis: Long = 10 * 60 * 1000L,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val seen = LinkedHashMap<String, Long>()

    @Synchronized
    fun shouldProcess(chatName: String, text: String): Boolean {
        val key = chatName.trim() + "\u0000" + text.trim().replace(Regex("\\s+"), " ")
        val t = now()
        val iterator = seen.entries.iterator()
        while (iterator.hasNext()) {
            if (t - iterator.next().value > windowMillis) iterator.remove()
        }
        val previous = seen[key]
        if (previous != null && t - previous <= windowMillis) return false
        seen[key] = t
        return true
    }
}
