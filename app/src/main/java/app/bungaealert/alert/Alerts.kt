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
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import app.bungaealert.MainActivity
import app.bungaealert.R
import app.bungaealert.detect.AlertMode
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
    const val ONCE = "bungae_once"
    const val ALARM = "bungae_alarm"

    fun ensure(context: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val manager = context.getSystemService(NotificationManager::class.java)
        val bypass = manager.isNotificationPolicyAccessGranted
        ensureOne(manager, ONCE, context.getString(R.string.channel_once), context.getString(R.string.channel_once_desc), bypass, sound = true)
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
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setContentIntent(content)
            .build()
        notify(context, id.hashCode(), notification)
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
    private var ringtone: Ringtone? = null
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
        AlertChannels.ensure(this)
        val notification = alarmNotification(id, title, body)
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

    /** Starts sound and vibration and returns the [SystemClock.elapsedRealtime] when playback began. */
    private fun startRinging(): Long {
        stopRinging()
        var startedAt = SystemClock.elapsedRealtime()
        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        ringtone = RingtoneManager.getRingtone(this, uri)?.also { tone ->
            if (Build.VERSION.SDK_INT >= 21) {
                tone.audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            }
            if (Build.VERSION.SDK_INT >= 28) tone.isLooping = true
            tone.play()
            startedAt = SystemClock.elapsedRealtime()
        }
        val vibrator = if (Build.VERSION.SDK_INT >= 31) {
            getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Vibrator::class.java)
        }
        vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 600, 400), 0))
        if (Build.VERSION.SDK_INT < 28) {
            scope.launch {
                while (true) {
                    delay(1_500)
                    val tone = ringtone ?: break
                    if (!tone.isPlaying) tone.play()
                }
            }
        }
        return startedAt
    }

    private fun stopRinging() {
        ringtone?.stop()
        ringtone = null
        val vibrator = if (Build.VERSION.SDK_INT >= 31) {
            getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Vibrator::class.java)
        }
        vibrator.cancel()
    }

    private fun alarmNotification(id: String, title: String, body: String): Notification {
        val activity = PendingIntent.getActivity(
            this,
            1,
            Intent(this, AlarmActivity::class.java).apply {
                putExtra(AlarmActivity.EXTRA_ID, id)
                putExtra(AlarmActivity.EXTRA_TITLE, title)
                putExtra(AlarmActivity.EXTRA_BODY, body)
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
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
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
        ) {
            KakaoOpener.remember(id, kakao)
            val intent = Intent(context, AlarmRingService::class.java).apply {
                putExtra(AlarmActivity.EXTRA_ID, id)
                putExtra(AlarmActivity.EXTRA_TITLE, title)
                putExtra(AlarmActivity.EXTRA_BODY, body)
                putExtra(EXTRA_SECONDS, seconds)
            }
            try {
                if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent) else context.startService(intent)
            } catch (_: Exception) {
                AlertPoster.postOnce(context, id, title, body, kakao)
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
) {
    val title = "${alertTitle(type)} · $chatName"
    if (mode == AlertMode.REPEAT) {
        AlarmRingService.start(context, id, title, body, seconds, kakao)
    } else {
        AlertPoster.postOnce(context, id, title, body, kakao)
    }
}
