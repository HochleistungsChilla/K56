package app.resona.player.data.webdav

import app.resona.player.core.model.Album
import app.resona.player.core.model.AlbumDetails
import app.resona.player.core.model.Artist
import app.resona.player.core.model.ArtistDetails
import app.resona.player.core.model.BackendKind
import app.resona.player.core.model.LibraryView
import app.resona.player.core.model.PlaybackSource
import app.resona.player.core.model.SearchResults
import app.resona.player.core.model.Session
import app.resona.player.core.model.Track
import app.resona.player.core.net.EndpointResolver
import app.resona.player.core.net.NetworkMonitor
import app.resona.player.core.net.NetworkStatus
import app.resona.player.core.net.ServerUrl
import app.resona.player.data.MediaRepository
import app.resona.player.data.NotAuthenticatedException
import app.resona.player.data.SessionStore
import app.resona.player.data.SettingsStore
import kotlinx.coroutines.flow.StateFlow
import java.util.Base64

/**
 * WebDAV music source. Scans folders breadth-first (bounded) and treats every
 * folder containing audio files as an album, with the parent folder as artist.
 * Metadata comes from file/folder names because WebDAV has no library index.
 */
class WebDavRepository(
    private val sessionStore: SessionStore,
    private val settingsStore: SettingsStore,
    networkMonitor: NetworkMonitor,
) : MediaRepository {

    @Volatile
    private var session: Session? = sessionStore.session.value?.takeIf { it.backend == BackendKind.WEBDAV }

    @Volatile
    private var client: WebDavClient? = null

    @Volatile
    private var clientBase: String? = null

    @Volatile
    private var scannedFiles: List<DavFile>? = null

    @Volatile
    private var scannedBase: String? = null

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
        require(backend == BackendKind.WEBDAV) { "WebDAV-Adapter kann ${backend.label} nicht anbinden." }
        val internal = ServerUrl.normalize(internalUrl)
        val external = externalUrl?.takeIf { it.isNotBlank() }?.let(ServerUrl::normalize)
        val candidates = listOfNotNull(internal, external).distinct()
        val basicAuth = if (username.isBlank()) {
            null
        } else {
            Base64.getEncoder().encodeToString("$username:$password".toByteArray())
        }

        var lastError: Exception? = null
        for (base in candidates) {
            try {
                // Depth 0 would be enough to validate; Depth 1 also confirms listing rights.
                WebDavClient(base, basicAuth).list("")
                val result = Session(
                    backend = BackendKind.WEBDAV,
                    serverUrl = base,
                    internalUrl = internal,
                    externalUrl = external,
                    preferExternal = settingsStore.preferExternal.value,
                    serverName = ServerUrl.host(base),
                    serverVersion = "",
                    serverId = "",
                    userId = "",
                    userName = username.ifBlank { "WebDAV" },
                    token = "dav",
                    deviceId = sessionStore.deviceId,
                    extra = basicAuth?.let { mapOf(EXTRA_BASIC_AUTH to it) }.orEmpty(),
                )
                persist(result)
                return result
            } catch (e: Exception) {
                lastError = e
            }
        }
        throw lastError ?: IllegalStateException("WebDAV-Server nicht erreichbar.")
    }

    override suspend fun quickConnect(
        backend: BackendKind,
        internalUrl: String,
        externalUrl: String?,
        onCode: (String) -> Unit,
    ): Session = throw IllegalStateException("Quick Connect gibt es bei WebDAV nicht.")

    override fun logout() {
        sessionStore.clear()
        session = null
        client = null
        clientBase = null
        scannedFiles = null
        scannedBase = null
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
        scannedFiles = null
        scannedBase = null
    }

    private suspend fun ensureClient(): WebDavClient {
        val current = requireSession()
        val base = resolver.resolve() ?: current.serverUrl
        val existing = client
        if (existing != null && clientBase == base) return existing
        val created = WebDavClient(base, current.extra[EXTRA_BASIC_AUTH])
        client = created
        clientBase = base
        scannedFiles = null
        scannedBase = null
        return created
    }

    private fun requireSession(): Session = session ?: throw NotAuthenticatedException()

    // ------------------------------------------------------------- library

    override suspend fun libraries(): List<LibraryView> {
        requireSession()
        return listOf(LibraryView(LIBRARY_ID, "WebDAV", "music"))
    }

    override suspend fun defaultLibraryId(): String = LIBRARY_ID

    override suspend fun recentAlbums(limit: Int): List<Album> =
        albums(null, "SortName", "Ascending", limit, 0)

    override suspend fun albums(
        parentId: String?,
        sortBy: String,
        sortOrder: String,
        limit: Int,
        startIndex: Int,
    ): List<Album> {
        val all = scan().groupBy { it.folder }.values.map { it.toAlbum() }
            .sortedBy { it.name.lowercase() }
        return all.drop(startIndex).take(limit)
    }

    override suspend fun artists(parentId: String?, limit: Int): List<Artist> =
        scan()
            .mapNotNull { file ->
                val artist = file.artistName() ?: return@mapNotNull null
                Artist(
                    id = artistId(artist),
                    name = artist,
                    imageUrl = null,
                )
            }
            .distinctBy { it.name }
            .sortedBy { it.name.lowercase() }
            .take(limit)

    override suspend fun albumDetails(albumId: String): AlbumDetails {
        val folder = decodeId(albumId)
        val files = scan().filter { it.folder == folder }.sortedBy { it.title().lowercase() }
        if (files.isEmpty()) throw IllegalStateException("WebDAV-Album nicht gefunden.")
        return AlbumDetails(files.toAlbum(), files.map { it.toTrack() })
    }

    override suspend fun artistDetails(artistId: String): ArtistDetails {
        val name = decodeArtist(artistId)
        val files = scan().filter { it.artistName() == name }
        val albums = files.groupBy { it.folder }.values.map { it.toAlbum() }
        return ArtistDetails(Artist(artistId, name, null), albums)
    }

    override suspend fun songs(parentId: String?, limit: Int): List<Track> =
        scan()
            .sortedBy { it.title().lowercase() }
            .take(limit)
            .map { it.toTrack() }

    override suspend fun search(term: String): SearchResults {
        val query = term.trim()
        if (query.length < 2) return SearchResults()
        val files = scan().filter {
            it.title().contains(query, ignoreCase = true) ||
                it.folder.contains(query, ignoreCase = true) ||
                (it.artistName()?.contains(query, ignoreCase = true) == true)
        }
        return SearchResults(
            albums = files.groupBy { it.folder }.values.map { it.toAlbum() }.distinctBy { it.id },
            artists = files.mapNotNull { it.artistName() }
                .distinct()
                .map { Artist(artistId(it), it, null) },
            tracks = files.take(50).map { it.toTrack() },
        )
    }

    override suspend fun setFavorite(itemId: String, favorite: Boolean): Boolean = favorite

    // ------------------------------------------------------------ playback

    override suspend fun playbackSource(track: Track): PlaybackSource {
        val url = track.streamKey
            ?: throw IllegalStateException("WebDAV-Track ohne URL.")
        return PlaybackSource(url = url, mimeType = null, transcoded = false)
    }

    override fun playbackHeaders(): Map<String, String> {
        val auth = session?.extra?.get(EXTRA_BASIC_AUTH) ?: return emptyMap()
        return mapOf("Authorization" to "Basic $auth")
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

    // -------------------------------------------------------------- scan

    private suspend fun scan(): List<DavFile> {
        val client = ensureClient()
        val base = clientBase ?: return emptyList()
        val cached = scannedFiles
        if (cached != null && scannedBase == base) return cached

        val files = mutableListOf<DavFile>()
        val queue = ArrayDeque<Pair<String, Int>>()
        queue.add("" to 0)
        var directories = 0

        while (queue.isNotEmpty() && directories < MAX_DIRECTORIES && files.size < MAX_FILES) {
            val (path, depth) = queue.removeFirst()
            directories++
            val entries = runCatching { client.list(path) }.getOrDefault(emptyList())
            for (entry in entries) {
                if (entry.isDirectory) {
                    if (depth < MAX_DEPTH && !entry.name.startsWith(".")) {
                        queue.add(entry.path to depth + 1)
                    }
                } else if (entry.isAudio()) {
                    files += DavFile(
                        path = entry.path,
                        url = entry.url,
                        name = entry.name,
                        folder = path,
                    )
                }
            }
        }

        scannedFiles = files
        scannedBase = base
        return files
    }

    // ------------------------------------------------------------- mapper

    private fun DavEntry.isAudio(): Boolean {
        val type = contentType.orEmpty()
        if (type.startsWith("audio/", ignoreCase = true)) return true
        val extension = name.substringAfterLast('.', "").lowercase()
        return extension in AUDIO_EXTENSIONS
    }

    private fun DavFile.title(): String {
        val base = name.substringBeforeLast('.')
        return base
            .replace(Regex("^\\d{1,3}[\\s._-]+"), "")
            .replace('_', ' ')
            .trim()
            .ifBlank { base }
    }

    private fun DavFile.trackNumber(): Int? =
        Regex("^(\\d{1,3})").find(name.substringBeforeLast('.'))?.groupValues?.getOrNull(1)?.toIntOrNull()

    private fun DavFile.folderName(): String =
        folder.substringAfterLast('/').ifBlank { folder }

    private fun DavFile.artistName(): String? {
        val parent = folder.substringBeforeLast('/', "")
        return parent.substringAfterLast('/').takeIf { it.isNotBlank() }
    }

    private fun List<DavFile>.toAlbum(): Album {
        val first = first()
        val folder = first.folder
        return Album(
            id = encodeId(folder),
            name = folder.substringAfterLast('/').ifBlank { "WebDAV" },
            artist = first.artistName(),
            artistId = first.artistName()?.let(::artistId),
            year = null,
            imageUrl = null,
            trackCount = size,
        )
    }

    private fun DavFile.toTrack(): Track = Track(
        id = encodeId(path),
        title = title(),
        albumId = encodeId(folder),
        albumName = folderName(),
        artistName = artistName(),
        durationMs = 0L,
        indexNumber = trackNumber(),
        discNumber = null,
        container = name.substringAfterLast('.', "").lowercase().takeIf { it.isNotBlank() },
        bitrate = null,
        imageUrl = null,
        streamKey = url,
    )

    private fun encodeId(path: String): String =
        ID_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(path.toByteArray())

    private fun decodeId(id: String): String = runCatching {
        String(Base64.getUrlDecoder().decode(id.removePrefix(ID_PREFIX)))
    }.getOrDefault(id)

    private fun artistId(name: String): String =
        ARTIST_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(name.toByteArray())

    private fun decodeArtist(id: String): String = runCatching {
        String(Base64.getUrlDecoder().decode(id.removePrefix(ARTIST_PREFIX)))
    }.getOrDefault(id)

    private data class DavFile(
        val path: String,
        val url: String,
        val name: String,
        val folder: String,
    )

    private companion object {
        const val LIBRARY_ID = "dav"
        const val EXTRA_BASIC_AUTH = "webdav_basic_auth"
        const val ID_PREFIX = "dav_p_"
        const val ARTIST_PREFIX = "dav_a_"
        const val MAX_DEPTH = 6
        const val MAX_DIRECTORIES = 300
        const val MAX_FILES = 5000

        val AUDIO_EXTENSIONS = setOf(
            "mp3", "flac", "m4a", "m4b", "aac", "ogg", "opus", "wav", "alac",
            "wma", "ape", "dsf", "dff", "mpc", "tta", "wv", "aiff", "aif",
        )
    }
}
