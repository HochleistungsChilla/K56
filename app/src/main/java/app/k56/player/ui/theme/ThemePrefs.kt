package app.k56.player.ui.theme

/** User-selectable appearance. */
enum class ThemeMode { System, Light, Dark }

/** User-selectable colour set. Only the dark schemes differ; light falls back to Warm. */
enum class ThemePalette { Warm, Matrix, Ice, Twilight, Sakura, Cobalt }

/** Resolved pair of the two independent choices. */
data class ThemeSelection(val mode: ThemeMode, val palette: ThemePalette)

/**
 * Turns the two stored preference strings into a [ThemeSelection].
 *
 * Kept free of Compose and Android so the migration is testable on the JVM.
 *
 * Migration: before the split there was a single `theme_mode`, and `Matrix` was
 * a mode. A stored `Matrix` becomes mode=Dark plus palette=Matrix, so existing
 * installs keep their look instead of dropping back to the default.
 */
fun resolveSelection(modeRaw: String?, paletteRaw: String?): ThemeSelection {
    val legacy = modeRaw?.trim()
    val legacyMatrix = legacy.equals(ThemePalette.Matrix.name, ignoreCase = true)
    val palette = ThemePalette.entries.firstOrNull { it.name.equals(paletteRaw?.trim(), true) }

    val mode = if (legacyMatrix) {
        ThemeMode.Dark
    } else {
        ThemeMode.entries.firstOrNull { it.name.equals(legacy, ignoreCase = true) } ?: ThemeMode.System
    }
    // A stored palette wins; the legacy Matrix mode only implies one when it is alone.
    return ThemeSelection(mode, palette ?: if (legacyMatrix) ThemePalette.Matrix else ThemePalette.Warm)
}

/** True when [modeRaw] still uses the single-preference shape and must be rewritten. */
fun needsPaletteMigration(modeRaw: String?, paletteRaw: String?): Boolean =
    paletteRaw.isNullOrBlank() &&
        modeRaw?.trim().equals(ThemePalette.Matrix.name, ignoreCase = true)
