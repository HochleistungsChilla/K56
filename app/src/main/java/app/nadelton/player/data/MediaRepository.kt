package app.nadelton.player.data

import app.nadelton.player.core.model.Album
import app.nadelton.player.core.model.AlbumDetails
import app.nadelton.player.core.model.Artist
import app.nadelton.player.core.model.ArtistDetails
import app.nadelton.player.core.model.BackendKind
import app.nadelton.player.core.model.DiscoveredServer
import app.nadelton.player.core.model.LibraryView
import app.nadelton.player.core.model.PlaybackSource
import app.nadelton.player.core.model.SearchResults
import app.nadelton.player.core.model.Session
import app.nadelton.player.core.model.Track
import app.nadelton.player.core.net.NetworkStatus
import kotlinx.coroutines.flow.StateFlow

/**
 * Backend-neutral surface used by the UI and the playback service.
 * Implementations live in `data/jellyfin` and `data/subsonic`; the router
 * selects one based on the persisted session.
 */
interface MediaRepository {

    val sessionFlow: StateFlow<Session?>
    val forceTranscode: StateFlow<Boolean>
    val preferExternal: StateFlow<Boolean>
    val networkStatus: StateFlow<NetworkStatus>

    fun setForceTranscode(value: Boolean)
    fun setPreferExternal(value: Boolean)
    fun logout()

    fun supportsQuickConnect(backend: BackendKind): Boolean = backend == BackendKind.JELLYFIN

    suspend fun login(
        backend: BackendKind,
        internalUrl: String,
        externalUrl: String?,
        username: String,
        password: String,
    ): Session

    suspend fun quickConnect(
        backend: BackendKind,
        internalUrl: String,
        externalUrl: String?,
        onCode: (String) -> Unit,
    ): Session

    /** Optional LAN discovery; Jellyfin answers a UDP broadcast on port 7359. */
    suspend fun discoverLocalServers(): List<DiscoveredServer> = emptyList()

    /** Plex uses a PIN/link flow instead of URL+password. */
    suspend fun plexSignIn(onCode: (String) -> Unit): Session =
        throw IllegalStateException("Plex-Login wird von diesem Backend nicht unterstützt.")

    suspend fun libraries(): List<LibraryView>
    suspend fun defaultLibraryId(): String?

    suspend fun recentAlbums(limit: Int = 30): List<Album>
    suspend fun albums(
        parentId: String?,
        sortBy: String = "SortName",
        sortOrder: String = "Ascending",
        limit: Int = 200,
        startIndex: Int = 0,
    ): List<Album>

    suspend fun artists(parentId: String?, limit: Int = 500): List<Artist>
    suspend fun albumDetails(albumId: String): AlbumDetails
    suspend fun artistDetails(artistId: String): ArtistDetails
    suspend fun songs(parentId: String?, limit: Int = 500): List<Track>
    suspend fun search(term: String): SearchResults

    suspend fun setFavorite(itemId: String, favorite: Boolean): Boolean

    suspend fun playbackSource(track: Track): PlaybackSource

    /** Extra HTTP headers required by the active backend (e.g. WebDAV basic auth). */
    fun playbackHeaders(): Map<String, String> = emptyMap()

    suspend fun reportPlaybackStart(itemId: String, playSessionId: String, positionTicks: Long)
    suspend fun reportPlaybackProgress(
        itemId: String,
        playSessionId: String,
        positionTicks: Long,
        isPaused: Boolean,
        repeatMode: String,
    )

    suspend fun reportPlaybackStopped(itemId: String, playSessionId: String, positionTicks: Long)
}

class NotAuthenticatedException : IllegalStateException("Nicht mit einem Server verbunden.")

class UnsupportedBackendException(val backend: BackendKind) : IllegalStateException(
    "${backend.label} ist in dieser Version noch nicht implementiert (${backend.hint}).",
)
