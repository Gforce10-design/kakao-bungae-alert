package app.bungaealert.notify

/**
 * Pure connection policy. Android binds the listener on the main thread and
 * may drop it without killing the process. A Kakao notification that is still
 * active, posted after the last successful bind, and absent from the keys we
 * actually received means callbacks stopped while the service still looked bound.
 */
data class ActiveKakaoNotice(
    val key: String,
    val postTimeMillis: Long,
)

data class ListenerHealthInput(
    val accessEnabled: Boolean,
    val bound: Boolean,
    val nowMillis: Long,
    val lastConnectedAtMillis: Long,
    val lastNotificationAtMillis: Long,
    val lastRebindAtMillis: Long,
    val seenKeys: Set<String>,
    val activeKakao: List<ActiveKakaoNotice>,
    val fromWatchdog: Boolean,
    val throttleMillis: Long = ListenerHealth.WATCHDOG_THROTTLE_MILLIS,
)

enum class RebindReason {
    NONE,
    NOT_BOUND,
    STALE_POSTS,
}

object ListenerHealth {
    const val WATCHDOG_THROTTLE_MILLIS = 60_000L

    fun decide(input: ListenerHealthInput): RebindReason {
        if (!input.accessEnabled) return RebindReason.NONE
        if (!input.bound) {
            if (throttled(input)) return RebindReason.NONE
            return RebindReason.NOT_BOUND
        }
        if (missedCount(input) == 0) return RebindReason.NONE
        if (throttled(input)) return RebindReason.NONE
        return RebindReason.STALE_POSTS
    }

    fun missedCount(input: ListenerHealthInput): Int {
        if (!input.bound || input.lastConnectedAtMillis <= 0L) return 0
        return input.activeKakao.count { notice ->
            notice.key.isNotBlank() &&
                notice.key !in input.seenKeys &&
                notice.postTimeMillis > input.lastConnectedAtMillis &&
                notice.postTimeMillis > input.lastNotificationAtMillis
        }
    }

    fun standbyBucketLabel(bucket: Int): String = when (bucket) {
        5 -> "exempted (5)"
        10 -> "active (10)"
        20 -> "working set (20)"
        30 -> "frequent (30)"
        40 -> "rare (40)"
        45 -> "restricted (45)"
        50 -> "never (50)"
        else -> "unknown ($bucket)"
    }

    private fun throttled(input: ListenerHealthInput): Boolean {
        if (!input.fromWatchdog) return false
        if (input.lastRebindAtMillis <= 0L) return false
        return input.nowMillis - input.lastRebindAtMillis < input.throttleMillis
    }
}
