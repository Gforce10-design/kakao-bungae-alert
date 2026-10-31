package app.bungaealert.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.bungaealert.BungaeApp
import app.bungaealert.data.DetectionRecord
import app.bungaealert.detect.PostType
import app.bungaealert.notify.ListenerRuntime
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Locale

internal data class BannerUi(val online: Boolean, val title: String, val subtitle: String)

internal enum class FeedKind { Meetup, Giveaway }

internal data class FeedCard(
    val id: String,
    val chat: String,
    val snippet: String,
    val time: String,
    val score: Int,
    val kind: FeedKind,
)

internal data class DayGroup(val label: String, val cards: List<FeedCard>)

internal fun groupFeed(records: List<DetectionRecord>, nowMillis: Long): List<DayGroup> {
    val zone = Seoul
    val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
    val timeFmt = DateTimeFormatter.ofPattern("HH:mm", Locale.KOREA)
    val dateFmt = DateTimeFormatter.ofPattern("M월 d일", Locale.KOREA)
    val grouped = linkedMapOf<java.time.LocalDate, MutableList<FeedCard>>()
    records.sortedByDescending { it.atMillis }.forEach { record ->
        val moment = Instant.ofEpochMilli(record.atMillis).atZone(zone)
        val card = FeedCard(
            id = record.id,
            chat = record.chatName.ifBlank { "채팅" },
            snippet = record.text.replace(Regex("\\s+"), " ").trim(),
            time = moment.format(timeFmt),
            score = record.score,
            kind = record.kind(),
        )
        grouped.getOrPut(moment.toLocalDate()) { mutableListOf() }.add(card)
    }
    return grouped.map { (date, cards) ->
        val label = when (date) {
            today -> "오늘"
            today.minusDays(1) -> "어제"
            else -> date.format(dateFmt)
        }
        DayGroup(label, cards)
    }
}

private fun DetectionRecord.kind(): FeedKind {
    if (alertType == PostType.GIVEAWAY.wire) return FeedKind.Giveaway
    if (alertType == PostType.MEETUP.wire) return FeedKind.Meetup
    return if (giveawayScore > meetupScore) FeedKind.Giveaway else FeedKind.Meetup
}

internal fun listenerBanner(access: Boolean, bound: Boolean, lastKakaoMillis: Long, now: Long): BannerUi {
    if (!access) return BannerUi(false, "알림을 받지 않는 중", "알림 접근이 꺼져 있습니다")
    if (!bound) return BannerUi(false, "알림을 받지 않는 중", "리스너 연결이 끊겼습니다")
    val subtitle = if (lastKakaoMillis <= 0L) "카카오톡 알림 대기" else "마지막 ${formatWhen(lastKakaoMillis, now)}"
    return BannerUi(true, "감시 중", subtitle)
}

@Composable
internal fun HomeRoute(onOpenRules: () -> Unit, onOpenConnection: () -> Unit) {
    val context = LocalContext.current
    val graph = (context.applicationContext as BungaeApp).graph
    val log by graph.log.state.collectAsState()
    var tick by remember { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                ListenerRuntime.refreshProbe(context)
                tick++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val now = remember(tick, log) { System.currentTimeMillis() }
    val access = remember(tick) { Permissions.listenerEnabled(context) }
    val bound = remember(tick) { ListenerRuntime.bound }
    val lastKakao = remember(tick) { ListenerRuntime.clock(context).lastKakaoAtMillis }
    HomeContent(
        banner = listenerBanner(access, bound, lastKakao, now),
        groups = groupFeed(log, now),
        onBanner = onOpenConnection,
        onEmpty = onOpenRules,
    )
}

@Composable
internal fun HomeContent(
    banner: BannerUi,
    groups: List<DayGroup>,
    onBanner: () -> Unit,
    onEmpty: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            ScreenTitle("번개알림")
            Spacer(Modifier.size(16.dp))
            ListenerBanner(banner.online, banner.title, banner.subtitle, onBanner)
        }
        if (groups.isEmpty()) {
            item {
                EmptyState(
                    title = "아직 감지된 글이 없습니다",
                    body = "규칙에 맞는 방이 오면 여기에 모입니다.",
                    action = "규칙 추가",
                    onAction = onEmpty,
                )
            }
        } else {
            groups.forEach { group ->
                item(key = "day-${group.label}") {
                    Text(
                        group.label,
                        modifier = Modifier.padding(top = 12.dp, bottom = 2.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                items(group.cards, key = { it.id }) { card ->
                    DetectionCard(card)
                }
            }
        }
    }
}

@Composable
private fun DetectionCard(card: FeedCard) {
    val tone = tones
    val container = if (card.kind == FeedKind.Meetup) tone.meetup else tone.giveaway
    val content = if (card.kind == FeedKind.Meetup) tone.onMeetup else tone.onGiveaway
    val icon = if (card.kind == FeedKind.Meetup) Sym.bolt else Sym.redeem
    val label = if (card.kind == FeedKind.Meetup) "번개" else "선착순"
    Row(
        modifier = Modifier.fillMaxWidth().bungaeCard().padding(14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier.size(36.dp).background(container, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Symbol(icon, null, tint = content, size = 18.dp)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    card.chat,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(card.time, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                KindChip(label, container, content)
                Text("${card.score}점", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (card.snippet.isNotBlank()) {
                Text(
                    card.snippet,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
