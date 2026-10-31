package app.bungaealert.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.MaterialTheme

@Composable
fun AlarmScreen(
    title: String,
    body: String,
    onOpen: () -> Unit,
    onClose: () -> Unit,
    animate: Boolean = true,
) {
    val tone = tones
    val scale = if (animate) pulse() else 1f
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(tone.alarmTop, tone.alarmBottom)))
            .systemBarsPadding()
            .padding(horizontal = 24.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .semantics { liveRegion = LiveRegionMode.Assertive },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(140.dp)) {
                Box(
                    modifier = Modifier
                        .size(112.dp)
                        .scale(scale)
                        .background(Color.White.copy(alpha = 0.14f), CircleShape),
                )
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .background(Color.White.copy(alpha = 0.18f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Symbol(Sym.alarm, "알람", tint = Color.White, size = 40.dp)
                }
            }
            Spacer(Modifier.height(28.dp))
            Text(
                "알람",
                style = MaterialTheme.typography.labelLarge,
                color = Color.White.copy(alpha = 0.82f),
            )
            Spacer(Modifier.height(12.dp))
            Text(
                title.ifBlank { "새 알림" },
                style = MaterialTheme.typography.displayMedium,
                color = Color.White,
                textAlign = TextAlign.Center,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            if (body.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                Text(
                    body,
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White.copy(alpha = 0.92f),
                    textAlign = TextAlign.Center,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Button(
            onClick = onOpen,
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            shape = AlarmButtonShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = tone.alarmButton,
                contentColor = tone.onAlarmButton,
            ),
        ) {
            Text("카톡 열기", style = MaterialTheme.typography.labelLarge)
        }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(
            onClick = onClose,
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            shape = AlarmButtonShape,
            border = BorderStroke(1.5.dp, Color.White),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
        ) {
            Text("끄기", style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun pulse(): Float {
    val transition = rememberInfiniteTransition(label = "alarm")
    val scale by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "scale",
    )
    return scale
}
