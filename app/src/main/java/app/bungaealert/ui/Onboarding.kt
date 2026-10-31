package app.bungaealert.ui

import android.Manifest
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

private data class Step(
    val title: String,
    val body: String,
    val icon: Int,
    val action: String,
    val granted: Boolean,
    val detectable: Boolean,
    val open: () -> Unit,
)

@Composable
internal fun OnboardingRoute(onDone: () -> Unit) {
    val context = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    var index by remember { mutableIntStateOf(0) }
    var armed by remember { mutableStateOf(false) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) tick++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val askNotification = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        tick++
    }
    val steps = remember(tick) {
        onboardingSteps(context) {
            if (Build.VERSION.SDK_INT >= 33) askNotification.launch(Manifest.permission.POST_NOTIFICATIONS)
            else open(context, Permissions.appNotificationSettings(context))
        }
    }
    androidx.compose.runtime.LaunchedEffect(tick) {
        val snapshot = steps
        if (armed) {
            val current = snapshot.getOrNull(index)
            if (current != null && !current.detectable) index += 1
            armed = false
        }
        while (index < snapshot.size && snapshot[index].granted) index += 1
        if (snapshot.isNotEmpty() && index >= snapshot.size) onDone()
    }
    val step = steps.getOrNull(index) ?: return
    OnboardingPage(
        index = index,
        count = steps.size,
        title = step.title,
        body = step.body,
        icon = step.icon,
        action = step.action,
        onAction = {
            armed = true
            step.open()
        },
        onSkip = onDone,
    )
}

@Composable
internal fun OnboardingPage(
    index: Int,
    count: Int,
    title: String,
    body: String,
    icon: Int,
    action: String,
    onAction: () -> Unit,
    onSkip: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 16.dp),
    ) {
        Text("처음 설정", style = MaterialTheme.typography.labelLarge, color = scheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        ProgressMarks(index, count)
        Text(
            "${index + 1} / $count",
            modifier = Modifier.padding(top = 8.dp),
            style = MaterialTheme.typography.labelMedium,
            color = scheme.onSurfaceVariant,
        )
        Column(
            modifier = Modifier.weight(1f).fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier.size(96.dp).background(scheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Symbol(icon, null, tint = scheme.primary, size = 40.dp)
            }
            Spacer(Modifier.height(24.dp))
            Text(
                title,
                modifier = Modifier.semantics { heading() },
                style = MaterialTheme.typography.headlineLarge,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                body,
                style = MaterialTheme.typography.bodyLarge,
                color = scheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        PrimaryButton(action, onAction)
        QuietButton("나중에", onSkip, modifier = Modifier.align(Alignment.CenterHorizontally))
    }
}

private fun onboardingSteps(context: android.content.Context, askNotification: () -> Unit): List<Step> {
    val listenerOn = Permissions.listenerEnabled(context)
    return buildList {
        if (Build.VERSION.SDK_INT >= 33 && !listenerOn) {
            add(
                Step(
                    title = "제한된 설정",
                    body = "설치 파일의 알림 접근을 엽니다.",
                    icon = Sym.settings_applications,
                    action = "앱 정보",
                    granted = false,
                    detectable = false,
                    open = { open(context, Permissions.appDetails(context)) },
                ),
            )
        }
        add(
            Step(
                title = "알림 접근",
                body = "카카오톡 알림을 읽습니다.",
                icon = Sym.notifications,
                action = "허용하기",
                granted = listenerOn,
                detectable = true,
                open = { open(context, Permissions.listenerSettings()) },
            ),
        )
        add(
            Step(
                title = "알림 표시",
                body = "모집과 나눔을 알립니다.",
                icon = Sym.notifications_active,
                action = "허용하기",
                granted = Permissions.notificationsAllowed(context),
                detectable = true,
                open = askNotification,
            ),
        )
        add(
            Step(
                title = "전체 화면",
                body = "잠금 화면 위로 알람을 띄웁니다.",
                icon = Sym.fullscreen,
                action = "허용하기",
                granted = Permissions.fullScreenAllowed(context),
                detectable = true,
                open = { open(context, Permissions.fullScreenSettings(context)) },
            ),
        )
        add(
            Step(
                title = "방해금지",
                body = "방해금지 중에도 울립니다.",
                icon = Sym.do_not_disturb_on,
                action = "허용하기",
                granted = Permissions.dndAllowed(context),
                detectable = true,
                open = { open(context, Permissions.dndSettings()) },
            ),
        )
        add(
            Step(
                title = "배터리",
                body = "절전 중에도 감시를 유지합니다.",
                icon = Sym.battery_5_bar,
                action = "허용하기",
                granted = Permissions.batteryUnrestricted(context),
                detectable = true,
                open = { open(context, Permissions.batterySettings(context)) },
            ),
        )
        if (Permissions.isSamsung()) {
            add(
                Step(
                    title = "삼성 잠자기",
                    body = "잠들지 않는 앱에 넣습니다.",
                    icon = Sym.phone_android,
                    action = "설정 열기",
                    granted = false,
                    detectable = false,
                    open = { open(context, Permissions.samsungBackgroundList(2)) },
                ),
            )
        }
    }
}

private fun open(context: android.content.Context, intent: android.content.Intent) {
    if (!context.tryStart(intent)) {
        Toast.makeText(context, "설정을 열지 못했습니다.", Toast.LENGTH_LONG).show()
    }
}
