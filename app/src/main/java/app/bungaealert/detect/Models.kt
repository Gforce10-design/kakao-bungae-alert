package app.bungaealert.detect

enum class PostType(val wire: String, val label: String) {
    MEETUP("meetup", "번개모임"),
    GIVEAWAY("giveaway", "선착순 나눔"),
    ;

    companion object {
        fun fromWire(value: String?): PostType? = when (value?.trim()?.lowercase()) {
            "meetup", "번개", "번개모임" -> MEETUP
            "giveaway", "gift", "나눔", "선착순" -> GIVEAWAY
            else -> null
        }
    }
}

enum class MatchMode(val wire: String, val label: String) {
    EXACT("exact", "정확히 일치"),
    CONTAINS("contains", "포함"),
    ENDS_WITH("ends_with", "끝 일치"),
    ;

    companion object {
        fun fromWire(value: String?): MatchMode =
            entries.firstOrNull { it.wire == value } ?: CONTAINS
    }
}

enum class AlertMode(val wire: String, val label: String) {
    ONCE("once", "한 번만"),
    REPEAT("repeat", "확인할 때까지 반복"),
    ;

    companion object {
        fun fromWire(value: String?): AlertMode =
            entries.firstOrNull { it.wire == value } ?: ONCE
    }
}

data class WatchRule(
    val id: String,
    val pattern: String,
    val matchMode: MatchMode,
    val detectMeetup: Boolean,
    val detectGiveaway: Boolean,
    val alertMode: AlertMode,
    val enabled: Boolean = true,
) {
    val types: Set<PostType>
        get() = buildSet {
            if (detectMeetup) add(PostType.MEETUP)
            if (detectGiveaway) add(PostType.GIVEAWAY)
        }
}

data class ScoreBreakdown(
    val meetup: Int,
    val giveaway: Int,
    val reasons: List<String>,
    val mediaOrEmpty: Boolean,
) {
    fun scoreFor(type: PostType): Int = when (type) {
        PostType.MEETUP -> meetup
        PostType.GIVEAWAY -> giveaway
    }
}

data class JudgeRequest(
    val text: String,
    val chatName: String,
    val sender: String?,
    val enabledTypes: Set<PostType>,
    val score: Int,
    val reasons: List<String>,
)

data class JudgeVerdict(
    val isMatch: Boolean,
    val type: PostType?,
    val confidence: Double,
    val reason: String,
    val backendId: String,
    val requestId: String? = null,
)

enum class ScoreZone { CLEAR_MATCH, CLEAR_REJECT, BORDERLINE }

data class Evaluation(
    val breakdown: ScoreBreakdown,
    val relevantScore: Int,
    val relevantType: PostType?,
    val cutoff: Int,
    val zone: ScoreZone,
    val verdict: JudgeVerdict?,
    val alert: Boolean,
    val alertType: PostType?,
    val summary: String,
)

object Sensitivity {
    /** 0 is strict (cutoff 80). 100 is loose (cutoff 35). */
    fun cutoff(sensitivity: Int): Int {
        val s = sensitivity.coerceIn(0, 100)
        return 80 - (s * 45 / 100)
    }
}

object TopicGenerator {
    private const val ALPHABET = "abcdefghijklmnopqrstuvwxyz0123456789"

    fun random(random: java.util.Random = java.util.Random()): String {
        val body = buildString(18) {
            repeat(18) { append(ALPHABET[random.nextInt(ALPHABET.length)]) }
        }
        return "bungae-$body"
    }

    fun isSafeTopic(topic: String): Boolean =
        topic.matches(Regex("[A-Za-z0-9][A-Za-z0-9_-]{0,63}"))
}

object BackendIds {
    const val GEMINI_NANO = "gemini_nano"
    const val GEMINI_API = "gemini_api"
    const val OPENAI_API = "openai_api"
    const val CLAUDE_API = "claude_api"
    const val CHATGPT_SUB = "chatgpt_sub"
    const val CLAUDE_SUB = "claude_sub"
    const val COPILOT_SUB = "copilot_sub"
    const val GROK_WEBHOOK = "grok_webhook"

    val DEFAULT_ORDER = listOf(
        GEMINI_NANO,
        GEMINI_API,
        OPENAI_API,
        CLAUDE_API,
        CHATGPT_SUB,
        CLAUDE_SUB,
        COPILOT_SUB,
        GROK_WEBHOOK,
    )

    fun label(id: String): String = when (id) {
        GEMINI_NANO -> "Gemini Nano (기기 안)"
        GEMINI_API -> "Gemini API 키"
        OPENAI_API -> "OpenAI API 키"
        CLAUDE_API -> "Claude API 키"
        CHATGPT_SUB -> "ChatGPT·Codex 구독"
        CLAUDE_SUB -> "Claude 구독"
        COPILOT_SUB -> "GitHub Copilot 구독"
        GROK_WEBHOOK -> "Grok Bot 웹훅"
        else -> id
    }
}
