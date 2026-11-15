package app.k56.player.data.plex

import app.k56.player.BuildConfig
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
import app.k56.player.core.net.EndpointResolver
import app.k56.player.core.net.NetworkMonitor
import app.k56.player.core.net.NetworkStatus
import app.k56.player.core.net.NetworkTransport
import app.k56.player.core.net.ServerUrl
import app.k56.player.data.MediaRepository
import app.k56.player.data.NotAuthenticatedException
import app.k56.player.data.SessionStore
import app.k56.player.data.SettingsStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow

/**
 * Plex adapter.
 *
 * Login is the official PIN flow on plex.tv; the server list and local/remote
 * connection URIs come from the Plex resource API, which maps directly onto
 * K56's LAN/external routing.
 */
class PlexRepository(
    private val sessionStore: SessionStore,
    private val settingsStore: SettingsStore,
    private val networkMonitor: NetworkMonitor,
) : MediaRepository {

    @Volatile
    private var session: Session? = sessionStore.session.value?.takeIf { it.backend == BackendKind.PLEX }

    @Volatile
    private var client: PlexClient? = null

    @Volatile
    private var clientBase: String? = null

    @Volatile
    private var cachedLibraryId: String? = null

    private val resolver = EndpointResolver(networkMonitor) { session }

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
    ): Session = throw IllegalStateException("Plex nutzt den Plex-Login (PIN), nicht Benutzer/Passwort.")

    override suspend fun plexSignIn(onCode: (String) -> Unit): Session {
        val tv = PlexTvClient(sessionStore.deviceId, BuildConfig.VERSION_NAME)
        val pin = tv.createPin()
        onCode(pin.code.orEmpty())

        repeat(PIN_POLLS) {
            val state = tv.pollPin(pin.id)
            val accountToken = state.authToken
            if (!accountToken.isNullOrBlank()) {
                val resources = tv.resources(accountToken)
                    .filter { it.provides?.contains("server", ignoreCase = true) == true }
                val resource = resources.firstOrNull()
                    ?: throw IllegalStateException("Kein Plex-Media-Server im Konto gefunden.")

                val connections = resource.connections.orEmpty()
                    .filter { !it.uri.isNullOrBlank() }
                val local = connections.firstOrNull { it.local == true }
                val remote = connections.firstOrNull { it.local != true && it.relay != true }
                val onLocalNetwork = networkMonitor.status.value.transport in setOf(
                    NetworkTransport.WIFI,
                    NetworkTransport.ETHERNET,
                    NetworkTransport.OTHER,
                )
                val chosen = if (onLocalNetwork) {
                    local ?: remote
                } else {
                    remote ?: local
                } ?: connections.firstOrNull()
                    ?: throw IllegalStateException("Plex-Server meldet keine Verbindung.")

                val chosenUri = chosen.uri ?: throw IllegalStateException("Plex-Verbindung ohne URI.")
                val normalizedServer = ServerUrl.normalize(chosenUri)
                val serverToken = resource.accessToken?.takeIf { it.isNotBlank() } ?: accountToken

                val result = Session(
                    backend = BackendKind.PLEX,
                    serverUrl = normalizedServer,
                    internalUrl = local?.uri?.let(ServerUrl::normalize),
                    externalUrl = remote?.uri?.let(ServerUrl::normalize),
                    preferExternal = settingsStore.preferExternal.value,
                    serverName = resource.name?.takeIf { it.isNotBlank() }
                        ?: ServerUrl.host(normalizedServer),
                    serverVersion = resource.productVersion.orEmpty(),
                    serverId = resource.clientIdentifier.orEmpty(),
                    userId = "",
                    userName = "Plex",
                    token = serverToken,
                    deviceId = sessionStore.deviceId,
                    extra = mapOf(EXTRA_ACCOUNT_TOKEN to accountToken),
                )
                persist(result)
                return result
            }
            delay(PIN_INTERVAL_MS)
        }
        throw IllegalStateException("Plex-Login wurde nicht bestätigt.")
    }

    override suspend fun quickConnect(
        backend: BackendKind,
        internalUrl: String,
        externalUrl: String?,
        onCode: (String) -> Unit,
    ): Session = throw IllegalStateException("Plex nutzt den Plex-Login (PIN).")

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

    private suspend fun ensureClient(): PlexClient {
        val current = requireSession()
        val base = resolver.resolve() ?: current.serverUrl
        val existing = client
        if (existing != null && clientBase == base) return existing
        val created = PlexClient(base, current.token, current.deviceId)
        client = created
        clientBase = base
        return created
    }

    private fun currentClient(): PlexClient {
        client?.let { return it }
        val current = session ?: throw NotAuthenticatedException()
        return PlexClient(current.serverUrl, current.token, current.deviceId)
    }

    private fun requireSession(): Session = session ?: throw NotAuthenticatedException()

    private fun baseUrl(): String = clientBase ?: session?.serverUrl.orEmpty()

    // ------------------------------------------------------------- library

    override suspend fun libraries(): List<LibraryView> {
        val response = ensureClient().get(
            path = "/library/sections",
            deserializer = PlexDirectoriesEnvelope.serializer(),
        )
        val directories = response.container?.directory.orEmpty()
        val music = directories.filter { it.type == "artist" }
        val effective = music.ifEmpty { directories }
        effective.firstOrNull()?.key?.let { cachedLibraryId = it }
        return effective.map {
            LibraryView(it.key.orEmpty(), it.title ?: "Plex", it.type)
        }
    }

    override suspend fun defaultLibraryId(): String? {
        cachedLibraryId?.let { return it }
        return libraries().firstOrNull()?.id
    }

    override suspend fun recentAlbums(limit: Int): List<Album> =
        sectionAll(sectionId(null), type = "9", sort = "addedAt:desc", limit = limit)

    override suspend fun albums(
        parentId: String?,
        sortBy: String,
        sortOrder: String,
        limit: Int,
        startIndex: Int,
    ): List<Album> {
        val sort = when {
            sortBy.equals("DateCreated", ignoreCase = true) -> "addedAt:desc"
            sortBy.equals("random", ignoreCase = true) -> null
            sortOrder.equals("Descending", ignoreCase = true) -> "titleSort:desc"
            else -> "titleSort"
        }
        return sectionAll(sectionId(parentId), type = "9", sort = sort, limit = limit, startIndex = startIndex)
    }

    override suspend fun artists(parentId: String?, limit: Int): List<Artist> {
        val client = ensureClient()
        val response = client.get(
            path = "/library/sections/${sectionId(parentId)}/all",
            params = mapOf("type" to "8", "sort" to "titleSort"),
            deserializer = PlexMetadataEnvelope.serializer(),
        )
        return response.container?.metadata.orEmpty()
            .take(limit)
            .map { it.toArtist(client) }
    }

    override suspend fun albumDetails(albumId: String): AlbumDetails {
        val client = ensureClient()
        val album = getItem(client, albumId)
        val children = client.get(
            path = "/library/metadata/$albumId/children",
            deserializer = PlexMetadataEnvelope.serializer(),
        )
        val tracks = children.container?.metadata.orEmpty()
            .filter { it.type == "track" }
            .sortedWith(compareBy({ it.parentIndex ?: 0 }, { it.index ?: 0 }))
            .map { it.toTrack(client) }
        return AlbumDetails(album.toAlbum(client), tracks)
    }

    override suspend fun artistDetails(artistId: String): ArtistDetails {
        val client = ensureClient()
        val artist = getItem(client, artistId)
        val children = client.get(
            path = "/library/metadata/$artistId/children",
            deserializer = PlexMetadataEnvelope.serializer(),
        )
        val albums = children.container?.metadata.orEmpty()
            .filter { it.type == "album" }
            .map { it.toAlbum(client) }
        return ArtistDetails(artist.toArtist(client), albums)
    }

    override suspend fun songs(parentId: String?, limit: Int): List<Track> {
        val client = ensureClient()
        val response = client.get(
            path = "/library/sections/${sectionId(parentId)}/all",
            params = mapOf("type" to "10", "sort" to "titleSort"),
            deserializer = PlexMetadataEnvelope.serializer(),
        )
        return response.container?.metadata.orEmpty()
            .take(limit)
            .map { it.toTrack(client) }
    }

    override suspend fun search(term: String): SearchResults {
        val query = term.trim()
        if (query.length < 2) return SearchResults()
        val client = ensureClient()
        val response = client.get(
            path = "/hubs/search",
            params = mapOf("query" to query, "limit" to "40"),
            deserializer = PlexHubsEnvelope.serializer(),
        )
        val items = response.container?.hub.orEmpty()
            .flatMap { it.metadata.orEmpty() }
            .distinctBy { it.type to it.ratingKey }

        return SearchResults(
            albums = items.filter { it.type == "album" }.map { it.toAlbum(client) },
            artists = items.filter { it.type == "artist" }.map { it.toArtist(client) },
            tracks = items.filter { it.type == "track" }.map { it.toTrack(client) },
        )
    }

    override suspend fun setFavorite(itemId: String, favorite: Boolean): Boolean = favorite

    // ------------------------------------------------------------ playback

    override suspend fun playbackSource(track: Track): PlaybackSource {
        val key = track.streamKey
            ?: throw IllegalStateException("Plex-Track ohne Stream-Pfad.")
        return PlaybackSource(
            url = currentClient().streamUrl(key),
            mimeType = null,
            transcoded = false,
        )
    }

    override suspend fun reportPlaybackStart(itemId: String, playSessionId: String, positionTicks: Long) {
        runCatching {
            ensureClient().timeline(
                ratingKey = itemId,
                state = "playing",
                timeMs = positionTicks / TICKS_PER_MS,
                durationMs = 0L,
            )
        }
    }

    override suspend fun reportPlaybackProgress(
        itemId: String,
        playSessionId: String,
        positionTicks: Long,
        isPaused: Boolean,
        repeatMode: String,
    ) {
        runCatching {
            ensureClient().timeline(
                ratingKey = itemId,
                state = if (isPaused) "paused" else "playing",
                timeMs = positionTicks / TICKS_PER_MS,
                durationMs = 0L,
            )
        }
    }

    override suspend fun reportPlaybackStopped(itemId: String, playSessionId: String, positionTicks: Long) {
        runCatching {
            ensureClient().timeline(
                ratingKey = itemId,
                state = "stopped",
                timeMs = positionTicks / TICKS_PER_MS,
                durationMs = 0L,
            )
        }
    }

    // -------------------------------------------------------------- query

    private suspend fun sectionId(parentId: String?): String =
        parentId?.takeIf { it.isNotBlank() }
            ?: defaultLibraryId()
            ?: throw IllegalStateException("Keine Plex-Musikbibliothek gefunden.")

    private suspend fun sectionAll(
        sectionId: String,
        type: String,
        sort: String?,
        limit: Int,
        startIndex: Int = 0,
    ): List<Album> {
        val client = ensureClient()
        val params = buildMap {
            put("type", type)
            sort?.let { put("sort", it) }
        }
        val response = client.get(
            path = "/library/sections/$sectionId/all",
            params = params,
            deserializer = PlexMetadataEnvelope.serializer(),
        )
        return response.container?.metadata.orEmpty()
            .drop(startIndex)
            .take(limit)
            .map { it.toAlbum(client) }
    }

    private suspend fun getItem(client: PlexClient, itemId: String): PlexItem {
        val response = client.get(
            path = "/library/metadata/$itemId",
            deserializer = PlexMetadataEnvelope.serializer(),
        )
        return response.container?.metadata?.firstOrNull()
            ?: throw IllegalStateException("Plex-Objekt $itemId nicht gefunden.")
    }

    // ------------------------------------------------------------- mapper

    private fun PlexItem.toAlbum(client: PlexClient): Album = Album(
        id = ratingKey.orEmpty(),
        name = title ?: "Unbekanntes Album",
        artist = parentTitle,
        artistId = parentRatingKey,
        year = year,
        imageUrl = client.imageUrl(thumb ?: parentThumb),
        trackCount = leafCount,
    )

    private fun PlexItem.toArtist(client: PlexClient): Artist = Artist(
        id = ratingKey.orEmpty(),
        name = title ?: "Unbekannter Interpret",
        imageUrl = client.imageUrl(thumb),
    )

    private fun PlexItem.toTrack(client: PlexClient): Track {
        val media = media?.firstOrNull()
        val part = media?.part?.firstOrNull()
        return Track(
            id = ratingKey.orEmpty(),
            title = title ?: "Unbekannter Titel",
            albumId = parentRatingKey,
            albumName = parentTitle,
            artistName = grandparentTitle,
            durationMs = duration ?: 0L,
            indexNumber = index,
            discNumber = parentIndex,
            container = part?.container ?: media?.container,
            bitrate = media?.bitrate,
            imageUrl = client.imageUrl(thumb ?: parentThumb ?: grandparentThumb),
            streamKey = part?.key,
        )
    }

    private companion object {
        const val PIN_POLLS = 90
        const val PIN_INTERVAL_MS = 2_000L
        const val TICKS_PER_MS = 10_000L
        const val EXTRA_ACCOUNT_TOKEN = "plex_account_token"
    }
}
