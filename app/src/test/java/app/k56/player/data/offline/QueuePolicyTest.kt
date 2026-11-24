package app.k56.player.data.offline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** The rules `OfflineStore` applies to the persisted queue, without Android. */
class QueuePolicyTest {

    private fun entry(
        id: String,
        state: DownloadState,
        attempts: Int = 1,
    ) = QueueEntry(
        trackId = id,
        title = id,
        artist = "artist",
        album = "album",
        albumId = "album-1",
        durationMs = 200_000,
        imageUrl = null,
        quality = "original",
        format = "flac",
        state = state,
        attempts = attempts,
        bytes = 0,
        totalBytes = 0,
        createdAt = 0,
        url = "",
        headers = emptyMap(),
    )

    @Test
    fun aDownloadedEntryBecomesDoneOnRestart() {
        val saved = listOf(entry("a", DownloadState.QUEUED, attempts = 2))
        val result = QueuePolicy.reconcile(saved) { true }
        assertEquals(DownloadState.DONE, result.single().state)
    }

    @Test
    fun aFailedEntryStaysFailedOnRestart() {
        val saved = listOf(entry("a", DownloadState.FAILED, attempts = 3))
        val result = QueuePolicy.reconcile(saved) { false }
        assertEquals(DownloadState.FAILED, result.single().state)
        assertEquals(3, result.single().attempts)
    }

    @Test
    fun aQueuedEntryGetsAFreshAttemptCountOnRestart() {
        val saved = listOf(entry("a", DownloadState.QUEUED, attempts = 2))
        val result = QueuePolicy.reconcile(saved) { false }
        assertEquals(DownloadState.QUEUED, result.single().state)
        assertEquals(0, result.single().attempts)
    }

    @Test
    fun retryRequeuesAFailedEntryExactlyOnce() {
        val entries = listOf(entry("a", DownloadState.FAILED, attempts = 3))
        val (updated, toPump) = QueuePolicy.retryOne(entries, "a")
        assertEquals(DownloadState.QUEUED, updated.single().state)
        assertEquals(0, updated.single().attempts)
        assertEquals(listOf("a"), toPump)
    }

    @Test
    fun aSecondRetryOfTheSameEntryDoesNothing() {
        val entries = listOf(entry("a", DownloadState.FAILED))
        val (requeued, first) = QueuePolicy.retryOne(entries, "a")
        val (again, second) = QueuePolicy.retryOne(requeued, "a")
        assertEquals(listOf("a"), first)
        assertTrue("a queued entry must not be enqueued twice", second.isEmpty())
        assertEquals(DownloadState.QUEUED, again.single().state)
    }

    @Test
    fun retryIgnoresAQueuedOrRunningEntry() {
        listOf(DownloadState.QUEUED, DownloadState.ACTIVE, DownloadState.DONE).forEach { state ->
            val entries = listOf(entry("a", state))
            val (updated, toPump) = QueuePolicy.retryOne(entries, "a")
            assertTrue("$state must not be re-enqueued", toPump.isEmpty())
            assertSame(entries, updated)
            assertEquals(state, updated.single().state)
        }
    }

    @Test
    fun retryOfAnUnknownIdChangesNothing() {
        val entries = listOf(entry("a", DownloadState.FAILED))
        val (updated, toPump) = QueuePolicy.retryOne(entries, "nope")
        assertTrue(toPump.isEmpty())
        assertSame(entries, updated)
    }

    @Test
    fun retryAllTakesOnlyFailedEntries() {
        val entries = listOf(
            entry("failed-1", DownloadState.FAILED),
            entry("queued", DownloadState.QUEUED),
            entry("active", DownloadState.ACTIVE),
            entry("done", DownloadState.DONE),
            entry("failed-2", DownloadState.FAILED),
        )
        val (updated, toPump) = QueuePolicy.retryAll(entries)

        assertEquals(listOf("failed-1", "failed-2"), toPump)
        assertEquals(DownloadState.QUEUED, updated.first { it.trackId == "failed-1" }.state)
        assertEquals(DownloadState.QUEUED, updated.first { it.trackId == "failed-2" }.state)
        assertEquals(DownloadState.QUEUED, updated.first { it.trackId == "queued" }.state)
        assertEquals(DownloadState.ACTIVE, updated.first { it.trackId == "active" }.state)
        assertEquals(DownloadState.DONE, updated.first { it.trackId == "done" }.state)
    }

    @Test
    fun retryAllWithoutFailuresIsANoOp() {
        val entries = listOf(entry("a", DownloadState.QUEUED), entry("b", DownloadState.DONE))
        val (updated, toPump) = QueuePolicy.retryAll(entries)
        assertTrue(toPump.isEmpty())
        assertSame(entries, updated)
    }
}
