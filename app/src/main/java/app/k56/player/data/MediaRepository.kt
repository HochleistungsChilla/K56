package app.k56.player.data

import app.k56.player.core.model.Album
import app.k56.player.core.model.AlbumDetails
import app.k56.player.core.model.Artist
import app.k56.player.core.model.ArtistDetails
import app.k56.player.core.model.BackendKind
import app.k56.player.core.model.DiscoveredServer
import app.k56.player.core.model.LibraryCounts
import app.k56.player.core.model.LibraryDelta
import app.k56.player.core.model.LibraryView
import app.k56.player.core.model.PlaybackSource
import app.k56.player.core.model.Playlist
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

    /**
     * Reachability and auth check for the stored session. Returns a report
     * instead of throwing; backends that do not implement it say so.
     */
    suspend fun testConnection(): ConnectionReport = ConnectionReport(
        reachable = false,
        serverName = null,
        serverVersion = null,
        authOk = false,
        latencyMs = 0,
        error = "Not supported for this backend",
        errorKind = ConnectionErrorKind.UNSUPPORTED,
    )

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

    /** Total item counts for one library, so the UI can show truncation. */
    suspend fun libraryCounts(parentId: String?): LibraryCounts = LibraryCounts()

    /** Items changed since a UTC timestamp (ms), for incremental sync. */
    suspend fun changedSince(minDateLastSaved: Long): LibraryDelta = LibraryDelta()

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
     * One page of tracks, used by the offline library sync. Backends without
     * offset support answer only the first page, so the index stops there.
     */
    suspend fun songsPage(parentId: String?, startIndex: Int, limit: Int): List<Track> =
        if (startIndex == 0) songs(parentId, limit) else emptyList()

    /**
     * Other albums by the same album artist. Backends without an artist filter
     * fall back to filtering the full album list locally.
     */
    suspend fun albumsByArtist(artistId: String, limit: Int = 100): List<Album> =
        albums(null, limit = 2000).filter { it.artistId == artistId }.take(limit)

    /**
     * An artist's most-played tracks. Backends without a play-count query return
     * an empty list; the artist page hides the section when there is nothing.
     */
    suspend fun artistTopTracks(artistId: String, limit: Int = 20): List<Track> = emptyList()

    /** Albums the artist appears on but does not head as album artist. */
    suspend fun artistAppearsOn(artistId: String, limit: Int = 50): List<Album> = emptyList()

    /** Distinct genre names present in the library. */
    suspend fun genres(parentId: String? = null): List<String> = emptyList()

    /** Albums tagged with one genre. */
    suspend fun genreAlbums(genre: String): List<Album> = emptyList()

    /** Tracks tagged with one genre. */
    suspend fun genreTracks(genre: String): List<Track> = emptyList()

    /** Server-side playlists, newest first is up to the backend. */
    suspend fun playlists(): List<Playlist> = emptyList()
    suspend fun playlistTracks(playlistId: String): List<Track> = emptyList()
    suspend fun createPlaylist(name: String, itemIds: List<String> = emptyList()): Playlist? = null
    suspend fun renamePlaylist(playlistId: String, name: String): Boolean = false
    suspend fun deletePlaylist(playlistId: String): Boolean = false
    suspend fun addToPlaylist(playlistId: String, itemIds: List<String>): Boolean = false
    suspend fun removeFromPlaylist(playlistId: String, itemIds: List<String>): Boolean = false

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
