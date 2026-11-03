package app.bungaealert.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.DoNotDisturbOn
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.OpenInFull
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

private data class OnboardingStep(
    val title: String,
    val body: String,
    val icon: ImageVector,
    val action: String,
    val done: Boolean,
    val skip: Boolean = false,
    val advanceOnReturn: Boolean = false,
    val open: () -> Unit,
)

@Composable
fun OnboardingRoute(onDone: () -> Unit) {
    val context = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    var index by rememberSaveable { mutableIntStateOf(0) }
    var armed by rememberSaveable { mutableStateOf(false) }
    var returnAdvance by remember { mutableStateOf(false) }
    var finished by remember { mutableStateOf(false) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                if (armed) {
                    armed = false
                    returnAdvance = true
                }
                tick++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        tick++
    }
    val steps = buildSteps(
        listener = Permissions.listenerEnabled(context),
        notifications = Permissions.notificationsAllowed(context),
        fullScreen = Permissions.fullScreenAllowed(context),
        dnd = Permissions.dndAllowed(context),
        battery = Permissions.batteryUnrestricted(context),
        samsung = Permissions.isSamsung(),
        openListener = { context.safeStart(Permissions.listenerSettings()) },
        openNotifications = {
            if (Build.VERSION.SDK_INT >= 33) permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            else context.safeStart(Permissions.appNotificationSettings(context))
        },
        openFullScreen = { context.safeStart(Permissions.fullScreenSettings(context)) },
        openDnd = { context.safeStart(Permissions.dndSettings()) },
        openBattery = { context.safeStart(Permissions.batterySettings(context)) },
        openSamsung = { context.safeStart(Permissions.samsungBackgroundList(2)) },
        openRestricted = { context.safeStart(Permissions.appDetails(context)) },
    )
    val visible = steps.filter { !it.skip }
    fun finish() {
        if (finished) return
        finished = true
        onDone()
    }
    LaunchedEffect(tick, index, visible.size) {
        if (finished) return@LaunchedEffect
        if (visible.isEmpty() || index > visible.lastIndex) {
            finish()
            return@LaunchedEffect
        }
        val step = visible[index]
        val fromReturn = returnAdvance && step.advanceOnReturn
        if (returnAdvance) returnAdvance = false
        if (step.done || fromReturn) {
            if (index == visible.lastIndex) finish() else index += 1
        }
    }
    val step = visible.getOrNull(index.coerceAtMost(visible.lastIndex.coerceAtLeast(0)))
    if (step == null) {
        Box(Modifier.fillMaxSize())
        return
    }
    OnboardingScreen(
        title = step.title,
        body = step.body,
        icon = step.icon,
        action = step.action,
        index = index.coerceAtMost(visible.lastIndex),
        total = visible.size,
        onPrimary = {
            step.open()
            if (step.advanceOnReturn) armed = true
        },
    )
}

private fun buildSteps(
    listener: Boolean,
    notifications: Boolean,
    fullScreen: Boolean,
    dnd: Boolean,
    battery: Boolean,
    samsung: Boolean,
    openListener: () -> Unit,
    openNotifications: () -> Unit,
    openFullScreen: () -> Unit,
    openDnd: () -> Unit,
    openBattery: () -> Unit,
    openSamsung: () -> Unit,
    openRestricted: () -> Unit,
): List<OnboardingStep> = listOf(
    OnboardingStep(
        title = "제한된 설정",
        body = "파일로 설치했다면 앱 정보에서 허용하세요",
        icon = Icons.Rounded.LockOpen,
        action = "설정 열기",
        done = listener,
        advanceOnReturn = true,
        open = openRestricted,
    ),
    OnboardingStep(
        title = "알림 접근",
        body = "카카오톡 알림을 읽습니다",
        icon = Icons.Rounded.Notifications,
        action = "허용하기",
        done = listener,
        open = openListener,
    ),
    OnboardingStep(
        title = "알림 표시",
        body = "모집 글을 이 앱이 띄웁니다",
        icon = Icons.Rounded.NotificationsActive,
        action = "허용하기",
        done = notifications,
        open = openNotifications,
    ),
    OnboardingStep(
        title = "전체 화면",
        body = "잠금 화면 위로 알람을 띄웁니다",
        icon = Icons.Rounded.OpenInFull,
        action = "허용하기",
        done = fullScreen,
        open = openFullScreen,
    ),
    OnboardingStep(
        title = "방해금지",
        body = "방해금지 중에도 울립니다",
        icon = Icons.Rounded.DoNotDisturbOn,
        action = "허용하기",
        done = dnd,
        open = openDnd,
    ),
    OnboardingStep(
        title = "배터리",
        body = "절전 모드에서도 감시합니다",
        icon = Icons.Rounded.Bolt,
        action = "허용하기",
        done = battery,
        open = openBattery,
    ),
    OnboardingStep(
        title = "잠들지 않는 앱",
        body = "삼성 잠자기 목록에서 빼 주세요",
        icon = Icons.Rounded.Bedtime,
        action = "설정 열기",
        done = false,
        skip = !samsung,
        advanceOnReturn = true,
        open = openSamsung,
    ),
)

@Composable
fun OnboardingScreen(
    title: String,
    body: String,
    icon: ImageVector,
    action: String,
    index: Int,
    total: Int,
    onPrimary: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize().padding(horizontal = 24.dp).padding(top = 12.dp, bottom = 24.dp)) {
        StepProgress(index = index, total = total)
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Spacer(Modifier.height(24.dp))
            Box(
                Modifier.size(112.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(52.dp), tint = MaterialTheme.colorScheme.onPrimary)
            }
            Spacer(Modifier.height(28.dp))
            Text(
                title,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                body,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))
        }
        PrimaryButton(action, onPrimary, Modifier.fillMaxWidth())
    }
}

@Composable
private fun StepProgress(index: Int, total: Int) {
    Column(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        Text(
            "${index + 1} / $total",
            modifier = Modifier.align(Alignment.End).semantics { contentDescription = "${index + 1}단계, 총 ${total}단계" },
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(total.coerceAtLeast(1)) { step ->
                val color = if (step <= index) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                Box(
                    Modifier
                        .weight(1f)
                        .height(4.dp)
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(99.dp))
                        .background(color),
                )
            }
        }
    }
}
