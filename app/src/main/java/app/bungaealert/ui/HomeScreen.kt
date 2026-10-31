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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material.icons.rounded.Redeem
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.bungaealert.BungaeApp
import app.bungaealert.detect.PostType
import app.bungaealert.notify.ListenerRuntime

@Composable
fun HomeRoute(onStatus: () -> Unit) {
    val context = LocalContext.current
    val graph = (context.applicationContext as BungaeApp).graph
    val log by graph.log.state.collectAsState()
    var tick by remember { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) tick++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val health = remember(tick) {
        when {
            !Permissions.listenerEnabled(context) -> ListenerHealthUi.Blocked
            !ListenerRuntime.bound -> ListenerHealthUi.Unbound
            else -> ListenerHealthUi.Watching
        }
    }
    HomeScreen(
        health = health,
        groups = groupDetections(log, System.currentTimeMillis()),
        onBanner = {
            if (!Permissions.listenerEnabled(context)) {
                openSetting(context, Permissions.listenerSettings(), "알림 접근 설정을 열지 못했습니다.")
            } else {
                onStatus()
            }
        },
    )
}

@Composable
fun HomeScreen(
    health: ListenerHealthUi,
    groups: List<DetectionGroupUi>,
    onBanner: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { ScreenTitle("번개알림") }
        item { ListenerBanner(health, onBanner) }
        if (groups.isEmpty()) {
            item {
                EmptyPane(
                    icon = Icons.Rounded.NotificationsNone,
                    title = "아직 없어요",
                    message = "감지된 글이 여기에 모입니다",
                )
            }
        } else {
            groups.forEach { group ->
                item(key = "day-${group.label}") {
                    Text(
                        group.label,
                        modifier = Modifier.padding(start = 4.dp, top = 12.dp, bottom = 4.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                items(group.items, key = { it.id }) { card ->
                    DetectionCard(card)
                }
            }
        }
    }
}

@Composable
private fun DetectionCard(card: DetectionCardUi) {
    val extra = LocalBungaeExtra.current
    val icon: ImageVector
    val bubble: Color
    val tint: Color
    when (card.type) {
        PostType.MEETUP -> {
            icon = Icons.Rounded.Bolt
            bubble = extra.meetupBg
            tint = extra.meetupFg
        }
        PostType.GIVEAWAY -> {
            icon = Icons.Rounded.Redeem
            bubble = extra.giveawayBg
            tint = extra.giveawayFg
        }
        null -> {
            icon = Icons.Rounded.NotificationsNone
            bubble = MaterialTheme.colorScheme.surfaceVariant
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        }
    }
    SurfaceCard(contentPadding = PaddingValues(14.dp)) {
        Row(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.Top) {
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(bubble),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = tint)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        card.chatName,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        card.timeLabel,
                        style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (card.snippet.isNotBlank()) {
                    Spacer(Modifier.size(4.dp))
                    Text(
                        card.snippet,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.size(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (card.type != null) TypeChip(card.type)
                    Spacer(Modifier.weight(1f))
                    Text(
                        "${card.score}점",
                        style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}
