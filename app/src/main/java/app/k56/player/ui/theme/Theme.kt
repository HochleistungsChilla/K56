package app.k56.player.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import android.content.Context

/** User-selectable appearance. */
enum class ThemeMode { System, Light, Dark, Matrix }

/** Persists the selected [ThemeMode] and exposes it as Compose state. */
object ThemeController {
    private const val PREFS = "k56_appearance"
    private const val KEY = "theme_mode"

    var mode by mutableStateOf(ThemeMode.System)
        private set

    fun load(context: Context) {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
        mode = runCatching { ThemeMode.valueOf(raw.orEmpty()) }.getOrDefault(ThemeMode.System)
    }

    fun set(context: Context, value: ThemeMode) {
        mode = value
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, value.name).apply()
    }
}

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

private val K56Typography = Typography(
    headlineMedium = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.3).sp),
    headlineSmall = TextStyle(fontSize = 23.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 19.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium),
    bodyMedium = TextStyle(fontSize = 14.sp),
    labelMedium = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.2.sp),
)

private val MatrixColors = darkColorScheme(
    primary = Color(0xFF00FF41),
    onPrimary = Color(0xFF001B06),
    primaryContainer = Color(0xFF0A3B18),
    onPrimaryContainer = Color(0xFF7CFFA6),
    secondary = Color(0xFF12B34A),
    onSecondary = Color(0xFF001B06),
    background = Color(0xFF040705),
    onBackground = Color(0xFFB8FFC9),
    surface = Color(0xFF040705),
    onSurface = Color(0xFFB8FFC9),
    surfaceVariant = Color(0xFF0D1F14),
    onSurfaceVariant = Color(0xFF5FBF83),
    surfaceContainer = Color(0xFF08120B),
    surfaceContainerHigh = Color(0xFF0D1F14),
    outline = Color(0xFF1F5F3A),
    error = Color(0xFFFF6B6B),
)

private val MatrixTypography = Typography(
    headlineMedium = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 26.sp, fontWeight = FontWeight.Bold),
    headlineSmall = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 22.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 18.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 15.sp, fontWeight = FontWeight.Medium),
    bodyMedium = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp),
    labelMedium = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp, fontWeight = FontWeight.Medium),
)

@Composable
fun K56Theme(
    mode: ThemeMode = ThemeMode.System,
    content: @Composable () -> Unit,
) {
    val scheme = when (mode) {
        ThemeMode.Light -> LightColors
        ThemeMode.Dark -> DarkColors
        ThemeMode.Matrix -> MatrixColors
        ThemeMode.System -> if (isSystemInDarkTheme()) DarkColors else LightColors
    }
    MaterialTheme(
        colorScheme = scheme,
        typography = if (mode == ThemeMode.Matrix) MatrixTypography else K56Typography,
        content = content,
    )
}
