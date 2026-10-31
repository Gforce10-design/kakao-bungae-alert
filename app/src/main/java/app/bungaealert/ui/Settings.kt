@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package app.bungaealert.ui

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.bungaealert.BungaeApp
import app.bungaealert.ai.NtfySubscribeService
import app.bungaealert.auth.ClaudeSubscription
import app.bungaealert.auth.CodexSubscription
import app.bungaealert.auth.CopilotSubscription
import app.bungaealert.auth.upstream.CopilotDeviceFlow
import app.bungaealert.auth.upstream.CopilotFeatureFlag
import app.bungaealert.data.AppSettings
import app.bungaealert.data.SecretStore
import app.bungaealert.detect.BackendIds
import app.bungaealert.detect.DecisionPolicy
import app.bungaealert.detect.GrokRoutinePrompt
import app.bungaealert.detect.PostType
import app.bungaealert.detect.ScoreClassifier
import app.bungaealert.detect.Sensitivity
import app.bungaealert.detect.TopicGenerator
import app.bungaealert.notify.ListenerHealth
import app.bungaealert.notify.ListenerRuntime
import kotlinx.coroutines.launch

internal data class BackendRow(val id: String, val label: String, val enabled: Boolean)

internal enum class PermAction { Listener, Notifications, FullScreen, Dnd, Battery, AppDetails }

internal data class PermRow(val action: PermAction, val icon: Int, val title: String, val granted: Boolean)

internal data class ConnectionModel(
    val access: Boolean = false,
    val bound: Boolean = false,
    val lastConnected: String = "없음",
    val lastDisconnected: String = "없음",
    val lastRebind: String = "없음",
    val lastAny: String = "없음",
    val lastKakao: String = "없음",
    val probe: String = "없음",
    val active: String = "0",
    val missed: String = "0",
    val bucket: String = "확인 불가",
    val battery: Boolean = false,
    val background: String = "확인 불가",
    val backgroundOk: Boolean? = null,
    val powerSave: Boolean = false,
    val doze: Boolean = false,
    val keepAlive: Boolean = true,
    val samsung: Boolean = false,
)

internal data class SettingsModel(
    val sensitivity: Float = 0.4f,
    val sensitivityValue: String = "40",
    val cutoff: String = "62점",
    val ring: Float = 0.3f,
    val ringValue: String = "3분",
    val backends: List<BackendRow> = emptyList(),
    val geminiKey: String = "",
    val geminiModel: String = "",
    val openaiKey: String = "",
    val openaiModel: String = "",
    val claudeKey: String = "",
    val claudeModel: String = "",
    val codexModel: String = "",
    val copilotModel: String = "",
    val riskAccepted: Boolean = false,
    val webhook: String = "",
    val headerName: String = "Authorization",
    val headerValue: String = "",
    val ntfyServer: String = "https://ntfy.sh",
    val ntfyTopic: String = "",
    val grok: Float = 0.25f,
    val grokValue: String = "45초",
    val connection: ConnectionModel = ConnectionModel(),
    val permissions: List<PermRow> = emptyList(),
    val secretsOk: Boolean = true,
    val note: String = "",
)

internal class SettingsHandlers(
    val onSensitivity: (Float) -> Unit = {},
    val onRing: (Float) -> Unit = {},
    val onBackend: (String, Boolean) -> Unit = { _, _ -> },
    val onMove: (String, Int) -> Unit = { _, _ -> },
    val onGeminiKey: (String) -> Unit = {},
    val onGeminiModel: (String) -> Unit = {},
    val onOpenaiKey: (String) -> Unit = {},
    val onOpenaiModel: (String) -> Unit = {},
    val onClaudeKey: (String) -> Unit = {},
    val onClaudeModel: (String) -> Unit = {},
    val onCodexModel: (String) -> Unit = {},
    val onCopilotModel: (String) -> Unit = {},
    val onWebhook: (String) -> Unit = {},
    val onHeaderName: (String) -> Unit = {},
    val onHeaderValue: (String) -> Unit = {},
    val onServer: (String) -> Unit = {},
    val onTopic: (String) -> Unit = {},
    val onGrok: (Float) -> Unit = {},
    val onCopyTopic: () -> Unit = {},
    val onNewTopic: () -> Unit = {},
    val onCopyRoutine: () -> Unit = {},
    val onRisk: () -> Unit = {},
    val onCodex: () -> Unit = {},
    val onClaude: () -> Unit = {},
    val onCopilot: () -> Unit = {},
    val onLogout: () -> Unit = {},
    val onPermission: (PermAction) -> Unit = {},
    val onRebind: () -> Unit = {},
    val onRefresh: () -> Unit = {},
    val onKeepAlive: (Boolean) -> Unit = {},
    val onSamsung: (Int) -> Unit = {},
    val onOss: () -> Unit = {},
    val onTrial: () -> Unit = {},
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsRoute(focusToken: Int) {
    val context = LocalContext.current
    val graph = (context.applicationContext as BungaeApp).graph
    val settings by graph.settings.state.collectAsState()
    val scope = rememberCoroutineScope()
    var tick by remember { mutableIntStateOf(0) }
    var geminiKey by remember { mutableStateOf(graph.secrets.get(SecretStore.GEMINI_KEY)) }
    var openaiKey by remember { mutableStateOf(graph.secrets.get(SecretStore.OPENAI_KEY)) }
    var claudeKey by remember { mutableStateOf(graph.secrets.get(SecretStore.CLAUDE_KEY)) }
    var webhook by remember { mutableStateOf(graph.secrets.get(SecretStore.GROK_URL)) }
    var headerValue by remember { mutableStateOf(graph.secrets.get(SecretStore.GROK_HEADER)) }
    var geminiModel by remember { mutableStateOf(settings.geminiModel) }
    var openaiModel by remember { mutableStateOf(settings.openaiModel) }
    var claudeModel by remember { mutableStateOf(settings.claudeModel) }
    var codexModel by remember { mutableStateOf(settings.codexModel) }
    var copilotModel by remember { mutableStateOf(settings.copilotModel) }
    var note by remember { mutableStateOf("") }
    var riskOpen by remember { mutableStateOf(false) }
    var ossOpen by remember { mutableStateOf(false) }
    var trialOpen by remember { mutableStateOf(false) }
    var device by remember { mutableStateOf<CopilotDeviceFlow.DeviceAuthorization?>(null) }
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
    val askNotification = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { tick++ }
    val model = settingsModel(
        context = context,
        settings = settings,
        tick = tick,
        geminiKey = geminiKey,
        openaiKey = openaiKey,
        claudeKey = claudeKey,
        webhook = webhook,
        headerValue = headerValue,
        geminiModel = geminiModel,
        openaiModel = openaiModel,
        claudeModel = claudeModel,
        codexModel = codexModel,
        copilotModel = copilotModel,
        secretsOk = graph.secrets.available,
        note = note,
    )
    val handlers = SettingsHandlers(
        onSensitivity = { graph.settings.update(graph.settings.current().copy(sensitivity = (it * 100).toInt())) },
        onRing = { graph.settings.update(graph.settings.current().copy(maxRingSeconds = (it * 600).toInt().coerceAtLeast(15))) },
        onBackend = { id, checked ->
            val current = graph.settings.current()
            val next = current.enabledBackends.toMutableSet().apply { if (checked) add(id) else remove(id) }
            graph.settings.update(current.copy(enabledBackends = next))
            if (id == BackendIds.GROK_WEBHOOK) {
                if (checked && webhook.isNotBlank()) NtfySubscribeService.start(context) else NtfySubscribeService.stop(context)
            }
        },
        onMove = { id, delta -> moveBackend(graph, id, delta) },
        onGeminiKey = { geminiKey = it; graph.secrets.put(SecretStore.GEMINI_KEY, it) },
        onGeminiModel = { geminiModel = it; graph.settings.update(graph.settings.current().copy(geminiModel = it)) },
        onOpenaiKey = { openaiKey = it; graph.secrets.put(SecretStore.OPENAI_KEY, it) },
        onOpenaiModel = { openaiModel = it; graph.settings.update(graph.settings.current().copy(openaiModel = it)) },
        onClaudeKey = { claudeKey = it; graph.secrets.put(SecretStore.CLAUDE_KEY, it) },
        onClaudeModel = { claudeModel = it; graph.settings.update(graph.settings.current().copy(claudeModel = it)) },
        onCodexModel = { codexModel = it; graph.settings.update(graph.settings.current().copy(codexModel = it)) },
        onCopilotModel = { copilotModel = it; graph.settings.update(graph.settings.current().copy(copilotModel = it)) },
        onWebhook = { webhook = it; graph.secrets.put(SecretStore.GROK_URL, it) },
        onHeaderName = { graph.settings.update(graph.settings.current().copy(grokHeaderName = it)) },
        onHeaderValue = { headerValue = it; graph.secrets.put(SecretStore.GROK_HEADER, it) },
        onServer = { graph.settings.update(graph.settings.current().copy(ntfyServer = it)) },
        onTopic = { graph.settings.update(graph.settings.current().copy(ntfyTopic = it.trim())) },
        onGrok = { graph.settings.update(graph.settings.current().copy(grokTimeoutSeconds = (it * 180).toInt().coerceAtLeast(5))) },
        onCopyTopic = { copy(context, graph.settings.current().ntfyTopic) },
        onNewTopic = { graph.settings.update(graph.settings.current().copy(ntfyTopic = TopicGenerator.random())) },
        onCopyRoutine = {
            val current = graph.settings.current()
            copy(context, GrokRoutinePrompt.korean(current.ntfyServer, current.ntfyTopic))
        },
        onRisk = { riskOpen = true },
        onCodex = { scope.launch { note = CodexSubscription.login(context, graph.secrets).message } },
        onClaude = { scope.launch { note = ClaudeSubscription.login(context, graph.secrets).message } },
        onCopilot = {
            scope.launch {
                try {
                    device = CopilotSubscription.startDevice()
                } catch (e: Exception) {
                    note = e.message ?: "Copilot 로그인을 시작하지 못했습니다."
                }
            }
        },
        onLogout = {
            graph.secrets.clear(SecretStore.CODEX_TOKENS)
            graph.secrets.clear(SecretStore.CLAUDE_TOKENS)
            graph.secrets.clear(SecretStore.COPILOT_GITHUB)
            graph.secrets.clear(SecretStore.COPILOT_SESSION)
            note = "구독 토큰을 지웠습니다."
        },
        onPermission = { action ->
            when (action) {
                PermAction.Listener -> open(context, Permissions.listenerDetail(context))
                PermAction.Notifications -> {
                    if (Build.VERSION.SDK_INT >= 33) askNotification.launch(Manifest.permission.POST_NOTIFICATIONS)
                    else open(context, Permissions.appNotificationSettings(context))
                }
                PermAction.FullScreen -> open(context, Permissions.fullScreenSettings(context))
                PermAction.Dnd -> open(context, Permissions.dndSettings())
                PermAction.Battery -> open(context, Permissions.batterySettings(context))
                PermAction.AppDetails -> open(context, Permissions.appDetails(context))
            }
        },
        onRebind = {
            ListenerRuntime.requestRebind(context)
            tick++
        },
        onRefresh = {
            ListenerRuntime.refreshProbe(context)
            tick++
        },
        onKeepAlive = { enabled ->
            graph.settings.update(graph.settings.current().copy(listenerKeepAlive = enabled))
            ListenerRuntime.ensure(context, fromWatchdog = false)
            tick++
        },
        onSamsung = { type -> open(context, Permissions.samsungBackgroundList(type)) },
        onOss = { ossOpen = true },
        onTrial = { trialOpen = true },
    )
    SettingsContent(model, handlers, focusToken)
    if (riskOpen) {
        NoticeSheet(
            title = "비공식 구독 로그인",
            lines = listOf(
                "공식 프로그램의 로그인 절차를 그대로 따릅니다.",
                "제공자가 막으면 멈출 수 있습니다.",
                "이용약관에 어긋날 수 있습니다.",
                "계정이 제한될 수 있습니다.",
                "토큰은 이 기기에만 암호화해 둡니다.",
                "동의 전에는 요청을 보내지 않습니다.",
            ),
            confirm = "위험을 이해하고 동의",
            dismiss = "동의하지 않음",
            onConfirm = {
                CopilotFeatureFlag.recordConsent(context)
                graph.settings.update(graph.settings.current().copy(subscriptionRiskAccepted = true))
                riskOpen = false
            },
            onDismiss = { riskOpen = false },
        )
    }
    if (ossOpen) {
        NoticeSheet(
            title = "오픈소스",
            lines = listOf(
                "GNU GPL 3.0으로 배포합니다.",
                "수정본을 배포하면 같은 라이선스의 소스가 필요합니다.",
                "구독 로그인은 OpenMinis(GPL-3.0) 인증 코드를 고쳤습니다.",
                "OpenMinis의 공식 제품이 아닙니다.",
            ),
            confirm = null,
            dismiss = "닫기",
            onConfirm = {},
            onDismiss = { ossOpen = false },
        )
    }
    if (trialOpen) {
        TrialSheet(
            onDismiss = { trialOpen = false },
            onRun = { chat, text -> runTrial(graph, chat, text) },
        )
    }
    device?.let { auth ->
        NoticeSheet(
            title = "GitHub 기기 코드",
            lines = listOf(auth.userCode, auth.verificationUri, "브라우저에서 승인한 뒤 확인을 누르세요."),
            confirm = "승인 확인",
            dismiss = "코드 복사 후 열기",
            onConfirm = {
                scope.launch {
                    note = CopilotSubscription.finishDevice(graph.secrets, auth).message
                    device = null
                }
            },
            onDismiss = {
                copy(context, auth.userCode)
                open(context, android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(auth.verificationUri)))
            },
        )
    }
}

@Composable
internal fun SettingsContent(model: SettingsModel, handlers: SettingsHandlers, focusToken: Int) {
    val requester = remember { BringIntoViewRequester() }
    LaunchedEffect(focusToken) {
        if (focusToken > 0) requester.bringIntoView()
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        ScreenTitle("설정")
        if (!model.secretsOk) {
            ErrorState("저장할 수 없습니다", "암호화 저장소를 열지 못했습니다.")
        }
        if (model.note.isNotBlank()) Note(model.note)
        SectionTitle("알람", "기준 이상이면 알리고, 근처만 보조 판정합니다.")
        SurfaceCard {
            SliderLine("민감도 ${model.sensitivityValue}", model.cutoff, model.sensitivity, handlers.onSensitivity)
            SliderLine("반복", model.ringValue, model.ring, handlers.onRing)
        }
        SectionTitle("보조 판정", "위에서부터, 준비된 것만 씁니다.")
        SurfaceCard {
            model.backends.forEachIndexed { index, row ->
                BackendLine(
                    row = row,
                    up = index > 0,
                    down = index < model.backends.lastIndex,
                    onEnabled = { handlers.onBackend(row.id, it) },
                    onUp = { handlers.onMove(row.id, -1) },
                    onDown = { handlers.onMove(row.id, 1) },
                )
            }
        }
        SectionTitle("API 키", "키는 기기에 암호화됩니다.")
        SurfaceCard {
            SecretLine("Gemini API 키", model.geminiKey, handlers.onGeminiKey)
            PlainLine("Gemini 모델", model.geminiModel, handlers.onGeminiModel)
            SecretLine("OpenAI API 키", model.openaiKey, handlers.onOpenaiKey)
            PlainLine("OpenAI 모델", model.openaiModel, handlers.onOpenaiModel)
            SecretLine("Claude API 키", model.claudeKey, handlers.onClaudeKey)
            PlainLine("Claude 모델", model.claudeModel, handlers.onClaudeModel)
        }
        SectionTitle("구독")
        SurfaceCard {
            if (!model.riskAccepted) {
                Text("동의 전에는 로그인하지 않습니다.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 8.dp))
                PrimaryButton("위험 안내", handlers.onRisk)
            } else {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.heightIn(min = 48.dp)) {
                    Symbol(Sym.verified, null, tint = tones.good, size = 18.dp)
                    Text("동의함", modifier = Modifier.padding(start = 8.dp).weight(1f), style = MaterialTheme.typography.bodyLarge)
                }
                PrimaryButton("ChatGPT·Codex", handlers.onCodex)
                Spacer(Modifier.height(8.dp))
                SecondaryButton("Claude", handlers.onClaude)
                Spacer(Modifier.height(8.dp))
                SecondaryButton("Copilot", handlers.onCopilot)
                Spacer(Modifier.height(8.dp))
                QuietButton("구독 로그아웃", handlers.onLogout, modifier = Modifier.align(Alignment.CenterHorizontally))
                PlainLine("Codex 모델", model.codexModel, handlers.onCodexModel)
                PlainLine("Copilot 모델", model.copilotModel, handlers.onCopilotModel)
            }
        }
        SectionTitle("Grok", "선착순에는 느리고, 사용량을 씁니다.")
        SurfaceCard {
            SecretLine("웹훅 URL", model.webhook, handlers.onWebhook)
            PlainLine("인증 헤더 이름", model.headerName, handlers.onHeaderName)
            SecretLine("인증 헤더 값", model.headerValue, handlers.onHeaderValue)
            PlainLine("ntfy 서버", model.ntfyServer, handlers.onServer)
            PlainLine("ntfy 토픽", model.ntfyTopic, handlers.onTopic)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                SecondaryButton("토픽 복사", handlers.onCopyTopic, Modifier.weight(1f))
                SecondaryButton("새로 만들기", handlers.onNewTopic, Modifier.weight(1f))
            }
            SliderLine("제한 시간", model.grokValue, model.grok, handlers.onGrok)
            Spacer(Modifier.height(8.dp))
            SecondaryButton("루틴 안내 복사", handlers.onCopyRoutine)
        }
        Column(Modifier.bringIntoViewRequester(requester)) {
            SectionTitle("연결", "이 프로세스의 연결입니다.")
            SurfaceCard {
                FlagLine("알림 접근", model.connection.access)
                FlagLine("리스너", model.connection.bound)
                ValueLine("마지막 연결", model.connection.lastConnected)
                ValueLine("마지막 끊김", model.connection.lastDisconnected)
                ValueLine("다시 연결", model.connection.lastRebind)
                ValueLine("받은 알림", model.connection.lastAny)
                ValueLine("카카오톡", model.connection.lastKakao)
                ValueLine("점검", model.connection.probe)
                ValueLine("떠 있는 알림", model.connection.active)
                ValueLine("놓친 알림", model.connection.missed)
                ValueLine("대기 버킷", model.connection.bucket)
                FlagLine("배터리 제외", model.connection.battery)
                ValueLine("백그라운드", model.connection.background, model.connection.backgroundOk)
                FlagLine("절전", !model.connection.powerSave, if (model.connection.powerSave) "켜짐" else "꺼짐")
                FlagLine("기기 절전", !model.connection.doze, if (model.connection.doze) "들어감" else "아님")
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.heightIn(min = 48.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text("상태 알림", style = MaterialTheme.typography.bodyLarge)
                        Text("낮은 중요도로 프로세스를 유지합니다.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(model.connection.keepAlive, handlers.onKeepAlive)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                    SecondaryButton("새로고침", handlers.onRefresh, Modifier.weight(1f))
                    PrimaryButton("다시 연결", handlers.onRebind, Modifier.weight(1f))
                }
                if (model.connection.samsung) {
                    Text("잠들지 않는 앱에 넣으세요.", modifier = Modifier.padding(top = 12.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    SecondaryButton("잠들지 않는 앱", { handlers.onSamsung(2) })
                    Spacer(Modifier.height(8.dp))
                    SecondaryButton("잠자는 앱", { handlers.onSamsung(0) })
                    Spacer(Modifier.height(8.dp))
                    SecondaryButton("깊게 잠자는 앱", { handlers.onSamsung(1) })
                }
                Text(
                    "강제 중지 후에는 앱을 다시 열어야 합니다.",
                    modifier = Modifier.padding(top = 8.dp, bottom = 8.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        SectionTitle("권한")
        SurfaceCard {
            model.permissions.forEach { row ->
                ClickRow(row) { handlers.onPermission(row.action) }
            }
            Text(
                "파일 설치는 앱 정보에서 제한을 풉니다.",
                modifier = Modifier.padding(bottom = 8.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        SectionTitle("고지")
        SurfaceCard {
            ClickRow(PermRow(PermAction.AppDetails, Sym.science, "문장 시험", false)) { handlers.onTrial() }
            ClickRow(PermRow(PermAction.AppDetails, Sym.menu_book, "오픈소스", false)) { handlers.onOss() }
            ClickRow(PermRow(PermAction.AppDetails, Sym.policy, "위험 안내", model.riskAccepted)) { handlers.onRisk() }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
internal fun JudgePanel(
    chat: String,
    message: String,
    busy: Boolean,
    error: String?,
    lines: List<String>,
    onChat: (String) -> Unit,
    onMessage: (String) -> Unit,
    onSubmit: () -> Unit,
    onRetry: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        Text("문장 시험", style = MaterialTheme.typography.headlineMedium)
        Text("알림은 보내지 않습니다.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(chat, onChat, modifier = Modifier.fillMaxWidth(), label = { Text("채팅방") }, singleLine = true)
        OutlinedTextField(message, onMessage, modifier = Modifier.fillMaxWidth(), label = { Text("메시지") }, minLines = 4)
        if (busy) {
            LoadingState("판정 중")
        } else {
            PrimaryButton("판정", onSubmit, enabled = message.isNotBlank())
        }
        if (error != null) ErrorState("판정하지 못했습니다", error, "다시 시도", onRetry)
        if (lines.isNotEmpty()) {
            SurfaceCard {
                lines.forEach { line ->
                    Text(line, modifier = Modifier.padding(vertical = 6.dp), style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TrialSheet(onDismiss: () -> Unit, onRun: suspend (String, String) -> List<String>) {
    var chat by remember { mutableStateOf("시험방") }
    var message by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var lines by remember { mutableStateOf<List<String>>(emptyList()) }
    val scope = rememberCoroutineScope()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp).imePadding()) {
            JudgePanel(
                chat = chat,
                message = message,
                busy = busy,
                error = error,
                lines = lines,
                onChat = { chat = it },
                onMessage = { message = it },
                onSubmit = {
                    busy = true
                    error = null
                    scope.launch {
                        try {
                            lines = onRun(chat, message)
                        } catch (t: Throwable) {
                            error = t.message ?: "판정에 실패했습니다."
                            lines = emptyList()
                        } finally {
                            busy = false
                        }
                    }
                },
                onRetry = {
                    error = null
                    busy = true
                    scope.launch {
                        try {
                            lines = onRun(chat, message)
                        } catch (t: Throwable) {
                            error = t.message ?: "판정에 실패했습니다."
                        } finally {
                            busy = false
                        }
                    }
                },
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NoticeSheet(
    title: String,
    lines: List<String>,
    confirm: String?,
    dismiss: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            Modifier.padding(horizontal = 20.dp, vertical = 8.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(title, style = MaterialTheme.typography.headlineMedium)
            lines.forEach { line ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Spacer(
                        Modifier
                            .padding(top = 8.dp)
                            .size(6.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape),
                    )
                    Text(line, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                }
            }
            if (confirm != null) PrimaryButton(confirm, onConfirm)
            QuietButton(dismiss, onDismiss, modifier = Modifier.align(Alignment.CenterHorizontally))
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SliderLine(label: String, value: String, fraction: Float, onChange: (Float) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Text(value, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    }
    Slider(value = fraction.coerceIn(0f, 1f), onValueChange = onChange)
}

@Composable
private fun BackendLine(
    row: BackendRow,
    up: Boolean,
    down: Boolean,
    onEnabled: (Boolean) -> Unit,
    onUp: () -> Unit,
    onDown: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
        Text(row.label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        SymbolButton(Sym.arrow_upward, "위로", onUp, enabled = up)
        SymbolButton(Sym.arrow_downward, "아래로", onDown, enabled = down)
        Switch(row.enabled, onEnabled)
    }
}

@Composable
private fun SecretLine(label: String, value: String, onChange: (String) -> Unit) {
    var reveal by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        label = { Text(label) },
        singleLine = true,
        visualTransformation = if (reveal) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = if (reveal) KeyboardType.Text else KeyboardType.Password),
        trailingIcon = {
            IconButton(onClick = { reveal = !reveal }) {
                Symbol(if (reveal) Sym.visibility_off else Sym.visibility, if (reveal) "숨기기" else "보기")
            }
        },
    )
}

@Composable
private fun PlainLine(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        label = { Text(label) },
        singleLine = true,
    )
}

@Composable
private fun FlagLine(label: String, ok: Boolean, value: String = if (ok) "켜짐" else "꺼짐") {
    ValueLine(label, value, ok)
}

@Composable
private fun ValueLine(label: String, value: String, ok: Boolean? = null) {
    val scheme = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        if (ok != null) {
            Symbol(
                if (ok) Sym.check else Sym.warning,
                if (ok) "정상" else "확인 필요",
                tint = if (ok) tones.good else scheme.error,
                size = 18.dp,
            )
            Spacer(Modifier.size(6.dp))
        }
        Text(value, style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
    }
}

@Composable
private fun ClickRow(row: PermRow, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(role = Role.Button, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Symbol(row.icon, null, tint = scheme.primary, size = 20.dp)
        Text(row.title, modifier = Modifier.weight(1f).padding(horizontal = 12.dp), style = MaterialTheme.typography.bodyLarge)
        val notice = row.title == "위험 안내"
        val allowed = row.granted && row.action != PermAction.AppDetails
        if (notice && row.granted || allowed) {
            Symbol(Sym.check, if (notice) "동의함" else "허용됨", tint = tones.good, size = 18.dp)
        } else {
            Symbol(Sym.chevron_right, null, tint = scheme.onSurfaceVariant, size = 20.dp)
        }
    }
}

@Composable
private fun Note(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp).bungaeCard().padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Symbol(Sym.info, null, tint = MaterialTheme.colorScheme.primary, size = 18.dp)
        Text(text, modifier = Modifier.padding(start = 10.dp), style = MaterialTheme.typography.bodyMedium)
    }
}

private fun settingsModel(
    context: Context,
    settings: AppSettings,
    tick: Int,
    geminiKey: String,
    openaiKey: String,
    claudeKey: String,
    webhook: String,
    headerValue: String,
    geminiModel: String,
    openaiModel: String,
    claudeModel: String,
    codexModel: String,
    copilotModel: String,
    secretsOk: Boolean,
    note: String,
): SettingsModel {
    if (tick < 0) return SettingsModel()
    val now = System.currentTimeMillis()
    val clock = ListenerRuntime.clock(context)
    val power = Permissions.powerSnapshot(context)
    val ringSeconds = settings.maxRingSeconds.coerceIn(15, 600)
    return SettingsModel(
        sensitivity = settings.sensitivity / 100f,
        sensitivityValue = settings.sensitivity.toString(),
        cutoff = "${Sensitivity.cutoff(settings.sensitivity)}점",
        ring = ringSeconds / 600f,
        ringValue = ringLabel(settings.maxRingSeconds),
        backends = settings.backendOrder.map { id ->
            BackendRow(id, BackendIds.label(id), id in settings.enabledBackends)
        },
        geminiKey = geminiKey,
        geminiModel = geminiModel,
        openaiKey = openaiKey,
        openaiModel = openaiModel,
        claudeKey = claudeKey,
        claudeModel = claudeModel,
        codexModel = codexModel,
        copilotModel = copilotModel,
        riskAccepted = settings.subscriptionRiskAccepted,
        webhook = webhook,
        headerName = settings.grokHeaderName,
        headerValue = headerValue,
        ntfyServer = settings.ntfyServer,
        ntfyTopic = settings.ntfyTopic,
        grok = settings.grokTimeoutSeconds.coerceIn(5, 180) / 180f,
        grokValue = "${settings.grokTimeoutSeconds}초",
        connection = ConnectionModel(
            access = Permissions.listenerEnabled(context),
            bound = ListenerRuntime.bound,
            lastConnected = formatWhen(clock.lastConnectedAtMillis, now),
            lastDisconnected = formatWhen(clock.lastDisconnectedAtMillis, now),
            lastRebind = formatWhen(clock.lastRebindAtMillis, now),
            lastAny = formatWhen(clock.lastCallbackAtMillis, now),
            lastKakao = formatWhen(clock.lastKakaoAtMillis, now),
            probe = formatWhen(clock.lastProbeAtMillis, now),
            active = clock.lastActiveKakaoCount.toString(),
            missed = clock.lastMissedCount.toString(),
            bucket = power.standbyBucket?.let(ListenerHealth::standbyBucketLabel) ?: "확인 불가",
            battery = power.batteryUnrestricted,
            background = when (power.backgroundRestricted) {
                true -> "제한"
                false -> "정상"
                null -> "확인 불가"
            },
            backgroundOk = power.backgroundRestricted?.let { !it },
            powerSave = power.powerSaveMode,
            doze = power.deviceIdle,
            keepAlive = settings.listenerKeepAlive,
            samsung = power.samsung,
        ),
        permissions = listOf(
            PermRow(PermAction.Listener, Sym.notifications, "알림 접근", Permissions.listenerEnabled(context)),
            PermRow(PermAction.Notifications, Sym.notifications_active, "알림 표시", Permissions.notificationsAllowed(context)),
            PermRow(PermAction.FullScreen, Sym.fullscreen, "전체 화면", Permissions.fullScreenAllowed(context)),
            PermRow(PermAction.Dnd, Sym.do_not_disturb_on, "방해금지", Permissions.dndAllowed(context)),
            PermRow(PermAction.Battery, Sym.battery_5_bar, "배터리", Permissions.batteryUnrestricted(context)),
            PermRow(PermAction.AppDetails, Sym.settings_applications, "앱 정보", false),
        ),
        secretsOk = secretsOk,
        note = note,
    )
}

private fun ringLabel(seconds: Int): String {
    val minutes = seconds / 60
    val remain = seconds % 60
    return if (remain == 0) "${minutes}분" else "${minutes}분 ${remain}초"
}

private fun moveBackend(graph: app.bungaealert.AppGraph, id: String, delta: Int) {
    val settings = graph.settings.current()
    val order = settings.backendOrder.toMutableList()
    val index = order.indexOf(id)
    val target = index + delta
    if (index !in order.indices || target !in order.indices) return
    val item = order.removeAt(index)
    order.add(target, item)
    graph.settings.update(settings.copy(backendOrder = order))
}

private suspend fun runTrial(graph: app.bungaealert.AppGraph, chat: String, text: String): List<String> {
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
    return listOf(
        "모임 ${preview.meetup}점 · 나눔 ${preview.giveaway}점",
        "기준 ${eval.cutoff}점 · ${eval.zone}",
        if (eval.alert) "알림 ${eval.alertType?.label ?: ""}".trim() else "알림 없음",
        eval.summary,
    )
}

private fun copy(context: Context, text: String) {
    val clipboard = context.getSystemService(ClipboardManager::class.java)
    clipboard.setPrimaryClip(ClipData.newPlainText("bungae", text))
    Toast.makeText(context, "복사했습니다", Toast.LENGTH_SHORT).show()
}

private fun open(context: Context, intent: android.content.Intent) {
    if (!context.tryStart(intent)) {
        Toast.makeText(context, "설정을 열지 못했습니다.", Toast.LENGTH_LONG).show()
    }
}
