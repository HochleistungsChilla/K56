package app.nadelton.player.data.local

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import app.nadelton.player.core.model.Album
import app.nadelton.player.core.model.AlbumDetails
import app.nadelton.player.core.model.Artist
import app.nadelton.player.core.model.ArtistDetails
import app.nadelton.player.core.model.BackendKind
import app.nadelton.player.core.model.LibraryView
import app.nadelton.player.core.model.PlaybackSource
import app.nadelton.player.core.model.SearchResults
import app.nadelton.player.core.model.Session
import app.nadelton.player.core.model.Track
import app.nadelton.player.core.net.NetworkMonitor
import app.nadelton.player.core.net.NetworkStatus
import app.nadelton.player.data.MediaRepository
import app.nadelton.player.data.NotAuthenticatedException
import app.nadelton.player.data.SessionStore
import app.nadelton.player.data.SettingsStore
import kotlinx.coroutines.flow.StateFlow
import java.util.Base64

/**
 * Local music via MediaStore. No network, no location, no cloud.
 * Album art uses the legacy albumart content URI, which most devices still
 * expose; devices without it simply show the placeholder artwork.
 */
class LocalRepository(
    private val context: Context,
    private val sessionStore: SessionStore,
    private val settingsStore: SettingsStore,
    networkMonitor: NetworkMonitor,
) : MediaRepository {

    @Volatile
    private var session: Session? = sessionStore.session.value?.takeIf { it.backend == BackendKind.LOCAL }

    @Volatile
    private var cachedTracks: List<LocalTrackRow> = emptyList()

    @Volatile
    private var cachedAtMs: Long = 0L

    override val sessionFlow: StateFlow<Session?> = sessionStore.session
    override val forceTranscode: StateFlow<Boolean> = settingsStore.forceTranscode
    override val preferExternal: StateFlow<Boolean> = settingsStore.preferExternal
    override val networkStatus: StateFlow<NetworkStatus> = networkMonitor.status

    override suspend fun login(
        backend: BackendKind,
        internalUrl: String,
        externalUrl: String?,
        username: String,
        password: String,
    ): Session {
        require(backend == BackendKind.LOCAL) { "LocalRepository kann ${backend.label} nicht anbinden." }
        // Trigger a scan so a missing permission is reported at login time.
        loadTracks(force = true)
        val result = Session(
            backend = BackendKind.LOCAL,
            serverUrl = LOCAL_URL,
            internalUrl = null,
            externalUrl = null,
            preferExternal = false,
            serverName = "Dieses Gerät",
            serverVersion = "",
            serverId = "",
            userId = "",
            userName = "Lokale Dateien",
            token = "local",
            deviceId = sessionStore.deviceId,
        )
        sessionStore.save(result)
        session = result
        return result
    }

    override suspend fun quickConnect(
        backend: BackendKind,
        internalUrl: String,
        externalUrl: String?,
        onCode: (String) -> Unit,
    ): Session = throw IllegalStateException("Quick Connect gibt es nur bei Jellyfin.")

    override fun logout() {
        sessionStore.clear()
        session = null
        cachedTracks = emptyList()
        cachedAtMs = 0L
    }

    override fun setForceTranscode(value: Boolean) {
        settingsStore.setForceTranscode(value)
    }

    override fun setPreferExternal(value: Boolean) {
        settingsStore.setPreferExternal(value)
    }

    private fun requireSession(): Session = session ?: throw NotAuthenticatedException()

    // ------------------------------------------------------------- library

    override suspend fun libraries(): List<LibraryView> {
        requireSession()
        return listOf(LibraryView(LIBRARY_ID, "Auf diesem Gerät", "music"))
    }

    override suspend fun defaultLibraryId(): String = LIBRARY_ID

    override suspend fun recentAlbums(limit: Int): List<Album> =
        albums(null, "DateCreated", "Descending", limit, 0)

    override suspend fun albums(
        parentId: String?,
        sortBy: String,
        sortOrder: String,
        limit: Int,
        startIndex: Int,
    ): List<Album> {
        val all = loadTracks()
            .groupBy { it.albumId }
            .values
            .map { it.toAlbum() }

        val sorted = if (sortBy.equals("DateCreated", ignoreCase = true)) {
            all.sortedByDescending { it.id.toLongOrNull() ?: 0L }
        } else {
            all.sortedBy { it.name.lowercase() }
        }
        return sorted.drop(startIndex).take(limit)
    }

    override suspend fun artists(parentId: String?, limit: Int): List<Artist> {
        return loadTracks()
            .mapNotNull { row ->
                val name = row.artist?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                Artist(
                    id = authorId(name),
                    name = name,
                    imageUrl = albumArtUri(row.albumId),
                )
            }
            .distinctBy { it.name }
            .sortedBy { it.name.lowercase() }
            .take(limit)
    }

    override suspend fun albumDetails(albumId: String): AlbumDetails {
        val rows = loadTracks().filter { it.albumId.toString() == albumId }
        if (rows.isEmpty()) throw IllegalStateException("Album nicht gefunden.")
        val sorted = rows.sortedBy { it.track }
        return AlbumDetails(sorted.toAlbum(), sorted.map { it.toTrack() })
    }

    override suspend fun artistDetails(artistId: String): ArtistDetails {
        val name = decodeAuthor(artistId)
        val rows = loadTracks().filter { it.artist == name }
        val albums = rows.groupBy { it.albumId }.values.map { it.toAlbum() }
        return ArtistDetails(
            artist = Artist(artistId, name, rows.firstOrNull()?.let { albumArtUri(it.albumId) }),
            albums = albums,
        )
    }

    override suspend fun songs(parentId: String?, limit: Int): List<Track> =
        loadTracks()
            .sortedBy { it.title.lowercase() }
            .take(limit)
            .map { it.toTrack() }

    override suspend fun search(term: String): SearchResults {
        val query = term.trim()
        if (query.length < 2) return SearchResults()
        val rows = loadTracks().filter { row ->
            row.title.contains(query, ignoreCase = true) ||
                row.album.contains(query, ignoreCase = true) ||
                (row.artist?.contains(query, ignoreCase = true) == true)
        }
        return SearchResults(
            albums = rows.groupBy { it.albumId }.values.map { it.toAlbum() }.distinctBy { it.id },
            artists = rows.mapNotNull { it.artist?.takeIf { name -> name.isNotBlank() } }
                .distinct()
                .map { Artist(authorId(it), it, null) },
            tracks = rows.take(50).map { it.toTrack() },
        )
    }

    override suspend fun setFavorite(itemId: String, favorite: Boolean): Boolean = favorite

    // ------------------------------------------------------------ playback

    override suspend fun playbackSource(track: Track): PlaybackSource {
        val uri = ContentUris.withAppendedId(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            track.id.toLongOrNull() ?: throw IllegalStateException("Ungültige lokale Track-ID."),
        )
        return PlaybackSource(
            url = uri.toString(),
            mimeType = null,
            transcoded = false,
        )
    }

    override suspend fun reportPlaybackStart(itemId: String, playSessionId: String, positionTicks: Long) = Unit

    override suspend fun reportPlaybackProgress(
        itemId: String,
        playSessionId: String,
        positionTicks: Long,
        isPaused: Boolean,
        repeatMode: String,
    ) = Unit

    override suspend fun reportPlaybackStopped(itemId: String, playSessionId: String, positionTicks: Long) = Unit

    // -------------------------------------------------------------- query

    private fun loadTracks(force: Boolean = false): List<LocalTrackRow> {
        val now = android.os.SystemClock.elapsedRealtime()
        if (!force && cachedTracks.isNotEmpty() && now - cachedAtMs < CACHE_TTL_MS) {
            return cachedTracks
        }

        val rows = mutableListOf<LocalTrackRow>()
        try {
            context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                PROJECTION,
                "${MediaStore.Audio.Media.IS_MUSIC} != 0",
                null,
                "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC",
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    rows += cursor.toRow()
                }
            }
        } catch (e: SecurityException) {
            throw IllegalStateException("Kein Zugriff auf lokale Musik. Bitte die Medienberechtigung erteilen.", e)
        }

        cachedTracks = rows
        cachedAtMs = now
        return rows
    }

    private fun android.database.Cursor.toRow(): LocalTrackRow = LocalTrackRow(
        id = getLong(getColumnIndexOrThrow(MediaStore.Audio.Media._ID)),
        title = getString(getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)) ?: "Unbekannter Titel",
        album = getString(getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)) ?: "Unbekanntes Album",
        artist = getString(getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)),
        albumId = getLong(getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)),
        durationMs = getLong(getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)),
        track = getInt(getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)),
        year = getInt(getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR)),
        displayName = getString(getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)),
        mimeType = getString(getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)),
    )

    private fun List<LocalTrackRow>.toAlbum(): Album {
        val first = first()
        return Album(
            id = first.albumId.toString(),
            name = first.album,
            artist = first.artist,
            artistId = first.artist?.takeIf { it.isNotBlank() }?.let(::authorId),
            year = first.year.takeIf { it > 0 },
            imageUrl = albumArtUri(first.albumId),
            trackCount = size,
        )
    }

    private fun LocalTrackRow.toTrack(): Track = Track(
        id = id.toString(),
        title = title,
        albumId = albumId.takeIf { it > 0 }?.toString(),
        albumName = album,
        artistName = artist,
        durationMs = durationMs,
        indexNumber = track.takeIf { it > 0 },
        discNumber = null,
        container = displayName?.substringAfterLast('.', "")?.lowercase()
            ?.takeIf { it.isNotBlank() }
            ?: mimeType?.substringAfter('/'),
        bitrate = null,
        imageUrl = albumArtUri(albumId),
    )

    private fun albumArtUri(albumId: Long): String? {
        if (albumId <= 0) return null
        return ContentUris.withAppendedId(
            Uri.parse("content://media/external/audio/albumart"),
            albumId,
        ).toString()
    }

    private fun authorId(name: String): String =
        AUTHOR_PREFIX + Base64.getUrlEncoder().withoutPadding()
            .encodeToString(name.toByteArray())

    private fun decodeAuthor(id: String): String = runCatching {
        String(Base64.getUrlDecoder().decode(id.removePrefix(AUTHOR_PREFIX)))
    }.getOrDefault(id)

    private data class LocalTrackRow(
        val id: Long,
        val title: String,
        val album: String,
        val artist: String?,
        val albumId: Long,
        val durationMs: Long,
        val track: Int,
        val year: Int,
        val displayName: String?,
        val mimeType: String?,
    )

    private companion object {
        const val LOCAL_URL = "local://"
        const val LIBRARY_ID = "device"
        const val CACHE_TTL_MS = 30_000L
        const val AUTHOR_PREFIX = "local_a_"

        val PROJECTION = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.TRACK,
            MediaStore.Audio.Media.YEAR,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.MIME_TYPE,
        )
    }
}
