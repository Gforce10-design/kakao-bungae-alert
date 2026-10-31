package app.bungaealert.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
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
import app.bungaealert.notify.ListenerHealth
import app.bungaealert.notify.ListenerRuntime
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ListenerStatusScreen() {
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
    val access = Permissions.listenerEnabled(context)
    val bound = ListenerRuntime.bound
    val clock = ListenerRuntime.clock(context)
    val power = Permissions.powerSnapshot(context)
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("연결 상태", style = MaterialTheme.typography.headlineMedium)
        Text("이 화면의 연결 여부는 지금 이 프로세스 기준입니다. 프로세스가 죽으면 메모리의 연결 표시는 지워지고, 시각만 남습니다.")
        if (tick < 0) Text("")
        Text("알림 접근: ${if (access) "허용" else "꺼짐"}")
        Text("리스너 연결: ${if (bound) "연결됨" else "끊김"}")
        Text("마지막 연결: ${formatWhen(clock.lastConnectedAtMillis)}")
        Text("마지막 끊김: ${formatWhen(clock.lastDisconnectedAtMillis)}")
        Text("마지막 재연결 요청: ${formatWhen(clock.lastRebindAtMillis)}")
        Text("마지막으로 받은 알림(이 앱 포함): ${formatWhen(clock.lastCallbackAtMillis)}")
        Text("마지막 카카오톡 알림: ${formatWhen(clock.lastKakaoAtMillis)}")
        Text("마지막 점검: ${formatWhen(clock.lastProbeAtMillis)} · 떠 있는 카카오톡 알림 ${clock.lastActiveKakaoCount}개 · 놓친 것으로 보임 ${clock.lastMissedCount}개")
        Text(
            "놓친 알림은, 연결된 뒤에 올라온 카카오톡 알림이 아직 알림 바에 있는데 이 앱의 콜백이 그 키를 받지 못한 경우입니다.",
            style = MaterialTheme.typography.bodySmall,
        )
        Button(onClick = {
            ListenerRuntime.requestRebind(context)
            tick++
        }, modifier = Modifier.fillMaxWidth()) { Text("다시 연결") }
        OutlinedButton(onClick = {
            ListenerRuntime.refreshProbe(context)
            tick++
        }, modifier = Modifier.fillMaxWidth()) { Text("상태 새로고침") }
        Text("상태 알림", style = MaterialTheme.typography.titleMedium)
        Text("낮은 중요도의 진행 중 알림으로 이 프로세스를 캐시 상태에서 빼 둡니다. 삼성 잠자기·깊은 잠자기는 포그라운드 서비스도 막으므로, 이 알림만으로 절전이 해결되지는 않습니다.")
        Switch(checked = settings.listenerKeepAlive, onCheckedChange = { enabled ->
            graph.settings.update(graph.settings.current().copy(listenerKeepAlive = enabled))
            ListenerRuntime.ensure(context, fromWatchdog = false)
            tick++
        })
        Text("권한과 절전", style = MaterialTheme.typography.titleMedium)
        Text("배터리 최적화 제외: ${if (power.batteryUnrestricted) "예" else "아니오"}")
        Text("백그라운드 제한: ${when (power.backgroundRestricted) { true -> "제한됨"; false -> "아님"; null -> "이 버전에서 확인 불가" }}")
        Text("앱 대기 버킷: ${power.standbyBucket?.let(ListenerHealth::standbyBucketLabel) ?: "확인 불가"}")
        Text("절전 모드: ${if (power.powerSaveMode) "켜짐" else "꺼짐"} · 기기 절전(Doze): ${if (power.deviceIdle) "들어감" else "아님"}")
        Text(
            "잠자는 앱·깊게 잠자는 앱·자동 절전 목록은 앱이 읽지 못합니다. 삼성은 그 목록을 배터리 최적화 제외와 따로 둡니다.",
            style = MaterialTheme.typography.bodySmall,
        )
        OutlinedButton(onClick = { openOrToast(context, Permissions.listenerDetail(context), "알림 접근 설정을 열지 못했습니다.") }, modifier = Modifier.fillMaxWidth()) {
            Text("알림 접근")
        }
        OutlinedButton(onClick = { openOrToast(context, Permissions.batterySettings(context), "배터리 설정을 열지 못했습니다.") }, modifier = Modifier.fillMaxWidth()) {
            Text("배터리 최적화 제외")
        }
        OutlinedButton(onClick = { openOrToast(context, Permissions.appDetails(context), "앱 정보를 열지 못했습니다.") }, modifier = Modifier.fillMaxWidth()) {
            Text("앱 정보 (제한된 설정)")
        }
        if (power.samsung) {
            Text("Galaxy에서는 설정 → 배터리 및 디바이스 케어 → 배터리 → 백그라운드 사용 제한에서 이 앱을 잠자는 앱과 깊게 잠자는 앱에서 빼고, 잠들지 않는 앱에 넣으세요. 사용하지 않는 앱 절전도 이 앱을 재울 수 있습니다.")
            OutlinedButton(onClick = { openOrToast(context, Permissions.samsungBackgroundList(2), "잠들지 않는 앱 화면을 열지 못했습니다. 배터리 → 백그라운드 사용 제한으로 이동하세요.") }, modifier = Modifier.fillMaxWidth()) {
                Text("잠들지 않는 앱")
            }
            OutlinedButton(onClick = { openOrToast(context, Permissions.samsungBackgroundList(0), "잠자는 앱 화면을 열지 못했습니다.") }, modifier = Modifier.fillMaxWidth()) {
                Text("잠자는 앱")
            }
            OutlinedButton(onClick = { openOrToast(context, Permissions.samsungBackgroundList(1), "깊게 잠자는 앱 화면을 열지 못했습니다.") }, modifier = Modifier.fillMaxWidth()) {
                Text("깊게 잠자는 앱")
            }
        }
        Text("사이드로드", style = MaterialTheme.typography.titleMedium)
        Text("Play 스토어 밖 APK는 Android 13 이상에서 알림 접근이 제한된 설정일 수 있습니다. 앱 정보 오른쪽 위 메뉴에서 제한된 설정을 허용한 뒤 알림 접근을 켜세요. 서명이 다른 APK를 덮어 설치할 수 없어 앱을 지우면 알림 접근도 지워집니다.")
        Text("설정에서 앱을 강제 중지하면, 앱을 다시 열기 전까지 리스너가 붙지 않습니다.", style = MaterialTheme.typography.bodySmall)
    }
}

private fun formatWhen(millis: Long): String {
    if (millis <= 0L) return "없음"
    return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.KOREA).format(Date(millis))
}

private fun openOrToast(context: android.content.Context, intent: android.content.Intent, failure: String) {
    if (!context.tryStart(intent)) {
        Toast.makeText(context, failure, Toast.LENGTH_LONG).show()
    }
}
