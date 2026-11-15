package app.resona.player.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val Amber = Color(0xFFFFB74D)
private val AmberSoft = Color(0xFFFFCC80)
private val Ink = Color(0xFF0E0F12)
private val InkSurface = Color(0xFF16181D)
private val InkSurfaceHigh = Color(0xFF1F222A)
private val Paper = Color(0xFFFBF8F3)

private val DarkColors = darkColorScheme(
    primary = Amber,
    onPrimary = Color(0xFF201400),
    primaryContainer = Color(0xFF3A2A0F),
    onPrimaryContainer = AmberSoft,
    secondary = Color(0xFFB8C4D9),
    onSecondary = Color(0xFF1A2230),
    background = Ink,
    onBackground = Color(0xFFE8E6E1),
    surface = Ink,
    onSurface = Color(0xFFE8E6E1),
    surfaceVariant = InkSurfaceHigh,
    onSurfaceVariant = Color(0xFFB9B6AE),
    surfaceContainer = InkSurface,
    surfaceContainerHigh = InkSurfaceHigh,
    outline = Color(0xFF4A4D55),
    error = Color(0xFFFF8A80),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF8A5A00),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFE0B2),
    onPrimaryContainer = Color(0xFF2B1A00),
    secondary = Color(0xFF4F5B71),
    background = Paper,
    onBackground = Color(0xFF1C1B18),
    surface = Paper,
    onSurface = Color(0xFF1C1B18),
    surfaceVariant = Color(0xFFEDE7DC),
    onSurfaceVariant = Color(0xFF4E4A42),
    surfaceContainer = Color(0xFFF3EEE5),
    surfaceContainerHigh = Color(0xFFEDE7DC),
    outline = Color(0xFF8C857A),
)

private val ResonaTypography = Typography(
    headlineMedium = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.3).sp),
    headlineSmall = TextStyle(fontSize = 23.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 19.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium),
    bodyMedium = TextStyle(fontSize = 14.sp),
    labelMedium = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.2.sp),
)

@Composable
fun ResonaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = ResonaTypography,
        content = content,
    )
}
