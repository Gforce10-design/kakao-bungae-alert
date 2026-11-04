package app.bungaealert.detect

import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.UUID

enum class KeywordKind { MEETUP, GIVEAWAY, CUSTOM }

/**
 * Which stored keywords a chat rule actually uses. The rule editor and the
 * notification pipeline both call this, so a phrase added in 찾을 말 shows up
 * as a choice and is what matching looks for.
 */
object RuleKeywords {
    const val MEETUP_ID = "default-bungae"
    const val GIVEAWAY_ID = "default-giveaway"

    fun kind(keyword: MatchKeyword): KeywordKind {
        val text = KeywordMatcher.normalize(keyword.text)
        return when {
            keyword.id == MEETUP_ID || text == "번개" || text == "번개모임" -> KeywordKind.MEETUP
            keyword.id == GIVEAWAY_ID || text == "선착순" || text == "선착순 나눔" -> KeywordKind.GIVEAWAY
            else -> KeywordKind.CUSTOM
        }
    }

    fun applicable(rule: WatchRule, keywords: List<MatchKeyword>): List<MatchKeyword> =
        keywords.filter { applies(rule, it) }

    fun applies(rule: WatchRule, keyword: MatchKeyword): Boolean {
        if (!keyword.enabled) return false
        if (KeywordMatcher.canonicalize(keyword.text).isEmpty()) return false
        if (rule.keywordsSpecified) return keyword.id in rule.keywordIds
        val watchesSomething = rule.detectMeetup || rule.detectGiveaway
        return when (kind(keyword)) {
            KeywordKind.MEETUP -> rule.detectMeetup
            KeywordKind.GIVEAWAY -> rule.detectGiveaway
            KeywordKind.CUSTOM -> watchesSomething
        }
    }

    fun withSelection(
        rule: WatchRule,
        pattern: String,
        matchMode: MatchMode,
        alertMode: AlertMode,
        enabled: Boolean,
        selected: List<MatchKeyword>,
    ): WatchRule {
        val types = postTypes(selected)
        return rule.copy(
            pattern = pattern,
            matchMode = matchMode,
            detectMeetup = PostType.MEETUP in types,
            detectGiveaway = PostType.GIVEAWAY in types,
            alertMode = alertMode,
            enabled = enabled,
            keywordIds = selected.map { it.id }.toSet(),
            keywordsSpecified = true,
        )
    }

    fun postTypes(keywords: List<MatchKeyword>): Set<PostType> = buildSet {
        for (keyword in keywords) {
            when (kind(keyword)) {
                KeywordKind.MEETUP -> add(PostType.MEETUP)
                KeywordKind.GIVEAWAY -> add(PostType.GIVEAWAY)
                KeywordKind.CUSTOM -> {
                    add(PostType.MEETUP)
                    add(PostType.GIVEAWAY)
                }
            }
        }
    }
}

data class MatchKeyword(
    val id: String,
    val text: String,
    val enabled: Boolean = true,
)

/**
 * Substring match of user words and phrases against a notification title and
 * body. Whitespace is collapsed on both sides, so a phrase matches only as
 * that whole phrase. Disabled keywords never match.
 */
object KeywordMatcher {
    private val whitespace = Regex("[\\p{Z}\\s]+")

    fun canonicalize(raw: String): String = raw.trim().replace(whitespace, " ")

    fun normalize(raw: String): String = canonicalize(raw).lowercase(Locale.ROOT)

    fun matches(keyword: MatchKeyword, title: String?, text: String?): Boolean {
        if (!keyword.enabled) return false
        val needle = normalize(keyword.text)
        if (needle.isEmpty()) return false
        val haystack = normalize(combine(title, text))
        if (haystack.isEmpty()) return false
        return haystack.contains(needle)
    }

    fun matchesAny(keywords: List<MatchKeyword>, title: String?, text: String?): Boolean =
        keywords.any { matches(it, title, text) }

    fun notificationText(raw: RawKakaoNotice): String = buildList {
        raw.text?.let(::add)
        raw.bigText?.let(::add)
        addAll(raw.textLines)
        raw.messages.forEach { add(it.text) }
    }.joinToString("\n")

    fun validationError(
        text: String,
        existing: List<MatchKeyword>,
        editingId: String? = null,
    ): String? {
        if (canonicalize(text).isEmpty()) return "내용을 입력하세요"
        val key = normalize(text)
        val duplicate = existing.any { it.id != editingId && normalize(it.text) == key }
        if (duplicate) return "같은 말이 이미 있습니다"
        return null
    }

    private fun combine(title: String?, text: String?): String =
        listOfNotNull(title, text).joinToString("\n")
}

object KeywordCodec {
    fun defaults(): List<MatchKeyword> = listOf(
        MatchKeyword(id = "default-bungae", text = "번개", enabled = true),
        MatchKeyword(id = "default-giveaway", text = "선착순", enabled = true),
    )

    /**
     * Missing storage (first launch and upgrades from before keywords existed)
     * seeds 번개 and 선착순. A stored empty list stays empty. Corrupt JSON falls
     * back to the same defaults.
     */
    fun load(raw: String?): List<MatchKeyword> {
        if (raw == null) return defaults()
        return try {
            parse(raw)
        } catch (_: Exception) {
            defaults()
        }
    }

    fun isCorrupt(raw: String?): Boolean {
        if (raw == null) return false
        return try {
            parse(raw)
            false
        } catch (_: Exception) {
            true
        }
    }

    fun parse(raw: String): List<MatchKeyword> {
        val array = JSONArray(raw)
        return buildList {
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val text = KeywordMatcher.canonicalize(obj.optString("text"))
                if (text.isEmpty()) continue
                add(
                    MatchKeyword(
                        id = obj.optString("id").ifBlank { UUID.randomUUID().toString() },
                        text = text,
                        enabled = obj.optBoolean("enabled", true),
                    ),
                )
            }
        }
    }

    fun toJson(keywords: List<MatchKeyword>): String {
        val array = JSONArray()
        keywords.forEach { keyword ->
            array.put(
                JSONObject().apply {
                    put("id", keyword.id)
                    put("text", keyword.text)
                    put("enabled", keyword.enabled)
                },
            )
        }
        return array.toString()
    }
}
