package app.bungaealert.ui

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Surface
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import app.bungaealert.R
import androidx.compose.foundation.shape.RoundedCornerShape

private val Pretendard = FontFamily(
    Font(R.font.pretendard_regular, FontWeight.Normal),
    Font(R.font.pretendard_medium, FontWeight.Medium),
    Font(R.font.pretendard_semibold, FontWeight.SemiBold),
    Font(R.font.pretendard_bold, FontWeight.Bold),
)

private val line = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None,
)

private fun type(size: Int, height: Int, weight: FontWeight, tracking: Float = 0f) = TextStyle(
    fontFamily = Pretendard,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = height.sp,
    letterSpacing = tracking.sp,
    platformStyle = PlatformTextStyle(includeFontPadding = true),
    lineHeightStyle = line,
)

private val AppType = Typography(
    displayLarge = type(40, 48, FontWeight.SemiBold, -0.4f),
    displayMedium = type(34, 42, FontWeight.SemiBold, -0.3f),
    displaySmall = type(28, 36, FontWeight.SemiBold, -0.2f),
    headlineLarge = type(26, 34, FontWeight.SemiBold, -0.2f),
    headlineMedium = type(22, 30, FontWeight.SemiBold),
    headlineSmall = type(18, 26, FontWeight.SemiBold),
    titleLarge = type(18, 26, FontWeight.SemiBold),
    titleMedium = type(16, 22, FontWeight.SemiBold),
    titleSmall = type(14, 20, FontWeight.Medium),
    bodyLarge = type(15, 22, FontWeight.Normal),
    bodyMedium = type(14, 20, FontWeight.Normal),
    bodySmall = type(12, 16, FontWeight.Normal),
    labelLarge = type(15, 20, FontWeight.SemiBold),
    labelMedium = type(13, 18, FontWeight.Medium),
    labelSmall = type(11, 14, FontWeight.Medium),
)

private val Ink = Color(0xFF2A2118)
private val Muted = Color(0xFF5C534C)
private val Paper = Color(0xFFF6F3EE)
private val Card = Color(0xFFFFFCF9)
private val Line = Color(0xFFE6DDD4)
private val Accent = Color(0xFF7C2D12)
private val AccentSoft = Color(0xFFF3E4D8)

private val LightColors = lightColorScheme(
    primary = Accent,
    onPrimary = Color.White,
    primaryContainer = AccentSoft,
    onPrimaryContainer = Color(0xFF2A140C),
    secondary = Accent,
    onSecondary = Color.White,
    secondaryContainer = AccentSoft,
    onSecondaryContainer = Color(0xFF2A140C),
    tertiary = Color(0xFF1F5C45),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFE4F1EA),
    onTertiaryContainer = Color(0xFF0E2E22),
    background = Paper,
    onBackground = Ink,
    surface = Card,
    onSurface = Ink,
    surfaceVariant = Color(0xFFEFE8E1),
    onSurfaceVariant = Muted,
    surfaceTint = Color.Transparent,
    inverseSurface = Ink,
    inverseOnSurface = Paper,
    inversePrimary = Color(0xFFE7A07A),
    outline = Color(0xFFD9CFC5),
    outlineVariant = Line,
    error = Color(0xFFB42318),
    onError = Color.White,
    errorContainer = Color(0xFFF8E4E1),
    onErrorContainer = Color(0xFF5C120E),
    scrim = Color(0x992A2118),
    surfaceBright = Color.White,
    surfaceDim = Color(0xFFE7DFD6),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFBF7F3),
    surfaceContainer = Paper,
    surfaceContainerHigh = Color(0xFFEFE8E1),
    surfaceContainerHighest = Color(0xFFE7DFD6),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFE7A07A),
    onPrimary = Color(0xFF2A140C),
    primaryContainer = Color(0xFF5C2E1C),
    onPrimaryContainer = Color(0xFFF8E6DA),
    secondary = Color(0xFFE7A07A),
    onSecondary = Color(0xFF2A140C),
    secondaryContainer = Color(0xFF5C2E1C),
    onSecondaryContainer = Color(0xFFF8E6DA),
    tertiary = Color(0xFF8FCBB0),
    onTertiary = Color(0xFF0E2E22),
    tertiaryContainer = Color(0xFF1A3D30),
    onTertiaryContainer = Color(0xFFD7F3E6),
    background = Color(0xFF161310),
    onBackground = Color(0xFFF6F1EA),
    surface = Color(0xFF221E1A),
    onSurface = Color(0xFFF6F1EA),
    surfaceVariant = Color(0xFF3A322C),
    onSurfaceVariant = Color(0xFFD9CBBF),
    surfaceTint = Color.Transparent,
    inverseSurface = Color(0xFFF6F1EA),
    inverseOnSurface = Ink,
    inversePrimary = Accent,
    outline = Color(0xFF5C5148),
    outlineVariant = Color(0xFF3A322C),
    error = Color(0xFFF0A8A2),
    onError = Color(0xFF4A100C),
    errorContainer = Color(0xFF5C201C),
    onErrorContainer = Color(0xFFF8E4E1),
    scrim = Color(0xCC000000),
    surfaceBright = Color(0xFF3A322C),
    surfaceDim = Color(0xFF100E0C),
    surfaceContainerLowest = Color(0xFF100E0C),
    surfaceContainerLow = Color(0xFF1C1815),
    surfaceContainer = Color(0xFF221E1A),
    surfaceContainerHigh = Color(0xFF2C2723),
    surfaceContainerHighest = Color(0xFF3A322C),
)

@Immutable
internal data class BungaeTones(
    val meetup: Color,
    val onMeetup: Color,
    val giveaway: Color,
    val onGiveaway: Color,
    val good: Color,
    val banner: Color,
    val alarmTop: Color,
    val alarmBottom: Color,
    val alarmButton: Color,
    val onAlarmButton: Color,
)

private val LightTones = BungaeTones(
    meetup = Color(0xFFF8EBD9),
    onMeetup = Color(0xFF8A4B12),
    giveaway = Color(0xFFE4F1EA),
    onGiveaway = Color(0xFF1F5C45),
    good = Color(0xFF1B6B43),
    banner = Color(0xFFE7DDD2),
    alarmTop = Color(0xFFC4272E),
    alarmBottom = Color(0xFF8E1218),
    alarmButton = Color(0xFF1C1412),
    onAlarmButton = Color.White,
)

private val DarkTones = BungaeTones(
    meetup = Color(0xFF3A2A1C),
    onMeetup = Color(0xFFF0C9A0),
    giveaway = Color(0xFF1A332A),
    onGiveaway = Color(0xFFB7E2CF),
    good = Color(0xFF8FCBB0),
    banner = Color(0xFF3A2E26),
    alarmTop = Color(0xFF9E1A20),
    alarmBottom = Color(0xFF4C0A0E),
    alarmButton = Color(0xFF1C1412),
    onAlarmButton = Color.White,
)

internal val LocalTones = staticCompositionLocalOf { LightTones }

internal val tones: BungaeTones
    @Composable get() = LocalTones.current

@Composable
fun BungaeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    lightStatusBar: Boolean = !darkTheme,
    content: @Composable () -> Unit,
) {
    val scheme = if (darkTheme) DarkColors else LightColors
    val extra = if (darkTheme) DarkTones else LightTones
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = lightStatusBar
            controller.isAppearanceLightNavigationBars = lightStatusBar
        }
    }
    MaterialTheme(
        colorScheme = scheme,
        typography = AppType,
        shapes = Shapes(
            extraSmall = RoundedCornerShape(8.dp),
            small = RoundedCornerShape(12.dp),
            medium = RoundedCornerShape(16.dp),
            large = RoundedCornerShape(20.dp),
            extraLarge = RoundedCornerShape(28.dp),
        ),
    ) {
        CompositionLocalProvider(LocalTones provides extra) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = scheme.background,
                contentColor = scheme.onBackground,
            ) {
                content()
            }
        }
    }
}
