package app.bungaealert.ui

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

internal val Seoul: ZoneId = ZoneId.of("Asia/Seoul")

internal fun formatWhen(millis: Long, now: Long): String {
    if (millis <= 0L) return "없음"
    val zone = Seoul
    val moment = Instant.ofEpochMilli(millis).atZone(zone)
    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    val time = moment.format(DateTimeFormatter.ofPattern("HH:mm", Locale.KOREA))
    val day = when (val date = moment.toLocalDate()) {
        today -> "오늘"
        today.minusDays(1) -> "어제"
        else -> date.format(DateTimeFormatter.ofPattern("M월 d일", Locale.KOREA))
    }
    return "$day $time"
}
