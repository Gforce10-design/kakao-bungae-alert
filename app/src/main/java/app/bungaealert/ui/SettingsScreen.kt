package app.bungaealert.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import android.widget.Toast
import app.bungaealert.BuildConfig
import app.bungaealert.BungaeApp
import app.bungaealert.MainActivity
import app.bungaealert.ai.ModelProvider
import app.bungaealert.ai.ModelValidator
import app.bungaealert.ai.NtfySubscribeService
import app.bungaealert.auth.ClaudeSubscription
import app.bungaealert.auth.CodexSubscription
import app.bungaealert.auth.CopilotSubscription
import app.bungaealert.auth.upstream.CopilotDeviceFlow
import app.bungaealert.auth.upstream.CopilotFeatureFlag
import app.bungaealert.data.SecretStore
import app.bungaealert.detect.BackendIds
import app.bungaealert.detect.GrokRoutinePrompt
import app.bungaealert.detect.Sensitivity
import app.bungaealert.detect.TopicGenerator
import app.bungaealert.notify.ListenerRuntime
import app.bungaealert.update.SemanticVersion
import app.bungaealert.update.UpdateChecks
import app.bungaealert.update.UpdateController
import app.bungaealert.update.UpdateCopy
import app.bungaealert.update.UpdateDecision
import app.bungaealert.update.UpdatePermissions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class SettingsTab(val label: String) { Alarm("알림"), Judges("판정"), Connection("연결"), Notices("고지") }

data class BackendRow(val id: String, val label: String, val enabled: Boolean)

data class ConnectionModel(
    val access: Boolean,
    val bound: Boolean,
    val notifications: Boolean,
    val fullScreen: Boolean,
    val dnd: Boolean,
    val battery: Boolean,
    val keepAlive: Boolean,
)

data class SettingsModel(
    val tab: SettingsTab,
    val sensitivity: Int,
    val ringSeconds: Int,
    val backends: List<BackendRow>,
    val geminiKey: String,
    val geminiModel: String,
    val openaiKey: String,
    val openaiModel: String,
    val claudeKey: String,
    val claudeModel: String,
    val codexModel: String,
    val copilotModel: String,
    val riskAccepted: Boolean,
    val secretsAvailable: Boolean,
    val statusMessage: String,
    val busy: Boolean,
    val grokUrl: String,
    val grokHeaderName: String,
    val grokHeader: String,
    val ntfyServer: String,
    val ntfyTopic: String,
    val grokTimeout: Int,
    val connection: ConnectionModel,
    val samsung: Boolean,
    val appVersion: String = "",
    val versionCode: Int = 0,
    val updateMessage: String = "",
    val updateBusy: Boolean = false,
    val installBusy: Boolean = false,
    val updateProgress: Int? = null,
    val updateUrl: String = "",
    val feelSummary: String = "",
)

data class SettingsActions(
    val onTab: (SettingsTab) -> Unit = {},
    val onSensitivity: (Int) -> Unit = {},
    val onSensitivityFinish: () -> Unit = {},
    val onRing: (Int) -> Unit = {},
    val onRingFinish: () -> Unit = {},
    val onBackendToggle: (String, Boolean) -> Unit = { _, _ -> },
    val onBackendMove: (String, Int) -> Unit = { _, _ -> },
    val onGeminiKey: (String) -> Unit = {},
    val onGeminiModel: (String) -> Unit = {},
    val onOpenaiKey: (String) -> Unit = {},
    val onOpenaiModel: (String) -> Unit = {},
    val onClaudeKey: (String) -> Unit = {},
    val onClaudeModel: (String) -> Unit = {},
    val onValidateModel: suspend (ModelProvider, String) -> String? = { _, _ -> null },
    val onCodexModel: (String) -> Unit = {},
    val onCopilotModel: (String) -> Unit = {},
    val onOpenRisk: () -> Unit = {},
    val onCodex: () -> Unit = {},
    val onClaudeLogin: () -> Unit = {},
    val onCopilot: () -> Unit = {},
    val onLogout: () -> Unit = {},
    val onGrokUrl: (String) -> Unit = {},
    val onGrokHeaderName: (String) -> Unit = {},
    val onGrokHeader: (String) -> Unit = {},
    val onNtfyServer: (String) -> Unit = {},
    val onNtfyTopic: (String) -> Unit = {},
    val onCopyTopic: () -> Unit = {},
    val onNewTopic: () -> Unit = {},
    val onGrokTimeout: (Int) -> Unit = {},
    val onGrokTimeoutFinish: () -> Unit = {},
    val onCopyRoutine: () -> Unit = {},
    val onOpenTrial: () -> Unit = {},
    val onOpenStatus: () -> Unit = {},
    val onListener: () -> Unit = {},
    val onNotifications: () -> Unit = {},
    val onFullScreen: () -> Unit = {},
    val onDnd: () -> Unit = {},
    val onBattery: () -> Unit = {},
    val onAppDetails: () -> Unit = {},
    val onRebind: () -> Unit = {},
    val onRefresh: () -> Unit = {},
    val onKeepAlive: (Boolean) -> Unit = {},
    val onSamsung: (Int) -> Unit = {},
    val onOpenOss: () -> Unit = {},
    val onOpenAlarmFeel: () -> Unit = {},
    val onOpenHelp: () -> Unit = {},
    val onCheckUpdate: () -> Unit = {},
    val onInstallUpdate: () -> Unit = {},
    val onOpenUpdate: () -> Unit = {},
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsRoute(
    onOpenStatus: () -> Unit,
    onOpenTrial: () -> Unit,
    onOpenOss: () -> Unit,
    onOpenHelp: () -> Unit,
    onOpenAlarmFeel: () -> Unit = {},
) {
    val context = LocalContext.current
    val graph = (context.applicationContext as BungaeApp).graph
    val settings by graph.settings.state.collectAsState()
    val scope = rememberCoroutineScope()
    var tabName by rememberSaveable { mutableStateOf(SettingsTab.Alarm.name) }
    var tick by remember { mutableIntStateOf(0) }
    var sensitivity by remember(settings.sensitivity) { mutableIntStateOf(settings.sensitivity) }
    var ring by remember(settings.maxRingSeconds) { mutableIntStateOf(settings.maxRingSeconds) }
    var grokTimeout by remember(settings.grokTimeoutSeconds) { mutableIntStateOf(settings.grokTimeoutSeconds) }
    var gemini by remember { mutableStateOf(graph.secrets.get(SecretStore.GEMINI_KEY)) }
    var openai by remember { mutableStateOf(graph.secrets.get(SecretStore.OPENAI_KEY)) }
    var claude by remember { mutableStateOf(graph.secrets.get(SecretStore.CLAUDE_KEY)) }
    var webhook by remember { mutableStateOf(graph.secrets.get(SecretStore.GROK_URL)) }
    var header by remember { mutableStateOf(graph.secrets.get(SecretStore.GROK_HEADER)) }
    var risk by remember { mutableStateOf(false) }
    var device by remember { mutableStateOf<CopilotDeviceFlow.DeviceAuthorization?>(null) }
    var status by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val savedUpdate = remember { UpdateChecks.savedUpdate(context) }
    val install by UpdateController.state.collectAsState()
    var updateMessage by remember {
        mutableStateOf(
            if (savedUpdate == null) {
                ""
            } else {
                "새 버전 ${SemanticVersion.display(savedUpdate.tag)} (${savedUpdate.versionCode})가 있습니다"
            },
        )
    }
    var updateBusy by remember { mutableStateOf(false) }
    var updateUrl by remember { mutableStateOf(savedUpdate?.url.orEmpty()) }
    var awaitingInstallPermission by rememberSaveable { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (!awaitingInstallPermission) return@rememberLauncherForActivityResult
        awaitingInstallPermission = false
        if (UpdatePermissions.allowed(context)) {
            UpdateController.start(context)
        } else {
            UpdateController.show(UpdateCopy.ALLOW_DENIED)
        }
    }
    val requestInstall: () -> Unit = {
        if (!install.busy) {
            if (!UpdatePermissions.allowed(context)) {
                awaitingInstallPermission = true
                UpdateController.show(UpdateCopy.ALLOW)
                Toast.makeText(context, UpdateCopy.ALLOW, Toast.LENGTH_LONG).show()
                val opened = try {
                    permissionLauncher.launch(UpdatePermissions.unknownSources(context))
                    true
                } catch (_: Exception) {
                    false
                }
                if (!opened) {
                    awaitingInstallPermission = false
                    UpdateController.show(UpdateCopy.ALLOW_SCREEN)
                    Toast.makeText(context, UpdateCopy.ALLOW_SCREEN, Toast.LENGTH_LONG).show()
                }
            } else {
                UpdateController.start(context)
            }
        }
    }
    val host = context as? MainActivity
    val startUpdate = host?.pendingStartUpdate == true
    LaunchedEffect(startUpdate) {
        if (!startUpdate) return@LaunchedEffect
        host.consumeStartUpdate()
        requestInstall()
    }
    val access = remember(tick) { Permissions.listenerEnabled(context) }
    val bound = remember(tick) { ListenerRuntime.bound }
    val samsung = remember(tick) { Permissions.isSamsung() }
    val model = SettingsModel(
        tab = SettingsTab.valueOf(tabName),
        sensitivity = sensitivity,
        ringSeconds = ring,
        backends = settings.backendOrder.map { id ->
            BackendRow(id, BackendIds.label(id), id in settings.enabledBackends)
        },
        geminiKey = gemini,
        geminiModel = settings.geminiModel,
        openaiKey = openai,
        openaiModel = settings.openaiModel,
        claudeKey = claude,
        claudeModel = settings.claudeModel,
        codexModel = settings.codexModel,
        copilotModel = settings.copilotModel,
        riskAccepted = settings.subscriptionRiskAccepted,
        secretsAvailable = graph.secrets.available,
        statusMessage = status,
        busy = busy,
        grokUrl = webhook,
        grokHeaderName = settings.grokHeaderName,
        grokHeader = header,
        ntfyServer = settings.ntfyServer,
        ntfyTopic = settings.ntfyTopic,
        grokTimeout = grokTimeout,
        connection = ConnectionModel(
            access = access,
            bound = bound,
            notifications = Permissions.notificationsAllowed(context),
            fullScreen = Permissions.fullScreenAllowed(context),
            dnd = Permissions.dndAllowed(context),
            battery = Permissions.batteryUnrestricted(context),
            keepAlive = settings.listenerKeepAlive,
        ),
        samsung = samsung,
        appVersion = BuildConfig.VERSION_NAME,
        versionCode = BuildConfig.VERSION_CODE,
        updateMessage = if (install.message.isNotBlank()) install.message else updateMessage,
        updateBusy = updateBusy,
        installBusy = install.busy,
        updateProgress = install.progress,
        updateUrl = updateUrl,
        feelSummary = alertFeelSummary(settings.feel),
    )
    SettingsScreen(
        model = model,
        actions = SettingsActions(
            onTab = { tabName = it.name },
            onSensitivity = { sensitivity = it.coerceIn(0, 100) },
            onSensitivityFinish = {
                graph.settings.update(graph.settings.current().copy(sensitivity = sensitivity))
            },
            onRing = { ring = (it).coerceIn(15, 600) },
            onRingFinish = {
                graph.settings.update(graph.settings.current().copy(maxRingSeconds = ring))
            },
            onBackendToggle = { id, checked ->
                val current = graph.settings.current()
                val next = current.enabledBackends.toMutableSet()
                if (checked) next += id else next -= id
                graph.settings.update(current.copy(enabledBackends = next))
                if (id == BackendIds.GROK_WEBHOOK) {
                    if (checked && graph.secrets.get(SecretStore.GROK_URL).isNotBlank()) {
                        NtfySubscribeService.start(context)
                    } else {
                        NtfySubscribeService.stop(context)
                    }
                }
            },
            onBackendMove = { id, delta ->
                val current = graph.settings.current()
                val order = current.backendOrder.toMutableList()
                val index = order.indexOf(id)
                val target = index + delta
                if (index >= 0 && target in order.indices) {
                    val item = order.removeAt(index)
                    order.add(target, item)
                    graph.settings.update(current.copy(backendOrder = order))
                }
            },
            onGeminiKey = { gemini = it; graph.secrets.put(SecretStore.GEMINI_KEY, it) },
            onGeminiModel = { graph.settings.update(graph.settings.current().copy(geminiModel = it)) },
            onOpenaiKey = { openai = it; graph.secrets.put(SecretStore.OPENAI_KEY, it) },
            onOpenaiModel = { graph.settings.update(graph.settings.current().copy(openaiModel = it)) },
            onClaudeKey = { claude = it; graph.secrets.put(SecretStore.CLAUDE_KEY, it) },
            onClaudeModel = { graph.settings.update(graph.settings.current().copy(claudeModel = it)) },
            onValidateModel = { provider, id ->
                val key = when (provider) {
                    ModelProvider.Gemini -> gemini
                    ModelProvider.OpenAI -> openai
                    ModelProvider.Claude -> claude
                }
                withContext(Dispatchers.IO) { ModelValidator.validate(provider, key, id) }
            },
            onCodexModel = { graph.settings.update(graph.settings.current().copy(codexModel = it)) },
            onCopilotModel = { graph.settings.update(graph.settings.current().copy(copilotModel = it)) },
            onOpenRisk = { risk = true },
            onCodex = {
                if (!busy) {
                    busy = true
                    scope.launch {
                        try {
                            status = CodexSubscription.login(context, graph.secrets).message
                        } finally {
                            busy = false
                        }
                    }
                }
            },
            onClaudeLogin = {
                if (!busy) {
                    busy = true
                    scope.launch {
                        try {
                            status = ClaudeSubscription.login(context, graph.secrets).message
                        } finally {
                            busy = false
                        }
                    }
                }
            },
            onCopilot = {
                if (!busy) {
                    scope.launch {
                        try {
                            device = CopilotSubscription.startDevice()
                        } catch (e: Exception) {
                            status = e.message ?: "Copilot 로그인을 시작하지 못했습니다."
                        }
                    }
                }
            },
            onLogout = {
                graph.secrets.clear(SecretStore.CODEX_TOKENS)
                graph.secrets.clear(SecretStore.CLAUDE_TOKENS)
                graph.secrets.clear(SecretStore.COPILOT_GITHUB)
                graph.secrets.clear(SecretStore.COPILOT_SESSION)
                status = "구독 토큰을 지웠습니다."
            },
            onGrokUrl = { webhook = it; graph.secrets.put(SecretStore.GROK_URL, it) },
            onGrokHeaderName = { graph.settings.update(graph.settings.current().copy(grokHeaderName = it)) },
            onGrokHeader = { header = it; graph.secrets.put(SecretStore.GROK_HEADER, it) },
            onNtfyServer = { graph.settings.update(graph.settings.current().copy(ntfyServer = it)) },
            onNtfyTopic = { graph.settings.update(graph.settings.current().copy(ntfyTopic = it.trim())) },
            onCopyTopic = { copyText(context, graph.settings.current().ntfyTopic) },
            onNewTopic = { graph.settings.update(graph.settings.current().copy(ntfyTopic = TopicGenerator.random())) },
            onGrokTimeout = { grokTimeout = it.coerceIn(5, 180) },
            onGrokTimeoutFinish = {
                graph.settings.update(graph.settings.current().copy(grokTimeoutSeconds = grokTimeout))
            },
            onCopyRoutine = {
                val current = graph.settings.current()
                copyText(context, GrokRoutinePrompt.korean(current.ntfyServer, current.ntfyTopic))
            },
            onOpenTrial = onOpenTrial,
            onOpenStatus = onOpenStatus,
            onListener = { openSetting(context, Permissions.listenerDetail(context), "알림 접근 설정을 열지 못했습니다.") },
            onNotifications = { openSetting(context, Permissions.appNotificationSettings(context), "알림 설정을 열지 못했습니다.") },
            onFullScreen = { openSetting(context, Permissions.fullScreenSettings(context), "전체 화면 설정을 열지 못했습니다.") },
            onDnd = { openSetting(context, Permissions.dndSettings(), "방해금지 설정을 열지 못했습니다.") },
            onBattery = { openSetting(context, Permissions.batterySettings(context), "배터리 설정을 열지 못했습니다.") },
            onAppDetails = { openSetting(context, Permissions.appDetails(context), "앱 정보를 열지 못했습니다.") },
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
            onSamsung = { type ->
                openSetting(context, Permissions.samsungBackgroundList(type), "삼성 배터리 화면을 열지 못했습니다.")
            },
            onOpenOss = onOpenOss,
            onOpenAlarmFeel = onOpenAlarmFeel,
            onOpenHelp = onOpenHelp,
            onCheckUpdate = {
                if (!updateBusy && !install.busy) {
                    updateBusy = true
                    UpdateController.dismiss()
                    scope.launch {
                        val result = withContext(Dispatchers.IO) { UpdateChecks.run(context) }
                        updateMessage = result.message
                        if (result is UpdateDecision.Current) {
                            updateUrl = ""
                        } else if (!result.pageUrl.isNullOrBlank()) {
                            updateUrl = result.pageUrl.orEmpty()
                        }
                        updateBusy = false
                    }
                }
            },
            onInstallUpdate = requestInstall,
            onOpenUpdate = {
                val url = updateUrl
                if (url.startsWith("https://")) {
                    val opened = context.tryStart(
                        android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url)),
                    )
                    if (!opened) {
                        android.widget.Toast.makeText(context, "릴리스 페이지를 열지 못했습니다", android.widget.Toast.LENGTH_LONG).show()
                    }
                }
            },
        ),
    )
    if (risk) {
        ModalBottomSheet(
            onDismissRequest = { risk = false },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            dragHandle = null,
        ) {
            RiskSheet(
                onAccept = {
                    CopilotFeatureFlag.recordConsent(context)
                    graph.settings.update(graph.settings.current().copy(subscriptionRiskAccepted = true))
                    risk = false
                },
                onDismiss = { risk = false },
            )
        }
    }
    device?.let { auth ->
        ModalBottomSheet(
            onDismissRequest = { device = null },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            dragHandle = null,
        ) {
            DeviceSheet(
                code = auth.userCode,
                uri = auth.verificationUri,
                onConfirm = {
                    scope.launch {
                        status = CopilotSubscription.finishDevice(graph.secrets, auth).message
                        device = null
                    }
                },
                onOpen = {
                    copyText(context, auth.userCode)
                    context.safeStart(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(auth.verificationUri)))
                },
            )
        }
    }
}

@Composable
fun SettingsScreen(
    model: SettingsModel,
    actions: SettingsActions = SettingsActions(),
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 20.dp).padding(top = 12.dp)) {
            ScreenTitle("설정")
            if (model.updateUrl.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                UpdateBanner(model, actions)
            }
            Spacer(Modifier.height(12.dp))
            ChipRow(
                labels = SettingsTab.entries.map { it.label },
                selected = model.tab.ordinal,
                onSelect = { actions.onTab(SettingsTab.entries[it]) },
            )
        }
        Spacer(Modifier.height(16.dp))
        AnimatedContent(
            targetState = model.tab,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
            label = "settings",
        ) { tab ->
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 24.dp),
            ) {
                when (tab) {
                    SettingsTab.Alarm -> AlarmTab(model, actions)
                    SettingsTab.Judges -> JudgesTab(model, actions)
                    SettingsTab.Connection -> ConnectionTab(model, actions)
                    SettingsTab.Notices -> NoticesTab(model, actions)
                }
            }
        }
    }
}

@Composable
private fun AlarmTab(model: SettingsModel, actions: SettingsActions) {
    SectionLabel("소리·진동")
    SurfaceCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 4.dp)) {
        CheckLine("알람 소리·진동", value = model.feelSummary.ifBlank { null }, onClick = actions.onOpenAlarmFeel)
    }
    Spacer(Modifier.height(16.dp))
    SectionLabel("민감도")
    SurfaceCard {
        SettingValue("기준", "${Sensitivity.cutoff(model.sensitivity)}점")
        BungaeSlider(
            value = model.sensitivity / 100f,
            onValueChange = { actions.onSensitivity((it * 100).toInt()) },
            onFinished = actions.onSensitivityFinish,
        )
        Row(Modifier.fillMaxWidth()) {
            Text("엄격", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("느슨", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(4.dp))
        Text("이 점수부터 알림", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Spacer(Modifier.height(16.dp))
    SectionLabel("반복 한도")
    SurfaceCard {
        SettingValue("최대", ringLabel(model.ringSeconds))
        BungaeSlider(
            value = model.ringSeconds.coerceIn(15, 600) / 600f,
            onValueChange = { actions.onRing((it * 600).toInt().coerceAtLeast(15)) },
            onFinished = actions.onRingFinish,
        )
    }
}

@Composable
private fun JudgesTab(model: SettingsModel, actions: SettingsActions) {
    SectionLabel("순서")
    SurfaceCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 4.dp)) {
        model.backends.forEachIndexed { index, row ->
            BackendLine(
                row = row,
                up = index > 0,
                down = index < model.backends.lastIndex,
                onToggle = { actions.onBackendToggle(row.id, it) },
                onMove = { actions.onBackendMove(row.id, it) },
            )
        }
    }
    Text(
        "실패하면 다음, 없으면 점수로 결정합니다",
        modifier = Modifier.padding(start = 4.dp, top = 8.dp),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(16.dp))
    SectionLabel("키")
    if (!model.secretsAvailable) {
        ErrorPane(title = "저장소를 열지 못했습니다", message = "키를 저장할 수 없습니다")
    } else {
        SurfaceCard {
            BungaeTextField(model.geminiKey, actions.onGeminiKey, "Gemini", secret = true)
            Spacer(Modifier.height(8.dp))
            ModelField(ModelProvider.Gemini, model.geminiModel, { actions.onValidateModel(ModelProvider.Gemini, it) }, actions.onGeminiModel)
            Spacer(Modifier.height(12.dp))
            BungaeTextField(model.openaiKey, actions.onOpenaiKey, "OpenAI", secret = true)
            Spacer(Modifier.height(8.dp))
            ModelField(ModelProvider.OpenAI, model.openaiModel, { actions.onValidateModel(ModelProvider.OpenAI, it) }, actions.onOpenaiModel)
            Spacer(Modifier.height(12.dp))
            BungaeTextField(model.claudeKey, actions.onClaudeKey, "Claude", secret = true)
            Spacer(Modifier.height(8.dp))
            ModelField(ModelProvider.Claude, model.claudeModel, { actions.onValidateModel(ModelProvider.Claude, it) }, actions.onClaudeModel)
        }
    }
    Spacer(Modifier.height(16.dp))
    SectionLabel("구독")
    SurfaceCard {
        if (!model.riskAccepted) {
            Text("동의 전에는 로그인하지 않습니다", style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(12.dp))
            PrimaryButton("위험 안내", actions.onOpenRisk, Modifier.fillMaxWidth())
        } else {
            Text("위험에 동의함", style = MaterialTheme.typography.bodyMedium, color = LocalBungaeExtra.current.good)
            Spacer(Modifier.height(12.dp))
            PrimaryButton("ChatGPT·Codex", actions.onCodex, Modifier.fillMaxWidth(), enabled = !model.busy)
            Spacer(Modifier.height(8.dp))
            PrimaryButton("Claude", actions.onClaudeLogin, Modifier.fillMaxWidth(), enabled = !model.busy)
            Spacer(Modifier.height(8.dp))
            PrimaryButton("Copilot", actions.onCopilot, Modifier.fillMaxWidth(), enabled = !model.busy)
            Spacer(Modifier.height(8.dp))
            SecondaryButton("구독 로그아웃", actions.onLogout, Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            BungaeTextField(model.codexModel, actions.onCodexModel, "Codex 모델")
            Spacer(Modifier.height(8.dp))
            BungaeTextField(model.copilotModel, actions.onCopilotModel, "Copilot 모델")
        }
        if (model.statusMessage.isNotBlank()) {
            Spacer(Modifier.height(12.dp))
            Text(model.statusMessage, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    Spacer(Modifier.height(16.dp))
    SectionLabel("Grok")
    SurfaceCard {
        Text("응답이 늦고 사용량을 씁니다", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        BungaeTextField(model.grokUrl, actions.onGrokUrl, "웹훅", secret = true)
        Spacer(Modifier.height(8.dp))
        BungaeTextField(model.grokHeaderName, actions.onGrokHeaderName, "헤더 이름")
        Spacer(Modifier.height(8.dp))
        BungaeTextField(model.grokHeader, actions.onGrokHeader, "헤더 값", secret = true)
        Spacer(Modifier.height(8.dp))
        BungaeTextField(model.ntfyServer, actions.onNtfyServer, "ntfy 서버")
        Spacer(Modifier.height(8.dp))
        BungaeTextField(model.ntfyTopic, actions.onNtfyTopic, "토픽")
        Spacer(Modifier.height(12.dp))
        SettingValue("제한 시간", "${model.grokTimeout}초")
        BungaeSlider(
            value = model.grokTimeout.coerceIn(5, 180) / 180f,
            onValueChange = { actions.onGrokTimeout((it * 180).toInt().coerceAtLeast(5)) },
            onFinished = actions.onGrokTimeoutFinish,
        )
        Spacer(Modifier.height(8.dp))
        SecondaryButton("토픽 복사", actions.onCopyTopic, Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        SecondaryButton("토픽 새로 만들기", actions.onNewTopic, Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        SecondaryButton("루틴 안내 복사", actions.onCopyRoutine, Modifier.fillMaxWidth())
    }
    Spacer(Modifier.height(16.dp))
    SurfaceCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 4.dp)) {
        CheckLine("문장 시험", onClick = actions.onOpenTrial)
    }
}

@Composable
private fun ConnectionTab(model: SettingsModel, actions: SettingsActions) {
    val health = when {
        !model.connection.access -> ListenerHealthUi.Blocked
        !model.connection.bound -> ListenerHealthUi.Unbound
        else -> ListenerHealthUi.Watching
    }
    ListenerBanner(
        health = health,
        onClick = if (health == ListenerHealthUi.Blocked) actions.onListener else actions.onOpenStatus,
    )
    Spacer(Modifier.height(16.dp))
    SectionLabel("권한")
    SurfaceCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 4.dp)) {
        CheckLine("알림 접근", ok = model.connection.access, onClick = actions.onListener)
        CheckLine("리스너", ok = model.connection.bound, onClick = actions.onRebind)
        CheckLine("알림 표시", ok = model.connection.notifications, onClick = actions.onNotifications)
        CheckLine("전체 화면", ok = model.connection.fullScreen, onClick = actions.onFullScreen)
        CheckLine("방해금지", ok = model.connection.dnd, onClick = actions.onDnd)
        CheckLine("배터리", ok = model.connection.battery, onClick = actions.onBattery)
    }
    Spacer(Modifier.height(16.dp))
    SectionLabel("유지")
    SurfaceCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("상태 알림", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            BungaeSwitch(model.connection.keepAlive, actions.onKeepAlive, "상태 알림")
        }
        Text("잠자기를 대신하지는 않습니다", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (model.samsung) {
        Spacer(Modifier.height(16.dp))
        SectionLabel("삼성")
        SurfaceCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 4.dp)) {
            CheckLine("잠들지 않는 앱", onClick = { actions.onSamsung(2) })
            CheckLine("잠자는 앱", onClick = { actions.onSamsung(0) })
            CheckLine("깊게 잠자는 앱", onClick = { actions.onSamsung(1) })
        }
    }
    Spacer(Modifier.height(16.dp))
    PrimaryButton("다시 연결", actions.onRebind, Modifier.fillMaxWidth())
    Spacer(Modifier.height(8.dp))
    SecondaryButton("새로고침", actions.onRefresh, Modifier.fillMaxWidth())
    Spacer(Modifier.height(8.dp))
    SecondaryButton("연결 자세히", actions.onOpenStatus, Modifier.fillMaxWidth())
}

@Composable
private fun NoticesTab(model: SettingsModel, actions: SettingsActions) {
    if (model.updateUrl.isBlank()) {
        UpdateBanner(model, actions)
        Spacer(Modifier.height(16.dp))
    }
    SectionLabel("사용법")
    SurfaceCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 4.dp)) {
        CheckLine("사용법", onClick = actions.onOpenHelp)
    }
    Spacer(Modifier.height(16.dp))
    SectionLabel("구독 로그인")
    SurfaceCard {
        Text(
            if (model.riskAccepted) "위험에 동의함" else "동의 전에는 로그인하지 않습니다",
            style = MaterialTheme.typography.bodyLarge,
            color = if (model.riskAccepted) LocalBungaeExtra.current.good else MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(12.dp))
        SecondaryButton(if (model.riskAccepted) "안내 다시 보기" else "위험 안내", actions.onOpenRisk, Modifier.fillMaxWidth())
    }
    Spacer(Modifier.height(16.dp))
    SectionLabel("오픈소스")
    SurfaceCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 4.dp)) {
        CheckLine("GPL-3.0 · OpenMinis · Pretendard", onClick = actions.onOpenOss)
    }
}

@Composable
private fun UpdateBanner(model: SettingsModel, actions: SettingsActions) {
    SurfaceCard {
        Text("업데이트", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        SettingValue("현재 버전", versionLabel(model.appVersion, model.versionCode))
        if (model.updateMessage.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(
                model.updateMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(12.dp))
        SecondaryButton(
            text = if (model.updateBusy) "확인 중" else "업데이트 확인",
            onClick = actions.onCheckUpdate,
            modifier = Modifier.fillMaxWidth(),
            enabled = !model.updateBusy && !model.installBusy,
        )
        if (model.updateUrl.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            PrimaryButton(
                text = "업데이트",
                onClick = actions.onInstallUpdate,
                modifier = Modifier.fillMaxWidth(),
                enabled = !model.updateBusy && !model.installBusy,
            )
            if (model.installBusy) {
                Spacer(Modifier.height(8.dp))
                if (model.updateProgress == null) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                } else {
                    LinearProgressIndicator(
                        progress = { model.updateProgress.coerceIn(0, 100) / 100f },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            SecondaryButton("받으러 가기", actions.onOpenUpdate, Modifier.fillMaxWidth())
        }
    }
}

private fun versionLabel(name: String, code: Int): String {
    val trimmed = name.trim()
    val pretty = when {
        trimmed.isEmpty() -> "—"
        trimmed[0] == 'v' || trimmed[0] == 'V' -> trimmed
        else -> "v$trimmed"
    }
    return if (code > 0) "$pretty ($code)" else pretty
}

@Composable
private fun BackendLine(
    row: BackendRow,
    up: Boolean,
    down: Boolean,
    onToggle: (Boolean) -> Unit,
    onMove: (Int) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            row.label,
            modifier = Modifier.weight(1f).padding(start = 8.dp),
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        MoveButton(Icons.Rounded.ArrowUpward, "위로", up) { onMove(-1) }
        MoveButton(Icons.Rounded.ArrowDownward, "아래로", down) { onMove(1) }
        BungaeSwitch(row.enabled, onToggle, row.label)
    }
}

@Composable
private fun MoveButton(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = description,
            tint = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.28f),
        )
    }
}

@Composable
private fun RiskSheet(onAccept: () -> Unit, onDismiss: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 24.dp),
    ) {
        SheetHandle()
        Text("구독 로그인", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp))
        listOf(
            "공식 프로그램의 로그인 절차를 따라갑니다",
            "제공자가 막거나 바꾸면 멈출 수 있습니다",
            "이용약관에 어긋날 수 있습니다",
            "계정이 제한될 수 있습니다",
            "토큰은 기기에 암호화해 저장합니다",
            "판정이 필요할 때만 글을 보냅니다",
            "동의 전에는 요청하지 않습니다",
        ).forEach { line ->
            Text("· $line", modifier = Modifier.padding(vertical = 4.dp), style = MaterialTheme.typography.bodyLarge)
        }
        Spacer(Modifier.height(16.dp))
        PrimaryButton("동의", onAccept, Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        SecondaryButton("닫기", onDismiss, Modifier.fillMaxWidth())
    }
}

@Composable
private fun DeviceSheet(code: String, uri: String, onConfirm: () -> Unit, onOpen: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
        SheetHandle()
        Text("GitHub 기기 코드", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp))
        Text(code, style = MaterialTheme.typography.headlineMedium.copy(fontFeatureSettings = "tnum"))
        Spacer(Modifier.height(8.dp))
        Text(uri, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        Text("브라우저에서 승인한 뒤 확인을 누르세요", style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(16.dp))
        PrimaryButton("승인 확인", onConfirm, Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        SecondaryButton("복사하고 열기", onOpen, Modifier.fillMaxWidth())
    }
}
