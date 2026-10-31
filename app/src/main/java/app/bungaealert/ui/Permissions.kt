package app.bungaealert.ui

import android.Manifest
import android.app.ActivityManager
import android.app.NotificationManager
import android.app.usage.UsageStatsManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import app.bungaealert.notify.KakaoNotificationListener

data class PowerSnapshot(
    val samsung: Boolean,
    val batteryUnrestricted: Boolean,
    val backgroundRestricted: Boolean?,
    val standbyBucket: Int?,
    val powerSaveMode: Boolean,
    val deviceIdle: Boolean,
)

object Permissions {
    fun isSamsung(): Boolean = Build.MANUFACTURER.equals("samsung", ignoreCase = true)

    fun listenerEnabled(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= 27) {
            val manager = context.getSystemService(NotificationManager::class.java) ?: return false
            val component = ComponentName(context, KakaoNotificationListener::class.java)
            return manager.isNotificationListenerAccessGranted(component)
        }
        val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
            ?: return false
        val mine = ComponentName(context, KakaoNotificationListener::class.java).flattenToString()
        return flat.split(":").any { it.equals(mine, ignoreCase = true) }
    }

    fun powerSnapshot(context: Context): PowerSnapshot {
        val power = context.getSystemService(PowerManager::class.java)
        val background = if (Build.VERSION.SDK_INT >= 28) {
            context.getSystemService(ActivityManager::class.java)?.isBackgroundRestricted
        } else {
            null
        }
        val bucket = if (Build.VERSION.SDK_INT >= 28) {
            context.getSystemService(UsageStatsManager::class.java)?.appStandbyBucket
        } else {
            null
        }
        return PowerSnapshot(
            samsung = isSamsung(),
            batteryUnrestricted = power?.isIgnoringBatteryOptimizations(context.packageName) == true,
            backgroundRestricted = background,
            standbyBucket = bucket,
            powerSaveMode = power?.isPowerSaveMode == true,
            deviceIdle = power?.isDeviceIdleMode == true,
        )
    }

    fun notificationsAllowed(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < 33) return true
        return ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    }

    fun fullScreenAllowed(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < 34) return true
        val manager = context.getSystemService(NotificationManager::class.java)
        return manager.canUseFullScreenIntent()
    }

    fun dndAllowed(context: Context): Boolean {
        val manager = context.getSystemService(NotificationManager::class.java)
        return manager.isNotificationPolicyAccessGranted
    }

    fun batteryUnrestricted(context: Context): Boolean {
        val power = context.getSystemService(PowerManager::class.java)
        return power.isIgnoringBatteryOptimizations(context.packageName)
    }

    fun listenerSettings(): Intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)

    fun listenerDetail(context: Context): Intent {
        val component = ComponentName(context, KakaoNotificationListener::class.java)
        if (Build.VERSION.SDK_INT >= 30) {
            return Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS).putExtra(
                Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                component.flattenToString(),
            )
        }
        return listenerSettings()
    }

    fun appDetails(context: Context): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).setData(
            Uri.fromParts("package", context.packageName, null),
        )

    fun samsungBackgroundList(activityType: Int): Intent =
        Intent("com.samsung.android.sm.ACTION_OPEN_CHECKABLE_LISTACTIVITY").apply {
            setPackage("com.samsung.android.lool")
            putExtra("activity_type", activityType)
        }

    fun appNotificationSettings(context: Context): Intent =
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)

    fun fullScreenSettings(context: Context): Intent =
        if (Build.VERSION.SDK_INT >= 34) {
            Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT).setData(
                Uri.parse("package:${context.packageName}"),
            )
        } else {
            appNotificationSettings(context)
        }

    fun dndSettings(): Intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)

    fun batterySettings(context: Context): Intent {
        val direct = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).setData(
            Uri.parse("package:${context.packageName}"),
        )
        val list = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        return if (direct.resolveActivity(context.packageManager) != null) direct else list
    }
}

fun Context.safeStart(intent: Intent) {
    tryStart(intent)
}

fun Context.tryStart(intent: Intent): Boolean {
    return try {
        startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (_: Exception) {
        false
    }
}
