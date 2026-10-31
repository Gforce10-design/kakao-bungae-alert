package app.bungaealert.detect

/**
 * On-device meaning score for two KakaoTalk post types.
 * Weights are intentionally explicit so unit tests can name the reason a
 * message did or did not clear the bar.
 */
object ScoreClassifier {
    private val mediaExact = setOf(
        "사진", "동영상", "영상", "이모티콘", "이모지", "음성메시지", "음성 메시지",
        "파일", "gif", "GIF", "삭제된 메시지", "삭제된 메시지입니다",
        "사진을 보냈습니다", "동영상을 보냈습니다", "이모티콘을 보냈습니다",
        "음성메시지를 보냈습니다", "파일을 보냈습니다", "(이모티콘)", "(사진)", "(동영상)",
        "사진 1장", "동영상 1개",
    )

    private val replyOnly = Regex(
        "^(저요|저도요|저도|나요|나도요|나도|갈게요|갈게|갑니다|참여합니다|참여요|참여|신청합니다|신청|넵|네|응|ㅇㅇ|ㄱㄱ|좋아요|감사합니다|감사|올림|하이)+$",
    )

    private val past = Regex("어제|그저께|지난번|지난\\s*주|저번|했었|였었|갔었|갔다\\s*왔|재미있었|재밌었|아쉬웠|했던")
    private val pastQuestion = Regex("언제였|뭐였|뭐가|누구|받았|있었어|몇\\s*명|했어\\?|있어\\?")
    private val date = Regex("오늘|내일|모레|글피|이번\\s*주|다음\\s*주|\\d{1,2}\\s*월\\s*\\d{1,2}\\s*일|\\d{1,2}/\\d{1,2}|[월화수목금토일]요일")
    private val time = Regex("오전|오후|저녁|점심|아침|\\d{1,2}\\s*시|\\d{1,2}:\\d{2}")
    private val placeWord = Regex("장소|카페|집합|코엑스|홍대|강남|잠실|성수|신촌|판교|여의도|건대|롯데월드")
    private val station = Regex("([가-힣]{2,6})역(?!사)")
    private val notStation = setOf("번역", "지역", "영역", "세력", "폭력")
    private val headcount = Regex("(?:선착순\\s*)?\\d{1,2}\\s*명")
    private val peopleReceiving = Regex("\\d{1,3}\\s*분께")
    private val recruitPhrase = Regex("모집|구합니다|구함|모십니다")
    private val attendPhrase = Regex("참석\\s*가능|가능하신\\s*분|가능하신분|오실\\s*분|오시는\\s*분|하실\\s*분|계실\\s*분|계신\\s*분")
    private val giftWord = Regex("기프티콘|기프트콘|기프티")
    private val giveVerb = Regex("드려요|드립니다|나눔|나눠|선물합니다|선물해|쏠게|쏘겠|쏩니다")
    private val goods = Regex("상품권|쿠폰|아메리카노")

    fun score(raw: String): ScoreBreakdown {
        val text = raw.trim().replace(Regex("\\s+"), " ")
        if (text.isEmpty()) {
            return ScoreBreakdown(0, 0, listOf("빈 메시지"), true)
        }
        val bare = text.trim('(', ')', '[', ']', ' ', '.', '…')
        if (text in mediaExact || bare in mediaExact) {
            return ScoreBreakdown(0, 0, listOf("사진·이모티콘·파일 등 본문 없음"), true)
        }
        if (isLaughOnly(text)) {
            return ScoreBreakdown(0, 0, listOf("웃음만 있는 메시지"), true)
        }

        val meetupHits = mutableListOf<Pair<String, Int>>()
        val giveHits = mutableListOf<Pair<String, Int>>()

        when {
            Regex("번개모임|번개 모임").containsMatchIn(text) -> meetupHits += "번개모임" to 36
            Regex("번개").containsMatchIn(text) && !text.contains("번개장터") -> meetupHits += "번개" to 28
        }
        if (recruitPhrase.containsMatchIn(text)) meetupHits += "모집 표현" to 16
        if (attendPhrase.containsMatchIn(text)) {
            meetupHits += "참석 권유" to 18
        } else if (text.contains("참석")) {
            meetupHits += "참석" to 8
        }
        if (headcount.containsMatchIn(text)) meetupHits += "인원" to 16
        if (date.containsMatchIn(text)) meetupHits += "날짜" to 12
        if (time.containsMatchIn(text)) meetupHits += "시간" to 12
        if (hasPlace(text)) meetupHits += "장소" to 12

        if (giftWord.containsMatchIn(text)) giveHits += "기프티콘" to 38
        if (giveVerb.containsMatchIn(text)) giveHits += "나눔·증정" to 20
        if (text.contains("선착순")) giveHits += "선착순" to 18
        if (peopleReceiving.containsMatchIn(text)) giveHits += "n분께" to 18
        if (goods.containsMatchIn(text)) giveHits += "상품·쿠폰" to 8

        val giveAnchor = giveHits.any { it.first == "기프티콘" || it.first == "나눔·증정" || it.first == "n분께" }
        if (text.contains("선착순") && giveAnchor) giveHits += "선착순 나눔" to 12
        if (headcount.containsMatchIn(text) && (giveAnchor || text.contains("선착순"))) {
            giveHits += "나눔 인원" to 14
        }
        if (text.contains("댓글")) {
            if (giveHits.isNotEmpty()) giveHits += "댓글 유도" to 6
            if (meetupHits.isNotEmpty()) meetupHits += "댓글 유도" to 4
        }

        var penalty = 0
        val negatives = mutableListOf<String>()
        if (isReplyOnly(text)) {
            negatives += "짧은 참여 대답"
        }
        if (past.containsMatchIn(text)) {
            penalty += 26
            negatives += "지난 일정·회고"
        }
        val strongIntent = meetupHits.any { it.first in setOf("모집 표현", "참석 권유", "인원") } ||
            (meetupHits.any { it.first == "번개" || it.first == "번개모임" } &&
                meetupHits.any { it.first == "날짜" || it.first == "시간" || it.first == "장소" }) ||
            giveAnchor
        val questioning = text.contains('?') || text.trimEnd().let { it.endsWith("까") || it.endsWith("나요") || it.endsWith("가요") }
        if (pastQuestion.containsMatchIn(text)) {
            penalty += 20
            negatives += "확인 질문"
        } else if (questioning && !strongIntent) {
            penalty += 16
            negatives += "질문"
        }
        if (Regex("다음에|나중에|언젠가").containsMatchIn(text) && !date.containsMatchIn(text)) {
            penalty += 14
            negatives += "막연한 다음"
        }

        var meetup = (meetupHits.sumOf { it.second } - penalty).coerceIn(0, 100)
        var giveaway = (giveHits.sumOf { it.second } - penalty).coerceIn(0, 100)
        if (isReplyOnly(text)) {
            meetup = meetup.coerceAtMost(6)
            giveaway = giveaway.coerceAtMost(6)
        }

        val reasons = buildList {
            meetupHits.forEach { add("모임 +${it.second} ${it.first}") }
            giveHits.forEach { add("나눔 +${it.second} ${it.first}") }
            if (penalty > 0) add("감점 -$penalty")
            negatives.forEach { add("참고: $it") }
            if (isEmpty()) add("해당하는 신호 없음")
        }
        return ScoreBreakdown(meetup, giveaway, reasons.distinct(), false)
    }

    private fun hasPlace(text: String): Boolean {
        if (placeWord.containsMatchIn(text)) return true
        return station.findAll(text).any { it.groupValues[1] !in notStation }
    }

    private fun isLaughOnly(text: String): Boolean {
        val core = text.replace(Regex("[\\s!.~^…]+"), "")
        return core.isNotEmpty() && core.all { it == 'ㅋ' || it == 'ㅎ' || it == 'ㅠ' || it == 'ㅜ' }
    }

    private fun isReplyOnly(text: String): Boolean {
        val stripped = text.replace(Regex("[\\sㅋㅎ!.~?^…]+"), "")
        if (stripped.isEmpty() || stripped.length > 16) return false
        return replyOnly.matches(stripped)
    }
}
