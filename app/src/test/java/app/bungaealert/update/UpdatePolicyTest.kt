package app.bungaealert.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdatePolicyTest {
    private val found = ReleaseQuery.Found(
        tag = "v1.2.0",
        htmlUrl = "https://github.com/Gforce10-design/kakao-bungae-alert/releases/tag/v1.2.0",
    )

    @Test
    fun newerVersionAsksToNotify() {
        val decision = UpdatePolicy.decide(found, "1.0.0", notifiedNormalized = null, canNotify = true)
        assertTrue(decision is UpdateDecision.Notify)
        decision as UpdateDecision.Notify
        assertEquals("1.2.0", decision.normalized)
        assertEquals(found.htmlUrl, decision.htmlUrl)
        assertEquals("새 버전 v1.2.0 알림을 보냈습니다", decision.message)
    }

    @Test
    fun sameOrOlderVersionStaysQuiet() {
        assertTrue(UpdatePolicy.decide(found, "1.2.0", null, true) is UpdateDecision.Current)
        assertTrue(UpdatePolicy.decide(found, "v1.3.0", null, true) is UpdateDecision.Current)
    }

    @Test
    fun doesNotNotifyTheSameVersionTwice() {
        val decision = UpdatePolicy.decide(found, "1.0.0", notifiedNormalized = "1.2.0", canNotify = true)
        assertTrue(decision is UpdateDecision.AlreadySent)
    }

    @Test
    fun permissionDeniedDoesNotCountAsSent() {
        val decision = UpdatePolicy.decide(found, "1.0.0", null, canNotify = false)
        assertTrue(decision is UpdateDecision.NeedsPermission)
    }

    @Test
    fun missingReleaseAndRateLimitAreQuiet() {
        assertEquals("배포된 릴리스가 없습니다", UpdatePolicy.decide(ReleaseQuery.NotFound, "1.0.0", null, true).message)
        assertEquals(
            "GitHub 요청 한도에 걸렸습니다. 나중에 다시 확인하세요",
            UpdatePolicy.decide(ReleaseQuery.RateLimited, "1.0.0", null, true).message,
        )
        assertEquals(
            "업데이트를 확인하지 못했습니다",
            UpdatePolicy.decide(ReleaseQuery.Failed("offline"), "1.0.0", null, true).message,
        )
    }
}
