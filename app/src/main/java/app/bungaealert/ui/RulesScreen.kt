package app.bungaealert.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.bungaealert.BungaeApp
import app.bungaealert.detect.AlertMode
import app.bungaealert.detect.MatchMode
import app.bungaealert.detect.WatchRule
import java.util.UUID

enum class RuleFilter(val label: String) {
    All("전체"),
    On("사용"),
    Off("꺼짐"),
    Meetup("번개"),
    Giveaway("선착순"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RulesRoute() {
    val context = LocalContext.current
    val graph = (context.applicationContext as BungaeApp).graph
    val rules by graph.rules.state.collectAsState()
    var filterName by rememberSaveable { mutableStateOf(RuleFilter.All.name) }
    val filter = RuleFilter.valueOf(filterName)
    var editing by remember { mutableStateOf<WatchRule?>(null) }
    var creating by remember { mutableStateOf(false) }
    RulesScreen(
        rules = rules,
        filter = filter,
        onFilter = { filterName = it.name },
        onToggle = { id, enabled ->
            graph.rules.save(graph.rules.current().map { if (it.id == id) it.copy(enabled = enabled) else it })
        },
        onEdit = { editing = it },
        onAdd = { creating = true },
    )
    val draft = when {
        creating -> WatchRule(UUID.randomUUID().toString(), "", MatchMode.ENDS_WITH, true, true, AlertMode.ONCE)
        editing != null -> editing
        else -> null
    }
    if (draft != null) {
        ModalBottomSheet(
            onDismissRequest = { creating = false; editing = null },
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            shape = androidx.compose.foundation.shape.RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            dragHandle = null,
        ) {
            RuleEditor(
                initial = draft,
                isNew = creating,
                onDismiss = { creating = false; editing = null },
                onDelete = if (creating) {
                    null
                } else {
                    {
                        graph.rules.save(graph.rules.current().filterNot { it.id == draft.id })
                        creating = false
                        editing = null
                    }
                },
                onSave = { saved ->
                    val current = graph.rules.current().filterNot { it.id == saved.id }
                    graph.rules.save(current + saved)
                    creating = false
                    editing = null
                },
            )
        }
    }
}

@Composable
fun RulesScreen(
    rules: List<WatchRule>,
    filter: RuleFilter,
    onFilter: (RuleFilter) -> Unit,
    onToggle: (String, Boolean) -> Unit,
    onEdit: (WatchRule) -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shown = rules.filter { it.matches(filter) }
    val enabledCount = rules.count { it.enabled }
    Column(modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 20.dp).padding(top = 12.dp)) {
            ScreenTitle("규칙")
            if (rules.isNotEmpty()) {
                Text(
                    "${enabledCount}개 사용",
                    modifier = Modifier.padding(top = 4.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                ChipRow(
                    labels = RuleFilter.entries.map { it.label },
                    selected = filter.ordinal,
                    onSelect = { onFilter(RuleFilter.entries[it]) },
                )
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                rules.isEmpty() -> EmptyPane(
                    icon = Icons.Rounded.Tune,
                    title = "규칙이 없습니다",
                    message = "감시할 채팅방을 추가하세요",
                    modifier = Modifier.align(Alignment.Center),
                )
                shown.isEmpty() -> EmptyPane(
                    icon = Icons.Rounded.Tune,
                    title = "결과 없음",
                    message = "다른 필터를 골라 보세요",
                    modifier = Modifier.align(Alignment.Center),
                )
                else -> LazyColumn(
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(shown, key = { it.id }) { rule ->
                        RuleRow(rule, onToggle = { onToggle(rule.id, it) }, onEdit = { onEdit(rule) })
                    }
                }
            }
        }
        PrimaryButton(
            text = "규칙 추가",
            onClick = onAdd,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
        )
    }
}

@Composable
private fun RuleRow(rule: WatchRule, onToggle: (Boolean) -> Unit, onEdit: () -> Unit) {
    SurfaceCard(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .clickable(role = Role.Button, onClick = onEdit),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    rule.pattern.trim().firstOrNull()?.toString() ?: "·",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Column(
                Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .clickable(role = Role.Button, onClick = onEdit)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Text(
                    rule.pattern,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (rule.enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    ruleMeta(rule),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            BungaeSwitch(checked = rule.enabled, onCheckedChange = onToggle, label = rule.pattern)
        }
    }
}

@Composable
fun RuleEditor(
    initial: WatchRule,
    isNew: Boolean,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)?,
    onSave: (WatchRule) -> Unit,
    modifier: Modifier = Modifier,
) {
    var pattern by remember(initial.id) { mutableStateOf(initial.pattern) }
    var mode by remember(initial.id) { mutableStateOf(initial.matchMode) }
    var meetup by remember(initial.id) { mutableStateOf(initial.detectMeetup) }
    var giveaway by remember(initial.id) { mutableStateOf(initial.detectGiveaway) }
    var alert by remember(initial.id) { mutableStateOf(initial.alertMode) }
    var enabled by remember(initial.id) { mutableStateOf(initial.enabled) }
    var confirmDelete by remember { mutableStateOf(false) }
    val canSave = pattern.isNotBlank() && (meetup || giveaway)
    Column(
        modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(horizontal = 20.dp)
            .padding(bottom = 24.dp),
    ) {
        SheetHandle()
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (isNew) "규칙 추가" else "규칙 수정",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.headlineSmall,
            )
            Box(
                Modifier
                    .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                    .clip(CircleShape)
                    .clickable(role = Role.Button, onClick = onDismiss),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Close, contentDescription = "닫기", tint = MaterialTheme.colorScheme.onSurface)
            }
        }
        Spacer(Modifier.height(8.dp))
        BungaeTextField(pattern, { pattern = it }, label = "채팅방")
        Spacer(Modifier.height(16.dp))
        SectionLabel("맞추기")
        ChipRow(
            labels = MatchMode.entries.map { matchLabel(it) },
            selected = mode.ordinal,
            onSelect = { mode = MatchMode.entries[it] },
        )
        Spacer(Modifier.height(16.dp))
        SectionLabel("종류")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BungaeChip("번개", meetup, { meetup = !meetup })
            BungaeChip("선착순", giveaway, { giveaway = !giveaway })
        }
        Spacer(Modifier.height(16.dp))
        SectionLabel("알림")
        ChipRow(
            labels = AlertMode.entries.map { alertLabel(it) },
            selected = alert.ordinal,
            onSelect = { alert = AlertMode.entries[it] },
        )
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("사용", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            BungaeSwitch(enabled, { enabled = it }, "사용")
        }
        Spacer(Modifier.height(12.dp))
        PrimaryButton(
            text = "저장",
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
            modifier = Modifier.fillMaxWidth(),
            enabled = canSave,
        )
        if (onDelete != null) {
            DangerButton("삭제", { confirmDelete = true }, Modifier.fillMaxWidth())
        }
    }
    if (confirmDelete && onDelete != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            title = { Text("규칙 삭제") },
            text = { Text("이 규칙을 지울까요?") },
            confirmButton = {
                TextButton(onClick = onDelete) {
                    Text("삭제", color = LocalBungaeExtra.current.danger)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("취소") }
            },
        )
    }
}

private fun WatchRule.matches(filter: RuleFilter): Boolean = when (filter) {
    RuleFilter.All -> true
    RuleFilter.On -> enabled
    RuleFilter.Off -> !enabled
    RuleFilter.Meetup -> detectMeetup
    RuleFilter.Giveaway -> detectGiveaway
}
