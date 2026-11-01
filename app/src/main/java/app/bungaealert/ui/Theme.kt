package app.bungaealert.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.bungaealert.R

internal data class BungaeExtra(
    val good: Color,
    val warning: Color,
    val onWarning: Color,
    val danger: Color,
    val blocked: Color,
    val onBlocked: Color,
    val onBlockedMuted: Color,
    val meetupBg: Color,
    val meetupFg: Color,
    val giveawayBg: Color,
    val giveawayFg: Color,
    val shadow: Color,
)

private val Ink = Color(0xFF1C1612)
private val InkMuted = Color(0xFF4E463F)
private val Paper = Color(0xFFF6F3EE)
private val PaperDeep = Color(0xFFEBE6DE)
private val Card = Color(0xFFFFFCF9)
private val Line = Color(0xFFE3DBD2)
private val Accent = Color(0xFF3F2A22)
private val OnAccent = Color(0xFFFFF8F3)
private val AccentSoft = Color(0xFFF3E7DC)

private val NightInk = Color(0xFFF6F1EA)
private val NightMuted = Color(0xFFCDBFB2)
private val NightPaper = Color(0xFF12100E)
private val NightDeep = Color(0xFF2A241F)
private val NightCard = Color(0xFF1C1916)
private val NightLine = Color(0xFF3A332C)
private val NightAccent = Color(0xFFE8D3C0)
private val NightOnAccent = Color(0xFF1A120E)
private val NightSoft = Color(0xFF3A2E26)

private val lightExtra = BungaeExtra(
    good = Color(0xFF1E6B45),
    warning = Color(0xFF8A4B12),
    onWarning = Color(0xFFFFF8F3),
    danger = Color(0xFFB42318),
    blocked = Color(0xFF3F3A36),
    onBlocked = Color(0xFFFFF8F3),
    onBlockedMuted = Color(0xFFE7D9CF),
    meetupBg = Color(0xFFF3E1C8),
    meetupFg = Color(0xFF6B3E12),
    giveawayBg = Accent,
    giveawayFg = OnAccent,
    shadow = Color(0x141C1612),
)

private val darkExtra = BungaeExtra(
    good = Color(0xFF8ED4AE),
    warning = Color(0xFF8A4B12),
    onWarning = Color(0xFFFFF8F3),
    danger = Color(0xFFFFB4AB),
    blocked = Color(0xFF2C2824),
    onBlocked = NightInk,
    onBlockedMuted = Color(0xFFD9CBBF),
    meetupBg = Color(0xFF3A2C22),
    meetupFg = Color(0xFFF0D7C0),
    giveawayBg = NightAccent,
    giveawayFg = NightOnAccent,
    shadow = Color(0x00000000),
)

internal val LocalBungaeExtra = staticCompositionLocalOf { lightExtra }

private val lightScheme = lightColorScheme(
    primary = Accent,
    onPrimary = OnAccent,
    primaryContainer = AccentSoft,
    onPrimaryContainer = Accent,
    inversePrimary = AccentSoft,
    secondary = Accent,
    onSecondary = OnAccent,
    secondaryContainer = AccentSoft,
    onSecondaryContainer = Accent,
    tertiary = Accent,
    onTertiary = OnAccent,
    tertiaryContainer = AccentSoft,
    onTertiaryContainer = Accent,
    background = Paper,
    onBackground = Ink,
    surface = Card,
    onSurface = Ink,
    surfaceVariant = PaperDeep,
    onSurfaceVariant = InkMuted,
    surfaceTint = Color.Transparent,
    inverseSurface = Ink,
    inverseOnSurface = Paper,
    error = Color(0xFFB42318),
    onError = Color.White,
    errorContainer = Color(0xFFF9E4E1),
    onErrorContainer = Color(0xFF6B120C),
    outline = Line,
    outlineVariant = Line,
    scrim = Color(0x99000000),
    surfaceBright = Card,
    surfaceDim = PaperDeep,
    surfaceContainer = Card,
    surfaceContainerHigh = PaperDeep,
    surfaceContainerHighest = PaperDeep,
    surfaceContainerLow = Paper,
    surfaceContainerLowest = Card,
)

private val darkScheme = darkColorScheme(
    primary = NightAccent,
    onPrimary = NightOnAccent,
    primaryContainer = NightSoft,
    onPrimaryContainer = NightAccent,
    inversePrimary = Accent,
    secondary = NightAccent,
    onSecondary = NightOnAccent,
    secondaryContainer = NightSoft,
    onSecondaryContainer = NightAccent,
    tertiary = NightAccent,
    onTertiary = NightOnAccent,
    tertiaryContainer = NightSoft,
    onTertiaryContainer = NightAccent,
    background = NightPaper,
    onBackground = NightInk,
    surface = NightCard,
    onSurface = NightInk,
    surfaceVariant = NightDeep,
    onSurfaceVariant = NightMuted,
    surfaceTint = Color.Transparent,
    inverseSurface = NightInk,
    inverseOnSurface = NightPaper,
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF4A1C18),
    onErrorContainer = Color(0xFFFFB4AB),
    outline = NightLine,
    outlineVariant = NightLine,
    scrim = Color(0xCC000000),
    surfaceBright = NightDeep,
    surfaceDim = NightPaper,
    surfaceContainer = NightCard,
    surfaceContainerHigh = NightDeep,
    surfaceContainerHighest = NightDeep,
    surfaceContainerLow = NightPaper,
    surfaceContainerLowest = NightCard,
)

private val Pretendard = FontFamily(
    Font(R.font.pretendard_regular, FontWeight.Normal),
    Font(R.font.pretendard_medium, FontWeight.Medium),
    Font(R.font.pretendard_semibold, FontWeight.SemiBold),
    Font(R.font.pretendard_bold, FontWeight.Bold),
)

private val LineCenter = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None,
)

private fun type(weight: FontWeight, size: Int, line: Int) = TextStyle(
    fontFamily = Pretendard,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = line.sp,
    lineHeightStyle = LineCenter,
)

private val BungaeTypography = Typography(
    displayLarge = type(FontWeight.Bold, 40, 48),
    displayMedium = type(FontWeight.Bold, 36, 44),
    displaySmall = type(FontWeight.SemiBold, 32, 40),
    headlineLarge = type(FontWeight.SemiBold, 32, 40),
    headlineMedium = type(FontWeight.SemiBold, 26, 34),
    headlineSmall = type(FontWeight.SemiBold, 22, 30),
    titleLarge = type(FontWeight.SemiBold, 20, 28),
    titleMedium = type(FontWeight.SemiBold, 16, 24),
    titleSmall = type(FontWeight.SemiBold, 14, 20),
    bodyLarge = type(FontWeight.Normal, 16, 24),
    bodyMedium = type(FontWeight.Normal, 14, 22),
    bodySmall = type(FontWeight.Normal, 13, 18),
    labelLarge = type(FontWeight.Medium, 14, 20),
    labelMedium = type(FontWeight.Medium, 12, 16),
    labelSmall = type(FontWeight.Medium, 11, 14),
)

private val BungaeShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun BungaeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val scheme = if (darkTheme) darkScheme else lightScheme
    val extra = if (darkTheme) darkExtra else lightExtra
    CompositionLocalProvider(LocalBungaeExtra provides extra) {
        MaterialTheme(
            colorScheme = scheme,
            typography = BungaeTypography,
            shapes = BungaeShapes,
            content = content,
        )
    }
}
