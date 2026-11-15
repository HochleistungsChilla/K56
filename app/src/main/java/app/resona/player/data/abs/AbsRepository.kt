package app.resona.player.data.abs

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
 * Audiobookshelf adapter. Books map to albums, tracks to chapters/files.
 * Author names become artist ids (URL-safe encoded, because Jellyfin-style ids
 * are not available here).
 */
class AbsRepository(
    private val sessionStore: SessionStore,
    private val settingsStore: SettingsStore,
    networkMonitor: NetworkMonitor,
) : MediaRepository {

    @Volatile
    private var session: Session? = sessionStore.session.value?.takeIf {
        it.backend == BackendKind.AUDIOBOOKSHELF
    }

    @Volatile
    private var client: AbsClient? = null

    @Volatile
    private var clientBase: String? = null

    @Volatile
    private var cachedLibraryId: String? = null

    @Volatile
    private var cachedItemsLibraryId: String? = null

    @Volatile
    private var cachedItems: List<AbsLibraryItem> = emptyList()

    private val resolver = EndpointResolver(networkMonitor) { session }
    private val trackDurations = java.util.concurrent.ConcurrentHashMap<String, Long>()

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
        require(backend == BackendKind.AUDIOBOOKSHELF) {
            "Audiobookshelf-Adapter kann ${backend.label} nicht anbinden."
        }
        val internal = ServerUrl.normalize(internalUrl)
        val external = externalUrl?.takeIf { it.isNotBlank() }?.let(ServerUrl::normalize)
        val candidates = listOfNotNull(internal, external).distinct()

        var lastError: Exception? = null
        for (base in candidates) {
            try {
                val auth = AbsClient(base).login(username.trim(), password)
                val token = auth.user?.token
                    ?: throw IllegalStateException("Audiobookshelf lieferte kein Token.")
                val authed = AbsClient(base, token)
                val status = runCatching { authed.status() }.getOrNull()

                val result = Session(
                    backend = BackendKind.AUDIOBOOKSHELF,
                    serverUrl = base,
                    internalUrl = internal,
                    externalUrl = external,
                    preferExternal = settingsStore.preferExternal.value,
                    serverName = status?.serverName?.takeIf { it.isNotBlank() }
                        ?: ServerUrl.host(base),
                    serverVersion = status?.serverVersion ?: status?.version.orEmpty(),
                    serverId = "",
                    userId = auth.user?.id.orEmpty(),
                    userName = auth.user?.username ?: username.trim(),
                    token = token,
                    deviceId = sessionStore.deviceId,
                    extra = auth.userDefaultLibraryId?.let {
                        mapOf(EXTRA_DEFAULT_LIBRARY to it)
                    }.orEmpty(),
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
    ): Session = throw IllegalStateException("Quick Connect wird von Audiobookshelf nicht unterstützt.")

    override fun logout() {
        sessionStore.clear()
        session = null
        client = null
        clientBase = null
        cachedLibraryId = null
        cachedItems = emptyList()
        cachedItemsLibraryId = null
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
        cachedLibraryId = result.extra[EXTRA_DEFAULT_LIBRARY]
        cachedItems = emptyList()
        cachedItemsLibraryId = null
    }

    private suspend fun ensureClient(): AbsClient {
        val current = requireSession()
        val base = resolver.resolve() ?: current.serverUrl
        val existing = client
        if (existing != null && clientBase == base) return existing
        val created = AbsClient(base, current.token)
        client = created
        clientBase = base
        cachedItems = emptyList()
        cachedItemsLibraryId = null
        return created
    }

    private fun requireSession(): Session = session ?: throw NotAuthenticatedException()

    // ------------------------------------------------------------- library

    override suspend fun libraries(): List<LibraryView> {
        val response = ensureClient().libraries()
        val views = response.libraries.orEmpty().map {
            LibraryView(it.id.orEmpty(), it.name ?: "Hörbücher", it.mediaType)
        }
        return views.ifEmpty { listOf(LibraryView("", "Hörbücher", "book")) }
    }

    override suspend fun defaultLibraryId(): String? {
        cachedLibraryId?.let { return it }
        return libraries().firstOrNull()?.id?.also { cachedLibraryId = it }
    }

    override suspend fun recentAlbums(limit: Int): List<Album> =
        items(defaultLibraryId().orEmpty()).take(limit).map { it.toAlbum(ensureClient()) }

    override suspend fun albums(
        parentId: String?,
        sortBy: String,
        sortOrder: String,
        limit: Int,
        startIndex: Int,
    ): List<Album> {
        val client = ensureClient()
        val list = items(parentId ?: defaultLibraryId().orEmpty())
            .drop(startIndex)
            .take(limit)
        return list.map { it.toAlbum(client) }
    }

    override suspend fun artists(parentId: String?, limit: Int): List<Artist> {
        val client = ensureClient()
        return items(parentId ?: defaultLibraryId().orEmpty())
            .mapNotNull { item ->
                val author = item.media?.metadata?.authorName?.takeIf { it.isNotBlank() }
                    ?: return@mapNotNull null
                Artist(
                    id = authorId(author),
                    name = author,
                    imageUrl = client.coverUrl(item.media?.coverPath),
                )
            }
            .distinctBy { it.name }
            .take(limit)
    }

    override suspend fun albumDetails(albumId: String): AlbumDetails {
        val client = ensureClient()
        val item = client.item(albumId)
        return item.toAlbumDetails(client)
    }

    override suspend fun artistDetails(artistId: String): ArtistDetails {
        val client = ensureClient()
        val authorName = decodeAuthor(artistId)
        val books = items(defaultLibraryId().orEmpty())
            .filter { it.media?.metadata?.authorName == authorName }
        val image = books.firstOrNull()?.media?.coverPath?.let(client::coverUrl)
        return ArtistDetails(
            artist = Artist(artistId, authorName, image),
            albums = books.map { it.toAlbum(client) },
        )
    }

    override suspend fun songs(parentId: String?, limit: Int): List<Track> {
        // Audiobooks are consumed book-wise; a global chapter list is not useful
        // and would require one detail request per book.
        return emptyList()
    }

    override suspend fun search(term: String): SearchResults {
        val query = term.trim()
        if (query.length < 2) return SearchResults()
        val client = ensureClient()
        val books = items(defaultLibraryId().orEmpty()).filter { item ->
            val meta = item.media?.metadata
            meta?.title?.contains(query, ignoreCase = true) == true ||
                meta?.authorName?.contains(query, ignoreCase = true) == true
        }
        return SearchResults(
            albums = books.map { it.toAlbum(client) },
            artists = books.mapNotNull { item ->
                item.media?.metadata?.authorName?.takeIf { it.isNotBlank() }?.let {
                    Artist(authorId(it), it, null)
                }
            }.distinctBy { it.name },
            tracks = emptyList(),
        )
    }

    override suspend fun setFavorite(itemId: String, favorite: Boolean): Boolean = favorite

    // ------------------------------------------------------------ playback

    override suspend fun playbackSource(track: Track): PlaybackSource {
        val key = track.streamKey
            ?: throw IllegalStateException("Audiobookshelf-Track ohne Dateipfad.")
        trackDurations[track.id] = track.durationMs
        return PlaybackSource(
            url = currentClient().streamUrl(key),
            mimeType = null,
            transcoded = false,
        )
    }

    override suspend fun reportPlaybackStart(itemId: String, playSessionId: String, positionTicks: Long) {
        // No separate start signal; progress carries the position.
    }

    override suspend fun reportPlaybackProgress(
        itemId: String,
        playSessionId: String,
        positionTicks: Long,
        isPaused: Boolean,
        repeatMode: String,
    ) {
        val baseItemId = itemId.substringBefore("#")
        val seconds = positionTicks / 10_000_000.0
        val durationSeconds = trackDurations[itemId]?.let { it / 1000.0 }
        runCatching { ensureClient().reportProgress(baseItemId, seconds, durationSeconds) }
    }

    override suspend fun reportPlaybackStopped(itemId: String, playSessionId: String, positionTicks: Long) {
        val baseItemId = itemId.substringBefore("#")
        val seconds = positionTicks / 10_000_000.0
        val durationSeconds = trackDurations[itemId]?.let { it / 1000.0 }
        runCatching { ensureClient().reportProgress(baseItemId, seconds, durationSeconds) }
    }

    // -------------------------------------------------------------- query

    private suspend fun items(libraryId: String): List<AbsLibraryItem> {
        if (cachedItemsLibraryId == libraryId && cachedItems.isNotEmpty()) return cachedItems
        val response = ensureClient().items(libraryId, limit = 500, page = 0)
        cachedItems = response.results.orEmpty()
        cachedItemsLibraryId = libraryId
        return cachedItems
    }

    private fun currentClient(): AbsClient {
        client?.let { return it }
        val current = session ?: throw NotAuthenticatedException()
        return AbsClient(current.serverUrl, current.token)
    }

    // ------------------------------------------------------------- mapper

    private fun AbsLibraryItem.toAlbum(client: AbsClient): Album {
        val meta = media?.metadata
        return Album(
            id = id.orEmpty(),
            name = meta?.title ?: "Unbekanntes Hörbuch",
            artist = meta?.authorName,
            artistId = meta?.authorName?.let(::authorId),
            year = meta?.publishedYear,
            imageUrl = client.coverUrl(media?.coverPath),
            trackCount = media?.numTracks,
        )
    }

    private fun AbsItemResponse.toAlbumDetails(client: AbsClient): AlbumDetails {
        val meta = media?.metadata
        val bookId = id.orEmpty()
        val album = Album(
            id = bookId,
            name = meta?.title ?: "Unbekanntes Hörbuch",
            artist = meta?.authorName,
            artistId = meta?.authorName?.let(::authorId),
            year = meta?.publishedYear,
            imageUrl = client.coverUrl(media?.coverPath),
            trackCount = media?.numTracks,
        )
        val tracks = media?.tracks.orEmpty().mapIndexed { index, track ->
            Track(
                id = "$bookId#${track.index ?: index}",
                title = track.title?.takeIf { it.isNotBlank() }
                    ?: meta?.title
                    ?: "Kapitel ${index + 1}",
                albumId = bookId,
                albumName = meta?.title,
                artistName = meta?.authorName,
                durationMs = ((track.duration ?: 0.0) * 1000).toLong(),
                indexNumber = track.index ?: (index + 1),
                discNumber = null,
                container = track.codec,
                bitrate = track.bitRate,
                imageUrl = client.coverUrl(media?.coverPath),
                streamKey = track.contentUrl ?: "/api/items/$bookId/file",
            )
        }.sortedBy { it.indexNumber ?: 0 }

        return AlbumDetails(album, tracks)
    }

    private fun authorId(name: String): String =
        AUTHOR_PREFIX + Base64.getUrlEncoder().withoutPadding()
            .encodeToString(name.toByteArray())

    private fun decodeAuthor(id: String): String = runCatching {
        String(Base64.getUrlDecoder().decode(id.removePrefix(AUTHOR_PREFIX)))
    }.getOrDefault(id)

    private companion object {
        const val EXTRA_DEFAULT_LIBRARY = "abs_default_library"
        const val AUTHOR_PREFIX = "abs_a_"
    }
}
