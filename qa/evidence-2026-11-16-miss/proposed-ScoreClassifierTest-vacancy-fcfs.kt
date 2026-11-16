    @Test
    fun vacancyFCFSSignupScoresAsMeetup() {
        // Pseudonymized S25 miss 2026-11-16, room 테스트방A, history 30점 / no alert. Target sentence paraphrased for the public repo.
        val samples = listOf(
            "12/12 모임 못오시는 분이 있어서 선착순 갠톡으로 신청 받겠습니다",
            "12/12 모임 취소하신 분이 생겨서 선착순으로 신청 받습니다",
            "내일 모임 한 자리 비어서 갠톡으로 신청 받을게요 선착순",
        )
        samples.forEach { text ->
            val score = ScoreClassifier.score(text)
            assertTrue("$text -> $score", score.meetup >= 70)
            assertTrue(text, score.meetup > score.giveaway)
            assertFalse(score.mediaOrEmpty)
        }
    }

    @Test
    fun cannotAttendIsNotAnAttendInvite() {
        val score = ScoreClassifier.score("12/12 모임 못오시는 분이 있어서 선착순 갠톡으로 신청 받겠습니다")
        assertFalse(
            "못오시는 분 must not count as 참석 권유: ${score.reasons}",
            score.reasons.any { it.contains("참석 권유") },
        )
    }
