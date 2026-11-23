package app.k56.player.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemePrefsTest {

    @Test
    fun legacyMatrixBecomesDarkPlusMatrix() {
        val selection = resolveSelection("Matrix", null)
        assertEquals(ThemeMode.Dark, selection.mode)
        assertEquals(ThemePalette.Matrix, selection.palette)
    }

    @Test
    fun unknownModeFallsBackToSystemAndWarm() {
        val selection = resolveSelection("Bogus", null)
        assertEquals(ThemeMode.System, selection.mode)
        assertEquals(ThemePalette.Warm, selection.palette)
    }

    @Test
    fun missingPreferencesUseDefaults() {
        val selection = resolveSelection(null, null)
        assertEquals(ThemeMode.System, selection.mode)
        assertEquals(ThemePalette.Warm, selection.palette)
    }

    @Test
    fun splitPreferencesAreKept() {
        val selection = resolveSelection("Dark", "Ice")
        assertEquals(ThemeMode.Dark, selection.mode)
        assertEquals(ThemePalette.Ice, selection.palette)
    }

    @Test
    fun storedValuesAreCaseInsensitive() {
        val selection = resolveSelection("matrix", "sakura")
        assertEquals(ThemeMode.Dark, selection.mode)
        assertEquals(ThemePalette.Sakura, selection.palette)
    }

    @Test
    fun unknownPaletteFallsBackToWarm() {
        val selection = resolveSelection("Light", "Neon")
        assertEquals(ThemeMode.Light, selection.mode)
        assertEquals(ThemePalette.Warm, selection.palette)
    }

    @Test
    fun migrationIsReportedOnlyForLegacyMatrix() {
        assertTrue(needsPaletteMigration("Matrix", null))
        assertFalse(needsPaletteMigration("Matrix", "Matrix"))
        assertFalse(needsPaletteMigration("Dark", null))
        assertFalse(needsPaletteMigration(null, null))
    }
}
