package app.bungaealert.ui

import app.bungaealert.data.DetectionRecord
import app.bungaealert.detect.AlertMode
import app.bungaealert.detect.MatchMode
import app.bungaealert.detect.PostType
import app.bungaealert.detect.WatchRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class FormatTest {
    private val zone = ZoneId.of("Asia/Seoul")
    private val now = ZonedDateTime.of(2026, 10, 31, 15, 0, 0, 0, zone).toInstant().toEpochMilli()

    @Test
    fun groupsTodayYesterdayAndOlder() {
        val groups = groupDetections(
            listOf(
                sample("a", 10, 31, 14, 2, "meetup"),
                sample("b", 10, 30, 9, 0, "giveaway"),
                sample("c", 10, 28, 19, 5, null),
            ),
            now,
            zone,
        )
        assertEquals(listOf("오늘", "어제", "10월 28일"), groups.map { it.label })
        assertEquals("14:02", groups[0].items.single().timeLabel)
        assertEquals(PostType.MEETUP, groups[0].items.single().type)
        assertEquals(PostType.GIVEAWAY, groups[1].items.single().type)
    }

    @Test
    fun parsesAlarmTitle() {
        assertEquals(
            AlarmCopy("번개모임", "경제적 자유를 위하여~"),
            parseAlarmTitle("번개모임 · 경제적 자유를 위하여~"),
        )
        assertEquals(AlarmCopy("알림", "방만"), parseAlarmTitle("방만"))
    }

    @Test
    fun homeStaysOffWatchingUntilListenerAndNotificationsAreGranted() {
        assertEquals(
            ListenerHealthUi.NeedsPermission,
            homeListenerHealth(listenerGranted = false, notificationsGranted = false, listenerBound = true),
        )
        assertEquals(
            ListenerHealthUi.NeedsPermission,
            homeListenerHealth(listenerGranted = true, notificationsGranted = false, listenerBound = true),
        )
        assertEquals(
            ListenerHealthUi.NeedsPermission,
            homeListenerHealth(listenerGranted = false, notificationsGranted = true, listenerBound = true),
        )
        assertEquals(
            ListenerHealthUi.Unbound,
            homeListenerHealth(listenerGranted = true, notificationsGranted = true, listenerBound = false),
        )
        assertEquals(
            ListenerHealthUi.Watching,
            homeListenerHealth(listenerGranted = true, notificationsGranted = true, listenerBound = true),
        )
        assertFalse(notificationListenerGranted(managerGranted = true, listedInSettings = false))
        assertTrue(notificationListenerGranted(managerGranted = true, listedInSettings = null))
        assertTrue(notificationListenerGranted(managerGranted = false, listedInSettings = true))
        assertFalse(notificationListenerGranted(managerGranted = false, listedInSettings = false))
        assertFalse(notificationListenerGranted(managerGranted = null, listedInSettings = null))
    }

    @Test
    fun ruleMetaIsShort() {
        val rule = WatchRule("1", "수다방", MatchMode.ENDS_WITH, true, true, AlertMode.REPEAT)
        assertEquals("끝 · 반복 · 번개 · 선착순", ruleMeta(rule))
        assertEquals("3분", ringLabel(180))
        assertEquals("45초", ringLabel(45))
    }

    private fun sample(id: String, month: Int, day: Int, hour: Int, minute: Int, type: String?) = DetectionRecord(
        id = id,
        atMillis = ZonedDateTime.of(2026, month, day, hour, minute, 0, 0, zone).toInstant().toEpochMilli(),
        chatName = "방",
        sender = null,
        text = "오늘 번개",
        score = 80,
        meetupScore = if (type == "giveaway") 10 else 80,
        giveawayScore = if (type == "giveaway") 80 else 10,
        alerted = type != null,
        alertType = type,
        summary = "",
    )
}
