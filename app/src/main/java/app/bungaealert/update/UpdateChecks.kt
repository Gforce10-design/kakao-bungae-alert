package app.bungaealert.update

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.bungaealert.BuildConfig
import app.bungaealert.R
import java.util.concurrent.TimeUnit

object UpdateNotifications {
    const val CHANNEL_ID = "app_update"
    const val NOTIFICATION_ID = 77

    fun canPost(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= 33) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return false
        }
        val manager = context.getSystemService(NotificationManager::class.java) ?: return false
        if (!manager.areNotificationsEnabled()) return false
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = manager.getNotificationChannel(CHANNEL_ID)
            if (channel != null && channel.importance == NotificationManager.IMPORTANCE_NONE) return false
        }
        return true
    }

    fun post(context: Context, tag: String, htmlUrl: String) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (Build.VERSION.SDK_INT >= 26 && manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.channel_update),
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply {
                    description = context.getString(R.string.channel_update_desc)
                },
            )
        }
        val display = SemanticVersion.display(tag)
        val text = context.getString(R.string.update_notification_text, display)
        val view = Intent(Intent.ACTION_VIEW, Uri.parse(htmlUrl)).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val pending = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            view,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_lightning)
            .setContentTitle(context.getString(R.string.channel_update))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setContentIntent(pending)
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
            .build()
        manager.notify(NOTIFICATION_ID, notification)
    }
}

internal object UpdatePrefs {
    private const val FILE = "bungae_update"
    private const val KEY = "notified_version"

    fun notified(context: Context): String? =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(KEY, null)?.takeIf { it.isNotBlank() }

    fun save(context: Context, normalized: String) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putString(KEY, normalized).apply()
    }
}

object UpdateChecks {
    fun run(
        context: Context,
        source: ReleaseSource = GithubReleaseSource(),
        installedVersion: String = BuildConfig.VERSION_NAME,
    ): UpdateDecision {
        val query = try {
            source.latest()
        } catch (t: Throwable) {
            ReleaseQuery.Failed(t.message ?: t.javaClass.simpleName)
        }
        val decision = UpdatePolicy.decide(
            query = query,
            installedVersion = installedVersion,
            notifiedNormalized = UpdatePrefs.notified(context),
            canNotify = UpdateNotifications.canPost(context),
        )
        if (decision is UpdateDecision.Notify) {
            UpdateNotifications.post(context, decision.tag, decision.htmlUrl)
            UpdatePrefs.save(context, decision.normalized)
        }
        return decision
    }
}

class UpdateCheckWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        UpdateChecks.run(applicationContext)
        return Result.success()
    }

    companion object {
        private const val PERIODIC = "app-update-check"
        private const val ON_START = "app-update-check-now"

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val daily = PeriodicWorkRequestBuilder<UpdateCheckWorker>(1, TimeUnit.DAYS)
                .setConstraints(constraints)
                .build()
            val manager = WorkManager.getInstance(context)
            manager.enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.UPDATE, daily)
            val now = OneTimeWorkRequestBuilder<UpdateCheckWorker>()
                .setConstraints(constraints)
                .build()
            manager.enqueueUniqueWork(ON_START, ExistingWorkPolicy.KEEP, now)
        }
    }
}
