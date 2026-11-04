package app.bungaealert.update

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [33], application = Application::class)
class UpdateNotificationTest {
    private val releaseUrl = "https://github.com/Gforce10-design/kakao-bungae-alert/releases/tag/v1.2.0"
    private val source = ReleaseSource {
        ReleaseQuery.Found("v1.2.0", releaseUrl, 42)
    }

    @Test
    fun newerVersionPostsNotificationThatOpensTheReleasePage() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)

        val first = UpdateChecks.run(context, source, installedVersionCode = 1)
        assertTrue(first is UpdateDecision.Notify)

        val manager = context.getSystemService(NotificationManager::class.java)
        val shadowManager = shadowOf(manager)
        val notification = shadowManager.getNotification(UpdateNotifications.NOTIFICATION_ID)
        assertNotNull(notification)
        val channel = manager.getNotificationChannel(UpdateNotifications.CHANNEL_ID)
        assertEquals("업데이트", channel.name.toString())
        assertEquals("새 버전 v1.2.0 (42)가 있어요. 눌러서 APK를 받으세요.", notification.extras.getString("android.text"))

        val intent = shadowOf(notification.contentIntent).savedIntent
        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals(Uri.parse(releaseUrl), intent.data)
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)

        val second = UpdateChecks.run(context, source, installedVersionCode = 1)
        assertTrue(second is UpdateDecision.AlreadySent)
        assertEquals(1, shadowManager.size())
        assertEquals(42, UpdateChecks.savedUpdate(context, installedVersionCode = 1)!!.versionCode)
    }

    @Test
    fun missingPermissionDoesNotPost() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val decision = UpdateChecks.run(context, source, installedVersionCode = 1)
        assertTrue(decision is UpdateDecision.NeedsPermission)
        val manager = shadowOf(context.getSystemService(NotificationManager::class.java))
        assertNull(manager.getNotification(UpdateNotifications.NOTIFICATION_ID))
        val later = UpdateChecks.run(context, source, installedVersionCode = 1)
        assertTrue(later is UpdateDecision.NeedsPermission)
    }

    @Test
    fun currentVersionAndFailedLookupDoNotPost() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val current = UpdateChecks.run(context, source, installedVersionCode = 42)
        assertTrue(current is UpdateDecision.Current)
        val offline = UpdateChecks.run(
            context,
            ReleaseSource { ReleaseQuery.Failed("offline") },
            installedVersionCode = 1,
        )
        assertTrue(offline is UpdateDecision.Unavailable)
        val hidden = UpdateChecks.run(
            context,
            ReleaseSource { ReleaseQuery.Unauthorized },
            installedVersionCode = 1,
        )
        assertTrue(hidden is UpdateDecision.Unavailable)
        val missing = UpdateChecks.run(
            context,
            ReleaseSource { throw IllegalStateException("down") },
            installedVersionCode = 1,
        )
        assertTrue(missing is UpdateDecision.Unavailable)
        val manager = shadowOf(context.getSystemService(NotificationManager::class.java))
        assertNull(manager.getNotification(UpdateNotifications.NOTIFICATION_ID))
    }
}
