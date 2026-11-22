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

/*
 * Complete Material 3 role sets. Every role Material components can reach is
 * defined here on purpose: an unset role falls back to the baseline scheme,
 * which is purple-ish and clashes with the warm (or green) palette.
 *
 * Hierarchy rule used across the app:
 *   primary      -> interactive / active
 *   onSurface    -> content that matters
 *   onSurfaceVariant -> labels, secondary information
 *   containers   -> surfaces, never accent text
 */
private val DarkColors = darkColorScheme(
    primary = Amber,
    onPrimary = Color(0xFF201400),
    primaryContainer = Color(0xFF4A3410),
    onPrimaryContainer = Color(0xFFFFD9A0),
    inversePrimary = Color(0xFF8A5A00),
    secondary = Color(0xFFC9C2B4),
    onSecondary = Color(0xFF221F17),
    secondaryContainer = Color(0xFF33302A),
    onSecondaryContainer = Color(0xFFE7E0D3),
    tertiary = Color(0xFFA9C7B5),
    onTertiary = Color(0xFF12251C),
    tertiaryContainer = Color(0xFF233A2E),
    onTertiaryContainer = Color(0xFFC5E4D3),
    background = Ink,
    onBackground = Color(0xFFE8E6E1),
    surface = Ink,
    onSurface = Color(0xFFE8E6E1),
    surfaceVariant = InkSurfaceHigh,
    onSurfaceVariant = Color(0xFFABA8A0),
    surfaceTint = Amber,
    surfaceBright = Color(0xFF2E323B),
    surfaceDim = Color(0xFF0E0F12),
    surfaceContainerLowest = Color(0xFF0A0B0E),
    surfaceContainerLow = Color(0xFF121419),
    surfaceContainer = InkSurface,
    surfaceContainerHigh = InkSurfaceHigh,
    surfaceContainerHighest = Color(0xFF262A33),
    outline = Color(0xFF4A4D55),
    outlineVariant = Color(0xFF2C2F36),
    error = Color(0xFFFF8A80),
    onError = Color(0xFF3B0907),
    errorContainer = Color(0xFF5C1A16),
    onErrorContainer = Color(0xFFFFDAD6),
    inverseSurface = Color(0xFFE8E6E1),
    inverseOnSurface = Color(0xFF1C1B18),
    scrim = Color(0xFF000000),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF8A5A00),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFE0B2),
    onPrimaryContainer = Color(0xFF2B1A00),
    inversePrimary = Amber,
    secondary = Color(0xFF5C5548),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFEDE3D2),
    onSecondaryContainer = Color(0xFF1E1B14),
    tertiary = Color(0xFF3F6350),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFC1E8D3),
    onTertiaryContainer = Color(0xFF04271A),
    background = Paper,
    onBackground = Color(0xFF1C1B18),
    surface = Paper,
    onSurface = Color(0xFF1C1B18),
    surfaceVariant = Color(0xFFEDE7DC),
    onSurfaceVariant = Color(0xFF5A554C),
    surfaceTint = Color(0xFF8A5A00),
    surfaceBright = Color.White,
    surfaceDim = Color(0xFFDDD8CE),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF7F3EA),
    surfaceContainer = Color(0xFFF3EEE5),
    surfaceContainerHigh = Color(0xFFEDE7DC),
    surfaceContainerHighest = Color(0xFFE4DED2),
    outline = Color(0xFF8C857A),
    outlineVariant = Color(0xFFD3CCC0),
    error = Color(0xFFB3261E),
    onError = Color.White,
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
    inverseSurface = Color(0xFF31302B),
    inverseOnSurface = Color(0xFFF4F0E8),
    scrim = Color(0xFF000000),
)

private val K56Typography = Typography(
    headlineMedium = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.3).sp),
    headlineSmall = TextStyle(fontSize = 23.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 19.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium),
    titleSmall = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 15.sp, lineHeight = 21.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.1.sp),
    labelMedium = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.2.sp),
    labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.3.sp),
)

private val MatrixColors = darkColorScheme(
    primary = Color(0xFF00FF41),
    onPrimary = Color(0xFF001B06),
    primaryContainer = Color(0xFF0C3F1B),
    onPrimaryContainer = Color(0xFF9BFFB8),
    inversePrimary = Color(0xFF006B22),
    secondary = Color(0xFF7FD6A0),
    onSecondary = Color(0xFF04200F),
    secondaryContainer = Color(0xFF12331F),
    onSecondaryContainer = Color(0xFFB5FFD0),
    tertiary = Color(0xFF35C46A),
    onTertiary = Color(0xFF001B06),
    tertiaryContainer = Color(0xFF10301C),
    onTertiaryContainer = Color(0xFF9BFFBE),
    background = Color(0xFF040705),
    onBackground = Color(0xFFB8FFC9),
    surface = Color(0xFF040705),
    onSurface = Color(0xFFB8FFC9),
    surfaceVariant = Color(0xFF0D1F14),
    onSurfaceVariant = Color(0xFF6FAF86),
    surfaceTint = Color(0xFF00FF41),
    surfaceBright = Color(0xFF16351F),
    surfaceDim = Color(0xFF040705),
    surfaceContainerLowest = Color(0xFF020403),
    surfaceContainerLow = Color(0xFF06100A),
    surfaceContainer = Color(0xFF08120B),
    surfaceContainerHigh = Color(0xFF0D1F14),
    surfaceContainerHighest = Color(0xFF12291B),
    outline = Color(0xFF1F5F3A),
    outlineVariant = Color(0xFF153A25),
    error = Color(0xFFFF6B6B),
    onError = Color(0xFF2A0000),
    errorContainer = Color(0xFF4A1212),
    onErrorContainer = Color(0xFFFFD4D4),
    inverseSurface = Color(0xFFB8FFC9),
    inverseOnSurface = Color(0xFF04170A),
    scrim = Color(0xFF000000),
)

private val MatrixTypography = Typography(
    headlineMedium = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 26.sp, fontWeight = FontWeight.Bold),
    headlineSmall = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 22.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 18.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 15.sp, fontWeight = FontWeight.Medium),
    titleSmall = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 14.sp, lineHeight = 20.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, lineHeight = 18.sp),
    bodySmall = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 15.sp),
    labelLarge = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 10.sp, fontWeight = FontWeight.Medium),
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
