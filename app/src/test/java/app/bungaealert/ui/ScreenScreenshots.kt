package app.bungaealert.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.bungaealert.data.DetectionRecord
import app.bungaealert.detect.AlertMode
import app.bungaealert.detect.BackendIds
import app.bungaealert.detect.MatchMode
import app.bungaealert.detect.WatchRule
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.ZoneId
import java.time.ZonedDateTime

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    sdk = [33],
    qualifiers = "w393dp-h852dp-normal-long-hdpi",
    application = android.app.Application::class,
)
class ScreenScreenshots {
    @Test fun homeLight() = shot("home_light", dark = false, tab = "home") { HomeScreen(ListenerHealthUi.Watching, groups(), {}) }
    @Test fun homeDark() = shot("home_dark", dark = true, tab = "home") { HomeScreen(ListenerHealthUi.Watching, groups(), {}) }
    @Test fun homeEmptyLight() = shot("home_empty_light", dark = false, tab = "home") { HomeScreen(ListenerHealthUi.Watching, emptyList(), {}) }
    @Test fun homeEmptyDark() = shot("home_empty_dark", dark = true, tab = "home") { HomeScreen(ListenerHealthUi.Watching, emptyList(), {}) }
    @Test fun homeBlockedLight() = shot("home_blocked_light", dark = false, tab = "home") { HomeScreen(ListenerHealthUi.Blocked, groups(), {}) }
    @Test fun homeBlockedDark() = shot("home_blocked_dark", dark = true, tab = "home") { HomeScreen(ListenerHealthUi.Blocked, groups(), {}) }

    @Test fun rulesLight() = shot("rules_light", dark = false, tab = "rules") { RulesScreen(rules(), RuleFilter.All, {}, { _, _ -> }, {}, {}) }
    @Test fun rulesDark() = shot("rules_dark", dark = true, tab = "rules") { RulesScreen(rules(), RuleFilter.All, {}, { _, _ -> }, {}, {}) }
    @Test fun rulesEmptyLight() = shot("rules_empty_light", dark = false, tab = "rules") { RulesScreen(emptyList(), RuleFilter.All, {}, { _, _ -> }, {}, {}) }
    @Test fun rulesEmptyDark() = shot("rules_empty_dark", dark = true, tab = "rules") { RulesScreen(emptyList(), RuleFilter.All, {}, { _, _ -> }, {}, {}) }

    @Test fun rulesEditorLight() = editor("rules_editor_light", false)
    @Test fun rulesEditorDark() = editor("rules_editor_dark", true)

    @Test fun settingsLight() = shot("settings_light", dark = false, tab = "settings") { SettingsScreen(settings(SettingsTab.Alarm)) }
    @Test fun settingsDark() = shot("settings_dark", dark = true, tab = "settings") { SettingsScreen(settings(SettingsTab.Alarm)) }
    @Test fun settingsJudgesLight() = shot("settings_judges_light", dark = false, tab = "settings") { SettingsScreen(settings(SettingsTab.Judges)) }
    @Test fun settingsJudgesDark() = shot("settings_judges_dark", dark = true, tab = "settings") { SettingsScreen(settings(SettingsTab.Judges)) }

    @Test fun onboardingLight() = shot("onboarding_light", dark = false, tab = null) { onboarding() }
    @Test fun onboardingDark() = shot("onboarding_dark", dark = true, tab = null) { onboarding() }

    @Test fun alarmLight() = shot("alarm_light", dark = false, tab = null, bare = true) {
        AlarmScreen(
            title = "번개모임 · 경제적 자유를 위하여~",
            body = "오늘 7시 강남역 2번 출구. 4명 모집.",
            onOpen = {},
            onClose = {},
            pulse = false,
        )
    }
    @Test fun alarmDark() = shot("alarm_dark", dark = true, tab = null, bare = true) {
        AlarmScreen(
            title = "번개모임 · 경제적 자유를 위하여~",
            body = "오늘 7시 강남역 2번 출구. 4명 모집.",
            onOpen = {},
            onClose = {},
            pulse = false,
        )
    }

    @Test fun statusLight() = shot("status_light", dark = false, tab = "home") { status() }
    @Test fun statusDark() = shot("status_dark", dark = true, tab = "home") { status() }

    @Test fun loadingLight() = shot("loading_light", dark = false, tab = "settings") {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LoadingPane("판정 중") }
    }
    @Test fun loadingDark() = shot("loading_dark", dark = true, tab = "settings") {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LoadingPane("판정 중") }
    }
    @Test fun errorLight() = shot("error_light", dark = false, tab = "settings") {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            ErrorPane("판정하지 못했습니다", "다시 시도해 주세요", action = "다시 시도", onAction = {})
        }
    }
    @Test fun errorDark() = shot("error_dark", dark = true, tab = "settings") {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            ErrorPane("판정하지 못했습니다", "다시 시도해 주세요", action = "다시 시도", onAction = {})
        }
    }

    private fun shot(name: String, dark: Boolean, tab: String?, bare: Boolean = false, body: @Composable () -> Unit) {
        captureRoboImage(
            golden(name),
            RoborazziOptions(recordOptions = RoborazziOptions.RecordOptions(applyDeviceCrop = true)),
        ) {
            BungaeTheme(darkTheme = dark) {
                if (bare) {
                    Box(Modifier.fillMaxSize()) { body() }
                } else {
                    BungaeScaffold(tab = tab, onTab = {}) { padding ->
                        Box(Modifier.padding(padding).fillMaxSize()) { body() }
                    }
                }
            }
        }
    }

    private fun editor(name: String, dark: Boolean) {
        captureRoboImage(golden(name)) {
            BungaeTheme(darkTheme = dark) {
                BungaeScaffold(tab = "rules", onTab = {}) { padding ->
                    Box(Modifier.padding(padding).fillMaxSize()) {
                        RulesScreen(rules(), RuleFilter.All, {}, { _, _ -> }, {}, {})
                        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)))
                        Box(
                            Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .heightIn(max = 640.dp)
                                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                                .background(androidx.compose.material3.MaterialTheme.colorScheme.surface),
                        ) {
                            RuleEditor(
                                initial = WatchRule("new", "수다방", MatchMode.ENDS_WITH, true, true, AlertMode.REPEAT),
                                isNew = true,
                                onDismiss = {},
                                onDelete = null,
                                onSave = {},
                            )
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun onboarding() {
        OnboardingScreen(
            title = "알림 접근",
            body = "카카오톡 알림을 읽습니다",
            icon = Icons.Rounded.Notifications,
            action = "허용하기",
            index = 1,
            total = 6,
            onPrimary = {},
        )
    }
}

@Composable
private fun status() {
    StatusScreen(
        model = StatusModel(
            access = true,
            bound = false,
            lastConnected = "10월 31일 14:02",
            lastDisconnected = "10월 31일 14:11",
            lastRebind = "10월 31일 14:12",
            lastCallback = "10월 31일 13:58",
            lastKakao = "10월 31일 13:40",
            lastProbe = "10월 31일 14:12",
            activeKakao = 2,
            missed = 1,
            keepAlive = true,
            batteryUnrestricted = false,
            backgroundRestricted = "없음",
            standby = "자주",
            powerSave = false,
            deviceIdle = false,
            samsung = true,
        ),
        onBack = {},
        onRebind = {},
        onRefresh = {},
        onKeepAlive = {},
        onListener = {},
        onBattery = {},
        onAppDetails = {},
        onSamsung = {},
    )
}

private fun settings(tab: SettingsTab) = SettingsModel(
    tab = tab,
    sensitivity = 40,
    ringSeconds = 180,
    backends = BackendIds.DEFAULT_ORDER.mapIndexed { index, id ->
        BackendRow(id, BackendIds.label(id), index == 0 || index == 1)
    },
    geminiKey = "stored-key",
    geminiModel = "gemini-2.5-flash",
    openaiKey = "",
    openaiModel = "gpt-4.1-mini",
    claudeKey = "",
    claudeModel = "claude-sonnet-4-5",
    codexModel = "gpt-5.4",
    copilotModel = "gpt-4.1",
    riskAccepted = false,
    secretsAvailable = true,
    statusMessage = "",
    busy = false,
    grokUrl = "",
    grokHeaderName = "Authorization",
    grokHeader = "",
    ntfyServer = "https://ntfy.sh",
    ntfyTopic = "bungae-demo",
    grokTimeout = 45,
    connection = ConnectionModel(
        access = true,
        bound = true,
        notifications = true,
        fullScreen = true,
        dnd = false,
        battery = false,
        keepAlive = true,
    ),
    samsung = false,
)

private val zone: ZoneId = ZoneId.of("Asia/Seoul")

private fun groups() = groupDetections(
    listOf(
        detection("1", 10, 31, 14, 2, "경제적 자유를 위하여~", "오늘 7시 강남역 번개. 4명 모집.", 84, 90, 12, "meetup"),
        detection("2", 10, 31, 11, 18, "10월의 수다방", "아메리카노 기프티콘, 선착순 3분.", 76, 8, 88, "giveaway"),
        detection("3", 10, 30, 21, 40, "선착순 나눔방", "편의점 상품권 나눔. 선착순 5명.", 68, 4, 70, "giveaway"),
        detection("4", 10, 28, 19, 5, "주말 번개", "토요일 11시 성수 카페 번개.", 71, 74, 6, "meetup"),
    ),
    ZonedDateTime.of(2026, 10, 31, 15, 0, 0, 0, zone).toInstant().toEpochMilli(),
    zone,
)

private fun detection(
    id: String,
    month: Int,
    day: Int,
    hour: Int,
    minute: Int,
    chat: String,
    text: String,
    score: Int,
    meetup: Int,
    giveaway: Int,
    type: String,
) = DetectionRecord(
    id = id,
    atMillis = ZonedDateTime.of(2026, month, day, hour, minute, 0, 0, zone).toInstant().toEpochMilli(),
    chatName = chat,
    sender = null,
    text = text,
    score = score,
    meetupScore = meetup,
    giveawayScore = giveaway,
    alerted = true,
    alertType = type,
    summary = "",
)

private fun rules() = listOf(
    WatchRule("1", "수다방", MatchMode.ENDS_WITH, true, true, AlertMode.REPEAT, true),
    WatchRule("2", "경제적 자유를 위하여~", MatchMode.EXACT, true, false, AlertMode.ONCE, true),
    WatchRule("3", "나눔", MatchMode.CONTAINS, false, true, AlertMode.ONCE, false),
)

private fun golden(name: String): File {
    val cwd = File(System.getProperty("user.dir") ?: ".")
    val root = if (cwd.name == "app") cwd.parentFile ?: cwd else cwd
    val dir = File(root, "docs/screenshots").apply { mkdirs() }
    return File(dir, "$name.png")
}
