package app.k56.player.data

import app.k56.player.core.model.Album
import app.k56.player.core.model.AlbumDetails
import app.k56.player.core.model.Artist
import app.k56.player.core.model.ArtistDetails
import app.k56.player.core.model.BackendKind
import app.k56.player.core.model.DiscoveredServer
import app.k56.player.core.model.LibraryView
import app.k56.player.core.model.PlaybackSource
import app.k56.player.core.model.SearchResults
import app.k56.player.core.model.Session
import app.k56.player.core.model.Track
import app.k56.player.core.net.NetworkStatus
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
        throw IllegalStateException("Plex sign-in is not supported by this backend.")

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

    /**
     * Other albums by the same album artist. Backends without an artist filter
     * fall back to filtering the full album list locally.
     */
    suspend fun albumsByArtist(artistId: String, limit: Int = 100): List<Album> =
        albums(null, limit = 2000).filter { it.artistId == artistId }.take(limit)

    /**
     * Every favourite track the server knows, independent of how many songs the
     * library list happens to load. Backends without a filter fall back to
     * filtering [songs].
     */
    /**
     * Tracks that continue the current direction from a few seed tracks —
     * similarity based, never a plain shuffle. Backends without a recommender
     * fall back to the same artist/album.
     */
    suspend fun instantMix(seedIds: List<String>, limit: Int = 25): List<Track> {
        if (seedIds.isEmpty()) return emptyList()
        val all = songs(null, 2000)
        val seeds = all.filter { it.id in seedIds }
        val artists = seeds.mapNotNull { it.artistName }.toSet()
        val albums = seeds.mapNotNull { it.albumId }.toSet()
        return all.filter { it.id !in seedIds && (it.albumId in albums || it.artistName in artists) }
            .shuffled()
            .take(limit)
    }

    suspend fun favoriteTracks(limit: Int = 2000): List<Track> =
        songs(null, limit).filter { it.isFavorite }
    suspend fun search(term: String): SearchResults

    suspend fun setFavorite(itemId: String, favorite: Boolean): Boolean

    suspend fun playbackSource(track: Track): PlaybackSource

    /**
     * A URL suitable for storing on disk: always the original file, never a
     * live transcoding session. Backends that cannot offer this fall back to
     * [playbackSource].
     */
    suspend fun downloadSource(track: Track): PlaybackSource = playbackSource(track)

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
