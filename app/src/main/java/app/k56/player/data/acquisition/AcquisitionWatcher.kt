package app.k56.player.data.acquisition

import app.k56.player.data.acquisition.lidarr.LidarrHistoryRecordDto
import app.k56.player.data.acquisition.lidarr.LidarrService

/**
 * A3: notices when a queued album finished importing in Lidarr.
 *
 * Polls the queue and the history, remembers the last history id and the last
 * queue snapshot in [AcquisitionStore], and reports each album exactly once
 * when it moves from "in queue" to an import event in the history.
 *
 * Nothing is reported on the first look at a server: the current position is
 * adopted silently so an existing backlog does not produce a toast storm.
 */
class AcquisitionWatcher(
    private val store: AcquisitionStore,
) {

    /** Album titles that just landed; empty when nothing finished. */
    suspend fun check(): List<String> {
        store.reload()
        val config = store.config.value
        if (!config.enabled || !config.isComplete) return emptyList()

        val service = LidarrService(config)
        val queue = service.queue().records
        val history = service.history().records

        val previousQueue = store.queuedAlbumIds()
        val lastSeen = store.lastHistoryId()
        val newest = history.maxOfOrNull { it.id ?: 0 } ?: 0
        val arrived = mutableListOf<String>()

        if (lastSeen == 0) {
            // First snapshot: remember where we are, announce nothing.
            store.setLastHistoryId(newest)
        } else {
            history.asSequence()
                .filter { (it.id ?: 0) > lastSeen }
                .filter { isImport(it) }
                .forEach { record ->
                    val albumId = record.albumId ?: record.album?.id
                    // The transition matters: it was downloading, now it is imported.
                    val wasQueued = previousQueue.isEmpty() ||
                        (albumId != null && albumId in previousQueue)
                    if (wasQueued) {
                        val title = record.album?.title ?: record.sourceTitle
                        if (!title.isNullOrBlank()) arrived += title
                    }
                }
            store.setLastHistoryId(newest)
        }

        store.setQueuedAlbumIds(queue.mapNotNull { it.albumId }.toSet())
        return arrived.distinct()
    }

    private fun isImport(record: LidarrHistoryRecordDto): Boolean {
        val event = record.eventType ?: return false
        return event.equals("trackFileImported", ignoreCase = true) ||
            event.equals("downloadFolderImported", ignoreCase = true)
    }
}
