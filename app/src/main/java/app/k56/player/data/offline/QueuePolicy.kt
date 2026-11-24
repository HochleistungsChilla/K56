package app.k56.player.data.offline

/**
 * The queue's state rules, kept free of Android so they can be tested on the
 * JVM. `OfflineStore` owns the mutable list and the network; everything that
 * decides *what an entry becomes* lives here.
 */
internal object QueuePolicy {

    /**
     * After a restart: an entry that is already on disk is done, a failed one
     * stays failed (retrying is the user's decision), everything else goes back
     * into the queue with a fresh attempt count.
     */
    fun reconcile(saved: List<QueueEntry>, isDownloaded: (String) -> Boolean): List<QueueEntry> =
        saved.map { entry ->
            when {
                isDownloaded(entry.trackId) -> entry.copy(state = DownloadState.DONE)
                entry.state == DownloadState.FAILED -> entry
                else -> entry.copy(state = DownloadState.QUEUED, attempts = 0)
            }
        }

    /**
     * Re-queues exactly one entry, and only when it failed. Returns the new list
     * plus the ids that have to be pumped — empty when nothing changed, so a
     * second tap cannot enqueue a running or queued download twice.
     */
    fun retryOne(entries: List<QueueEntry>, trackId: String): Pair<List<QueueEntry>, List<String>> {
        val entry = entries.firstOrNull { it.trackId == trackId } ?: return entries to emptyList()
        if (entry.state != DownloadState.FAILED) return entries to emptyList()
        val updated = entries.map {
            if (it.trackId == trackId) it.copy(state = DownloadState.QUEUED, attempts = 0) else it
        }
        return updated to listOf(trackId)
    }

    /** Re-queues every failed entry; queueed, running and done entries stay untouched. */
    fun retryAll(entries: List<QueueEntry>): Pair<List<QueueEntry>, List<String>> {
        val failed = entries.filter { it.state == DownloadState.FAILED }
        if (failed.isEmpty()) return entries to emptyList()
        val updated = entries.map {
            if (it.state == DownloadState.FAILED) {
                it.copy(state = DownloadState.QUEUED, attempts = 0)
            } else {
                it
            }
        }
        return updated to failed.map { it.trackId }
    }
}
