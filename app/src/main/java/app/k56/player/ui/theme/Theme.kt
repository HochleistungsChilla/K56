package app.k56.player.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
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

/**
 * Persists mode and palette in one place and exposes both as Compose state.
 *
 * The two are independent: mode says light/dark, palette says which dark set.
 */
object ThemeController {
    private const val PREFS = "k56_appearance"
    private const val KEY_MODE = "theme_mode"
    private const val KEY_PALETTE = "theme_palette"

    var mode by mutableStateOf(ThemeMode.System)
        private set

    var palette by mutableStateOf(ThemePalette.Warm)
        private set

    fun load(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val modeRaw = prefs.getString(KEY_MODE, null)
        val paletteRaw = prefs.getString(KEY_PALETTE, null)
        val selection = resolveSelection(modeRaw, paletteRaw)
        mode = selection.mode
        palette = selection.palette
        // Rewrite once: the old single preference must not resurrect Matrix as a mode.
        if (needsPaletteMigration(modeRaw, paletteRaw)) {
            prefs.edit()
                .putString(KEY_MODE, selection.mode.name)
                .putString(KEY_PALETTE, selection.palette.name)
                .apply()
        }
    }

    fun setMode(context: Context, value: ThemeMode) {
        mode = value
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_MODE, value.name).apply()
    }

    fun setPalette(context: Context, value: ThemePalette) {
        palette = value
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_PALETTE, value.name).apply()
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

private val IceColors = darkColorScheme(
    primary = Color(0xFF7CD8F0),
    onPrimary = Color(0xFF00252E),
    primaryContainer = Color(0xFF0E3D4A),
    onPrimaryContainer = Color(0xFFB9EEFF),
    inversePrimary = Color(0xFF38616C),
    secondary = Color(0xFFA9C4D0),
    onSecondary = Color(0xFF0B1A20),
    secondaryContainer = Color(0xFF1B2B33),
    onSecondaryContainer = Color(0xFFCDE7F0),
    tertiary = Color(0xFF8FB8FF),
    onTertiary = Color(0xFF001B4D),
    tertiaryContainer = Color(0xFF12305E),
    onTertiaryContainer = Color(0xFFD9E3FF),
    background = Color(0xFF0A0F14),
    onBackground = Color(0xFFE6EDF2),
    surface = Color(0xFF0A0F14),
    onSurface = Color(0xFFE6EDF2),
    surfaceVariant = Color(0xFF10171F),
    onSurfaceVariant = Color(0xFFA6B7C1),
    surfaceTint = Color(0xFF7CD8F0),
    surfaceBright = Color(0xFF1C2B39),
    surfaceDim = Color(0xFF0A0F14),
    surfaceContainerLowest = Color(0xFF080C10),
    surfaceContainerLow = Color(0xFF0A0F14),
    surfaceContainer = Color(0xFF10171F),
    surfaceContainerHigh = Color(0xFF141E28),
    surfaceContainerHighest = Color(0xFF182430),
    outline = Color(0xFF344E68),
    outlineVariant = Color(0xFF1F2E3E),
    error = Color(0xFFFF8A80),
    onError = Color(0xFF3B0907),
    errorContainer = Color(0xFF5C1A16),
    onErrorContainer = Color(0xFFFFDAD6),
    inverseSurface = Color(0xFFE6EDF2),
    inverseOnSurface = Color(0xFF0A0F14),
    scrim = Color(0xFF000000),
)

private val TwilightColors = darkColorScheme(
    primary = Color(0xFFC9A9FF),
    onPrimary = Color(0xFF2A0F52),
    primaryContainer = Color(0xFF3B2266),
    onPrimaryContainer = Color(0xFFEADDFF),
    inversePrimary = Color(0xFF5A4C73),
    secondary = Color(0xFFC8BFD8),
    onSecondary = Color(0xFF191325),
    secondaryContainer = Color(0xFF272038),
    onSecondaryContainer = Color(0xFFE4DCF2),
    tertiary = Color(0xFFFFAFD4),
    onTertiary = Color(0xFF3F0A28),
    tertiaryContainer = Color(0xFF521436),
    onTertiaryContainer = Color(0xFFFFD9E8),
    background = Color(0xFF0B0910),
    onBackground = Color(0xFFEAE4F2),
    surface = Color(0xFF0B0910),
    onSurface = Color(0xFFEAE4F2),
    surfaceVariant = Color(0xFF110E19),
    onSurfaceVariant = Color(0xFFB4AAC6),
    surfaceTint = Color(0xFFC9A9FF),
    surfaceBright = Color(0xFF1F1A2E),
    surfaceDim = Color(0xFF0B0910),
    surfaceContainerLowest = Color(0xFF09070D),
    surfaceContainerLow = Color(0xFF0B0910),
    surfaceContainer = Color(0xFF110E19),
    surfaceContainerHigh = Color(0xFF161220),
    surfaceContainerHighest = Color(0xFF1B1627),
    outline = Color(0xFF392F53),
    outlineVariant = Color(0xFF221C32),
    error = Color(0xFFFF8A80),
    onError = Color(0xFF3B0907),
    errorContainer = Color(0xFF5C1A16),
    onErrorContainer = Color(0xFFFFDAD6),
    inverseSurface = Color(0xFFEAE4F2),
    inverseOnSurface = Color(0xFF0B0910),
    scrim = Color(0xFF000000),
)

private val SakuraColors = darkColorScheme(
    primary = Color(0xFFFF9EC4),
    onPrimary = Color(0xFF4A0B24),
    primaryContainer = Color(0xFF5C1B33),
    onPrimaryContainer = Color(0xFFFFD9E7),
    inversePrimary = Color(0xFF734758),
    secondary = Color(0xFFD8BFC7),
    onSecondary = Color(0xFF241419),
    secondaryContainer = Color(0xFF332027),
    onSecondaryContainer = Color(0xFFF2DCE4),
    tertiary = Color(0xFFFFB59B),
    onTertiary = Color(0xFF4A1505),
    tertiaryContainer = Color(0xFF5E2412),
    onTertiaryContainer = Color(0xFFFFDBCF),
    background = Color(0xFF100A0C),
    onBackground = Color(0xFFF2E6EA),
    surface = Color(0xFF100A0C),
    onSurface = Color(0xFFF2E6EA),
    surfaceVariant = Color(0xFF191013),
    onSurfaceVariant = Color(0xFFC6AFB7),
    surfaceTint = Color(0xFFFF9EC4),
    surfaceBright = Color(0xFF2E1C22),
    surfaceDim = Color(0xFF100A0C),
    surfaceContainerLowest = Color(0xFF0D080A),
    surfaceContainerLow = Color(0xFF100A0C),
    surfaceContainer = Color(0xFF191013),
    surfaceContainerHigh = Color(0xFF201418),
    surfaceContainerHighest = Color(0xFF27181D),
    outline = Color(0xFF53343E),
    outlineVariant = Color(0xFF321F25),
    error = Color(0xFFFF8A80),
    onError = Color(0xFF3B0907),
    errorContainer = Color(0xFF5C1A16),
    onErrorContainer = Color(0xFFFFDAD6),
    inverseSurface = Color(0xFFF2E6EA),
    inverseOnSurface = Color(0xFF100A0C),
    scrim = Color(0xFF000000),
)

private val CobaltColors = darkColorScheme(
    primary = Color(0xFF8FB0FF),
    onPrimary = Color(0xFF001A4D),
    primaryContainer = Color(0xFF14306B),
    onPrimaryContainer = Color(0xFFDCE4FF),
    inversePrimary = Color(0xFF404F73),
    secondary = Color(0xFFB9C3D9),
    onSecondary = Color(0xFF0E1626),
    secondaryContainer = Color(0xFF1C2739),
    onSecondaryContainer = Color(0xFFDCE3F5),
    tertiary = Color(0xFF7FD8C9),
    onTertiary = Color(0xFF00322C),
    tertiaryContainer = Color(0xFF0E4A42),
    onTertiaryContainer = Color(0xFFC9FFF6),
    background = Color(0xFF060A12),
    onBackground = Color(0xFFE7ECF5),
    surface = Color(0xFF060A12),
    onSurface = Color(0xFFE7ECF5),
    surfaceVariant = Color(0xFF09101C),
    onSurfaceVariant = Color(0xFFA8B4C8),
    surfaceTint = Color(0xFF8FB0FF),
    surfaceBright = Color(0xFF111C33),
    surfaceDim = Color(0xFF060A12),
    surfaceContainerLowest = Color(0xFF05080E),
    surfaceContainerLow = Color(0xFF060A12),
    surfaceContainer = Color(0xFF09101C),
    surfaceContainerHigh = Color(0xFF0C1424),
    surfaceContainerHighest = Color(0xFF0F182C),
    outline = Color(0xFF1F345E),
    outlineVariant = Color(0xFF131F38),
    error = Color(0xFFFF8A80),
    onError = Color(0xFF3B0907),
    errorContainer = Color(0xFF5C1A16),
    onErrorContainer = Color(0xFFFFDAD6),
    inverseSurface = Color(0xFFE7ECF5),
    inverseOnSurface = Color(0xFF060A12),
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

/**
 * The dark scheme behind each palette. Exposed so the settings swatches can read
 * the real colours instead of repeating hex values.
 */
fun paletteScheme(palette: ThemePalette): ColorScheme = when (palette) {
    ThemePalette.Warm -> DarkColors
    ThemePalette.Matrix -> MatrixColors
    ThemePalette.Ice -> IceColors
    ThemePalette.Twilight -> TwilightColors
    ThemePalette.Sakura -> SakuraColors
    ThemePalette.Cobalt -> CobaltColors
}

@Composable
fun K56Theme(
    mode: ThemeMode = ThemeMode.System,
    palette: ThemePalette = ThemePalette.Warm,
    content: @Composable () -> Unit,
) {
    // Light and system-light ignore the palette: the sets are dark schemes.
    val scheme = when {
        mode == ThemeMode.Light -> LightColors
        mode == ThemeMode.System && !isSystemInDarkTheme() -> LightColors
        else -> when (palette) {
            ThemePalette.Warm -> DarkColors
            ThemePalette.Matrix -> MatrixColors
            ThemePalette.Ice -> IceColors
            ThemePalette.Twilight -> TwilightColors
            ThemePalette.Sakura -> SakuraColors
            ThemePalette.Cobalt -> CobaltColors
        }
    }
    MaterialTheme(
        colorScheme = scheme,
        typography = if (palette == ThemePalette.Matrix && scheme == MatrixColors) {
            MatrixTypography
        } else {
            K56Typography
        },
        content = content,
    )
}
