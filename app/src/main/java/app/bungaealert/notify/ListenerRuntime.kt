package app.bungaealert.notify

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.service.notification.NotificationListenerService
import android.util.Log
import app.bungaealert.BungaeApp
import app.bungaealert.ui.Permissions
import java.util.ArrayDeque

object ListenerRuntime {
    private const val PREFS = "bungae_listener"
    private const val KEY_CONNECTED = "last_connected"
    private const val KEY_DISCONNECTED = "last_disconnected"
    private const val KEY_CALLBACK = "last_callback"
    private const val KEY_KAKAO = "last_kakao"
    private const val KEY_REBIND = "last_rebind"
    private const val KEY_PROBE = "last_probe"
    private const val KEY_ACTIVE = "last_active_kakao"
    private const val KEY_MISSED = "last_missed"
    private const val MAX_SEEN = 80
    private const val TAG = "KakaoListener"

    @Volatile
    var bound: Boolean = false
        private set

    @Volatile
    private var connectedAtMemory: Long = 0L

    @Volatile
    private var kakaoAtMemory: Long = 0L

    private var service: KakaoNotificationListener? = null
    private val seenOrder = ArrayDeque<String>()
    private val seenKeys = HashSet<String>()

    data class Clock(
        val lastConnectedAtMillis: Long,
        val lastDisconnectedAtMillis: Long,
        val lastCallbackAtMillis: Long,
        val lastKakaoAtMillis: Long,
        val lastRebindAtMillis: Long,
        val lastProbeAtMillis: Long,
        val lastActiveKakaoCount: Int,
        val lastMissedCount: Int,
    )

    fun onConnected(listener: KakaoNotificationListener) {
        val now = System.currentTimeMillis()
        connectedAtMemory = now
        synchronized(this) {
            service = listener
            bound = true
        }
        prefs(listener).edit().putLong(KEY_CONNECTED, now).apply()
        Log.i(TAG, "listener connected")
    }

    fun onDisconnected(context: Context) {
        synchronized(this) {
            service = null
            bound = false
        }
        prefs(context).edit().putLong(KEY_DISCONNECTED, System.currentTimeMillis()).apply()
        Log.w(TAG, "listener disconnected")
    }

    fun onDestroyed(listener: KakaoNotificationListener) {
        synchronized(this) {
            if (service === listener) {
                service = null
                bound = false
            }
        }
    }

    fun markCallback(context: Context, kakao: Boolean, key: String) {
        val now = System.currentTimeMillis()
        if (kakao) kakaoAtMemory = now
        val editor = prefs(context).edit().putLong(KEY_CALLBACK, now)
        if (kakao) editor.putLong(KEY_KAKAO, now)
        editor.apply()
        if (kakao && key.isNotBlank()) remember(key)
    }

    fun clock(context: Context): Clock {
        val stored = prefs(context)
        return Clock(
            lastConnectedAtMillis = maxOf(stored.getLong(KEY_CONNECTED, 0L), connectedAtMemory),
            lastDisconnectedAtMillis = stored.getLong(KEY_DISCONNECTED, 0L),
            lastCallbackAtMillis = stored.getLong(KEY_CALLBACK, 0L),
            lastKakaoAtMillis = maxOf(stored.getLong(KEY_KAKAO, 0L), kakaoAtMemory),
            lastRebindAtMillis = stored.getLong(KEY_REBIND, 0L),
            lastProbeAtMillis = stored.getLong(KEY_PROBE, 0L),
            lastActiveKakaoCount = stored.getInt(KEY_ACTIVE, 0),
            lastMissedCount = stored.getInt(KEY_MISSED, 0),
        )
    }

    fun requestRebind(context: Context) {
        val component = ComponentName(context, KakaoNotificationListener::class.java)
        try {
            NotificationListenerService.requestRebind(component)
            prefs(context).edit().putLong(KEY_REBIND, System.currentTimeMillis()).apply()
            Log.i(TAG, "requestRebind ${component.flattenToString()}")
        } catch (t: Throwable) {
            Log.w(TAG, "requestRebind failed", t)
        }
    }

    fun ensure(context: Context, fromWatchdog: Boolean) {
        if (!Permissions.listenerEnabled(context)) {
            ListenerStatusService.stop(context)
            return
        }
        syncStatusNotification(context)
        val reason = evaluate(context, fromWatchdog)
        if (reason != RebindReason.NONE) requestRebind(context)
    }

    fun refreshProbe(context: Context): RebindReason = evaluate(context, fromWatchdog = false)

    private fun syncStatusNotification(context: Context) {
        val app = context.applicationContext as? BungaeApp ?: return
        if (app.graph.settings.current().listenerKeepAlive) {
            ListenerStatusService.start(context)
        } else {
            ListenerStatusService.stop(context)
        }
    }

    private fun evaluate(context: Context, fromWatchdog: Boolean): RebindReason {
        val active = readActiveKakao()
        val stored = clock(context)
        val input = ListenerHealthInput(
            accessEnabled = Permissions.listenerEnabled(context),
            bound = bound && synchronized(this) { service != null },
            nowMillis = System.currentTimeMillis(),
            lastConnectedAtMillis = stored.lastConnectedAtMillis,
            lastNotificationAtMillis = stored.lastKakaoAtMillis,
            lastRebindAtMillis = stored.lastRebindAtMillis,
            seenKeys = synchronized(this) { seenKeys.toSet() },
            activeKakao = active,
            fromWatchdog = fromWatchdog,
        )
        val reason = ListenerHealth.decide(input)
        prefs(context).edit()
            .putLong(KEY_PROBE, input.nowMillis)
            .putInt(KEY_ACTIVE, active.size)
            .putInt(KEY_MISSED, ListenerHealth.missedCount(input))
            .apply()
        if (reason != RebindReason.NONE) {
            Log.w(TAG, "listener unhealthy: $reason active=${active.size}")
        }
        return reason
    }

    private fun readActiveKakao(): List<ActiveKakaoNotice> {
        val listener = synchronized(this) { if (bound) service else null } ?: return emptyList()
        val posted = try {
            listener.activeNotifications ?: emptyArray()
        } catch (t: Throwable) {
            Log.w(TAG, "getActiveNotifications failed", t)
            synchronized(this) {
                if (service === listener) {
                    service = null
                    bound = false
                }
            }
            return emptyList()
        }
        return posted.mapNotNull { notice ->
            val key = notice.key ?: return@mapNotNull null
            if (notice.packageName != app.bungaealert.detect.KakaoPackages.KAKAO_TALK) return@mapNotNull null
            ActiveKakaoNotice(key, notice.postTime)
        }
    }

    private fun remember(key: String) {
        synchronized(this) {
            if (!seenKeys.add(key)) return
            seenOrder.addLast(key)
            while (seenOrder.size > MAX_SEEN) {
                seenKeys.remove(seenOrder.removeFirst())
            }
        }
    }

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

class ListenerPackageReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        try {
            ListenerRuntime.ensure(context.applicationContext, fromWatchdog = false)
        } catch (t: Throwable) {
            Log.e("KakaoListener", "rebind after package replace failed", t)
        }
    }
}
