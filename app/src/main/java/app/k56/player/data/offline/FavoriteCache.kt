package app.k56.player.data.offline

import app.k56.player.core.model.Track
import app.k56.player.core.net.NetworkMonitor
import app.k56.player.core.net.NetworkTransport
import app.k56.player.data.MediaRepository
import app.k56.player.data.SettingsStore

/**
 * Keeps favorited tracks on disk automatically. Triggered when the option is
 * enabled, at app start, and whenever a track is newly favorited. Respects the
 * Wi-Fi-only download rule so it never surprises the user with mobile traffic.
 */
class FavoriteCache(
    private val repository: MediaRepository,
    private val offlineStore: OfflineStore,
    private val settingsStore: SettingsStore,
    private val networkMonitor: NetworkMonitor,
) {

    private fun enabled(): Boolean = settingsStore.autoCacheFavorites.value

    private fun onWifi(): Boolean {
        val transport = networkMonitor.status.value.transport
        return transport == NetworkTransport.WIFI || transport == NetworkTransport.ETHERNET
    }

    private fun allowed(): Boolean =
        enabled() && (!settingsStore.wifiOnlyDownloads.value || onWifi())

    /** Downloads every favorited track that is not on disk yet. */
    suspend fun sync() {
        if (!allowed()) return
        val favorites = runCatching { repository.favoriteTracks() }.getOrDefault(emptyList())
        for (track in favorites) {
            cache(track)
        }
    }

    /** Downloads one track, honoring the same guards. */
    suspend fun cache(track: Track) {
        if (!allowed()) return
        if (offlineStore.isDownloaded(track.id)) return
        runCatching {
            val source = repository.downloadSource(track)
            offlineStore.download(track, source.url, repository.playbackHeaders())
        }
    }
}
