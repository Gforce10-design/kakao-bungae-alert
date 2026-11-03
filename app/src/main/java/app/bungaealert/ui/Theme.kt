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

private val Ink = Color(0xFF191F28)
private val InkMuted = Color(0xFF4E5968)
private val Page = Color(0xFFF2F4F6)
private val Card = Color(0xFFFFFFFF)
private val Well = Color(0xFFE5E8EB)
private val Line = Color(0xFFD0D5DC)
private val Blue = Color(0xFF0B57D0)
private val OnBlue = Color(0xFFFFFFFF)
private val BlueSoft = Color(0xFFD7E6FF)
private val InkSoft = Color(0xFF333D4B)

private val NightInk = Color(0xFFF5F7FA)
private val NightMuted = Color(0xFFB0B8C2)
private val NightPage = Color(0xFF0E1013)
private val NightCard = Color(0xFF1A1D22)
private val NightWell = Color(0xFF2C3138)
private val NightLine = Color(0xFF3E4650)
private val NightBlue = Color(0xFF4C8DFF)
private val NightOnBlue = Color(0xFF07111F)
private val NightBlueSoft = Color(0xFF1A3E78)

private val lightExtra = BungaeExtra(
    good = Color(0xFF067647),
    warning = Color(0xFFC2410C),
    onWarning = Color.White,
    danger = Color(0xFFD92D20),
    blocked = Ink,
    onBlocked = Color.White,
    onBlockedMuted = Color(0xFFD0D5DC),
    meetupBg = Blue,
    meetupFg = OnBlue,
    giveawayBg = Color(0xFFE11D2E),
    giveawayFg = Color.White,
    shadow = Color(0x14191F28),
)

private val darkExtra = BungaeExtra(
    good = Color(0xFF3DDC97),
    warning = Color(0xFFC2410C),
    onWarning = Color.White,
    danger = Color(0xFFFF6B6B),
    blocked = Color(0xFF2A3038),
    onBlocked = NightInk,
    onBlockedMuted = NightMuted,
    meetupBg = NightBlue,
    meetupFg = NightOnBlue,
    giveawayBg = Color(0xFFE11D2E),
    giveawayFg = Color.White,
    shadow = Color(0x00000000),
)

internal val LocalBungaeExtra = staticCompositionLocalOf { lightExtra }

private val lightScheme = lightColorScheme(
    primary = Blue,
    onPrimary = OnBlue,
    primaryContainer = BlueSoft,
    onPrimaryContainer = Color(0xFF062E6B),
    inversePrimary = NightBlue,
    secondary = InkSoft,
    onSecondary = OnBlue,
    secondaryContainer = Color(0xFFE8ECF1),
    onSecondaryContainer = Ink,
    tertiary = InkSoft,
    onTertiary = OnBlue,
    tertiaryContainer = Color(0xFFE8ECF1),
    onTertiaryContainer = Ink,
    background = Page,
    onBackground = Ink,
    surface = Card,
    onSurface = Ink,
    surfaceVariant = Well,
    onSurfaceVariant = InkMuted,
    surfaceTint = Color.Transparent,
    inverseSurface = Ink,
    inverseOnSurface = Page,
    error = Color(0xFFD92D20),
    onError = Color.White,
    errorContainer = Color(0xFFFFE4E1),
    onErrorContainer = Color(0xFF7A1A12),
    outline = Line,
    outlineVariant = Well,
    scrim = Color(0x99000000),
    surfaceBright = Card,
    surfaceDim = Well,
    surfaceContainerLowest = Card,
    surfaceContainerLow = Color(0xFFF7F8FA),
    surfaceContainer = Card,
    surfaceContainerHigh = Color(0xFFEEF1F4),
    surfaceContainerHighest = Well,
    primaryFixed = Blue,
    primaryFixedDim = Color(0xFF0842A0),
    onPrimaryFixed = OnBlue,
    onPrimaryFixedVariant = BlueSoft,
    secondaryFixed = InkSoft,
    secondaryFixedDim = Ink,
    onSecondaryFixed = OnBlue,
    onSecondaryFixedVariant = Color(0xFFE8ECF1),
    tertiaryFixed = InkSoft,
    tertiaryFixedDim = Ink,
    onTertiaryFixed = OnBlue,
    onTertiaryFixedVariant = Color(0xFFE8ECF1),
)

private val darkScheme = darkColorScheme(
    primary = NightBlue,
    onPrimary = NightOnBlue,
    primaryContainer = NightBlueSoft,
    onPrimaryContainer = BlueSoft,
    inversePrimary = Blue,
    secondary = Color(0xFFC5CDD6),
    onSecondary = NightPage,
    secondaryContainer = Color(0xFF2A3038),
    onSecondaryContainer = NightInk,
    tertiary = Color(0xFFC5CDD6),
    onTertiary = NightPage,
    tertiaryContainer = Color(0xFF2A3038),
    onTertiaryContainer = NightInk,
    background = NightPage,
    onBackground = NightInk,
    surface = NightCard,
    onSurface = NightInk,
    surfaceVariant = NightWell,
    onSurfaceVariant = NightMuted,
    surfaceTint = Color.Transparent,
    inverseSurface = NightInk,
    inverseOnSurface = NightPage,
    error = Color(0xFFFF6B6B),
    onError = Color(0xFF3A0D10),
    errorContainer = Color(0xFF4A1518),
    onErrorContainer = Color(0xFFFFD4D2),
    outline = NightLine,
    outlineVariant = NightWell,
    scrim = Color(0xCC000000),
    surfaceBright = NightWell,
    surfaceDim = NightPage,
    surfaceContainerLowest = NightPage,
    surfaceContainerLow = Color(0xFF14171B),
    surfaceContainer = NightCard,
    surfaceContainerHigh = Color(0xFF24282E),
    surfaceContainerHighest = NightWell,
    primaryFixed = Blue,
    primaryFixedDim = Color(0xFF0842A0),
    onPrimaryFixed = OnBlue,
    onPrimaryFixedVariant = BlueSoft,
    secondaryFixed = InkSoft,
    secondaryFixedDim = Ink,
    onSecondaryFixed = OnBlue,
    onSecondaryFixedVariant = Color(0xFFE8ECF1),
    tertiaryFixed = InkSoft,
    tertiaryFixedDim = Ink,
    onTertiaryFixed = OnBlue,
    onTertiaryFixedVariant = Color(0xFFE8ECF1),
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
    // Dynamic color stays off. A system palette can inject purple, and every
    // scheme slot above is set so Material's default roles cannot leak in.
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
