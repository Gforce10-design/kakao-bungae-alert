package app.bungaealert.ui

import app.bungaealert.data.DetectionRecord
import app.bungaealert.detect.AlertMode
import app.bungaealert.detect.MatchKeyword
import app.bungaealert.detect.MatchMode
import app.bungaealert.detect.PostType
import app.bungaealert.detect.RuleKeywords
import app.bungaealert.detect.ScoreZone
import app.bungaealert.detect.WatchRule
import java.time.Instant
import java.time.ZoneId

enum class ListenerHealthUi { Watching, Unbound, Blocked, NeedsPermission }

fun homeListenerHealth(
    listenerGranted: Boolean,
    notificationsGranted: Boolean,
    listenerBound: Boolean,
): ListenerHealthUi = when {
    !listenerGranted || !notificationsGranted -> ListenerHealthUi.NeedsPermission
    !listenerBound -> ListenerHealthUi.Unbound
    else -> ListenerHealthUi.Watching
}

/**
 * Notification-listener access for the home banner.
 * A settings list that does not include this app wins over a manager API that
 * still reports access, which is what kept the banner on 감시 중.
 */
fun notificationListenerGranted(managerGranted: Boolean?, listedInSettings: Boolean?): Boolean {
    if (listedInSettings == false) return false
    if (managerGranted == false && listedInSettings != true) return false
    return managerGranted == true || listedInSettings == true
}

data class DetectionCardUi(
    val id: String,
    val chatName: String,
    val snippet: String,
    val timeLabel: String,
    val score: Int,
    val type: PostType?,
    val matchedWords: List<String> = emptyList(),
)

data class DetectionGroupUi(
    val label: String,
    val items: List<DetectionCardUi>,
)

data class AlarmCopy(val eyebrow: String, val chat: String)

fun groupDetections(
    records: List<DetectionRecord>,
    nowMillis: Long,
    zone: ZoneId = ZoneId.of("Asia/Seoul"),
): List<DetectionGroupUi> {
    if (records.isEmpty()) return emptyList()
    val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
    val grouped = linkedMapOf<String, MutableList<DetectionCardUi>>()
    for (record in records) {
        val date = Instant.ofEpochMilli(record.atMillis).atZone(zone).toLocalDate()
        val label = when (date) {
            today -> "오늘"
            today.minusDays(1) -> "어제"
            else -> "${date.monthValue}월 ${date.dayOfMonth}일"
        }
        grouped.getOrPut(label) { mutableListOf() }.add(
            DetectionCardUi(
                id = record.id.ifBlank { "${record.chatName}-${record.atMillis}" },
                chatName = record.chatName.ifBlank { "채팅방" },
                snippet = snippetOf(record.text),
                timeLabel = clockLabel(record.atMillis, zone),
                score = record.score,
                type = recordType(record),
                matchedWords = record.matchedWords,
            ),
        )
    }
    return grouped.map { (label, items) -> DetectionGroupUi(label, items) }
}

fun recordType(record: DetectionRecord): PostType? {
    PostType.fromWire(record.alertType)?.let { return it }
    if (record.meetupScore <= 0 && record.giveawayScore <= 0) return null
    return if (record.meetupScore >= record.giveawayScore) PostType.MEETUP else PostType.GIVEAWAY
}

fun typeShort(type: PostType): String = when (type) {
    PostType.MEETUP -> "번개"
    PostType.GIVEAWAY -> "선착순"
}

fun matchLabel(mode: MatchMode): String = when (mode) {
    MatchMode.EXACT -> "정확히"
    MatchMode.CONTAINS -> "포함"
    MatchMode.ENDS_WITH -> "끝"
}

fun alertLabel(mode: AlertMode): String = when (mode) {
    AlertMode.ONCE -> "한 번"
    AlertMode.REPEAT -> "반복"
}

fun ruleMeta(rule: WatchRule, keywords: List<MatchKeyword> = emptyList()): String {
    val types = if (keywords.isEmpty()) {
        buildList {
            if (rule.detectMeetup) add("번개")
            if (rule.detectGiveaway) add("선착순")
        }.joinToString(" · ")
    } else {
        RuleKeywords.applicable(rule, keywords).joinToString(" · ") { it.text }
    }
    return listOf(matchLabel(rule.matchMode), alertLabel(rule.alertMode), types)
        .filter { it.isNotBlank() }
        .joinToString(" · ")
}

fun ringLabel(seconds: Int): String {
    val safe = seconds.coerceAtLeast(0)
    val minutes = safe / 60
    val remain = safe % 60
    return when {
        minutes == 0 -> "${remain}초"
        remain == 0 -> "${minutes}분"
        else -> "${minutes}분 ${remain}초"
    }
}

fun zoneLabel(zone: ScoreZone): String = when (zone) {
    ScoreZone.CLEAR_MATCH -> "기준 위"
    ScoreZone.CLEAR_REJECT -> "기준 아래"
    ScoreZone.BORDERLINE -> "경계"
}

fun clockLabel(millis: Long, zone: ZoneId = ZoneId.of("Asia/Seoul")): String {
    val time = Instant.ofEpochMilli(millis).atZone(zone)
    return "%02d:%02d".format(time.hour, time.minute)
}

fun dateTimeLabel(millis: Long, zone: ZoneId = ZoneId.of("Asia/Seoul")): String {
    if (millis <= 0L) return "없음"
    val time = Instant.ofEpochMilli(millis).atZone(zone)
    return "${time.monthValue}월 ${time.dayOfMonth}일 ${clockLabel(millis, zone)}"
}

fun parseAlarmTitle(title: String): AlarmCopy {
    val parts = title.split(" · ", limit = 2)
    return if (parts.size == 2 && parts[0].isNotBlank() && parts[1].isNotBlank()) {
        AlarmCopy(parts[0], parts[1])
    } else {
        AlarmCopy("알림", title.ifBlank { "번개알림" })
    }
}

fun snippetOf(text: String): String = text.replace(Regex("\\s+"), " ").trim()

fun standbyKorean(bucket: Int?): String = when (bucket) {
    null -> "확인 불가"
    5 -> "예외"
    10 -> "활성"
    20 -> "작업 집합"
    30 -> "자주"
    40 -> "드묾"
    45 -> "제한"
    50 -> "없음"
    else -> "기타 ($bucket)"
}
