package app.bungaealert.notify

import app.bungaealert.data.AppSettings
import app.bungaealert.data.SettingsRepository
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ListenerHealthTest {
    @Test
    fun accessOffDoesNotRebind() {
        val reason = ListenerHealth.decide(sample(accessEnabled = false, bound = false))
        assertEquals(RebindReason.NONE, reason)
    }

    @Test
    fun unboundAsksForRebind() {
        assertEquals(RebindReason.NOT_BOUND, ListenerHealth.decide(sample(bound = false)))
    }

    @Test
    fun watchdogThrottlesRepeatedRebind() {
        val reason = ListenerHealth.decide(
            sample(
                bound = false,
                fromWatchdog = true,
                nowMillis = 90_000L,
                lastRebindAtMillis = 60_000L,
            ),
        )
        assertEquals(RebindReason.NONE, reason)
    }

    @Test
    fun manualRebindIgnoresThrottle() {
        val reason = ListenerHealth.decide(
            sample(
                bound = false,
                fromWatchdog = false,
                nowMillis = 90_000L,
                lastRebindAtMillis = 89_000L,
            ),
        )
        assertEquals(RebindReason.NOT_BOUND, reason)
    }

    @Test
    fun activeKakaoPostedAfterConnectAndNotSeenIsStale() {
        val input = sample(
            bound = true,
            lastConnectedAtMillis = 1_000L,
            lastNotificationAtMillis = 1_000L,
            activeKakao = listOf(ActiveKakaoNotice("k1", 5_000L)),
        )
        assertEquals(1, ListenerHealth.missedCount(input))
        assertEquals(RebindReason.STALE_POSTS, ListenerHealth.decide(input))
    }

    @Test
    fun seenKeyAndOlderShadeNotificationAreHealthy() {
        val seen = sample(
            bound = true,
            lastConnectedAtMillis = 1_000L,
            lastNotificationAtMillis = 5_000L,
            seenKeys = setOf("k1"),
            activeKakao = listOf(
                ActiveKakaoNotice("k1", 5_000L),
                ActiveKakaoNotice("old", 500L),
            ),
        )
        assertEquals(0, ListenerHealth.missedCount(seen))
        assertEquals(RebindReason.NONE, ListenerHealth.decide(seen))
    }

    @Test
    fun blankKeyIsNotAMiss() {
        val input = sample(
            bound = true,
            lastConnectedAtMillis = 1_000L,
            activeKakao = listOf(ActiveKakaoNotice("", 5_000L)),
        )
        assertEquals(0, ListenerHealth.missedCount(input))
    }

    @Test
    fun standbyBucketNames() {
        assertEquals("restricted (45)", ListenerHealth.standbyBucketLabel(45))
        assertEquals("active (10)", ListenerHealth.standbyBucketLabel(10))
        assertTrue(ListenerHealth.standbyBucketLabel(99).contains("99"))
    }

    @Test
    fun settingsKeepAliveDefaultsOnWhenMissing() {
        val parsed = SettingsRepository.fromJson(JSONObject())
        assertTrue(parsed.listenerKeepAlive)
        val off = SettingsRepository.fromJson(JSONObject().put("listenerKeepAlive", false))
        assertFalse(off.listenerKeepAlive)
        val roundTrip = SettingsRepository.fromJson(SettingsRepository.toJson(AppSettings(listenerKeepAlive = false)))
        assertFalse(roundTrip.listenerKeepAlive)
    }

    private fun sample(
        accessEnabled: Boolean = true,
        bound: Boolean = true,
        nowMillis: Long = 10_000L,
        lastConnectedAtMillis: Long = 1_000L,
        lastNotificationAtMillis: Long = 0L,
        lastRebindAtMillis: Long = 0L,
        seenKeys: Set<String> = emptySet(),
        activeKakao: List<ActiveKakaoNotice> = emptyList(),
        fromWatchdog: Boolean = false,
    ) = ListenerHealthInput(
        accessEnabled = accessEnabled,
        bound = bound,
        nowMillis = nowMillis,
        lastConnectedAtMillis = lastConnectedAtMillis,
        lastNotificationAtMillis = lastNotificationAtMillis,
        lastRebindAtMillis = lastRebindAtMillis,
        seenKeys = seenKeys,
        activeKakao = activeKakao,
        fromWatchdog = fromWatchdog,
    )
}
