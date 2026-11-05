package app.bungaealert.detect

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KakaoAndPolicyTest {
    @Test
    fun mutedImportanceIsStillRead() {
        listOf(null, 0, 1, 2, 3, 4, 5).forEach { importance ->
            assertTrue(
                KakaoPackages.shouldRead(KakaoPackages.KAKAO_TALK, importance),
            )
        }
        assertFalse(KakaoPackages.shouldRead("com.android.systemui", 4))
    }

    @Test
    fun messagingStyleUsesConversationTitle() {
        val parsed = KakaoNoticeParser.parse(
            RawKakaoNotice(
                packageName = KakaoPackages.KAKAO_TALK,
                title = "김민수",
                text = "오늘 저녁 7시 강남역 번개 하실 분",
                bigText = null,
                subText = null,
                conversationTitle = "10월의 수다방",
                messages = listOf(RawKakaoMessage("김민수", "오늘 저녁 7시 강남역 번개 하실 분")),
                importance = 1,
            ),
        )
        assertEquals(1, parsed.size)
        assertEquals("10월의 수다방", parsed[0].chatName)
        assertEquals("김민수", parsed[0].sender)
        assertTrue(parsed[0].text.contains("번개"))
    }

    private fun groupNotice(roomName: String?, conversationTitle: String? = null, subText: String? = null) =
        RawKakaoNotice(
            packageName = KakaoPackages.KAKAO_TALK,
            title = "김민수",
            text = "오늘 저녁 번개 하실 분",
            bigText = null,
            subText = subText,
            conversationTitle = conversationTitle,
            messages = listOf(RawKakaoMessage("김민수", "오늘 저녁 번개 하실 분")),
            importance = 3,
            roomName = roomName,
        )

    @Test
    fun shortcutRoomNameBeatsSenderTitleAndMatchesRule() {
        val parsed = KakaoNoticeParser.parse(groupNotice(roomName = "독서모임 (12)"))
        assertEquals(1, parsed.size)
        assertEquals("독서모임 (12)", parsed[0].chatName)
        assertEquals("김민수", parsed[0].sender)
        assertTrue(ChatMatcher.matches(MatchMode.CONTAINS, "독서모임", parsed[0].chatName))
    }

    @Test
    fun shortcutRoomNameTakesPrecedenceOverConversationTitleAndSubText() {
        val parsed = KakaoNoticeParser.parse(
            groupNotice(roomName = "독서모임", conversationTitle = "다른방", subText = "부제"),
        )
        assertEquals("독서모임", parsed[0].chatName)
    }

    @Test
    fun withoutRoomNameFallsBackToExistingOrder() {
        assertEquals("김민수", KakaoNoticeParser.parse(groupNotice(roomName = null))[0].chatName)
        assertEquals("김민수", KakaoNoticeParser.parse(groupNotice(roomName = "  "))[0].chatName)
        assertEquals("다른방", KakaoNoticeParser.parse(groupNotice(null, conversationTitle = "다른방"))[0].chatName)
        assertEquals("부제", KakaoNoticeParser.parse(groupNotice(null, subText = "부제"))[0].chatName)
    }

    @Test
    fun senderTitleAloneDoesNotMatchRoomRule() {
        val parsed = KakaoNoticeParser.parse(groupNotice(roomName = null))
        assertFalse(ChatMatcher.matches(MatchMode.CONTAINS, "독서모임", parsed[0].chatName))
    }

    @Test
    fun summaryTextLinesSplitSendersAndSkipCounts() {
        val parsed = KakaoNoticeParser.parse(
            RawKakaoNotice(
                packageName = KakaoPackages.KAKAO_TALK,
                title = "경제적 자유를 위하여~",
                text = "2개의 새 메시지",
                bigText = null,
                subText = null,
                conversationTitle = null,
                textLines = listOf(
                    "2개의 새 메시지",
                    "이서연 : 스타벅스 기프티콘 선착순 2분께 드려요",
                    "박하늘: 저요",
                ),
                isGroupSummary = true,
                importance = 2,
            ),
        )
        assertEquals(2, parsed.size)
        assertEquals("경제적 자유를 위하여~", parsed[0].chatName)
        assertEquals("이서연", parsed[0].sender)
        assertTrue(parsed[0].text.contains("기프티콘"))
        assertEquals("저요", parsed[1].text)
    }

    @Test
    fun emptyAndMissingExtrasDoNotCrash() {
        val parsed = KakaoNoticeParser.parse(
            RawKakaoNotice(
                packageName = KakaoPackages.KAKAO_TALK,
                title = "수다방",
                text = null,
                bigText = null,
                subText = null,
                conversationTitle = null,
                importance = 0,
            ),
        )
        assertEquals(1, parsed.size)
        assertEquals("", parsed[0].text)
        assertEquals(
            emptyList<ParsedChatMessage>(),
            KakaoNoticeParser.parse(
                RawKakaoNotice("com.kakao.talk", null, null, null, null, null),
            ),
        )
    }

    @Test
    fun appTitleSummaryWithoutRoomIsDropped() {
        val parsed = KakaoNoticeParser.parse(
            RawKakaoNotice(
                packageName = KakaoPackages.KAKAO_TALK,
                title = "카카오톡",
                text = "사진",
                bigText = null,
                subText = null,
                conversationTitle = null,
            ),
        )
        assertTrue(parsed.isEmpty())
    }

    @Test
    fun chatNameModes() {
        assertTrue(ChatMatcher.matches(MatchMode.ENDS_WITH, "수다방", "10월의 수다방"))
        assertFalse(ChatMatcher.matches(MatchMode.ENDS_WITH, "수다방", "수다방 공지"))
        assertTrue(ChatMatcher.matches(MatchMode.EXACT, "경제적 자유를 위하여~", "경제적 자유를 위하여~"))
        assertFalse(ChatMatcher.matches(MatchMode.EXACT, "경제적 자유를 위하여~", "경제적 자유를 위하여~ 2"))
        assertTrue(ChatMatcher.matches(MatchMode.CONTAINS, "자유", "경제적 자유를 위하여~"))
        assertFalse(ChatMatcher.matches(MatchMode.CONTAINS, "  ", "수다방"))
    }

    @Test
    fun dedupeSuppressesTheSamePost() {
        var clock = 1_000L
        val window = DedupeWindow(windowMillis = 1_000L, now = { clock })
        assertTrue(window.shouldProcess("수다방", "오늘 번개"))
        assertFalse(window.shouldProcess("수다방", "오늘   번개"))
        clock += 2_000L
        assertTrue(window.shouldProcess("수다방", "오늘 번개"))
    }

    @Test
    fun clearScoreSkipsJudgesAndBorderlineFallsBack() = runBlocking {
        var calls = 0
        val judge = NamedJudge("fake", ready = { true }) {
            calls += 1
            null
        }
        val high = DecisionPolicy.evaluate(
            text = "내일 오후 2시 홍대 카페에서 번개모임 해요~ 3명 더 구합니다",
            enabled = setOf(PostType.MEETUP, PostType.GIVEAWAY),
            sensitivity = 40,
            band = 12,
            judges = listOf(judge),
            chatName = "수다방",
            sender = "김",
        )
        assertTrue(high.alert)
        assertEquals(PostType.MEETUP, high.alertType)
        assertEquals(ScoreZone.CLEAR_MATCH, high.zone)
        assertEquals(0, calls)

        val low = DecisionPolicy.evaluate(
            text = "오늘 날씨 좋다",
            enabled = setOf(PostType.MEETUP),
            sensitivity = 40,
            band = 12,
            judges = listOf(judge),
            chatName = "수다방",
            sender = null,
        )
        assertFalse(low.alert)
        assertEquals(ScoreZone.CLEAR_REJECT, low.zone)
        assertEquals(0, calls)
    }

    @Test
    fun borderlineUsesJudgeOrderThenScore() = runBlocking {
        val text = "오늘 저녁 번개 하실 분"
        val breakdown = ScoreClassifier.score(text)
        val cutoff = Sensitivity.cutoff(40)
        assertTrue(
            "score=${breakdown.meetup} cutoff=$cutoff ${breakdown.reasons}",
            breakdown.meetup in (cutoff - 12) until (cutoff + 12),
        )

        val failing = NamedJudge("down") { null }
        val accepting = NamedJudge("cloud") {
            JudgeVerdict(true, PostType.MEETUP, 0.8, "모집 맞음", "cloud")
        }
        val used = DecisionPolicy.evaluate(
            text = text,
            enabled = setOf(PostType.MEETUP),
            sensitivity = 40,
            band = 12,
            judges = listOf(failing, accepting),
            chatName = "수다방",
            sender = null,
        )
        assertEquals("cloud", used.verdict?.backendId)
        assertTrue(used.alert)

        val rejected = DecisionPolicy.evaluate(
            text = text,
            enabled = setOf(PostType.MEETUP),
            sensitivity = 40,
            band = 12,
            judges = listOf(NamedJudge("cloud") {
                JudgeVerdict(false, null, 0.2, "지난 얘기", "cloud")
            }),
            chatName = "수다방",
            sender = null,
        )
        assertFalse(rejected.alert)

        val fallback = DecisionPolicy.evaluate(
            text = text,
            enabled = setOf(PostType.MEETUP),
            sensitivity = 40,
            band = 12,
            judges = listOf(NamedJudge("down") { throw IllegalStateException("offline") }),
            chatName = "수다방",
            sender = null,
        )
        assertNull(fallback.verdict)
        assertEquals(breakdown.meetup >= cutoff, fallback.alert)
    }

    @Test
    fun disabledTypeDoesNotAlert() = runBlocking {
        val eval = DecisionPolicy.evaluate(
            text = "스타벅스 아메리카노 기프티콘 선착순 3분께 드려요",
            enabled = setOf(PostType.MEETUP),
            sensitivity = 100,
            band = 0,
            judges = emptyList(),
            chatName = "수다방",
            sender = null,
        )
        assertFalse(eval.alert)
    }
}
