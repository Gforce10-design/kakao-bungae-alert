package app.bungaealert.alert

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import android.util.Log
import app.bungaealert.BungaeApp
import app.bungaealert.MainActivity
import app.bungaealert.R
import app.bungaealert.detect.AlertMode
import app.bungaealert.detect.MatchedWords
import app.bungaealert.detect.PostType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

object KakaoOpener {
    private val intents = ConcurrentHashMap<String, PendingIntent>()

    fun remember(id: String, intent: PendingIntent?) {
        if (intent != null) intents[id] = intent
    }

    fun open(context: Context, id: String) {
        val pending = intents.remove(id)
        if (pending != null) {
            try {
                pending.send()
                return
            } catch (_: Exception) {
            }
        }
        val launch = context.packageManager.getLaunchIntentForPackage("com.kakao.talk") ?: return
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(launch)
    }
}

object AlertChannels {
    /**
     * Versioned id: a channel's sound/vibration are frozen at creation, so the old
     * sounding "bungae_once" channel is dropped and this one is silent. Sound and
     * vibration come from [AlertFeedback], read from settings at alarm time.
     */
    const val ONCE = "bungae_once_v2"
    private const val ONCE_LEGACY = "bungae_once"
    const val ALARM = "bungae_alarm"

    fun ensure(context: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val manager = context.getSystemService(NotificationManager::class.java)
        val bypass = manager.isNotificationPolicyAccessGranted
        if (manager.getNotificationChannel(ONCE_LEGACY) != null) manager.deleteNotificationChannel(ONCE_LEGACY)
        ensureOne(manager, ONCE, context.getString(R.string.channel_once), context.getString(R.string.channel_once_desc), bypass, sound = false)
        ensureOne(manager, ALARM, context.getString(R.string.channel_alarm), context.getString(R.string.channel_alarm_desc), bypass, sound = false)
    }

    private fun ensureOne(
        manager: NotificationManager,
        id: String,
        name: String,
        description: String,
        bypass: Boolean,
        sound: Boolean,
    ) {
        val existing = manager.getNotificationChannel(id)
        if (existing != null && existing.canBypassDnd() != bypass) {
            manager.deleteNotificationChannel(id)
        }
        val channel = NotificationChannel(id, name, NotificationManager.IMPORTANCE_HIGH).apply {
            this.description = description
            setBypassDnd(bypass)
            if (!sound) setSound(null, null)
            enableVibration(false)
        }
        manager.createNotificationChannel(channel)
    }
}

object AlertPoster {
    fun postOnce(
        context: Context,
        id: String,
        title: String,
        body: String,
        kakao: PendingIntent?,
        matchedWords: List<String> = emptyList(),
    ) {
        AlertChannels.ensure(context)
        KakaoOpener.remember(id, kakao)
        val open = PendingIntent.getActivity(
            context,
            id.hashCode(),
            Intent(context, MainActivity::class.java).putExtra(AlarmActivity.EXTRA_OPEN_ID, id).setAction("open-$id"),
            PendingIntent.FLAG_UPDATE_CURRENT or immutable(),
        )
        val content = kakao ?: open
        val notification = NotificationCompat.Builder(context, AlertChannels.ONCE)
            .setSmallIcon(R.drawable.ic_stat_lightning)
            .setContentTitle(title)
            .setContentText(MatchedWords.collapsed(matchedWords, body))
            .setStyle(NotificationCompat.BigTextStyle().bigText(MatchedWords.expanded(matchedWords, body)))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setContentIntent(content)
            .build()
        notify(context, id.hashCode(), notification)
        playOnceFeedback(context)
    }

    private fun playOnceFeedback(context: Context) {
        try {
            val feel = BungaeApp.instance.graph.settings.current().feel
            AlertFeedback.shared(context).start(feel, loop = false)
        } catch (t: Throwable) {
            Log.w("BungaeAlert", "once feedback failed: ${t.javaClass.simpleName}")
        }
    }

    fun notify(context: Context, id: Int, notification: Notification) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.notify(id, notification)
    }

    private fun immutable(): Int = PendingIntent.FLAG_IMMUTABLE
}

class AlarmRingService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var becameForeground = false
    private var stopJob: Job? = null
    private val feedback by lazy { AlertFeedback(this) }
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            if (!becameForeground) {
                val placeholder = alarmNotification(
                    intent.getStringExtra(AlarmActivity.EXTRA_ID).orEmpty(),
                    "알림을 끄는 중",
                    "",
                )
                if (Build.VERSION.SDK_INT >= 29) {
                    startForeground(NOTIF_ID, placeholder, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
                } else {
                    startForeground(NOTIF_ID, placeholder)
                }
            }
            val open = intent.getBooleanExtra(EXTRA_OPEN, false)
            val id = intent.getStringExtra(AlarmActivity.EXTRA_ID).orEmpty()
            stopRinging()
            if (open && id.isNotEmpty()) KakaoOpener.open(this, id)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        val id = intent?.getStringExtra(AlarmActivity.EXTRA_ID).orEmpty()
        val title = intent?.getStringExtra(AlarmActivity.EXTRA_TITLE).orEmpty()
        val body = intent?.getStringExtra(AlarmActivity.EXTRA_BODY).orEmpty()
        val seconds = intent?.getIntExtra(EXTRA_SECONDS, 180) ?: 180
        val words = intent?.getStringArrayListExtra(AlarmActivity.EXTRA_WORDS).orEmpty()
        AlertChannels.ensure(this)
        val notification = alarmNotification(id, title, body, words)
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, notification)
        }
        becameForeground = true
        acquireWake(seconds)
        // The cap counts from the moment the ringtone is actually playing (not from the service
        // start, which includes slow ringtone setup), on the monotonic elapsedRealtime clock.
        val ringStartedAt = startRinging()
        stopJob?.cancel()
        stopJob = scope.launch {
            val deadline = ringStartedAt + seconds * 1000L
            while (true) {
                val remaining = deadline - SystemClock.elapsedRealtime()
                if (remaining <= 0L) break
                delay(remaining)
            }
            stopRinging()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        stopRinging()
        wakeLock?.let { if (it.isHeld) it.release() }
        scope.cancel()
        super.onDestroy()
    }

    private fun acquireWake(seconds: Int) {
        val power = getSystemService(PowerManager::class.java)
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "bungae:alarm").apply {
            setReferenceCounted(false)
            acquire(seconds * 1000L + 5_000L)
        }
    }

    /** Starts sound and vibration from the current settings; returns the [SystemClock.elapsedRealtime] when playback began. */
    private fun startRinging(): Long {
        stopRinging()
        val feel = BungaeApp.instance.graph.settings.current().feel
        feedback.start(feel, loop = true)
        return SystemClock.elapsedRealtime()
    }

    private fun stopRinging() {
        feedback.stop()
    }

    private fun alarmNotification(id: String, title: String, body: String, words: List<String> = emptyList()): Notification {
        val activity = PendingIntent.getActivity(
            this,
            1,
            Intent(this, AlarmActivity::class.java).apply {
                putExtra(AlarmActivity.EXTRA_ID, id)
                putExtra(AlarmActivity.EXTRA_TITLE, title)
                putExtra(AlarmActivity.EXTRA_BODY, body)
                putStringArrayListExtra(AlarmActivity.EXTRA_WORDS, ArrayList(words))
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getService(
            this,
            2,
            Intent(this, AlarmRingService::class.java).apply {
                action = ACTION_STOP
                putExtra(AlarmActivity.EXTRA_ID, id)
                putExtra(EXTRA_OPEN, true)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, AlertChannels.ALARM)
            .setSmallIcon(R.drawable.ic_stat_lightning)
            .setContentTitle(title)
            .setContentText(MatchedWords.collapsed(words, body))
            .setStyle(NotificationCompat.BigTextStyle().bigText(MatchedWords.expanded(words, body)))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setOngoing(true)
            .setSilent(true)
            .setFullScreenIntent(activity, true)
            .setContentIntent(activity)
            .addAction(0, "카톡 열기", stop)
            .build()
    }

    companion object {
        const val ACTION_STOP = "app.bungaealert.STOP_ALARM"
        const val EXTRA_OPEN = "open"
        const val EXTRA_SECONDS = "seconds"
        private const val NOTIF_ID = 7

        fun start(
            context: Context,
            id: String,
            title: String,
            body: String,
            seconds: Int,
            kakao: PendingIntent?,
            matchedWords: List<String> = emptyList(),
        ) {
            KakaoOpener.remember(id, kakao)
            val intent = Intent(context, AlarmRingService::class.java).apply {
                putExtra(AlarmActivity.EXTRA_ID, id)
                putExtra(AlarmActivity.EXTRA_TITLE, title)
                putExtra(AlarmActivity.EXTRA_BODY, body)
                putExtra(EXTRA_SECONDS, seconds)
                putStringArrayListExtra(AlarmActivity.EXTRA_WORDS, ArrayList(matchedWords))
            }
            try {
                if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent) else context.startService(intent)
            } catch (_: Exception) {
                AlertPoster.postOnce(context, id, title, body, kakao, matchedWords)
            }
        }

        fun stop(context: Context, id: String, openKakao: Boolean) {
            val intent = Intent(context, AlarmRingService::class.java).apply {
                action = ACTION_STOP
                putExtra(AlarmActivity.EXTRA_ID, id)
                putExtra(EXTRA_OPEN, openKakao)
            }
            context.startService(intent)
        }
    }
}

class AlarmStopReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra(AlarmActivity.EXTRA_ID).orEmpty()
        AlarmRingService.stop(context, id, intent.getBooleanExtra(AlarmRingService.EXTRA_OPEN, true))
    }
}

fun alertTitle(type: PostType?): String = when (type) {
    PostType.MEETUP -> "번개모임"
    PostType.GIVEAWAY -> "선착순 나눔"
    null -> "번개알림"
}

fun dispatchAlert(
    context: Context,
    id: String,
    chatName: String,
    body: String,
    type: PostType?,
    mode: AlertMode,
    seconds: Int,
    kakao: PendingIntent?,
    matchedWords: List<String> = emptyList(),
) {
    val title = "${alertTitle(type)} · $chatName"
    if (mode == AlertMode.REPEAT) {
        AlarmRingService.start(context, id, title, body, seconds, kakao, matchedWords)
    } else {
        AlertPoster.postOnce(context, id, title, body, kakao, matchedWords)
    }
}
