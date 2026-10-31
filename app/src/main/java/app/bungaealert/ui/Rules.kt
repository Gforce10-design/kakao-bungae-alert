package app.bungaealert.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.bungaealert.BungaeApp
import app.bungaealert.detect.AlertMode
import app.bungaealert.detect.MatchMode
import app.bungaealert.detect.WatchRule
import java.util.UUID

internal enum class RuleFilter(val label: String) {
    All("전체"),
    On("사용"),
    Off("꺼짐"),
    Meetup("번개"),
    Giveaway("선착순"),
}

internal data class RuleUi(
    val id: String,
    val pattern: String,
    val match: String,
    val alert: String,
    val meetup: Boolean,
    val giveaway: Boolean,
    val enabled: Boolean,
)

private fun WatchRule.toUi() = RuleUi(
    id = id,
    pattern = pattern,
    match = matchMode.short(),
    alert = alertMode.short(),
    meetup = detectMeetup,
    giveaway = detectGiveaway,
    enabled = enabled,
)

private fun MatchMode.short() = when (this) {
    MatchMode.EXACT -> "정확히"
    MatchMode.CONTAINS -> "포함"
    MatchMode.ENDS_WITH -> "끝 일치"
}

private fun AlertMode.short() = when (this) {
    AlertMode.ONCE -> "한 번"
    AlertMode.REPEAT -> "반복"
}

private fun WatchRule.matches(filter: RuleFilter) = when (filter) {
    RuleFilter.All -> true
    RuleFilter.On -> enabled
    RuleFilter.Off -> !enabled
    RuleFilter.Meetup -> detectMeetup
    RuleFilter.Giveaway -> detectGiveaway
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RulesRoute() {
    val context = LocalContext.current
    val graph = (context.applicationContext as BungaeApp).graph
    val rules by graph.rules.state.collectAsState()
    var filter by remember { mutableStateOf(RuleFilter.All) }
    var draft by remember { mutableStateOf<WatchRule?>(null) }
    var isNew by remember { mutableStateOf(false) }
    val shown = rules.filter { it.matches(filter) }
    RulesContent(
        filter = filter,
        rules = shown.map { it.toUi() },
        onFilter = { filter = it },
        onAdd = {
            isNew = true
            draft = WatchRule(
                id = UUID.randomUUID().toString(),
                pattern = "",
                matchMode = MatchMode.ENDS_WITH,
                detectMeetup = true,
                detectGiveaway = true,
                alertMode = AlertMode.ONCE,
            )
        },
        onOpen = { id ->
            rules.find { it.id == id }?.let {
                isNew = false
                draft = it
            }
        },
        onToggle = { id, enabled ->
            graph.rules.save(rules.map { if (it.id == id) it.copy(enabled = enabled) else it })
        },
    )
    val editing = draft
    if (editing != null) {
        ModalBottomSheet(
            onDismissRequest = { draft = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            shape = androidx.compose.foundation.shape.RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        ) {
            RuleEditor(
                initial = editing,
                isNew = isNew,
                onDismiss = { draft = null },
                onSave = { saved ->
                    graph.rules.save(rules.filterNot { it.id == saved.id } + saved)
                    draft = null
                },
                onDelete = if (isNew) {
                    null
                } else {
                    {
                        graph.rules.save(rules.filterNot { it.id == editing.id })
                        draft = null
                    }
                },
            )
        }
    }
}

@Composable
internal fun RulesContent(
    filter: RuleFilter,
    rules: List<RuleUi>,
    onFilter: (RuleFilter) -> Unit,
    onAdd: () -> Unit,
    onOpen: (String) -> Unit,
    onToggle: (String, Boolean) -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ScreenTitle("규칙", modifier = Modifier.weight(1f))
            SymbolButton(Sym.add, "규칙 추가", onAdd)
        }
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()).padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            RuleFilter.entries.forEach { item ->
                SelectChip(item.label, selected = filter == item, onClick = { onFilter(item) })
            }
        }
        if (rules.isEmpty()) {
            EmptyState(
                title = if (filter == RuleFilter.All) "감시할 채팅방이 없습니다" else "이 조건의 규칙이 없습니다",
                body = if (filter == RuleFilter.All) "이름과 글 종류를 정하면 그 방만 봅니다." else "다른 필터를 고르세요.",
                action = if (filter == RuleFilter.All) "규칙 추가" else "전체 보기",
                onAction = { if (filter == RuleFilter.All) onAdd() else onFilter(RuleFilter.All) },
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp),
            ) {
                items(rules, key = { it.id }) { rule ->
                    RuleRow(rule, onOpen, onToggle)
                }
            }
        }
    }
}

@Composable
private fun RuleRow(rule: RuleUi, onOpen: (String) -> Unit, onToggle: (String, Boolean) -> Unit) {
    val tone = tones
    Row(
        modifier = Modifier.fillMaxWidth().bungaeCard(onClick = { onOpen(rule.id) }).padding(start = 14.dp, end = 6.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                monogram(rule.pattern),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
        Column(
            modifier = Modifier.weight(1f).padding(horizontal = 12.dp).alpha(if (rule.enabled) 1f else 0.55f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(rule.pattern, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                "${rule.match} · ${rule.alert}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (rule.meetup) KindChip("번개", tone.meetup, tone.onMeetup)
                if (rule.giveaway) KindChip("선착순", tone.giveaway, tone.onGiveaway)
            }
        }
        Switch(
            checked = rule.enabled,
            onCheckedChange = { onToggle(rule.id, it) },
            modifier = Modifier.heightIn(min = 48.dp),
        )
    }
}

@Composable
internal fun RuleEditor(
    initial: WatchRule,
    isNew: Boolean,
    onDismiss: () -> Unit,
    onSave: (WatchRule) -> Unit,
    onDelete: (() -> Unit)?,
) {
    var pattern by remember(initial.id) { mutableStateOf(initial.pattern) }
    var mode by remember(initial.id) { mutableStateOf(initial.matchMode) }
    var meetup by remember(initial.id) { mutableStateOf(initial.detectMeetup) }
    var giveaway by remember(initial.id) { mutableStateOf(initial.detectGiveaway) }
    var alert by remember(initial.id) { mutableStateOf(initial.alertMode) }
    var enabled by remember(initial.id) { mutableStateOf(initial.enabled) }
    val canSave = pattern.isNotBlank() && (meetup || giveaway)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(if (isNew) "규칙 추가" else "규칙 수정", style = MaterialTheme.typography.headlineMedium)
        OutlinedTextField(
            value = pattern,
            onValueChange = { pattern = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("채팅방 이름") },
            singleLine = true,
        )
        Text("‘수다방’처럼 끝으로도 맞출 수 있습니다.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
            MatchMode.entries.forEach { item ->
                SelectChip(item.short(), selected = mode == item, onClick = { mode = item })
            }
        }
        ToggleLine("번개", meetup) { meetup = it }
        ToggleLine("선착순", giveaway) { giveaway = it }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            AlertMode.entries.forEach { item ->
                SelectChip(item.short(), selected = alert == item, onClick = { alert = item })
            }
        }
        ToggleLine("사용", enabled) { enabled = it }
        PrimaryButton(
            text = "저장",
            enabled = canSave,
            onClick = {
                onSave(
                    initial.copy(
                        pattern = pattern.trim(),
                        matchMode = mode,
                        detectMeetup = meetup,
                        detectGiveaway = giveaway,
                        alertMode = alert,
                        enabled = enabled,
                    ),
                )
            },
        )
        if (onDelete != null) {
            QuietButton(
                "삭제",
                onDelete,
                modifier = Modifier.align(Alignment.CenterHorizontally),
                color = MaterialTheme.colorScheme.error,
            )
        } else {
            QuietButton("취소", onDismiss, modifier = Modifier.align(Alignment.CenterHorizontally))
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun ToggleLine(label: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Switch(checked, onChecked)
    }
}

private fun monogram(pattern: String): String {
    val trimmed = pattern.trim()
    if (trimmed.isEmpty()) return "?"
    val end = trimmed.offsetByCodePoints(0, 1)
    return trimmed.substring(0, end)
}
