package app.bungaealert.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val AlarmTop = Color(0xFFC43428)
private val AlarmMid = Color(0xFFA51C16)
private val AlarmBottom = Color(0xFF7A120E)
private val AlarmInk = Color(0xFFFFF8F4)
private val AlarmButton = Color(0xFF1A120E)
private val Pill = RoundedCornerShape(28.dp)

@Composable
fun AlarmScreen(
    title: String,
    body: String,
    onOpen: () -> Unit,
    onClose: () -> Unit,
    pulse: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val copy = parseAlarmTitle(title)
    val scale = if (pulse) {
        val transition = rememberInfiniteTransition(label = "alarm")
        val animated by transition.animateFloat(
            initialValue = 1f,
            targetValue = 1.06f,
            animationSpec = infiniteRepeatable(tween(1100), RepeatMode.Reverse),
            label = "pulse",
        )
        animated
    } else {
        1f
    }
    Column(
        modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(AlarmTop, AlarmMid, AlarmBottom)))
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(36.dp))
        Box(
            Modifier
                .size(96.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.NotificationsActive,
                contentDescription = null,
                modifier = Modifier.size(44.dp),
                tint = AlarmInk,
            )
        }
        Spacer(Modifier.height(28.dp))
        Text(
            copy.eyebrow,
            style = MaterialTheme.typography.labelLarge,
            color = Color(0xFFFFE8E4),
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            copy.chat,
            style = MaterialTheme.typography.displayMedium.copy(fontSize = 36.sp, lineHeight = 44.sp),
            color = AlarmInk,
            textAlign = TextAlign.Center,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        if (body.isNotBlank()) {
            Spacer(Modifier.height(16.dp))
            Text(
                snippetOf(body),
                style = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp, lineHeight = 26.sp),
                color = AlarmInk,
                textAlign = TextAlign.Center,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.weight(1f))
        AlarmPill(text = "카톡 열기", filled = true, onClick = onOpen)
        Spacer(Modifier.height(12.dp))
        AlarmPill(text = "끄기", filled = false, onClick = onClose)
    }
}

@Composable
private fun AlarmPill(text: String, filled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(Pill)
            .then(
                if (filled) {
                    Modifier.background(AlarmButton)
                } else {
                    Modifier.border(1.5.dp, AlarmInk, Pill)
                },
            )
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.titleMedium,
            color = AlarmInk,
            textAlign = TextAlign.Center,
        )
    }
}
