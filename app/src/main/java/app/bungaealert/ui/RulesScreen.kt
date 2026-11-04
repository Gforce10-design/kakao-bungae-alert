package app.bungaealert.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.material3.ButtonDefaults
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
import app.bungaealert.detect.KeywordCodec
import app.bungaealert.detect.KeywordMatcher
import app.bungaealert.detect.MatchKeyword
import app.bungaealert.detect.MatchMode
import app.bungaealert.detect.RuleKeywords
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
    val keywords by graph.keywords.state.collectAsState()
    var filterName by rememberSaveable { mutableStateOf(RuleFilter.All.name) }
    val filter = RuleFilter.valueOf(filterName)
    var editing by remember { mutableStateOf<WatchRule?>(null) }
    var creating by remember { mutableStateOf(false) }
    var editingKeyword by remember { mutableStateOf<MatchKeyword?>(null) }
    var creatingKeyword by remember { mutableStateOf(false) }
    var keywordDraftId by rememberSaveable { mutableStateOf("") }
    var ruleDraftId by rememberSaveable { mutableStateOf("") }
    RulesScreen(
        rules = rules,
        filter = filter,
        onFilter = { filterName = it.name },
        onToggle = { id, enabled ->
            graph.rules.save(graph.rules.current().map { if (it.id == id) it.copy(enabled = enabled) else it })
        },
        onEdit = {
            creatingKeyword = false
            editingKeyword = null
            editing = it
        },
        onAdd = {
            creatingKeyword = false
            editingKeyword = null
            ruleDraftId = UUID.randomUUID().toString()
            creating = true
        },
        keywords = keywords,
        onToggleKeyword = { id, enabled ->
            graph.keywords.save(graph.keywords.current().map { if (it.id == id) it.copy(enabled = enabled) else it })
        },
        onEditKeyword = {
            creating = false
            editing = null
            creatingKeyword = false
            editingKeyword = it
        },
        onAddKeyword = {
            creating = false
            editing = null
            keywordDraftId = UUID.randomUUID().toString()
            editingKeyword = null
            creatingKeyword = true
        },
    )
    val keywordDraft = when {
        creatingKeyword -> MatchKeyword(keywordDraftId, "", true)
        editingKeyword != null -> editingKeyword
        else -> null
    }
    val draft = when {
        keywordDraft != null -> null
        creating -> WatchRule(ruleDraftId, "", MatchMode.ENDS_WITH, true, true, AlertMode.ONCE)
        editing != null -> editing
        else -> null
    }
    if (keywordDraft != null) {
        ModalBottomSheet(
            onDismissRequest = { creatingKeyword = false; editingKeyword = null },
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            shape = androidx.compose.foundation.shape.RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            dragHandle = null,
        ) {
            KeywordEditor(
                initial = keywordDraft,
                isNew = creatingKeyword,
                existing = keywords,
                onDismiss = { creatingKeyword = false; editingKeyword = null },
                onDelete = if (creatingKeyword) {
                    null
                } else {
                    {
                        graph.keywords.save(graph.keywords.current().filterNot { it.id == keywordDraft.id })
                        creatingKeyword = false
                        editingKeyword = null
                    }
                },
                onSave = { saved ->
                    val current = graph.keywords.current()
                    val next = if (current.any { it.id == saved.id }) {
                        current.map { if (it.id == saved.id) saved else it }
                    } else {
                        current + saved
                    }
                    graph.keywords.save(next)
                    creatingKeyword = false
                    editingKeyword = null
                },
            )
        }
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
                keywords = keywords,
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
    keywords: List<MatchKeyword> = emptyList(),
    onToggleKeyword: (String, Boolean) -> Unit = { _, _ -> },
    onEditKeyword: (MatchKeyword) -> Unit = {},
    onAddKeyword: () -> Unit = {},
) {
    val shown = rules.filter { it.matches(filter) }
    val enabledCount = rules.count { it.enabled }
    val enabledKeywords = keywords.count { it.enabled }
    Column(modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 20.dp).padding(top = 12.dp)) {
            ScreenTitle("규칙")
        }
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                SectionLabel("찾을 말")
                Text(
                    if (keywords.isEmpty()) {
                        "찾을 말이 없습니다. 알림을 고르지 않습니다"
                    } else {
                        "제목과 내용에서 찾습니다 · ${enabledKeywords}개 사용"
                    },
                    modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(keywords, key = { it.id }) { keyword ->
                KeywordRow(
                    keyword,
                    onToggle = { onToggleKeyword(keyword.id, it) },
                    onEdit = { onEditKeyword(keyword) },
                )
            }
            item {
                SecondaryButton("찾을 말 추가", onAddKeyword, Modifier.fillMaxWidth())
            }
            item {
                Spacer(Modifier.height(8.dp))
                SectionLabel("채팅방")
                if (rules.isNotEmpty()) {
                    Text(
                        "${enabledCount}개 사용",
                        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    ChipRow(
                        labels = RuleFilter.entries.map { it.label },
                        selected = filter.ordinal,
                        onSelect = { onFilter(RuleFilter.entries[it]) },
                    )
                }
            }
            when {
                rules.isEmpty() -> item {
                    Text(
                        "감시할 채팅방을 추가하세요",
                        modifier = Modifier.padding(start = 4.dp, top = 4.dp),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                shown.isEmpty() -> item {
                    Text(
                        "다른 필터를 골라 보세요",
                        modifier = Modifier.padding(start = 4.dp, top = 4.dp),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                else -> items(shown, key = { it.id }) { rule ->
                    RuleRow(rule, keywords, onToggle = { onToggle(rule.id, it) }, onEdit = { onEdit(rule) })
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
private fun KeywordRow(keyword: MatchKeyword, onToggle: (Boolean) -> Unit, onEdit: () -> Unit) {
    SurfaceCard(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
                    .clickable(role = Role.Button, onClick = onEdit),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    keyword.text.trim().firstOrNull()?.toString() ?: "·",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimary,
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
                    keyword.text,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (keyword.enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    if (keyword.enabled) "사용" else "꺼짐",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            BungaeSwitch(checked = keyword.enabled, onCheckedChange = onToggle, label = keyword.text)
        }
    }
}

@Composable
private fun KeywordEditor(
    initial: MatchKeyword,
    isNew: Boolean,
    existing: List<MatchKeyword>,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)?,
    onSave: (MatchKeyword) -> Unit,
    modifier: Modifier = Modifier,
) {
    var text by remember(initial.id) { mutableStateOf(initial.text) }
    var enabled by remember(initial.id) { mutableStateOf(initial.enabled) }
    var confirmDelete by remember { mutableStateOf(false) }
    val error = KeywordMatcher.validationError(text, existing, editingId = initial.id)
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
                if (isNew) "찾을 말 추가" else "찾을 말 수정",
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
        BungaeTextField(
            text,
            { text = it },
            label = "단어 또는 문장",
            singleLine = false,
            minLines = 2,
            maxLines = 6,
        )
        if (error != null) {
            Spacer(Modifier.height(8.dp))
            Text(error, style = MaterialTheme.typography.bodyMedium, color = LocalBungaeExtra.current.danger)
        } else {
            Spacer(Modifier.height(8.dp))
            Text(
                "띄어쓰기와 대소문자는 가리지 않고, 문장은 통째로 찾습니다",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("사용", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            BungaeSwitch(enabled, { enabled = it }, "사용")
        }
        Spacer(Modifier.height(12.dp))
        PrimaryButton(
            text = "저장",
            onClick = {
                val canonical = KeywordMatcher.canonicalize(text)
                if (KeywordMatcher.validationError(canonical, existing, initial.id) != null) return@PrimaryButton
                onSave(initial.copy(text = canonical, enabled = enabled))
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = error == null,
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
            title = { Text("찾을 말 삭제") },
            text = { Text("이 말을 지울까요?") },
            confirmButton = {
                TextButton(onClick = onDelete) {
                    Text("삭제", color = LocalBungaeExtra.current.danger)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { confirmDelete = false },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
                ) { Text("취소") }
            },
        )
    }
}

@Composable
private fun RuleRow(
    rule: WatchRule,
    keywords: List<MatchKeyword>,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
) {
    SurfaceCard(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
                    .clickable(role = Role.Button, onClick = onEdit),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    rule.pattern.trim().firstOrNull()?.toString() ?: "·",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimary,
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
                    ruleMeta(rule, keywords),
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
@OptIn(ExperimentalLayoutApi::class)
fun RuleEditor(
    initial: WatchRule,
    isNew: Boolean,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)?,
    onSave: (WatchRule) -> Unit,
    modifier: Modifier = Modifier,
    keywords: List<MatchKeyword> = KeywordCodec.defaults(),
) {
    var pattern by remember(initial.id) { mutableStateOf(initial.pattern) }
    var mode by remember(initial.id) { mutableStateOf(initial.matchMode) }
    var alert by remember(initial.id) { mutableStateOf(initial.alertMode) }
    var enabled by remember(initial.id) { mutableStateOf(initial.enabled) }
    var toggles by remember(initial.id) { mutableStateOf<Map<String, Boolean>>(emptyMap()) }
    var confirmDelete by remember { mutableStateOf(false) }
    val options = keywords.filter { keyword ->
        keyword.enabled && KeywordMatcher.canonicalize(keyword.text).isNotEmpty()
    }
    fun chosen(keyword: MatchKeyword): Boolean = toggles[keyword.id] ?: RuleKeywords.applies(initial, keyword)
    val selected = options.filter { chosen(it) }
    val canSave = pattern.isNotBlank() && selected.isNotEmpty()
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
        SectionLabel("찾을 말")
        if (options.isEmpty()) {
            Text(
                "사용 중인 찾을 말이 없습니다",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                options.forEach { keyword ->
                    val on = chosen(keyword)
                    BungaeChip(
                        label = keyword.text,
                        selected = on,
                        onClick = { toggles = toggles + (keyword.id to !on) },
                        modifier = Modifier.widthIn(max = 320.dp),
                        maxLines = 3,
                    )
                }
            }
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
                    RuleKeywords.withSelection(
                        rule = initial,
                        pattern = pattern.trim(),
                        matchMode = mode,
                        alertMode = alert,
                        enabled = enabled,
                        selected = selected,
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
                TextButton(
                    onClick = { confirmDelete = false },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
                ) { Text("취소") }
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
