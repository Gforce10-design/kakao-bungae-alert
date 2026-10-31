package app.bungaealert.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.bungaealert.detect.AlertMode
import app.bungaealert.detect.BackendIds
import app.bungaealert.detect.MatchMode
import app.bungaealert.detect.WatchRule
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import org.junit.Rule
import org.junit.Test

class ScreenShots {
    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5,
        theme = "android:Theme.Material.NoActionBar",
    )

    @Test fun onboardingLight() = page(dark = false, tab = null) { onboarding() }

    @Test fun onboardingDark() = page(dark = true, tab = null) { onboarding() }

    @Test fun homeLight() = page(false, MainTab.Home) { home(online = true, empty = false) }

    @Test fun homeDark() = page(true, MainTab.Home) { home(online = true, empty = false) }

    @Test fun homeOfflineLight() = page(false, MainTab.Home) { home(online = false, empty = false) }

    @Test fun homeOfflineDark() = page(true, MainTab.Home) { home(online = false, empty = false) }

    @Test fun homeEmptyLight() = page(false, MainTab.Home) { home(online = true, empty = true) }

    @Test fun homeEmptyDark() = page(true, MainTab.Home) { home(online = true, empty = true) }

    @Test fun rulesLight() = page(false, MainTab.Rules) { rules(empty = false) }

    @Test fun rulesDark() = page(true, MainTab.Rules) { rules(empty = false) }

    @Test fun rulesEmptyLight() = page(false, MainTab.Rules) { rules(empty = true) }

    @Test fun rulesEmptyDark() = page(true, MainTab.Rules) { rules(empty = true) }

    @Test fun ruleEditorLight() = editor(false)

    @Test fun ruleEditorDark() = editor(true)

    @Test fun settingsLight() = page(false, MainTab.Settings) { SettingsContent(sampleSettings(), SettingsHandlers(), 0) }

    @Test fun settingsDark() = page(true, MainTab.Settings) { SettingsContent(sampleSettings(), SettingsHandlers(), 0) }

    @Test fun alarmLight() = bare(false) { alarm() }

    @Test fun alarmDark() = bare(true) { alarm() }

    @Test fun loadingLight() = page(false, MainTab.Settings) { loading() }

    @Test fun loadingDark() = page(true, MainTab.Settings) { loading() }

    @Test fun errorLight() = page(false, MainTab.Settings) { SettingsContent(sampleSettings(secretsOk = false), SettingsHandlers(), 0) }

    @Test fun errorDark() = page(true, MainTab.Settings) { SettingsContent(sampleSettings(secretsOk = false), SettingsHandlers(), 0) }

    private fun page(dark: Boolean, tab: MainTab?, content: @Composable () -> Unit) {
        paparazzi.snapshot {
            BungaeTheme(darkTheme = dark) {
                BungaeScaffold(tab = tab, onTab = {}) { content() }
            }
        }
    }

    private fun bare(dark: Boolean, content: @Composable () -> Unit) {
        paparazzi.snapshot {
            BungaeTheme(darkTheme = dark, lightStatusBar = false) { content() }
        }
    }

    private fun editor(dark: Boolean) {
        paparazzi.snapshot {
            BungaeTheme(darkTheme = dark) {
                Box(Modifier.fillMaxSize()) {
                    BungaeScaffold(MainTab.Rules, {}) { rules(empty = false) }
                    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)))
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .fillMaxHeight(0.92f)
                            .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                            .background(MaterialTheme.colorScheme.surface),
                    ) {
                        Box(
                            modifier = Modifier
                                .padding(top = 10.dp)
                                .align(Alignment.CenterHorizontally)
                                .size(width = 36.dp, height = 4.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.outline),
                        )
                        RuleEditor(
                            initial = WatchRule(
                                id = "rule-1",
                                pattern = "수다방",
                                matchMode = MatchMode.ENDS_WITH,
                                detectMeetup = true,
                                detectGiveaway = true,
                                alertMode = AlertMode.REPEAT,
                            ),
                            isNew = false,
                            onDismiss = {},
                            onSave = {},
                            onDelete = {},
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun onboarding() {
    OnboardingPage(
        index = 0,
        count = 6,
        title = "알림 접근",
        body = "카카오톡 알림을 읽습니다.",
        icon = Sym.notifications,
        action = "허용하기",
        onAction = {},
        onSkip = {},
    )
}

@Composable
private fun home(online: Boolean, empty: Boolean) {
    HomeContent(
        banner = if (online) {
            BannerUi(true, "감시 중", "마지막 오늘 14:02")
        } else {
            BannerUi(false, "알림을 받지 않는 중", "리스너 연결이 끊겼습니다")
        },
        groups = if (empty) emptyList() else sampleFeed(),
        onBanner = {},
        onEmpty = {},
    )
}

@Composable
private fun rules(empty: Boolean) {
    RulesContent(
        filter = RuleFilter.All,
        rules = if (empty) emptyList() else sampleRules(),
        onFilter = {},
        onAdd = {},
        onOpen = {},
        onToggle = { _, _ -> },
    )
}

@Composable
private fun alarm() {
    AlarmScreen(
        title = "10월의 수다방",
        body = "오늘 7시 강남역 2번 출구에서 번개하실 분 계세요?",
        onOpen = {},
        onClose = {},
        animate = false,
    )
}

@Composable
private fun loading() {
    Column(Modifier.padding(20.dp)) {
        JudgePanel(
            chat = "시험방",
            message = "오늘 7시 강남역에서 번개하실 분 4명 모집합니다",
            busy = true,
            error = null,
            lines = emptyList(),
            onChat = {},
            onMessage = {},
            onSubmit = {},
            onRetry = {},
        )
    }
}

private fun sampleFeed() = listOf(
    DayGroup(
        "오늘",
        listOf(
            FeedCard(
                id = "a",
                chat = "10월의 수다방",
                snippet = "오늘 7시 강남역 2번 출구에서 번개하실 분 계세요? 4명 모집합니다",
                time = "14:02",
                score = 72,
                kind = FeedKind.Meetup,
            ),
            FeedCard(
                id = "b",
                chat = "경제적 자유를 위하여~",
                snippet = "스타벅스 아메리카노 기프티콘 3분께 선착순으로 드려요",
                time = "11:20",
                score = 81,
                kind = FeedKind.Giveaway,
            ),
        ),
    ),
    DayGroup(
        "어제",
        listOf(
            FeedCard(
                id = "c",
                chat = "주말 등산 모임",
                snippet = "토요일 북한산 우이령 코스 같이 가실 분",
                time = "19:40",
                score = 64,
                kind = FeedKind.Meetup,
            ),
        ),
    ),
)

private fun sampleRules() = listOf(
    RuleUi("1", "수다방", "끝 일치", "반복", meetup = true, giveaway = true, enabled = true),
    RuleUi("2", "경제적 자유를 위하여~", "정확히", "한 번", meetup = false, giveaway = true, enabled = true),
    RuleUi("3", "나눔방", "포함", "한 번", meetup = false, giveaway = true, enabled = false),
)

private fun sampleSettings(secretsOk: Boolean = true) = SettingsModel(
    sensitivity = 0.4f,
    sensitivityValue = "40",
    cutoff = "62점",
    ring = 180f / 600f,
    ringValue = "3분",
    backends = BackendIds.DEFAULT_ORDER.map { id ->
        BackendRow(id, BackendIds.label(id), id == BackendIds.GEMINI_NANO)
    },
    geminiModel = "gemini-2.5-flash",
    openaiModel = "gpt-4.1-mini",
    claudeModel = "claude-sonnet-4-5",
    codexModel = "gpt-5.4",
    copilotModel = "gpt-4.1",
    ntfyServer = "https://ntfy.sh",
    ntfyTopic = "bungae-preview",
    connection = ConnectionModel(
        access = true,
        bound = true,
        lastConnected = "오늘 09:12",
        lastDisconnected = "어제 23:40",
        lastRebind = "오늘 09:12",
        lastAny = "오늘 14:02",
        lastKakao = "오늘 14:02",
        probe = "오늘 14:05",
        active = "2",
        missed = "0",
        bucket = "active (10)",
        battery = true,
        background = "정상",
        backgroundOk = true,
        powerSave = false,
        doze = false,
        keepAlive = true,
    ),
    permissions = listOf(
        PermRow(PermAction.Listener, Sym.notifications, "알림 접근", true),
        PermRow(PermAction.Notifications, Sym.notifications_active, "알림 표시", true),
        PermRow(PermAction.FullScreen, Sym.fullscreen, "전체 화면", false),
        PermRow(PermAction.Dnd, Sym.do_not_disturb_on, "방해금지", false),
        PermRow(PermAction.Battery, Sym.battery_5_bar, "배터리", true),
        PermRow(PermAction.AppDetails, Sym.settings_applications, "앱 정보", false),
    ),
    secretsOk = secretsOk,
)
