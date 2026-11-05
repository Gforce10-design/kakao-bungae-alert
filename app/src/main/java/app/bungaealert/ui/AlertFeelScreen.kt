package app.bungaealert.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.bungaealert.BungaeApp
import app.bungaealert.alert.AlertFeedback
import app.bungaealert.detect.AlarmSound
import app.bungaealert.detect.AlertFeel
import app.bungaealert.detect.VibrationPattern
import app.bungaealert.detect.VibrationStrength

/** "차임 · 진동 강/하트비트" style one-line summary for the settings list. */
fun alertFeelSummary(feel: AlertFeel): String {
    val vibration = if (feel.vibrationEnabled) {
        "진동 ${feel.vibrationStrength.label}·${feel.vibrationPattern.label}"
    } else {
        "진동 끔"
    }
    return "${feel.sound.label} · $vibration"
}

@Composable
fun AlertFeelRoute(onBack: () -> Unit) {
    val context = LocalContext.current
    val graph = (context.applicationContext as BungaeApp).graph
    val settings by graph.settings.state.collectAsState()
    AlertFeelScreen(
        feel = settings.feel,
        onBack = {
            AlertFeedback.shared(context).stop()
            onBack()
        },
        onSound = { sound ->
            graph.settings.update(graph.settings.current().copy(alarmSound = sound))
            AlertFeedback.shared(context).start(graph.settings.current().feel.copy(vibrationEnabled = false), loop = false)
        },
        onVibrationEnabled = { enabled ->
            graph.settings.update(graph.settings.current().copy(vibrationEnabled = enabled))
        },
        onStrength = { strength ->
            graph.settings.update(graph.settings.current().copy(vibrationStrength = strength))
        },
        onPattern = { pattern ->
            graph.settings.update(graph.settings.current().copy(vibrationPattern = pattern))
        },
        onPreviewSound = {
            AlertFeedback.shared(context).start(graph.settings.current().feel.copy(vibrationEnabled = false), loop = false)
        },
        onPreviewVibration = {
            AlertFeedback.shared(context).start(
                graph.settings.current().feel.copy(sound = AlarmSound.SILENT, vibrationEnabled = true),
                loop = false,
            )
        },
        onPreviewAll = {
            AlertFeedback.shared(context).start(graph.settings.current().feel, loop = false)
        },
        onStopPreview = { AlertFeedback.shared(context).stop() },
    )
}

@Composable
fun AlertFeelScreen(
    feel: AlertFeel,
    onBack: () -> Unit,
    onSound: (AlarmSound) -> Unit = {},
    onVibrationEnabled: (Boolean) -> Unit = {},
    onStrength: (VibrationStrength) -> Unit = {},
    onPattern: (VibrationPattern) -> Unit = {},
    onPreviewSound: () -> Unit = {},
    onPreviewVibration: () -> Unit = {},
    onPreviewAll: () -> Unit = {},
    onStopPreview: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 4.dp, bottom = 24.dp),
    ) {
        BackHeader("소리·진동", onBack)
        Spacer(Modifier.height(12.dp))
        SectionLabel("알람 소리")
        SurfaceCard(contentPadding = PaddingValues(vertical = 4.dp)) {
            AlarmSound.entries.forEach { sound ->
                ChoiceLine(sound.label, selected = sound == feel.sound, onClick = { onSound(sound) })
            }
        }
        Spacer(Modifier.height(8.dp))
        SecondaryButton("소리 미리듣기", onPreviewSound, Modifier.fillMaxWidth(), enabled = feel.plays)
        Spacer(Modifier.height(16.dp))
        SectionLabel("진동")
        SurfaceCard {
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "진동 사용",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                BungaeSwitch(feel.vibrationEnabled, onVibrationEnabled, "진동 사용")
            }
            if (feel.vibrationEnabled) {
                Spacer(Modifier.height(12.dp))
                Text("세기", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                ChipRow(
                    labels = VibrationStrength.entries.map { it.label },
                    selected = feel.vibrationStrength.ordinal,
                    onSelect = { onStrength(VibrationStrength.entries[it]) },
                )
                Spacer(Modifier.height(12.dp))
                Text("패턴", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                ChipRow(
                    labels = VibrationPattern.entries.map { it.label },
                    selected = feel.vibrationPattern.ordinal,
                    onSelect = { onPattern(VibrationPattern.entries[it]) },
                )
            }
        }
        if (feel.vibrationEnabled) {
            Spacer(Modifier.height(8.dp))
            SecondaryButton("진동 미리보기", onPreviewVibration, Modifier.fillMaxWidth())
        }
        Spacer(Modifier.height(16.dp))
        PrimaryButton("전체 미리보기", onPreviewAll, Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        SecondaryButton("미리보기 멈춤", onStopPreview, Modifier.fillMaxWidth())
        Spacer(Modifier.height(12.dp))
        Text(
            "다음 번개 알림부터 바로 적용됩니다. 알람음은 무음·진동 모드와 상관없이 울립니다.",
            modifier = Modifier.padding(start = 4.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ChoiceLine(title: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 12.dp)
            .semantics { this.selected = selected },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (selected) {
            Icon(
                Icons.Rounded.Check,
                contentDescription = "선택됨",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}
