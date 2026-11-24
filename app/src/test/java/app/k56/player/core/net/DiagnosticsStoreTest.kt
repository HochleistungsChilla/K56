package app.k56.player.core.net

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticsStoreTest {

    @After
    fun clean() {
        DiagnosticsStore.setVerbose(false)
        DiagnosticsStore.clear()
    }

    private fun entry(index: Int) = DiagnosticsEntry(
        atEpochMs = index.toLong(),
        method = "GET",
        path = "/Items",
        status = 200,
        durationMs = 12,
        errorKind = null,
    )

    @Test
    fun keepsOnlyTheNewestEntries() {
        repeat(DiagnosticsStore.CAPACITY + 25) { DiagnosticsStore.record(entry(it)) }
        val entries = DiagnosticsStore.entries.value
        assertEquals(DiagnosticsStore.CAPACITY, entries.size)
        assertEquals(25L, entries.first().atEpochMs)
        assertEquals((DiagnosticsStore.CAPACITY + 24).toLong(), entries.last().atEpochMs)
    }

    @Test
    fun verboseModeKeepsMoreAndTrimsWhenSwitchedOff() {
        DiagnosticsStore.setVerbose(true)
        repeat(DiagnosticsStore.VERBOSE_CAPACITY + 10) { DiagnosticsStore.record(entry(it)) }
        assertEquals(DiagnosticsStore.VERBOSE_CAPACITY, DiagnosticsStore.entries.value.size)

        DiagnosticsStore.setVerbose(false)
        assertEquals(DiagnosticsStore.CAPACITY, DiagnosticsStore.entries.value.size)
        assertEquals(
            (DiagnosticsStore.VERBOSE_CAPACITY + 9).toLong(),
            DiagnosticsStore.entries.value.last().atEpochMs,
        )
    }

    @Test
    fun clearEmptiesTheBuffer() {
        DiagnosticsStore.record(entry(1))
        DiagnosticsStore.clear()
        assertTrue(DiagnosticsStore.entries.value.isEmpty())
    }

    @Test
    fun anEntryCarriesNoPayloadByDesign() {
        // No field can carry headers, bodies or query *values*; the one addition
        // is [DiagnosticsEntry.queryNames], which holds parameter names only and
        // is filled for failed requests so a 400 can be read. Adding a value
        // field fails this test on purpose.
        val fields = DiagnosticsEntry::class.java.declaredFields.map { it.name }.toSet()
        assertEquals(
            setOf(
                "atEpochMs", "method", "path", "status", "durationMs", "errorKind",
                "source", "queryNames", "detail",
            ),
            fields.filterNot { it.contains('$') }.toSet(),
        )
        assertNull(entry(1).errorKind)
        assertTrue(entry(1).queryNames.isEmpty())
    }
}
