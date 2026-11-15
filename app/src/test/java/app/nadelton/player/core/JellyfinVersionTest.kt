package app.nadelton.player.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JellyfinVersionTest {

    @Test
    fun exactMinimumIsSupported() {
        assertTrue(JellyfinVersion.isSupported("10.10.7", "10.10.7"))
    }

    @Test
    fun olderPatchIsRejected() {
        assertFalse(JellyfinVersion.isSupported("10.10.6", "10.10.7"))
    }

    @Test
    fun numericComparisonNotLexicographic() {
        assertTrue(JellyfinVersion.isSupported("10.10.10", "10.10.7"))
    }

    @Test
    fun newerMinorIsSupported() {
        assertTrue(JellyfinVersion.isSupported("10.11.0", "10.10.7"))
    }

    @Test
    fun majorVersionWins() {
        assertTrue(JellyfinVersion.compare("11.0.0", "10.99.99") > 0)
    }

    @Test
    fun trailingZeroComponentIsEqual() {
        assertEquals(0, JellyfinVersion.compare("10.10.7.0", "10.10.7"))
    }

    @Test
    fun unknownVersionIsNotSupported() {
        assertFalse(JellyfinVersion.isSupported("unknown", "10.10.7"))
    }
}
