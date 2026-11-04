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
        val component = ComponentName(context, KakaoNotificationListener::class.java)
        val listed = listenerListed(context, component)
        if (Build.VERSION.SDK_INT < 27) return notificationListenerGranted(null, listed)
        val manager = context.getSystemService(NotificationManager::class.java)
        val granted = try {
            manager?.isNotificationListenerAccessGranted(component)
        } catch (_: RuntimeException) {
            null
        }
        return notificationListenerGranted(granted, listed)
    }

    private fun listenerListed(context: Context, component: ComponentName): Boolean? {
        val flat = try {
            Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
        } catch (_: RuntimeException) {
            return null
        } ?: return false
        if (flat.isBlank()) return false
        val longName = component.flattenToString()
        val shortName = component.flattenToShortString()
        return flat.split(":").any { entry ->
            val value = entry.trim()
            value.equals(longName, ignoreCase = true) ||
                value.equals(shortName, ignoreCase = true) ||
                value.endsWith("/${component.className}", ignoreCase = true) ||
                (component.shortClassName.isNotEmpty() && value.endsWith("/${component.shortClassName}", ignoreCase = true))
        }
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
