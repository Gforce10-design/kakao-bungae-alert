package app.bungaealert.ai

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.bungaealert.BungaeApp
import app.bungaealert.R
import app.bungaealert.data.SecretStore
import app.bungaealert.detect.BackendIds
import app.bungaealert.detect.NtfyEvents
import app.bungaealert.net.Http
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.Request
import java.util.concurrent.TimeUnit

class NtfySubscribeService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startAsForeground()
        if (job == null) {
            job = scope.launch { listen() }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        job?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    private fun startAsForeground() {
        val notification = quietNotification(this)
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    // Resume point for reconnects: last received ntfy message id, else a unix-seconds timestamp.
    @Volatile private var lastCursor: String? = null
    private var cursorSource: String? = null

    private suspend fun listen() {
        var backoff = 1_000L
        while (scope.isActive) {
            val app = application as BungaeApp
            val settings = app.graph.settings.current()
            val webhook = app.graph.secrets.get(SecretStore.GROK_URL)
            if (BackendIds.GROK_WEBHOOK !in settings.enabledBackends || webhook.isBlank()) {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return
            }
            val source = settings.ntfyServer.trim() + "|" + settings.ntfyTopic
            if (cursorSource != source) {
                cursorSource = source
                lastCursor = null
            }
            val attemptStartSec = System.currentTimeMillis() / 1000 - SINCE_SKEW_SEC
            val url = NtfyEvents.streamUrl(settings.ntfyServer, settings.ntfyTopic, lastCursor)
            if (url == null) {
                delay(5_000)
                continue
            }
            val connected = readStream(url, app.graph.replies)
            // Nothing received yet: next attempt replays from when this one started, so a reply
            // published while we were disconnected is still delivered.
            if (lastCursor == null) lastCursor = attemptStartSec.toString()
            backoff = if (connected) 1_000L else (backoff * 2).coerceAtMost(60_000L)
            delay(backoff)
        }
    }

    private fun readStream(url: String, replies: ReplyHub): Boolean {
        val request = Request.Builder().url(url).header("Accept", "application/x-ndjson").get().build()
        return try {
            Http.streamClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return false
                val source = response.body?.source() ?: return false
                while (!source.exhausted()) {
                    val line = source.readUtf8Line() ?: break
                    NtfyEvents.cursorFromLine(line)?.let { lastCursor = it }
                    val verdict = NtfyEvents.verdictFromLine(line, BackendIds.GROK_WEBHOOK) ?: continue
                    replies.deliver(verdict)
                }
                true
            }
        } catch (_: Exception) {
            false
        }
    }

    companion object {
        private const val NOTIF_ID = 42
        private const val SINCE_SKEW_SEC = 5L
        private const val CHANNEL = "bungae_ntfy"

        fun start(context: Context) {
            val intent = Intent(context, NtfySubscribeService::class.java)
            try {
                if (Build.VERSION.SDK_INT >= 26) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (_: Exception) {
                // The listener may be backgrounded. A missed subscription falls back to the score.
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, NtfySubscribeService::class.java))
        }

        fun quietNotification(context: Context): Notification {
            val manager = context.getSystemService(NotificationManager::class.java)
            if (Build.VERSION.SDK_INT >= 26 && manager.getNotificationChannel(CHANNEL) == null) {
                manager.createNotificationChannel(
                    NotificationChannel(CHANNEL, context.getString(R.string.channel_ntfy), NotificationManager.IMPORTANCE_LOW).apply {
                        description = context.getString(R.string.channel_ntfy_desc)
                        setSound(null, null)
                    },
                )
            }
            return NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_lightning)
                .setContentTitle("그록 응답 채널")
                .setContentText("판정 답변을 받기 위해 연결 중입니다.")
                .setOngoing(true)
                .setSilent(true)
                .build()
        }
    }
}

class NtfyWatchdogWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as? BungaeApp ?: return Result.success()
        val settings = app.graph.settings.current()
        val webhook = app.graph.secrets.get(SecretStore.GROK_URL)
        if (BackendIds.GROK_WEBHOOK in settings.enabledBackends && webhook.isNotBlank()) {
            NtfySubscribeService.start(applicationContext)
        }
        return Result.success()
    }

    companion object {
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<NtfyWatchdogWorker>(15, TimeUnit.MINUTES).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "ntfy-watch",
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
        }
    }
}
