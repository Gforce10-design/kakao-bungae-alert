package app.bungaealert.alert

import android.app.Activity
import android.app.ActivityOptions
import android.app.Application
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.bungaealert.detect.KakaoPackages
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog

@RunWith(AndroidJUnit4::class)
@Config(sdk = [33], application = Application::class)
class KakaoOpenerTest {
    private val context get() = ApplicationProvider.getApplicationContext<Application>()

    @Test
    fun contentIntentIsSentAndKakaoTalkIsNotLaunched() {
        installKakaoTalk()
        val pending = chatIntent("room-1")
        KakaoOpener.remember("room-1", pending)
        val activity = foregroundActivity()

        KakaoOpener.open(activity, "room-1")

        assertEquals(listOf(CHAT_ACTION), broadcastActions())
        assertNull(shadowOf(context).nextStartedActivity)
        activity.finish()
    }

    @Test
    fun canceledContentIntentFallsBackToKakaoTalk() {
        installKakaoTalk()
        val pending = chatIntent("room-2")
        pending.cancel()
        KakaoOpener.remember("room-2", pending)
        val activity = foregroundActivity()
        ShadowLog.clear()

        KakaoOpener.open(activity, "room-2")

        assertEquals(KakaoPackages.KAKAO_TALK, shadowOf(context).nextStartedActivity?.component?.packageName)
        assertTrue(broadcastActions().isEmpty())
        assertTrue(warnings().any { it.contains("content intent failed") })
        activity.finish()
    }

    @Test
    fun missingKakaoTalkIsLogged() {
        ShadowLog.clear()
        val activity = foregroundActivity()

        KakaoOpener.open(activity, "room-3")

        assertNull(shadowOf(context).nextStartedActivity)
        assertTrue(warnings().any { it.contains("launch intent missing") && it.contains("room-3") })
        activity.finish()
    }

    @Test
    @Config(sdk = [34], application = Application::class)
    @Suppress("DEPRECATION")
    fun api34ActivityAllowsBackgroundStartWhenSending() {
        val activity = foregroundActivity()
        val options = KakaoOpener.backgroundStartOptions(activity)
        assertNotNull(options)
        assertEquals(
            ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED,
            options!!.pendingIntentBackgroundActivityStartMode,
        )
        assertNull(KakaoOpener.backgroundStartOptions(context))

        KakaoOpener.remember("room-4", chatIntent("room-4"))
        KakaoOpener.open(activity, "room-4")
        assertEquals(listOf(CHAT_ACTION), broadcastActions())
        activity.finish()
    }

    @Test
    fun serviceContextDoesNotOptIn() {
        assertNull(KakaoOpener.backgroundStartOptions(context))
    }

    @Test
    fun alarmActionOpensKakaoFromAnActivity() {
        val service = Robolectric.buildService(AlarmRingService::class.java).create().get()
        val notification = service.alarmNotification("alert-1", "번개모임 · 방", "오늘 번개", listOf("번개"))
        val action = notification.actions.single()
        assertEquals("카톡 열기", action.title.toString())
        val pending = action.actionIntent
        assertTrue(pending.isActivity)
        assertFalse(pending.isService)
        val intent = shadowOf(pending).savedIntent
        assertEquals(OpenKakaoActivity::class.java.name, intent.component?.className)
        assertEquals("alert-1", intent.getStringExtra(AlarmActivity.EXTRA_ID))
        assertEquals(AlarmActivity::class.java.name, shadowOf(notification.contentIntent).savedIntent.component?.className)
        service.onDestroy()
    }

    @Test
    fun notificationTrampolineOpensThenStopsWithoutOpeningAgain() {
        installKakaoTalk()
        val alarm = Robolectric.buildActivity(
            AlarmActivity::class.java,
            Intent().putExtra(AlarmActivity.EXTRA_ID, "alert-2"),
        ).create()
        val opener = Robolectric.buildActivity(
            OpenKakaoActivity::class.java,
            Intent().putExtra(AlarmActivity.EXTRA_ID, "alert-2"),
        ).create()

        assertEquals(KakaoPackages.KAKAO_TALK, shadowOf(context).nextStartedActivity?.component?.packageName)
        val stop = shadowOf(opener.get()).nextStartedService
        assertEquals(AlarmRingService.ACTION_STOP, stop.action)
        assertEquals("alert-2", stop.getStringExtra(AlarmActivity.EXTRA_ID))
        assertFalse(stop.getBooleanExtra(AlarmRingService.EXTRA_OPEN, true))
        assertTrue(opener.get().isFinishing)
        assertTrue(alarm.get().isFinishing)
        opener.destroy()
        alarm.destroy()
    }

    @Test
    fun serviceStopDoesNotOpenKakaoTalk() {
        installKakaoTalk()
        KakaoOpener.remember("alert-3", chatIntent("alert-3"))
        val service = Robolectric.buildService(AlarmRingService::class.java).create().get()
        val stop = Intent(context, AlarmRingService::class.java).apply {
            action = AlarmRingService.ACTION_STOP
            putExtra(AlarmActivity.EXTRA_ID, "alert-3")
            putExtra(AlarmRingService.EXTRA_OPEN, true)
        }

        service.onStartCommand(stop, 0, 1)

        assertTrue(broadcastActions().isEmpty())
        assertNull(shadowOf(context).nextStartedActivity)
        val activity = foregroundActivity()
        KakaoOpener.open(activity, "alert-3")
        assertEquals(listOf(CHAT_ACTION), broadcastActions())
        assertNull(shadowOf(context).nextStartedActivity)
        activity.finish()
        service.onDestroy()
    }

    private fun foregroundActivity(): Activity {
        return Robolectric.buildActivity(Activity::class.java).setup().get()
    }

    private fun chatIntent(id: String): PendingIntent {
        return PendingIntent.getBroadcast(
            context,
            id.hashCode(),
            Intent(CHAT_ACTION).setPackage(context.packageName).putExtra(AlarmActivity.EXTRA_ID, id),
            PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun broadcastActions(): List<String> {
        return shadowOf(context).broadcastIntents.mapNotNull { it.action }.filter { it == CHAT_ACTION }
    }

    private fun warnings(): List<String> {
        return ShadowLog.getLogs()
            .filter { it.tag == "KakaoOpener" && it.type == Log.WARN }
            .map { it.msg }
    }

    private fun installKakaoTalk() {
        val packageName = KakaoPackages.KAKAO_TALK
        val component = ComponentName(packageName, "com.kakao.talk.activity.SplashActivity")
        val shadow = shadowOf(context.packageManager)
        shadow.installPackage(
            PackageInfo().apply {
                this.packageName = packageName
                applicationInfo = ApplicationInfo().apply {
                    this.packageName = packageName
                    enabled = true
                }
            },
        )
        shadow.addActivityIfNotPresent(component)
        shadow.addIntentFilterForActivity(
            component,
            IntentFilter(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LAUNCHER) },
        )
    }

    private companion object {
        const val CHAT_ACTION = "app.bungaealert.TEST_CHAT"
    }
}
