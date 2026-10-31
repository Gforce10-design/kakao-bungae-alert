package app.bungaealert.detect

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScoreClassifierTest {
    @Test
    fun meetupRecruitmentScoresHigh() {
        val samples = listOf(
            "오늘 저녁 7시 강남역 번개 하실 분! 선착순 4명 모집합니다. 참석 가능하신 분 댓글 부탁드려요",
            "내일 오후 2시 홍대 카페에서 번개모임 해요~ 3명 더 구합니다",
            "이번 주 토요일 11시 잠실 롯데월드 번개! 선착순 6명",
            "[번개] 10/31(토) 19:00 성수 장소: ○○카페 4명 모집",
            "갑자기 오늘 번개 뜰 사람? 선착순 3명 저녁 8시 신촌",
        )
        samples.forEach { text ->
            val score = ScoreClassifier.score(text)
            assertTrue("$text -> $score", score.meetup >= 70)
            assertTrue(text, score.meetup > score.giveaway)
            assertFalse(score.mediaOrEmpty)
        }
    }

    @Test
    fun giveawayScoresHigh() {
        val samples = listOf(
            "스타벅스 아메리카노 기프티콘 선착순 3분께 드려요! 댓글 달아주세요",
            "편의점 상품권 나눔합니다 선착순 5명",
            "배민 쿠폰 선착순 10분께 드립니다 원하는 분 댓글!",
            "기프티콘 드려요~ 선착순 2분 댓글 선착",
        )
        samples.forEach { text ->
            val score = ScoreClassifier.score(text)
            assertTrue("$text meetup=${score.meetup} give=${score.giveaway} ${score.reasons}", score.giveaway >= 70)
            assertTrue(text, score.giveaway > score.meetup)
        }
    }

    @Test
    fun casualAndRepliesStayLow() {
        val samples = listOf(
            "저요!",
            "나도 갈게요 ㅋㅋ",
            "어제 번개 재밌었어",
            "오늘 날씨 좋다",
            "번개 언제였지? 지난주에 했던 거",
            "기프티콘 받은 사람 있어?",
            "선착순으로 자리 맡았어? 지난번 모임",
            "다음에 번개 하자",
            "몇 명 왔어?",
            "참석 못해서 아쉬웠어 어제",
            "오늘 시간 되는 사람?",
            "선착순이야?",
            "기프티콘 뭐가 좋아?",
            "ㅋㅋㅋㅋㅋ",
            "ㅎㅎㅎ",
        )
        samples.forEach { text ->
            val score = ScoreClassifier.score(text)
            assertTrue("$text -> meetup=${score.meetup} give=${score.giveaway} ${score.reasons}", score.meetup < 40)
            assertTrue(text, score.giveaway < 40)
        }
    }

    @Test
    fun mediaAndEmptyDoNotScore() {
        listOf("", "   ", "사진", "이모티콘", "동영상", "(이모티콘)", "사진을 보냈습니다", "삭제된 메시지입니다").forEach { text ->
            val score = ScoreClassifier.score(text)
            assertTrue(text, score.mediaOrEmpty)
            assertEquals(0, score.meetup)
            assertEquals(0, score.giveaway)
        }
    }

    @Test
    fun timeInsideBodyIsNotAFalseSenderSplit() {
        val (sender, body) = KakaoNoticeParser.splitSender("오늘 7:30 강남역에서 번개해요")
        assertEquals(null, sender)
        assertTrue(body.contains("7:30"))
    }
}
