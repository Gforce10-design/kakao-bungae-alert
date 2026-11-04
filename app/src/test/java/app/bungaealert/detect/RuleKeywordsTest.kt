package app.bungaealert.detect

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleKeywordsTest {
    private val phrase = MatchKeyword("phrase", "오늘 저녁에 봐요")
    private val hidden = MatchKeyword("hidden", "급모집", enabled = false)

    @Test
    fun unsavedRuleUsesEnabledStoreKeywordsIncludingPhrases() {
        val keywords = KeywordCodec.defaults() + phrase + hidden
        val meetupOnly = WatchRule("1", "수다방", MatchMode.CONTAINS, true, false, AlertMode.ONCE)
        val used = RuleKeywords.applicable(meetupOnly, keywords)

        assertEquals(listOf("번개", "오늘 저녁에 봐요"), used.map { it.text })
        assertTrue(KeywordMatcher.matchesAny(used, "수다방", "오늘 저녁에 봐요"))
        assertTrue(KeywordMatcher.matchesAny(used, "주말 번개", "모집"))
        assertFalse(KeywordMatcher.matchesAny(used, "나눔방", "선착순 3명"))
        assertFalse(KeywordMatcher.matchesAny(used, "방", "급모집"))
    }

    @Test
    fun explicitSelectionKeepsThePhraseAndDropsADisabledKeyword() {
        val renamed = MatchKeyword("default-bungae", "주말 번개", enabled = false)
        val keywords = listOf(renamed, phrase, MatchKeyword("default-giveaway", "선착순"))
        val rule = WatchRule(
            id = "1",
            pattern = "방",
            matchMode = MatchMode.CONTAINS,
            detectMeetup = true,
            detectGiveaway = true,
            alertMode = AlertMode.ONCE,
            keywordIds = setOf("default-bungae", "phrase"),
            keywordsSpecified = true,
        )
        val used = RuleKeywords.applicable(rule, keywords)

        assertEquals(listOf("오늘 저녁에 봐요"), used.map { it.text })
        assertEquals(
            setOf(PostType.MEETUP, PostType.GIVEAWAY),
            RuleKeywords.postTypes(used),
        )
        assertTrue(KeywordMatcher.matchesAny(used, "방", "오늘   저녁에 봐요"))
        assertFalse(KeywordMatcher.matchesAny(used, "방", "주말 번개"))
    }

    @Test
    fun savingARuleKeepsChosenPhrasesAndDropsTheOnesTurnedOff() {
        val keywords = KeywordCodec.defaults() + MatchKeyword("phrase", "오늘 저녁에 봐요")
        val chosen = keywords.filter { it.id != "phrase" }
        val saved = RuleKeywords.withSelection(
            rule = WatchRule("new", "수다방", MatchMode.CONTAINS, true, true, AlertMode.ONCE),
            pattern = "수다방",
            matchMode = MatchMode.CONTAINS,
            alertMode = AlertMode.ONCE,
            enabled = true,
            selected = chosen,
        )
        assertTrue(saved.keywordsSpecified)
        assertEquals(setOf("default-bungae", "default-giveaway"), saved.keywordIds)
        assertEquals(setOf(PostType.MEETUP, PostType.GIVEAWAY), saved.types)
        assertEquals(listOf("번개", "선착순"), RuleKeywords.applicable(saved, keywords).map { it.text })
    }
}
