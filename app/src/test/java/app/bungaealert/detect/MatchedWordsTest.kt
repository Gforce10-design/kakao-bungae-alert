package app.bungaealert.detect

import androidx.test.core.app.ApplicationProvider
import app.bungaealert.data.DetectionLogRepository
import app.bungaealert.data.DetectionRecord
import app.bungaealert.ui.groupDetections
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class MatchedWordsTest {
    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()

    private val keywords = listOf(
        MatchKeyword("default-bungae", "번개"),
        MatchKeyword("default-giveaway", "선착순"),
        MatchKeyword("k-offline", "오프라인"),
        MatchKeyword("k-meet", "모임"),
    )

    @Test
    fun listsKeywordsInOrderOfAppearanceNotKeywordOrder() {
        val words = MatchedWords.find(keywords, "오늘 저녁 7시 강남역 번개 모임 선착순 5명")
        assertEquals(listOf("번개", "모임", "선착순"), words)
        val reversed = MatchedWords.find(keywords, "선착순 5명 모임 번개")
        assertEquals(listOf("선착순", "모임", "번개"), reversed)
    }

    @Test
    fun dedupesRepeatedAndDuplicateKeywords() {
        val dup = keywords + MatchKeyword("again", " 번개 ")
        assertEquals(listOf("번개", "모임"), MatchedWords.find(dup, "번개 번개 모임 번개"))
    }

    @Test
    fun ignoresDisabledAbsentAndBlankKeywordsAndText() {
        val list = listOf(
            MatchKeyword("a", "번개", enabled = false),
            MatchKeyword("b", "오프라인"),
            MatchKeyword("c", "   "),
        )
        assertEquals(emptyList<String>(), MatchedWords.find(list, "오늘 번개 모임"))
        assertEquals(emptyList<String>(), MatchedWords.find(keywords, ""))
        assertEquals(emptyList<String>(), MatchedWords.find(keywords, null))
    }

    @Test
    fun matchesPhrasesWithCollapsedWhitespaceAndCase() {
        val list = listOf(MatchKeyword("p", "선착순  나눔"), MatchKeyword("q", "Offline"))
        assertEquals(listOf("선착순 나눔", "Offline"), MatchedWords.find(list, "선착순\n나눔 해요 offline 모임"))
    }

    @Test
    fun onlyKeywordsApplicableToTheRuleAreListed() {
        val rule = WatchRule(
            id = "r", pattern = "독서모임", matchMode = MatchMode.CONTAINS,
            detectMeetup = true, detectGiveaway = false, alertMode = AlertMode.ONCE,
            keywordsSpecified = true, keywordIds = setOf("default-bungae", "k-meet"),
        )
        val applicable = RuleKeywords.applicable(rule, keywords)
        assertEquals(
            listOf("번개", "모임"),
            MatchedWords.find(applicable, "번개 모임 선착순 오프라인"),
        )
    }

    @Test
    fun alertTextShowsLabelThenMessage() {
        val words = listOf("번개", "모임", "선착순")
        assertEquals("잡힌 단어: 번개, 모임, 선착순", MatchedWords.label(words))
        assertEquals("잡힌 단어: 번개, 모임, 선착순 · 오늘 번개", MatchedWords.collapsed(words, "오늘 번개"))
        assertEquals("잡힌 단어: 번개, 모임, 선착순\n오늘 번개", MatchedWords.expanded(words, "오늘 번개"))
        assertEquals("", MatchedWords.label(emptyList()))
        assertEquals("오늘 번개", MatchedWords.collapsed(emptyList(), "오늘 번개"))
        assertEquals("오늘 번개", MatchedWords.expanded(emptyList(), "오늘 번개"))
    }

    @Test
    fun recordStoresWordsAndOldRecordsStillLoad() {
        val file = File(context.filesDir, "detections.json").also { it.delete() }
        // A record written by the previous version has no matchedWords field.
        file.writeText(
            """[{"id":"old","atMillis":1000,"chatName":"독서모임","sender":"","text":"번개해요","score":70,
            |"meetupScore":70,"giveawayScore":0,"alerted":true,"alertType":"meetup","summary":""}]""".trimMargin(),
        )
        val repo = DetectionLogRepository(context)
        assertEquals(1, repo.state.value.size)
        assertEquals(emptyList<String>(), repo.state.value[0].matchedWords)

        repo.add(
            DetectionRecord(
                id = "new", atMillis = 2000, chatName = "독서모임", sender = null,
                text = "번개 모임 선착순", score = 88, meetupScore = 88, giveawayScore = 30,
                alerted = true, alertType = "meetup", summary = "",
                matchedWords = listOf("번개", "모임", "선착순"),
            ),
        )
        val reloaded = DetectionLogRepository(context).state.value
        assertEquals(listOf("번개", "모임", "선착순"), reloaded.first { it.id == "new" }.matchedWords)
        assertTrue(reloaded.first { it.id == "old" }.matchedWords.isEmpty())
    }

    @Test
    fun historyCardsCarryMatchedWords() {
        val record = DetectionRecord(
            id = "x", atMillis = 1_000, chatName = "독서모임", sender = null, text = "번개 모임",
            score = 80, meetupScore = 80, giveawayScore = 0, alerted = true, alertType = "meetup",
            summary = "", matchedWords = listOf("번개", "모임"),
        )
        val cards = groupDetections(listOf(record), nowMillis = 1_000).flatMap { it.items }
        assertEquals(listOf("번개", "모임"), cards[0].matchedWords)
    }
}
