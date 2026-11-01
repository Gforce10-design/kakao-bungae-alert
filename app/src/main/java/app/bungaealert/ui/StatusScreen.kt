package app.bungaealert.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.bungaealert.BungaeApp
import app.bungaealert.notify.ListenerRuntime

data class StatusModel(
    val access: Boolean,
    val bound: Boolean,
    val lastConnected: String,
    val lastDisconnected: String,
    val lastRebind: String,
    val lastCallback: String,
    val lastKakao: String,
    val lastProbe: String,
    val activeKakao: Int,
    val missed: Int,
    val keepAlive: Boolean,
    val batteryUnrestricted: Boolean,
    val backgroundRestricted: String,
    val standby: String,
    val powerSave: Boolean,
    val deviceIdle: Boolean,
    val samsung: Boolean,
)

@Composable
fun StatusRoute(onBack: () -> Unit) {
    val context = LocalContext.current
    val graph = (context.applicationContext as BungaeApp).graph
    val settings by graph.settings.state.collectAsState()
    var tick by remember { mutableIntStateOf(0) }
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
    val clock = remember(tick) { ListenerRuntime.clock(context) }
    val power = remember(tick) { Permissions.powerSnapshot(context) }
    val access = remember(tick) { Permissions.listenerEnabled(context) }
    val bound = remember(tick) { ListenerRuntime.bound }
    StatusScreen(
        model = StatusModel(
            access = access,
            bound = bound,
            lastConnected = dateTimeLabel(clock.lastConnectedAtMillis),
            lastDisconnected = dateTimeLabel(clock.lastDisconnectedAtMillis),
            lastRebind = dateTimeLabel(clock.lastRebindAtMillis),
            lastCallback = dateTimeLabel(clock.lastCallbackAtMillis),
            lastKakao = dateTimeLabel(clock.lastKakaoAtMillis),
            lastProbe = dateTimeLabel(clock.lastProbeAtMillis),
            activeKakao = clock.lastActiveKakaoCount,
            missed = clock.lastMissedCount,
            keepAlive = settings.listenerKeepAlive,
            batteryUnrestricted = power.batteryUnrestricted,
            backgroundRestricted = when (power.backgroundRestricted) {
                true -> "제한"
                false -> "없음"
                null -> "확인 불가"
            },
            standby = standbyKorean(power.standbyBucket),
            powerSave = power.powerSaveMode,
            deviceIdle = power.deviceIdle,
            samsung = power.samsung,
        ),
        onBack = onBack,
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
        onListener = { openSetting(context, Permissions.listenerDetail(context), "알림 접근 설정을 열지 못했습니다.") },
        onBattery = { openSetting(context, Permissions.batterySettings(context), "배터리 설정을 열지 못했습니다.") },
        onAppDetails = { openSetting(context, Permissions.appDetails(context), "앱 정보를 열지 못했습니다.") },
        onSamsung = { type ->
            openSetting(context, Permissions.samsungBackgroundList(type), "삼성 배터리 화면을 열지 못했습니다.")
        },
    )
}

@Composable
fun StatusScreen(
    model: StatusModel,
    onBack: () -> Unit,
    onRebind: () -> Unit,
    onRefresh: () -> Unit,
    onKeepAlive: (Boolean) -> Unit,
    onListener: () -> Unit,
    onBattery: () -> Unit,
    onAppDetails: () -> Unit,
    onSamsung: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val health = when {
        !model.access -> ListenerHealthUi.Blocked
        !model.bound -> ListenerHealthUi.Unbound
        else -> ListenerHealthUi.Watching
    }
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 4.dp, bottom = 24.dp),
    ) {
        BackHeader("연결", onBack)
        Spacer(Modifier.height(12.dp))
        ListenerBanner(health, onClick = if (health == ListenerHealthUi.Blocked) onListener else onRebind)
        Spacer(Modifier.height(16.dp))
        SectionLabel("상태")
        SurfaceCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 4.dp)) {
            CheckLine("알림 접근", ok = model.access, onClick = onListener)
            CheckLine("리스너", ok = model.bound, onClick = onRebind)
            CheckLine("마지막 연결", value = model.lastConnected)
            CheckLine("마지막 끊김", value = model.lastDisconnected)
            CheckLine("재연결 요청", value = model.lastRebind)
            CheckLine("받은 알림", value = model.lastCallback)
            CheckLine("카카오톡", value = model.lastKakao)
            CheckLine("마지막 점검", value = model.lastProbe)
            CheckLine("떠 있는 알림", value = "${model.activeKakao}개")
            CheckLine("놓친 알림", value = "${model.missed}개")
        }
        Text(
            "놓침은 콜백 없이 남아 있는 알림입니다",
            modifier = Modifier.padding(start = 4.dp, top = 8.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        SectionLabel("유지")
        SurfaceCard {
            RowSwitch("상태 알림", model.keepAlive, onKeepAlive)
            Text(
                "잠자기를 대신하지는 않습니다",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(16.dp))
        SectionLabel("절전")
        SurfaceCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 4.dp)) {
            CheckLine("배터리 제외", ok = model.batteryUnrestricted, onClick = onBattery)
            CheckLine("백그라운드", value = model.backgroundRestricted)
            CheckLine("대기 버킷", value = model.standby)
            CheckLine("절전 모드", value = if (model.powerSave) "켜짐" else "꺼짐")
            CheckLine("기기 절전", value = if (model.deviceIdle) "들어감" else "아님")
            CheckLine("앱 정보", onClick = onAppDetails)
        }
        Text(
            "파일 설치는 앱 정보에서 제한된 설정을 허용하세요",
            modifier = Modifier.padding(start = 4.dp, top = 8.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (model.samsung) {
            Spacer(Modifier.height(16.dp))
            SectionLabel("삼성")
            SurfaceCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 4.dp)) {
                CheckLine("잠들지 않는 앱", onClick = { onSamsung(2) })
                CheckLine("잠자는 앱", onClick = { onSamsung(0) })
                CheckLine("깊게 잠자는 앱", onClick = { onSamsung(1) })
            }
            Text(
                "잠자기 목록은 앱이 읽지 못합니다",
                modifier = Modifier.padding(start = 4.dp, top = 8.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(16.dp))
        PrimaryButton("다시 연결", onRebind, Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        SecondaryButton("새로고침", onRefresh, Modifier.fillMaxWidth())
    }
}

@Composable
private fun RowSwitch(title: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    androidx.compose.foundation.layout.Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
        BungaeSwitch(checked, onChecked, title)
    }
}
