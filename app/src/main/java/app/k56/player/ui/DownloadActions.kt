package app.k56.player.ui

import app.k56.player.core.model.Track
import app.k56.player.data.MediaRepository
import app.k56.player.data.offline.DownloadRequest
import app.k56.player.data.offline.OfflineStore

/**
 * Resolves every track's download source and hands the batch to the concurrent
 * queue. Tracks already on disk are skipped; the queue dedups by track id so a
 * twice-triggered album never downloads twice.
 */
suspend fun enqueueDownloads(
    tracks: List<Track>,
    repository: MediaRepository,
    offlineStore: OfflineStore,
) {
    val requests = tracks.mapNotNull { track ->
        if (offlineStore.isDownloaded(track.id)) return@mapNotNull null
        val source = runCatching { repository.downloadSource(track) }.getOrNull()
            ?: return@mapNotNull null
        DownloadRequest(track, source.url, repository.playbackHeaders())
    }
    if (requests.isNotEmpty()) offlineStore.enqueue(requests)
}
