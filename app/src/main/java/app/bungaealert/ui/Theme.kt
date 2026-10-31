package app.bungaealert.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val colors = lightColorScheme(
    primary = Color(0xFFB45309),
    onPrimary = Color.White,
    secondary = Color(0xFF9A3412),
    background = Color(0xFFFFF8F1),
    surface = Color(0xFFFFF8F1),
    onBackground = Color(0xFF3D1F00),
    onSurface = Color(0xFF3D1F00),
)

@Composable
fun BungaeTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, typography = Typography(), content = content)
}
