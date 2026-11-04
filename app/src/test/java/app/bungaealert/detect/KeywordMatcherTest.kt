package app.bungaealert.detect

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KeywordMatcherTest {
    @Test
    fun wordMatchesTitleOrText() {
        val keyword = MatchKeyword("1", "번개")
        assertTrue(KeywordMatcher.matches(keyword, "주말 번개", "오늘 모집"))
        assertTrue(KeywordMatcher.matches(keyword, "수다방", "강남역 번개모임 해요"))
        assertFalse(KeywordMatcher.matches(keyword, "수다방", "내일 카페에서 만나요"))
        assertTrue(KeywordMatcher.matches(MatchKeyword("2", "선착순"), "나눔방", "선착순 3명"))
        assertFalse(KeywordMatcher.matches(MatchKeyword("3", "급모집"), "나눔방", "선착순 3명"))
    }

    @Test
    fun phraseMatchesAsAWholePhrase() {
        val phrase = MatchKeyword("1", "선착순 나눔")
        assertTrue(KeywordMatcher.matches(phrase, "나눔방", "스타벅스 선착순 나눔 합니다"))
        assertFalse(KeywordMatcher.matches(phrase, "나눔방", "선착순으로 상품 나눔"))
        assertFalse(KeywordMatcher.matches(phrase, "선착순", "따로 적힌 나눔"))
        assertTrue(
            KeywordMatcher.matches(
                MatchKeyword("2", "오늘 저녁에 봐요"),
                "방",
                "다들 오늘 저녁에 봐요. 늦지 마세요",
            ),
        )
    }

    @Test
    fun caseInsensitive() {
        val keyword = MatchKeyword("1", "Flash")
        assertTrue(KeywordMatcher.matches(keyword, "FLASH SALE", null))
        assertTrue(KeywordMatcher.matches(keyword, "방", "flash sale tonight"))
        assertFalse(KeywordMatcher.matches(keyword, "방", "sale tonight"))
    }

    @Test
    fun spacingIsNormalized() {
        val phrase = MatchKeyword("1", "선착순   나눔")
        assertTrue(KeywordMatcher.matches(phrase, "방", "선착순 나눔"))
        assertTrue(KeywordMatcher.matches(phrase, "선착순\t나눔", null))
        assertTrue(KeywordMatcher.matches(phrase, "방", "선착순\n나눔 시작"))
        assertTrue(KeywordMatcher.matches(phrase, "방", "선착순\u3000나눔"))
        assertTrue(KeywordMatcher.matches(MatchKeyword("2", "오늘 번개"), "오늘   번개", "   "))
        val joined = KeywordMatcher.notificationText(
            RawKakaoNotice(
                packageName = "com.kakao.talk",
                title = "수다방",
                text = "선착순",
                bigText = "나눔 합니다",
                subText = null,
                conversationTitle = null,
            ),
        )
        assertTrue(KeywordMatcher.matches(MatchKeyword("3", "선착순 나눔"), "수다방", joined))
    }

    @Test
    fun disabledKeywordDoesNotMatch() {
        val keywords = listOf(
            MatchKeyword("1", "번개", enabled = false),
            MatchKeyword("2", "선착순", enabled = true),
        )
        assertFalse(KeywordMatcher.matches(keywords[0], "방", "오늘 번개"))
        assertFalse(KeywordMatcher.matchesAny(keywords, "방", "오늘 번개 해요"))
        assertTrue(KeywordMatcher.matchesAny(keywords, "방", "선착순 3명"))
        assertFalse(KeywordMatcher.matchesAny(emptyList(), "방", "번개"))
        assertFalse(KeywordMatcher.matches(MatchKeyword("3", "   ", enabled = true), "방", "번개"))
    }

    @Test
    fun rejectsEmptyAndDuplicateEntries() {
        val existing = listOf(
            MatchKeyword("1", "번개"),
            MatchKeyword("2", "선착순 나눔", enabled = false),
        )
        assertEquals("내용을 입력하세요", KeywordMatcher.validationError("   ", existing))
        assertEquals("내용을 입력하세요", KeywordMatcher.validationError("\n\t", existing))
        assertEquals("같은 말이 이미 있습니다", KeywordMatcher.validationError("  번개 ", existing))
        assertEquals("같은 말이 이미 있습니다", KeywordMatcher.validationError("선착순   나눔", existing))
        assertEquals("같은 말이 이미 있습니다", KeywordMatcher.validationError("FLASH", listOf(MatchKeyword("9", "flash"))))
        assertNull(KeywordMatcher.validationError("번개", existing, editingId = "1"))
        assertNull(KeywordMatcher.validationError("기프티콘", existing))
        assertNull(KeywordMatcher.validationError("오늘 저녁에 봐요", existing))
    }

    @Test
    fun missingStoreSeedsDefaultsAndKeepsExistingLists() {
        val seeded = KeywordCodec.load(null)
        assertEquals(listOf("번개", "선착순"), seeded.map { it.text })
        assertTrue(seeded.all { it.enabled })

        assertTrue(KeywordCodec.load("[]").isEmpty())
        assertEquals(listOf("번개", "선착순"), KeywordCodec.load("{").map { it.text })
        assertTrue(KeywordCodec.isCorrupt("{"))
        assertFalse(KeywordCodec.isCorrupt("[]"))
        assertFalse(KeywordCodec.isCorrupt(null))

        val stored = KeywordCodec.toJson(
            listOf(MatchKeyword("custom", "오늘 저녁에 봐요", enabled = false)),
        )
        val loaded = KeywordCodec.load(stored)
        assertEquals("custom", loaded.single().id)
        assertEquals("오늘 저녁에 봐요", loaded.single().text)
        assertFalse(loaded.single().enabled)
    }
}
