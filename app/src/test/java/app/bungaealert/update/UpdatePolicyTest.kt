package app.bungaealert.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdatePolicyTest {
    private val found = ReleaseQuery.Found(
        tag = "v1.2.0",
        htmlUrl = "https://github.com/Gforce10-design/kakao-bungae-alert/releases/tag/v1.2.0",
        versionCode = 42,
    )

    @Test
    fun newerVersionCodeAsksToNotify() {
        val decision = UpdatePolicy.decide(found, installedVersionCode = 1, notifiedVersionCode = null, canNotify = true)
        assertTrue(decision is UpdateDecision.Notify)
        decision as UpdateDecision.Notify
        assertEquals(42, decision.versionCode)
        assertEquals(found.htmlUrl, decision.htmlUrl)
        assertEquals(found.htmlUrl, decision.pageUrl)
        assertEquals("새 버전 v1.2.0 (42) 알림을 보냈습니다", decision.message)
        assertEquals(null, decision.apkUrl)
    }

    @Test
    fun keepsTheApkAddressOnTheUpdate() {
        val withFile = found.copy(
            apkUrl = "https://example.com/bungae-alert-v1.2.0-vc42.apk",
            apkName = "bungae-alert-v1.2.0-vc42.apk",
            checksumUrl = "https://example.com/SHA256SUMS",
        )
        val decision = UpdatePolicy.decide(withFile, installedVersionCode = 1, notifiedVersionCode = null, canNotify = true)
        decision as UpdateDecision.Notify
        assertEquals(withFile.apkUrl, decision.apkUrl)
        assertEquals(withFile.apkName, decision.apkName)
        assertEquals(withFile.checksumUrl, decision.checksumUrl)
    }

    @Test
    fun sameOrOlderVersionCodeStaysQuiet() {
        assertTrue(UpdatePolicy.decide(found, 42, null, true) is UpdateDecision.Current)
        assertEquals("최신 버전입니다", UpdatePolicy.decide(found, 42, null, true).message)
        val older = UpdatePolicy.decide(found, 43, null, true)
        assertTrue(older is UpdateDecision.Current)
        assertEquals("설치된 버전이 더 높습니다", older.message)
    }

    @Test
    fun doesNotNotifyTheSameVersionTwice() {
        val decision = UpdatePolicy.decide(found, installedVersionCode = 1, notifiedVersionCode = 42, canNotify = true)
        assertTrue(decision is UpdateDecision.AlreadySent)
        val again = UpdatePolicy.decide(
            found.copy(versionCode = 43, tag = "v1.3.0"),
            installedVersionCode = 1,
            notifiedVersionCode = 42,
            canNotify = true,
        )
        assertTrue(again is UpdateDecision.Notify)
        assertEquals(43, (again as UpdateDecision.Notify).versionCode)
    }

    @Test
    fun permissionDeniedDoesNotCountAsSent() {
        val decision = UpdatePolicy.decide(found, installedVersionCode = 1, notifiedVersionCode = null, canNotify = false)
        assertTrue(decision is UpdateDecision.NeedsPermission)
        assertEquals(found.htmlUrl, decision.pageUrl)
    }

    @Test
    fun missingReleaseAuthAndFailuresStayQuiet() {
        assertEquals(
            "릴리스를 확인하지 못했습니다",
            UpdatePolicy.decide(ReleaseQuery.NotFound, 1, null, true).message,
        )
        assertEquals(
            "릴리스를 확인하지 못했습니다",
            UpdatePolicy.decide(ReleaseQuery.Unauthorized, 1, null, true).message,
        )
        assertEquals(
            "GitHub 요청 한도에 걸렸습니다. 나중에 다시 확인하세요",
            UpdatePolicy.decide(ReleaseQuery.RateLimited, 1, null, true).message,
        )
        assertEquals(
            "업데이트를 확인하지 못했습니다",
            UpdatePolicy.decide(ReleaseQuery.Failed("offline"), 1, null, true).message,
        )
        assertEquals(null, UpdatePolicy.decide(ReleaseQuery.Failed("offline"), 1, null, true).pageUrl)
    }
}
