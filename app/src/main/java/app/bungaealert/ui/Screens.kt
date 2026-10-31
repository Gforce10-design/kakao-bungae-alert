package app.bungaealert.ui

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import app.bungaealert.BungaeApp
import app.bungaealert.MainActivity
import app.bungaealert.notify.ListenerRuntime
import app.bungaealert.ai.NtfySubscribeService
import app.bungaealert.auth.ClaudeSubscription
import app.bungaealert.auth.CodexSubscription
import app.bungaealert.auth.CopilotSubscription
import app.bungaealert.auth.upstream.CopilotDeviceFlow
import app.bungaealert.auth.upstream.CopilotFeatureFlag
import app.bungaealert.data.AppSettings
import app.bungaealert.data.SecretStore
import app.bungaealert.detect.AlertMode
import app.bungaealert.detect.BackendIds
import app.bungaealert.detect.DecisionPolicy
import app.bungaealert.detect.GrokRoutinePrompt
import app.bungaealert.detect.MatchMode
import app.bungaealert.detect.PostType
import app.bungaealert.detect.ScoreClassifier
import app.bungaealert.detect.Sensitivity
import app.bungaealert.detect.TopicGenerator
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import app.bungaealert.detect.WatchRule as Rule

private const val RISK = """이 로그인 방식은 공식 앱이 아닙니다.

ChatGPT·Codex, Claude, GitHub Copilot 구독으로 판정을 맡기려면, 각 공식 프로그램(Codex CLI, Claude Code, VS Code Copilot)이 쓰는 로그인 절차를 이 앱이 그대로 따라갑니다.

· 제공자가 언제든 막거나 바꿀 수 있어 갑자기 동작하지 않을 수 있습니다.
· 제공자 이용약관에 어긋날 수 있습니다.
· 그 때문에 계정이 제한될 위험이 있습니다.
· 토큰은 이 기기의 Android Keystore로 암호화해 저장합니다. 판정이 필요할 때만 해당 제공자에게 메시지 내용이 전송됩니다.

동의하기 전에는 이 기능이 꺼져 있으며, 어떤 로그인 요청도 보내지 않습니다."""

@Composable
fun RootScreen() {
    val context = LocalContext.current
    val graph = (context.applicationContext as BungaeApp).graph
    val settings by graph.settings.state.collectAsState()
    val nav = rememberNavController()
    val start = if (settings.onboardingDone) "home" else "onboarding"
    Scaffold(
        bottomBar = {
            val route = nav.currentBackStackEntryAsState().value?.destination?.route
            if (route != null && route != "onboarding" && route != "oss") {
                NavigationBar {
                    listOf(
                        "home" to "홈",
                        "rules" to "규칙",
                        "log" to "기록",
                        "test" to "시험",
                        "settings" to "설정",
                    ).forEach { (id, label) ->
                        NavigationBarItem(
                            selected = route == id,
                            onClick = { nav.navigate(id) { popUpTo("home") { saveState = true }; launchSingleTop = true; restoreState = true } },
                            icon = { Text(label.take(1)) },
                            label = { Text(label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        val activity = context as? android.app.Activity
        LaunchedEffect(settings.onboardingDone) {
            if (!settings.onboardingDone) return@LaunchedEffect
            if (activity?.intent?.getStringExtra(MainActivity.EXTRA_ROUTE) != "status") return@LaunchedEffect
            nav.navigate("status") { launchSingleTop = true }
            activity.intent.removeExtra(MainActivity.EXTRA_ROUTE)
        }
        NavHost(nav, startDestination = start, modifier = Modifier.padding(padding)) {
            composable("onboarding") { OnboardingScreen { graph.settings.update(graph.settings.current().copy(onboardingDone = true)); nav.navigate("home") { popUpTo("onboarding") { inclusive = true } } } }
            composable("home") { HomeScreen(nav) }
            composable("status") { ListenerStatusScreen() }
            composable("rules") { RulesScreen() }
            composable("log") { LogScreen() }
            composable("test") { TestScreen() }
            composable("settings") { SettingsScreen(nav) }
            composable("oss") { OssScreen { nav.popBackStack() } }
        }
    }
}

@Composable
private fun OnboardingScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) tick++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { tick++ }
    val steps = listOf(
        Step("알림 접근", "카카오톡 알림을 읽으려면 알림 접근 권한이 필요합니다. 무음 대화방도 채널 중요도와 상관없이 읽습니다.", Permissions.listenerEnabled(context)) { context.safeStart(Permissions.listenerSettings()) },
        Step("알림 표시", "이 앱이 직접 알림을 띄웁니다.", Permissions.notificationsAllowed(context)) {
            if (Build.VERSION.SDK_INT >= 33) permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            else context.safeStart(Permissions.appNotificationSettings(context))
        },
        Step("전체 화면", "반복 알림이 잠금 화면 위로 뜨려면 Android 14 이상에서 전체 화면 인텐트를 허용해야 합니다.", Permissions.fullScreenAllowed(context)) { context.safeStart(Permissions.fullScreenSettings(context)) },
        Step("방해금지 예외", "방해금지 중에도 울리려면 이 앱을 예외로 허용하세요.", Permissions.dndAllowed(context)) { context.safeStart(Permissions.dndSettings()) },
        Step("배터리", "Android 배터리 최적화 제외입니다. 삼성에서는 이것만으로 잠자기 목록에서 빠지지 않습니다.", Permissions.batteryUnrestricted(context)) { context.safeStart(Permissions.batterySettings(context)) },
        Step(
            "삼성 잠자기",
            if (Permissions.isSamsung()) "배터리 및 디바이스 케어 → 배터리 → 백그라운드 사용 제한에서 잠자는 앱·깊게 잠자는 앱을 빼고, 잠들지 않는 앱에 넣으세요. 사용하지 않는 앱 절전도 끄거나 이 앱을 예외로 두세요. 앱은 그 목록에 들어 있는지를 읽지 못합니다." else "이 기기는 삼성이 아닙니다. 이 단계는 지나도 됩니다.",
            !Permissions.isSamsung(),
        ) { context.safeStart(Permissions.samsungBackgroundList(2)) },
        Step("제한된 설정", "파일로 설치한 앱은 Android 13 이상에서 알림 접근이 막힐 수 있습니다. 앱 정보의 메뉴에서 제한된 설정을 허용한 뒤 알림 접근을 켜세요.", Permissions.listenerEnabled(context)) { context.safeStart(Permissions.appDetails(context)) },
    )
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("처음 설정", style = MaterialTheme.typography.headlineMedium)
        Text("번개알림은 카카오톡 알림에서 번개모임 모집과 선착순 나눔만 골라 바로 알려 줍니다. 광고와 분석 도구는 없습니다.")
        if (tick < 0) Text("")
        steps.forEach { step ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(step.title, style = MaterialTheme.typography.titleMedium)
                    Text(step.body)
                    Text(if (step.done) "허용됨" else "아직 아님")
                    Button(onClick = { step.open(); tick++ }) { Text("설정 열기") }
                }
            }
        }
        Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("시작하기") }
        Text("권한은 나중에 설정에서도 다시 열 수 있습니다.", style = MaterialTheme.typography.bodySmall)
    }
}

private data class Step(val title: String, val body: String, val done: Boolean, val open: () -> Unit)

@Composable
private fun HomeScreen(nav: NavHostController) {
    val context = LocalContext.current
    val graph = (context.applicationContext as BungaeApp).graph
    val settings by graph.settings.state.collectAsState()
    val rules by graph.rules.state.collectAsState()
    val log by graph.log.state.collectAsState()
    var refresh by remember { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("번개알림", style = MaterialTheme.typography.headlineMedium)
        Text(homeListenerLine(context))
        Text("민감도 ${settings.sensitivity} · 기준 ${Sensitivity.cutoff(settings.sensitivity)}점")
        Text("감시 규칙 ${rules.count { it.enabled }}개")
        val last = log.firstOrNull()
        Text(if (last == null) "아직 기록이 없습니다." else "최근: ${last.chatName} ${last.score}점 ${if (last.alerted) "알림" else "통과"}")
        Button(onClick = { nav.navigate("rules") }) { Text("규칙 만들기") }
        OutlinedButton(onClick = { nav.navigate("status") }) { Text("연결 상태") }
        OutlinedButton(onClick = { refresh++ }) { Text("권한 상태 새로고침") }
        if (refresh >= 0) {
            Text(permissionSummary(context))
        }
    }
}

private fun homeListenerLine(context: Context): String {
    val access = Permissions.listenerEnabled(context)
    val bound = ListenerRuntime.bound
    return when {
        !access -> "알림 접근이 꺼져 있습니다. 연결 상태에서 켜 주세요."
        bound -> "알림 접근이 허용됐고, 리스너가 연결되어 있습니다."
        else -> "알림 접근은 허용돼 있지만 리스너가 연결되지 않았습니다."
    }
}

private fun permissionSummary(context: Context): String = listOf(
    "알림 접근" to Permissions.listenerEnabled(context),
    "리스너 연결" to ListenerRuntime.bound,
    "알림 표시" to Permissions.notificationsAllowed(context),
    "전체 화면" to Permissions.fullScreenAllowed(context),
    "방해금지" to Permissions.dndAllowed(context),
    "배터리" to Permissions.batteryUnrestricted(context),
).joinToString("\n") { (name, ok) -> "$name: ${if (ok) "허용" else "필요"}" }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RulesScreen() {
    val context = LocalContext.current
    val graph = (context.applicationContext as BungaeApp).graph
    val rules by graph.rules.state.collectAsState()
    var editing by remember { mutableStateOf<Rule?>(null) }
    var creating by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("채팅방 규칙", style = MaterialTheme.typography.headlineSmall)
        Text("예: 끝이 ‘수다방’(10월의 수다방), 정확히 ‘경제적 자유를 위하여~’.")
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
            items(rules, key = { it.id }) { rule ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text(rule.pattern, style = MaterialTheme.typography.titleMedium)
                        Text("${rule.matchMode.label} · ${rule.alertMode.label} · ${if (rule.enabled) "사용" else "끔"}")
                        Text(buildString {
                            if (rule.detectMeetup) append("번개모임 ")
                            if (rule.detectGiveaway) append("선착순")
                        })
                        Row {
                            TextButton(onClick = { editing = rule }) { Text("수정") }
                            TextButton(onClick = { graph.rules.save(rules.filterNot { it.id == rule.id }) }) { Text("삭제") }
                        }
                    }
                }
            }
        }
        Button(onClick = { creating = true }, modifier = Modifier.fillMaxWidth()) { Text("규칙 추가") }
    }
    val draft = when {
        creating -> Rule(UUID.randomUUID().toString(), "", MatchMode.ENDS_WITH, true, true, AlertMode.ONCE)
        editing != null -> editing
        else -> null
    }
    if (draft != null) {
        RuleDialog(draft, onDismiss = { creating = false; editing = null }) { saved ->
            val next = rules.filterNot { it.id == saved.id } + saved
            graph.rules.save(next)
            creating = false
            editing = null
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RuleDialog(initial: Rule, onDismiss: () -> Unit, onSave: (Rule) -> Unit) {
    var pattern by remember { mutableStateOf(initial.pattern) }
    var mode by remember { mutableStateOf(initial.matchMode) }
    var meetup by remember { mutableStateOf(initial.detectMeetup) }
    var giveaway by remember { mutableStateOf(initial.detectGiveaway) }
    var alert by remember { mutableStateOf(initial.alertMode) }
    var enabled by remember { mutableStateOf(initial.enabled) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("규칙") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(pattern, { pattern = it }, label = { Text("채팅방 이름") }, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    MatchMode.entries.forEach { item ->
                        FilterChip(selected = mode == item, onClick = { mode = item }, label = { Text(item.label) })
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(meetup, { meetup = it }); Text("번개모임")
                    Checkbox(giveaway, { giveaway = it }); Text("선착순 나눔")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AlertMode.entries.forEach { item ->
                        FilterChip(selected = alert == item, onClick = { alert = item }, label = { Text(item.label) })
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("사용"); Switch(enabled, { enabled = it })
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (pattern.isNotBlank() && (meetup || giveaway)) {
                    onSave(initial.copy(pattern = pattern.trim(), matchMode = mode, detectMeetup = meetup, detectGiveaway = giveaway, alertMode = alert, enabled = enabled))
                }
            }) { Text("저장") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}

@Composable
private fun LogScreen() {
    val context = LocalContext.current
    val log by (context.applicationContext as BungaeApp).graph.log.state.collectAsState()
    val format = remember { SimpleDateFormat("M/d HH:mm", Locale.KOREA) }
    if (log.isEmpty()) {
        Column(Modifier.fillMaxSize().padding(20.dp)) { Text("최근 판정이 없습니다. 감시 중인 방에서 점수가 있는 글만 최대 100개 남깁니다.") }
        return
    }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(log, key = { it.id }) { item ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("${format.format(Date(item.atMillis))} · ${item.chatName}", style = MaterialTheme.typography.titleSmall)
                    Text("${item.score}점 · 모임 ${item.meetupScore} · 나눔 ${item.giveawayScore} · ${if (item.alerted) item.alertType ?: "알림" else "알림 없음"}")
                    Text(item.text, maxLines = 4)
                    Text(item.summary, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun TestScreen() {
    val context = LocalContext.current
    val graph = (context.applicationContext as BungaeApp).graph
    var text by remember { mutableStateOf("") }
    var chat by remember { mutableStateOf("시험방") }
    var result by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("문장 시험", style = MaterialTheme.typography.headlineSmall)
        Text("붙여 넣은 글의 점수와 이유를 봅니다. 기준 근처면 켜 둔 보조 판정을 호출할 수 있습니다.")
        OutlinedTextField(chat, { chat = it }, label = { Text("채팅방 이름") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(text, { text = it }, label = { Text("메시지") }, modifier = Modifier.fillMaxWidth(), minLines = 4)
        Button(onClick = {
            busy = true
            scope.launch {
                val settings = graph.settings.current()
                val preview = ScoreClassifier.score(text)
                val eval = DecisionPolicy.evaluate(
                    text = text,
                    enabled = setOf(PostType.MEETUP, PostType.GIVEAWAY),
                    sensitivity = settings.sensitivity,
                    band = settings.borderlineBand,
                    judges = graph.judges(),
                    chatName = chat.ifBlank { "시험방" },
                    sender = null,
                )
                result = buildString {
                    appendLine("모임 ${preview.meetup}점 · 나눔 ${preview.giveaway}점")
                    appendLine("기준 ${eval.cutoff}점 · ${eval.zone}")
                    appendLine(if (eval.alert) "알림: ${eval.alertType?.label}" else "알림 없음")
                    append(eval.summary)
                }
                busy = false
            }
        }, enabled = !busy) { Text("판정") }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (result.isNotBlank()) Text(result)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(nav: NavHostController) {
    val context = LocalContext.current
    val graph = (context.applicationContext as BungaeApp).graph
    val settings by graph.settings.state.collectAsState()
    val scope = rememberCoroutineScope()
    var gemini by remember { mutableStateOf(graph.secrets.get(SecretStore.GEMINI_KEY)) }
    var openai by remember { mutableStateOf(graph.secrets.get(SecretStore.OPENAI_KEY)) }
    var claude by remember { mutableStateOf(graph.secrets.get(SecretStore.CLAUDE_KEY)) }
    var webhook by remember { mutableStateOf(graph.secrets.get(SecretStore.GROK_URL)) }
    var header by remember { mutableStateOf(graph.secrets.get(SecretStore.GROK_HEADER)) }
    var risk by remember { mutableStateOf(false) }
    var device by remember { mutableStateOf<CopilotDeviceFlow.DeviceAuthorization?>(null) }
    var status by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("설정", style = MaterialTheme.typography.headlineSmall)
        Text("민감도 ${settings.sensitivity} · ${Sensitivity.cutoff(settings.sensitivity)}점 이상이면 알림. 그 근처만 보조 판정에 넘깁니다.")
        Slider(settings.sensitivity / 100f, { graph.settings.update(settings.copy(sensitivity = (it * 100).toInt())) })
        Text("반복 알림 최대 ${settings.maxRingSeconds / 60}분 ${settings.maxRingSeconds % 60}초")
        Slider(
            (settings.maxRingSeconds.coerceIn(15, 600)) / 600f,
            { graph.settings.update(settings.copy(maxRingSeconds = (it * 600).toInt().coerceAtLeast(15))) },
        )
        Text("보조 판정 순서. 위에서부터 준비된 것만 쓰고, 실패하면 다음, 모두 실패하면 점수로 결정합니다.")
        settings.backendOrder.forEachIndexed { index, id ->
            val enabled = id in settings.enabledBackends
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) { Text(BackendIds.label(id)) }
                TextButton(onClick = { if (index > 0) moveBackend(graph, settings, index, -1) }, enabled = index > 0) { Text("위") }
                TextButton(onClick = { if (index < settings.backendOrder.lastIndex) moveBackend(graph, settings, index, 1) }) { Text("아래") }
                Switch(enabled, { checked ->
                    val next = settings.enabledBackends.toMutableSet()
                    if (checked) next += id else next -= id
                    graph.settings.update(settings.copy(enabledBackends = next))
                    if (id == BackendIds.GROK_WEBHOOK) {
                        if (checked && webhook.isNotBlank()) NtfySubscribeService.start(context) else NtfySubscribeService.stop(context)
                    }
                })
            }
        }
        Text("API 키는 Keystore로 암호화됩니다. 네트워크는 여기서 켠 판정에만 사용합니다.")
        SecretField("Gemini API 키", gemini) { gemini = it; graph.secrets.put(SecretStore.GEMINI_KEY, it) }
        ModelField("Gemini 모델", settings.geminiModel) { graph.settings.update(graph.settings.current().copy(geminiModel = it)) }
        SecretField("OpenAI API 키", openai) { openai = it; graph.secrets.put(SecretStore.OPENAI_KEY, it) }
        ModelField("OpenAI 모델", settings.openaiModel) { graph.settings.update(graph.settings.current().copy(openaiModel = it)) }
        SecretField("Claude API 키", claude) { claude = it; graph.secrets.put(SecretStore.CLAUDE_KEY, it) }
        ModelField("Claude 모델", settings.claudeModel) { graph.settings.update(graph.settings.current().copy(claudeModel = it)) }
        Text("구독 계정", style = MaterialTheme.typography.titleMedium)
        Text("비공식 경로입니다. 동의 전에는 로그인 버튼을 눌러도 요청을 보내지 않습니다.")
        if (!settings.subscriptionRiskAccepted) {
            Button(onClick = { risk = true }) { Text("위험 안내 읽기") }
        } else {
            Text("위험 안내에 동의했습니다.")
            Button(onClick = {
                scope.launch {
                    status = CodexSubscription.login(context, graph.secrets).message
                }
            }) { Text("ChatGPT·Codex 로그인") }
            Button(onClick = {
                scope.launch { status = ClaudeSubscription.login(context, graph.secrets).message }
            }) { Text("Claude 로그인") }
            Button(onClick = {
                scope.launch {
                    try {
                        device = CopilotSubscription.startDevice()
                    } catch (e: Exception) {
                        status = e.message ?: "Copilot 로그인을 시작하지 못했습니다."
                    }
                }
            }) { Text("Copilot 기기 코드") }
            OutlinedButton(onClick = {
                graph.secrets.clear(SecretStore.CODEX_TOKENS)
                graph.secrets.clear(SecretStore.CLAUDE_TOKENS)
                graph.secrets.clear(SecretStore.COPILOT_GITHUB)
                graph.secrets.clear(SecretStore.COPILOT_SESSION)
                status = "구독 토큰을 지웠습니다."
            }) { Text("구독 로그아웃") }
            ModelField("Codex 모델", settings.codexModel) { graph.settings.update(graph.settings.current().copy(codexModel = it)) }
            ModelField("Copilot 모델", settings.copilotModel) { graph.settings.update(graph.settings.current().copy(copilotModel = it)) }
        }
        Text("Grok Bot 웹훅", style = MaterialTheme.typography.titleMedium)
        Text("선착순에는 느립니다. 응답이 수십 초 걸릴 수 있고 Grok Bot 사용량을 씁니다. 웹훅 주소와 ntfy 토픽은 직접 넣습니다. 앱에 정해 둔 주소는 없습니다.")
        SecretField("웹훅 URL", webhook) { webhook = it; graph.secrets.put(SecretStore.GROK_URL, it) }
        OutlinedTextField(settings.grokHeaderName, { graph.settings.update(graph.settings.current().copy(grokHeaderName = it)) }, label = { Text("인증 헤더 이름") }, modifier = Modifier.fillMaxWidth())
        SecretField("인증 헤더 값", header) { header = it; graph.secrets.put(SecretStore.GROK_HEADER, it) }
        OutlinedTextField(settings.ntfyServer, { graph.settings.update(graph.settings.current().copy(ntfyServer = it)) }, label = { Text("ntfy 서버") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(settings.ntfyTopic, { graph.settings.update(graph.settings.current().copy(ntfyTopic = it.trim())) }, label = { Text("ntfy 토픽") }, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { copy(context, settings.ntfyTopic) }) { Text("토픽 복사") }
            OutlinedButton(onClick = { graph.settings.update(graph.settings.current().copy(ntfyTopic = TopicGenerator.random())) }) { Text("토픽 새로 만들기") }
        }
        Text("그록 제한 시간 ${settings.grokTimeoutSeconds}초")
        Slider((settings.grokTimeoutSeconds.coerceIn(5, 180)) / 180f, { graph.settings.update(graph.settings.current().copy(grokTimeoutSeconds = (it * 180).toInt().coerceAtLeast(5))) })
        Button(onClick = { copy(context, GrokRoutinePrompt.korean(settings.ntfyServer, settings.ntfyTopic)) }) { Text("루틴 안내문 복사") }
        Text("권한", style = MaterialTheme.typography.titleMedium)
        OutlinedButton(onClick = { context.safeStart(Permissions.listenerSettings()) }, modifier = Modifier.fillMaxWidth()) { Text("알림 접근") }
        OutlinedButton(onClick = { context.safeStart(Permissions.fullScreenSettings(context)) }, modifier = Modifier.fillMaxWidth()) { Text("전체 화면 인텐트") }
        OutlinedButton(onClick = { context.safeStart(Permissions.dndSettings()) }, modifier = Modifier.fillMaxWidth()) { Text("방해금지 예외") }
        OutlinedButton(onClick = { context.safeStart(Permissions.batterySettings(context)) }, modifier = Modifier.fillMaxWidth()) { Text("배터리 최적화 예외") }
        OutlinedButton(onClick = { nav.navigate("status") }, modifier = Modifier.fillMaxWidth()) { Text("연결 상태") }
        TextButton(onClick = { nav.navigate("oss") }) { Text("오픈소스 고지") }
        if (status.isNotBlank()) Text(status)
        if (!graph.secrets.available) Text("암호화 저장소를 열지 못했습니다. 키를 저장할 수 없습니다.")
    }
    if (risk) {
        AlertDialog(
            onDismissRequest = { risk = false },
            title = { Text("비공식 구독 로그인") },
            text = { Text(RISK) },
            confirmButton = {
                TextButton(onClick = {
                    CopilotFeatureFlag.recordConsent(context)
                    graph.settings.update(graph.settings.current().copy(subscriptionRiskAccepted = true))
                    risk = false
                }) { Text("위험을 이해하고 동의") }
            },
            dismissButton = { TextButton(onClick = { risk = false }) { Text("동의하지 않음") } },
        )
    }
    device?.let { auth ->
        AlertDialog(
            onDismissRequest = { device = null },
            title = { Text("GitHub 기기 코드") },
            text = {
                Column {
                    Text("코드 ${auth.userCode}")
                    Text(auth.verificationUri)
                    Text("브라우저에서 승인한 뒤 확인을 누르세요. 승인 전에는 Copilot에 메시지를 보내지 않습니다.")
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        status = CopilotSubscription.finishDevice(graph.secrets, auth).message
                        device = null
                    }
                }) { Text("승인 확인") }
            },
            dismissButton = {
                TextButton(onClick = {
                    copy(context, auth.userCode)
                    context.safeStart(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(auth.verificationUri)))
                }) { Text("코드 복사 후 열기") }
            },
        )
    }
}

@Composable
private fun OssScreen(onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("오픈소스", style = MaterialTheme.typography.headlineSmall)
        Text("번개알림은 GNU General Public License 3.0으로 배포됩니다. 이 앱을 수정해 배포하려면 같은 라이선스로 소스 코드를 제공해야 합니다.")
        Text("구독 로그인 절차는 OpenMinis(GPL-3.0, https://github.com/OpenMinis/OpenMinis)의 인증 코드를 가져와 고쳤습니다. 가져온 파일에는 원래 저작권 표시를 남겨 두었습니다.")
        Text("OpenMinis 저작권은 해당 기여자에게 있습니다. 이 앱은 OpenMinis의 공식 제품이 아닙니다.")
        Button(onClick = onBack) { Text("뒤로") }
    }
}

@Composable
private fun SecretField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(value, onChange, label = { Text(label) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
}

@Composable
private fun ModelField(label: String, value: String, onChange: (String) -> Unit) {
    var text by remember(value) { mutableStateOf(value) }
    OutlinedTextField(text, { text = it; onChange(it) }, label = { Text(label) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
}

private fun moveBackend(graph: app.bungaealert.AppGraph, settings: AppSettings, index: Int, delta: Int) {
    val order = settings.backendOrder.toMutableList()
    val target = index + delta
    if (target !in order.indices) return
    val item = order.removeAt(index)
    order.add(target, item)
    graph.settings.update(settings.copy(backendOrder = order))
}

private fun copy(context: Context, text: String) {
    val clipboard = context.getSystemService(ClipboardManager::class.java)
    clipboard.setPrimaryClip(ClipData.newPlainText("bungae", text))
    Toast.makeText(context, "복사했습니다", Toast.LENGTH_SHORT).show()
}
