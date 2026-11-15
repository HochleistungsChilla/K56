package app.k56.player.data.subsonic

import app.k56.player.core.model.Album
import app.k56.player.core.model.AlbumDetails
import app.k56.player.core.model.Artist
import app.k56.player.core.model.ArtistDetails
import app.k56.player.core.model.BackendKind
import app.k56.player.core.model.LibraryView
import app.k56.player.core.model.PlaybackSource
import app.k56.player.core.model.SearchResults
import app.k56.player.core.model.Session
import app.k56.player.core.model.Track
import app.k56.player.core.model.isSubsonicFamily
import app.k56.player.core.net.EndpointResolver
import app.k56.player.core.net.NetworkMonitor
import app.k56.player.core.net.NetworkStatus
import app.k56.player.core.net.ServerUrl
import app.k56.player.data.MediaRepository
import app.k56.player.data.NotAuthenticatedException
import app.k56.player.data.SessionStore
import app.k56.player.data.SettingsStore
import kotlinx.coroutines.flow.StateFlow
import java.security.MessageDigest
import java.util.UUID

/**
 * Subsonic / OpenSubsonic / Navidrome adapter.
 *
 * Notes:
 *  - auth uses salt+MD5 token, never the plain password
 *  - Subsonic has no "all songs" endpoint, so the songs tab uses getRandomSongs
 *    and sorts locally (documented limitation of the protocol)
 */
class SubsonicRepository(
    private val sessionStore: SessionStore,
    private val settingsStore: SettingsStore,
    networkMonitor: NetworkMonitor,
) : MediaRepository {

    @Volatile
    private var session: Session? = sessionStore.session.value?.takeIf { it.backend.isSubsonicFamily }

    @Volatile
    private var client: SubsonicClient? = null

    @Volatile
    private var clientBase: String? = null

    @Volatile
    private var cachedLibraryId: String? = null

    private val resolver = EndpointResolver(networkMonitor) { session }

    override val sessionFlow: StateFlow<Session?> = sessionStore.session
    override val forceTranscode: StateFlow<Boolean> = settingsStore.forceTranscode
    override val preferExternal: StateFlow<Boolean> = settingsStore.preferExternal
    override val networkStatus: StateFlow<NetworkStatus> = networkMonitor.status

    // ---------------------------------------------------------------- auth

    override suspend fun login(
        backend: BackendKind,
        internalUrl: String,
        externalUrl: String?,
        username: String,
        password: String,
    ): Session {
        require(backend.isSubsonicFamily) { "SubsonicRepository kann ${backend.label} nicht anbinden." }
        val internal = ServerUrl.normalize(internalUrl)
        val external = externalUrl?.takeIf { it.isNotBlank() }?.let(ServerUrl::normalize)
        val candidates = listOfNotNull(internal, external).distinct()

        val salt = randomSalt()
        val token = md5(password + salt)

        var lastError: Exception? = null
        for (base in candidates) {
            try {
                val ping = SubsonicClient(base, username.trim(), token, salt).ping()
                val result = Session(
                    backend = backend,
                    serverUrl = base,
                    internalUrl = internal,
                    externalUrl = external,
                    preferExternal = settingsStore.preferExternal.value,
                    serverName = ServerUrl.host(base),
                    serverVersion = ping.version.orEmpty(),
                    serverId = "",
                    userId = "",
                    userName = username.trim(),
                    token = token,
                    deviceId = sessionStore.deviceId,
                    extra = mapOf(EXTRA_SALT to salt),
                )
                persist(result)
                return result
            } catch (e: Exception) {
                lastError = e
            }
        }
        throw lastError ?: IllegalStateException("Server nicht erreichbar.")
    }

    override suspend fun quickConnect(
        backend: BackendKind,
        internalUrl: String,
        externalUrl: String?,
        onCode: (String) -> Unit,
    ): Session = throw IllegalStateException(
        "Quick Connect is not supported by ${backend.label}.",
    )

    override fun logout() {
        sessionStore.clear()
        session = null
        client = null
        clientBase = null
        cachedLibraryId = null
    }

    override fun setForceTranscode(value: Boolean) {
        settingsStore.setForceTranscode(value)
    }

    override fun setPreferExternal(value: Boolean) {
        settingsStore.setPreferExternal(value)
        val current = session ?: return
        val updated = current.copy(preferExternal = value)
        session = updated
        sessionStore.save(updated)
    }

    private fun persist(result: Session) {
        sessionStore.save(result)
        session = result
        client = null
        clientBase = null
        cachedLibraryId = null
    }

    private suspend fun ensureClient(): SubsonicClient {
        val current = requireSession()
        val base = resolver.resolve() ?: current.serverUrl
        val existing = client
        if (existing != null && clientBase == base) return existing

        val salt = current.extra[EXTRA_SALT].orEmpty()
        val created = SubsonicClient(base, current.userName, current.token, salt)
        client = created
        clientBase = base
        return created
    }

    private fun requireSession(): Session = session ?: throw NotAuthenticatedException()

    private fun currentClient(): SubsonicClient {
        client?.let { return it }
        val current = session ?: throw NotAuthenticatedException()
        return SubsonicClient(
            current.serverUrl,
            current.userName,
            current.token,
            current.extra[EXTRA_SALT].orEmpty(),
        )
    }

    // ------------------------------------------------------------- library

    override suspend fun libraries(): List<LibraryView> {
        val folders = ensureClient().call("getMusicFolders")
            .musicFolders?.musicFolder.orEmpty()
        val views = folders.map { LibraryView(it.id, it.name ?: "Musik", "music") }
        return views.ifEmpty { listOf(LibraryView("", "Musik", "music")) }
    }

    override suspend fun defaultLibraryId(): String? {
        cachedLibraryId?.let { return it }
        return libraries().firstOrNull()?.id?.also { cachedLibraryId = it }
    }

    override suspend fun recentAlbums(limit: Int): List<Album> = albumList(
        parentId = null,
        type = "newest",
        limit = limit,
        offset = 0,
    )

    override suspend fun albums(
        parentId: String?,
        sortBy: String,
        sortOrder: String,
        limit: Int,
        startIndex: Int,
    ): List<Album> {
        val type = when {
            sortBy.equals("DateCreated", ignoreCase = true) -> "newest"
            sortBy.equals("random", ignoreCase = true) -> "random"
            sortBy.equals("PlayCount", ignoreCase = true) -> "frequent"
            sortBy.equals("LastPlayed", ignoreCase = true) -> "recent"
            else -> "alphabeticalByName"
        }
        return albumList(parentId, type, limit, startIndex)
    }

    private suspend fun albumList(
        parentId: String?,
        type: String,
        limit: Int,
        offset: Int,
    ): List<Album> {
        val params = buildMap {
            put("type", type)
            put("size", limit.coerceIn(1, 500).toString())
            if (offset > 0) put("offset", offset.toString())
            parentId?.takeIf { it.isNotBlank() }?.let { put("musicFolderId", it) }
        }
        val client = ensureClient()
        return client.call("getAlbumList2", params)
            .albumList2?.album.orEmpty()
            .map { it.toAlbum(client) }
    }

    override suspend fun artists(parentId: String?, limit: Int): List<Artist> {
        val params = buildMap {
            parentId?.takeIf { it.isNotBlank() }?.let { put("musicFolderId", it) }
        }
        val client = ensureClient()
        return client.call("getArtists", params)
            .artists?.index.orEmpty()
            .flatMap { it.artist.orEmpty() }
            .take(limit)
            .map { it.toArtist(client) }
    }

    override suspend fun albumDetails(albumId: String): AlbumDetails {
        val client = ensureClient()
        val album = client.call("getAlbum", mapOf("id" to albumId)).album
            ?: throw IllegalStateException("Album nicht gefunden.")
        val tracks = album.song.orEmpty()
            .sortedWith(compareBy({ it.discNumber ?: 0 }, { it.track ?: 0 }))
            .map { it.toTrack(client) }
        return AlbumDetails(album.toAlbum(client), tracks)
    }

    override suspend fun artistDetails(artistId: String): ArtistDetails {
        val client = ensureClient()
        val artist = client.call("getArtist", mapOf("id" to artistId)).artist
            ?: throw IllegalStateException("Interpret nicht gefunden.")
        val albums = artist.album.orEmpty().map { it.toAlbum(client) }
        return ArtistDetails(artist.toArtist(client), albums)
    }

    override suspend fun songs(parentId: String?, limit: Int): List<Track> {
        val params = buildMap {
            put("size", limit.coerceIn(1, 500).toString())
            parentId?.takeIf { it.isNotBlank() }?.let { put("musicFolderId", it) }
        }
        val client = ensureClient()
        return client.call("getRandomSongs", params)
            .randomSongs?.song.orEmpty()
            .map { it.toTrack(client) }
            .sortedBy { it.title.lowercase() }
    }

    override suspend fun search(term: String): SearchResults {
        val query = term.trim()
        if (query.length < 2) return SearchResults()
        val client = ensureClient()
        val result = client.call(
            "search3",
            mapOf(
                "query" to query,
                "artistCount" to "20",
                "albumCount" to "20",
                "songCount" to "30",
            ),
        ).searchResult3
        return SearchResults(
            albums = result?.album.orEmpty().map { it.toAlbum(client) },
            artists = result?.artist.orEmpty().map { it.toArtist(client) },
            tracks = result?.song.orEmpty().map { it.toTrack(client) },
        )
    }

    override suspend fun setFavorite(itemId: String, favorite: Boolean): Boolean {
        ensureClient().call(if (favorite) "star" else "unstar", mapOf("id" to itemId))
        return favorite
    }

    // ------------------------------------------------------------ playback

    override suspend fun playbackSource(track: Track): PlaybackSource {
        val transcode = settingsStore.forceTranscode.value ||
            (track.container?.lowercase()?.let { it !in DIRECT_PLAY_CONTAINERS } ?: false)
        val url = currentClient().streamUrl(track.id, transcode)
        return PlaybackSource(
            url = url,
            mimeType = if (transcode) "audio/mpeg" else null,
            transcoded = transcode,
        )
    }

    override suspend fun reportPlaybackStart(itemId: String, playSessionId: String, positionTicks: Long) {
        // Subsonic has no position-based reporting; announce "now playing".
        runCatching { ensureClient().scrobble(itemId, submission = false) }
    }

    override suspend fun reportPlaybackProgress(
        itemId: String,
        playSessionId: String,
        positionTicks: Long,
        isPaused: Boolean,
        repeatMode: String,
    ) {
        // No-op: Subsonic only knows scrobble at start/stop.
    }

    override suspend fun reportPlaybackStopped(itemId: String, playSessionId: String, positionTicks: Long) {
        runCatching { ensureClient().scrobble(itemId, submission = true) }
    }

    // ------------------------------------------------------------- mapper

    private fun AlbumDto.toAlbum(client: SubsonicClient): Album = Album(
        id = id,
        name = name ?: "Unbekanntes Album",
        artist = artist,
        artistId = artistId,
        year = year,
        imageUrl = coverArt?.let { client.coverUrl(it) },
        trackCount = songCount,
        isFavorite = starred != null,
    )

    private fun ArtistDto.toArtist(client: SubsonicClient): Artist = Artist(
        id = id,
        name = name ?: "Unbekannter Interpret",
        imageUrl = coverArt?.let { client.coverUrl(it) },
    )

    private fun SongDto.toTrack(client: SubsonicClient): Track = Track(
        id = id,
        title = title ?: "Unbekannter Titel",
        albumId = albumId,
        albumName = album,
        artistName = artist,
        durationMs = (duration ?: 0) * 1000L,
        indexNumber = track,
        discNumber = discNumber,
        container = suffix,
        bitrate = bitRate,
        imageUrl = coverArt?.let { client.coverUrl(it) },
        isFavorite = starred != null,
        played = (playCount ?: 0) > 0,
    )

    private fun md5(value: String): String = MessageDigest.getInstance("MD5")
        .digest(value.toByteArray())
        .joinToString("") { "%02x".format(it) }

    private fun randomSalt(): String =
        UUID.randomUUID().toString().replace("-", "").take(8)

    private companion object {
        const val EXTRA_SALT = "subsonic_salt"

        val DIRECT_PLAY_CONTAINERS = setOf(
            "mp3", "aac", "m4a", "m4b", "mp4", "flac", "ogg", "opus", "wav", "webm", "mka", "alac",
        )
    }
}
