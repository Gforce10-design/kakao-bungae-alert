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
import android.util.Log
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

data class AvailableUpdate(val versionCode: Int, val tag: String, val url: String)

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

    fun post(context: Context, tag: String, versionCode: Int, htmlUrl: String) {
        if (!htmlUrl.startsWith("https://")) return
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
        val label = "${SemanticVersion.display(tag)} ($versionCode)"
        val text = context.getString(R.string.update_notification_text, label)
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
    private const val KEY_NOTIFIED = "notified_version_code"
    private const val KEY_CHECKED = "last_check_at"
    private const val KEY_AVAILABLE_CODE = "available_version_code"
    private const val KEY_AVAILABLE_TAG = "available_tag"
    private const val KEY_AVAILABLE_URL = "available_url"

    private fun prefs(context: Context) =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun notified(context: Context): Int? {
        val stored = prefs(context)
        if (!stored.contains(KEY_NOTIFIED)) return null
        return try {
            stored.getInt(KEY_NOTIFIED, -1).takeIf { it > 0 }
        } catch (_: ClassCastException) {
            null
        }
    }

    fun saveNotified(context: Context, versionCode: Int) {
        prefs(context).edit().putInt(KEY_NOTIFIED, versionCode).apply()
    }

    fun lastCheckAt(context: Context): Long =
        try {
            prefs(context).getLong(KEY_CHECKED, 0L)
        } catch (_: ClassCastException) {
            0L
        }

    fun saveLastCheck(context: Context, atMillis: Long = System.currentTimeMillis()) {
        prefs(context).edit().putLong(KEY_CHECKED, atMillis).apply()
    }

    fun available(context: Context): AvailableUpdate? {
        val stored = prefs(context)
        val code = try {
            stored.getInt(KEY_AVAILABLE_CODE, -1)
        } catch (_: ClassCastException) {
            return null
        }
        val tag = stored.getString(KEY_AVAILABLE_TAG, null).orEmpty()
        val url = stored.getString(KEY_AVAILABLE_URL, null).orEmpty()
        if (code <= 0 || !url.startsWith("https://")) return null
        return AvailableUpdate(code, tag, url)
    }

    fun saveAvailable(context: Context, versionCode: Int, tag: String, url: String) {
        if (versionCode <= 0 || !url.startsWith("https://")) return
        prefs(context).edit()
            .putInt(KEY_AVAILABLE_CODE, versionCode)
            .putString(KEY_AVAILABLE_TAG, tag)
            .putString(KEY_AVAILABLE_URL, url)
            .apply()
    }

    fun clearAvailable(context: Context) {
        prefs(context).edit()
            .remove(KEY_AVAILABLE_CODE)
            .remove(KEY_AVAILABLE_TAG)
            .remove(KEY_AVAILABLE_URL)
            .apply()
    }
}

object UpdateChecks {
    private const val TAG = "BungaeUpdate"

    fun savedUpdate(
        context: Context,
        installedVersionCode: Int = BuildConfig.VERSION_CODE,
    ): AvailableUpdate? {
        val saved = UpdatePrefs.available(context) ?: return null
        return saved.takeIf { VersionCodes.isNewer(it.versionCode, installedVersionCode) }
    }

    fun run(
        context: Context,
        source: ReleaseSource = GithubReleaseSource(),
        installedVersionCode: Int = BuildConfig.VERSION_CODE,
        nowMillis: Long = System.currentTimeMillis(),
    ): UpdateDecision {
        return try {
            UpdatePrefs.saveLastCheck(context, nowMillis)
            val query = try {
                source.latest()
            } catch (t: Throwable) {
                ReleaseQuery.Failed(t.message ?: t.javaClass.simpleName)
            }
            when (query) {
                ReleaseQuery.NotFound -> Log.i(TAG, "GitHub latest release returned 404; not notifying")
                ReleaseQuery.Unauthorized -> Log.i(TAG, "GitHub latest release returned 401; not notifying")
                is ReleaseQuery.Failed -> Log.i(TAG, "GitHub latest release failed: ${query.detail}")
                else -> Unit
            }
            val decision = UpdatePolicy.decide(
                query = query,
                installedVersionCode = installedVersionCode,
                notifiedVersionCode = UpdatePrefs.notified(context),
                canNotify = UpdateNotifications.canPost(context),
            )
            apply(context, decision)
            decision
        } catch (t: Throwable) {
            Log.w(TAG, "update check failed", t)
            UpdateDecision.Unavailable("업데이트를 확인하지 못했습니다")
        }
    }

    private fun apply(context: Context, decision: UpdateDecision) {
        when (decision) {
            is UpdateDecision.Notify -> {
                UpdateNotifications.post(context, decision.tag, decision.versionCode, decision.htmlUrl)
                UpdatePrefs.saveNotified(context, decision.versionCode)
                UpdatePrefs.saveAvailable(context, decision.versionCode, decision.tag, decision.htmlUrl)
            }
            is UpdateDecision.AlreadySent ->
                UpdatePrefs.saveAvailable(context, decision.versionCode, decision.tag, decision.htmlUrl)
            is UpdateDecision.NeedsPermission ->
                UpdatePrefs.saveAvailable(context, decision.versionCode, decision.tag, decision.htmlUrl)
            is UpdateDecision.Current -> UpdatePrefs.clearAvailable(context)
            is UpdateDecision.Unavailable -> Unit
        }
    }
}

class UpdateCheckWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        return try {
            UpdateChecks.run(applicationContext)
            Result.success()
        } catch (t: Throwable) {
            Log.w("BungaeUpdate", "update check worker failed", t)
            Result.success()
        }
    }

    companion object {
        private const val PERIODIC = "app-update-check"
        private const val ON_START = "app-update-check-now"

        fun schedule(context: Context, nowMillis: Long = System.currentTimeMillis()) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val daily = PeriodicWorkRequestBuilder<UpdateCheckWorker>(24, TimeUnit.HOURS)
                .setConstraints(constraints)
                .build()
            val manager = WorkManager.getInstance(context)
            manager.enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, daily)
            if (UpdateThrottle.due(UpdatePrefs.lastCheckAt(context), nowMillis)) {
                val now = OneTimeWorkRequestBuilder<UpdateCheckWorker>()
                    .setConstraints(constraints)
                    .build()
                manager.enqueueUniqueWork(ON_START, ExistingWorkPolicy.KEEP, now)
            }
        }
    }
}
