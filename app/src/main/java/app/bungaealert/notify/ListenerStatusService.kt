package app.bungaealert.notify

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.bungaealert.MainActivity
import app.bungaealert.R
import java.util.concurrent.TimeUnit

/**
 * Low-importance ongoing notification in the same process as the listener.
 * Samsung sleeping apps still restrict foreground services, so this does not
 * replace the never-sleeping list. It only keeps a user-visible watcher out of
 * the cached state while Device Care has not put the app to sleep.
 */
class ListenerStatusService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = try {
            statusNotification(this)
        } catch (t: Throwable) {
            Log.e(TAG, "status notification build failed", t)
            NotificationCompat.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_lightning)
                .setContentTitle(getString(R.string.listener_status_title))
                .setContentText(getString(R.string.listener_status_text))
                .setOngoing(true)
                .setSilent(true)
                .build()
        }
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(NOTIF_ID, notification)
            }
        } catch (t: Throwable) {
            Log.e(TAG, "startForeground failed", t)
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    companion object {
        private const val TAG = "KakaoListener"
        const val NOTIF_ID = 48
        private const val CHANNEL = "bungae_listener"

        fun start(context: Context) {
            val intent = Intent(context, ListenerStatusService::class.java)
            try {
                if (Build.VERSION.SDK_INT >= 26) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (t: Throwable) {
                Log.w(TAG, "status service start blocked", t)
            }
        }

        fun stop(context: Context) {
            try {
                context.stopService(Intent(context, ListenerStatusService::class.java))
            } catch (t: Throwable) {
                Log.w(TAG, "status service stop failed", t)
            }
        }

        fun statusNotification(context: Context): Notification {
            val manager = context.getSystemService(NotificationManager::class.java)
            if (Build.VERSION.SDK_INT >= 26 && manager.getNotificationChannel(CHANNEL) == null) {
                manager.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL,
                        context.getString(R.string.channel_listener),
                        NotificationManager.IMPORTANCE_LOW,
                    ).apply {
                        description = context.getString(R.string.channel_listener_desc)
                        setSound(null, null)
                        setShowBadge(false)
                    },
                )
            }
            val open = PendingIntent.getActivity(
                context,
                48,
                Intent(context, MainActivity::class.java).apply {
                    putExtra(MainActivity.EXTRA_ROUTE, "status")
                    flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            return NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_lightning)
                .setContentTitle(context.getString(R.string.listener_status_title))
                .setContentText(context.getString(R.string.listener_status_text))
                .setOngoing(true)
                .setSilent(true)
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .setContentIntent(open)
                .build()
        }
    }
}

class ListenerWatchdogWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        try {
            ListenerRuntime.ensure(applicationContext, fromWatchdog = true)
        } catch (t: Throwable) {
            Log.e("KakaoListener", "watchdog failed", t)
        }
        return Result.success()
    }

    companion object {
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<ListenerWatchdogWorker>(15, TimeUnit.MINUTES).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "listener-watch",
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
        }
    }
}
