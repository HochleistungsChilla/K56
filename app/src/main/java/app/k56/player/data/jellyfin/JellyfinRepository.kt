package app.k56.player.data.jellyfin

import app.k56.player.BuildConfig
import app.k56.player.core.JellyfinVersion
import app.k56.player.core.model.Album
import app.k56.player.core.model.AlbumDetails
import app.k56.player.core.model.Artist
import app.k56.player.core.model.ArtistDetails
import app.k56.player.core.model.BackendKind
import app.k56.player.core.model.DiscoveredServer
import app.k56.player.core.model.LibraryView
import app.k56.player.core.model.PlaybackSource
import app.k56.player.core.model.Playlist
import app.k56.player.core.model.SearchResults
import app.k56.player.core.model.Session
import app.k56.player.core.model.Track
import app.k56.player.core.model.isJellyfinFamily
import app.k56.player.core.net.EndpointResolver
import app.k56.player.core.net.NetworkMonitor
import app.k56.player.core.net.NetworkStatus
import app.k56.player.core.net.NetworkTransport
import app.k56.player.core.net.ServerUrl
import app.k56.player.data.MediaRepository
import app.k56.player.data.NotAuthenticatedException
import app.k56.player.data.SessionStore
import app.k56.player.data.DownloadFormat
import app.k56.player.data.DownloadQuality
import app.k56.player.data.SettingsStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow

class UnsupportedServerException(version: String, minimum: String) : IllegalStateException(
    "Jellyfin $version is too old. K56 requires at least Jellyfin $minimum.",
)

class QuickConnectDisabledException : IllegalStateException("Quick Connect ist auf diesem Server deaktiviert.")

class QuickConnectTimeoutException : IllegalStateException("Quick Connect was not confirmed.")

/**
 * Jellyfin and Emby share the same REST surface for everything K56 uses.
 * Emby skips the Jellyfin version floor.
 */
class JellyfinRepository(
    private val sessionStore: SessionStore,
    private val settingsStore: SettingsStore,
    private val networkMonitor: NetworkMonitor,
) : MediaRepository {

    private val minimumVersion = BuildConfig.JELLYFIN_MIN_VERSION

    @Volatile
    private var session: Session? = sessionStore.session.value?.takeIf { it.backend.isJellyfinFamily }

    @Volatile
    private var api: JellyfinApi? = null

    @Volatile
    private var apiBase: String? = null

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
        require(backend.isJellyfinFamily) { "JellyfinRepository kann ${backend.label} nicht anbinden." }
        val internal = ServerUrl.normalize(internalUrl)
        val external = externalUrl?.takeIf { it.isNotBlank() }?.let(ServerUrl::normalize)
        val candidates = listOfNotNull(internal, external).distinct()

        var lastError: Exception? = null
        for (base in candidates) {
            try {
                val publicInfo = publicInfo(base)
                val version = publicInfo.Version.orEmpty()
                if (backend == BackendKind.JELLYFIN &&
                    !JellyfinVersion.isSupported(version, minimumVersion)
                ) {
                    throw UnsupportedServerException(version, minimumVersion)
                }

                val anonymous = JellyfinApiFactory.create(
                    baseUrl = base,
                    deviceId = sessionStore.deviceId,
                    clientVersion = BuildConfig.VERSION_NAME,
                    token = null,
                )
                val auth = anonymous.authenticate(LoginRequest(username.trim(), password))
                val result = Session(
                    backend = backend,
                    serverUrl = base,
                    internalUrl = internal,
                    externalUrl = external,
                    preferExternal = settingsStore.preferExternal.value,
                    serverName = publicInfo.ServerName?.takeIf { it.isNotBlank() }
                        ?: ServerUrl.host(base),
                    serverVersion = version,
                    serverId = auth.ServerId ?: publicInfo.Id.orEmpty(),
                    userId = auth.User.Id,
                    userName = auth.User.Name ?: username.trim(),
                    token = auth.AccessToken,
                    deviceId = sessionStore.deviceId,
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
    ): Session {
        require(backend == BackendKind.JELLYFIN) { "Quick Connect gibt es nur bei Jellyfin." }
        val internal = ServerUrl.normalize(internalUrl)
        val external = externalUrl?.takeIf { it.isNotBlank() }?.let(ServerUrl::normalize)
        val base = findReachableBase(listOfNotNull(internal, external).distinct())
        val publicInfo = publicInfo(base)
        val version = publicInfo.Version.orEmpty()
        if (!JellyfinVersion.isSupported(version, minimumVersion)) {
            throw UnsupportedServerException(version, minimumVersion)
        }

        val anonymous = JellyfinApiFactory.create(
            baseUrl = base,
            deviceId = sessionStore.deviceId,
            clientVersion = BuildConfig.VERSION_NAME,
            token = null,
        )
        if (!anonymous.quickConnectEnabled()) throw QuickConnectDisabledException()

        val initiated = anonymous.quickConnectInitiate()
        val secret = initiated.Secret
            ?: throw IllegalStateException("Quick Connect lieferte kein Secret.")
        onCode(initiated.Code.orEmpty())

        repeat(QUICK_CONNECT_POLLS) {
            val state = anonymous.quickConnectPoll(secret)
            if (state.Authenticated == true) {
                val auth = anonymous.authenticateWithQuickConnect(QuickConnectAuthDto(secret))
                val result = Session(
                    backend = BackendKind.JELLYFIN,
                    serverUrl = base,
                    internalUrl = internal,
                    externalUrl = external,
                    preferExternal = settingsStore.preferExternal.value,
                    serverName = publicInfo.ServerName?.takeIf { it.isNotBlank() }
                        ?: ServerUrl.host(base),
                    serverVersion = version,
                    serverId = auth.ServerId ?: publicInfo.Id.orEmpty(),
                    userId = auth.User.Id,
                    userName = auth.User.Name ?: "Jellyfin",
                    token = auth.AccessToken,
                    deviceId = sessionStore.deviceId,
                )
                persist(result)
                return result
            }
            delay(QUICK_CONNECT_INTERVAL_MS)
        }
        throw QuickConnectTimeoutException()
    }

    override suspend fun discoverLocalServers(): List<DiscoveredServer> =
        JellyfinDiscovery().discover()

    override fun logout() {
        sessionStore.clear()
        session = null
        api = null
        apiBase = null
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
        api = null
        apiBase = null
        cachedLibraryId = null
    }

    private suspend fun publicInfo(base: String) = JellyfinApiFactory.create(
        baseUrl = base,
        deviceId = sessionStore.deviceId,
        clientVersion = BuildConfig.VERSION_NAME,
        token = null,
    ).publicSystemInfo()

    private suspend fun findReachableBase(candidates: List<String>): String {
        var lastError: Exception? = null
        for (base in candidates) {
            try {
                publicInfo(base)
                return base
            } catch (e: Exception) {
                lastError = e
            }
        }
        throw lastError ?: IllegalStateException("Server nicht erreichbar.")
    }

    /** Resolves LAN/external routing and rebuilds Retrofit only when the base changes. */
    private suspend fun ensureApi(): JellyfinApi {
        val current = requireSession()
        val base = resolver.resolve() ?: current.serverUrl
        val existing = api
        if (existing != null && apiBase == base) return existing

        val created = JellyfinApiFactory.create(
            baseUrl = base,
            deviceId = current.deviceId,
            clientVersion = BuildConfig.VERSION_NAME,
            token = current.token,
        )
        api = created
        apiBase = base
        return created
    }

    private fun requireSession(): Session =
        session ?: throw NotAuthenticatedException()

    private fun baseUrl(): String = apiBase ?: session?.serverUrl.orEmpty()

    // ------------------------------------------------------------- library

    override suspend fun libraries(): List<LibraryView> {
        val current = requireSession()
        val views = ensureApi().userViews(current.userId).Items.orEmpty()
        val musicViews = views.filter { it.CollectionType.equals("music", ignoreCase = true) }
        val effective = musicViews.ifEmpty { views }
        effective.firstOrNull()?.let { cachedLibraryId = it.Id }
        return effective.map { LibraryView(it.Id, it.Name ?: "Bibliothek", it.CollectionType) }
    }

    override suspend fun defaultLibraryId(): String? {
        cachedLibraryId?.let { return it }
        return libraries().firstOrNull()?.id
    }

    override suspend fun recentAlbums(limit: Int): List<Album> = albums(
        parentId = null,
        sortBy = "DateCreated",
        sortOrder = "Descending",
        limit = limit,
    )

    override suspend fun albums(
        parentId: String?,
        sortBy: String,
        sortOrder: String,
        limit: Int,
        startIndex: Int,
    ): List<Album> = ensureApi()
        .items(
            itemQuery(
                parentId = parentId,
                includeTypes = "MusicAlbum",
                recursive = true,
                sortBy = sortBy,
                sortOrder = sortOrder,
                limit = limit,
                startIndex = startIndex,
            ),
        )
        .Items.orEmpty()
        .map { it.toAlbum() }

    override suspend fun artists(parentId: String?, limit: Int): List<Artist> = ensureApi()
        .artists(artistQuery(parentId = parentId, limit = limit))
        .Items.orEmpty()
        .map { it.toArtist() }

    override suspend fun albumDetails(albumId: String): AlbumDetails {
        val current = requireSession()
        val api = ensureApi()
        val album = api.item(albumId, current.userId)
        val tracks = api.items(
            itemQuery(
                parentId = albumId,
                includeTypes = "Audio",
                recursive = false,
                sortBy = "ParentIndexNumber,IndexNumber",
                sortOrder = "Ascending",
                limit = 500,
            ),
        ).Items.orEmpty().map { it.toTrack() }
        return AlbumDetails(album.toAlbum(), tracks)
    }

    override suspend fun albumsByArtist(artistId: String, limit: Int): List<Album> = ensureApi()
        .items(
            itemQuery(
                includeTypes = "MusicAlbum",
                recursive = true,
                sortBy = "ProductionYear,SortName",
                sortOrder = "Ascending",
                albumArtistIds = artistId,
                limit = limit,
            ),
        )
        .Items.orEmpty()
        .map { it.toAlbum() }

    override suspend fun artistDetails(artistId: String): ArtistDetails {
        val current = requireSession()
        val api = ensureApi()
        val artist = api.item(artistId, current.userId)
        val ownAlbums = api.items(
            itemQuery(
                parentId = null,
                includeTypes = "MusicAlbum",
                recursive = true,
                sortBy = "ProductionYear,SortName",
                sortOrder = "Ascending",
                albumArtistIds = artistId,
                limit = 200,
            ),
        ).Items.orEmpty().map { it.toAlbum() }
        return ArtistDetails(
            artist = artist.toArtist(),
            albums = ownAlbums,
            topTracks = artistTopTracks(artistId),
            appearsOn = artistAppearsOn(artistId),
        )
    }

    override suspend fun artistTopTracks(artistId: String, limit: Int): List<Track> = ensureApi()
        .items(
            itemQuery(
                includeTypes = "Audio",
                recursive = true,
                sortBy = "PlayCount,SortName",
                sortOrder = "Descending,Ascending",
                artistIds = artistId,
                limit = limit,
            ),
        )
        .Items.orEmpty()
        .map { it.toTrack() }

    override suspend fun artistAppearsOn(artistId: String, limit: Int): List<Album> {
        val ownIds = albumsByArtist(artistId, 200).map { it.id }.toSet()
        return ensureApi()
            .items(
                itemQuery(
                    includeTypes = "MusicAlbum",
                    recursive = true,
                    sortBy = "ProductionYear,SortName",
                    sortOrder = "Ascending",
                    artistIds = artistId,
                    limit = limit,
                ),
            )
            .Items.orEmpty()
            .map { it.toAlbum() }
            .filter { it.id !in ownIds }
    }

    override suspend fun genres(parentId: String?): List<String> {
        val current = requireSession()
        val query = buildMap {
            put("userId", current.userId)
            put("Recursive", "true")
            put("IncludeItemTypes", "Audio")
            put("SortBy", "SortName")
            parentId?.takeIf { it.isNotBlank() }?.let { put("ParentId", it) }
        }
        return ensureApi().genres(query).Items.orEmpty()
            .mapNotNull { it.Name?.takeIf { name -> name.isNotBlank() } }
            .distinct()
    }

    override suspend fun genreAlbums(genre: String): List<Album> = ensureApi()
        .items(
            itemQuery(
                includeTypes = "MusicAlbum",
                recursive = true,
                sortBy = "SortName",
                sortOrder = "Ascending",
                limit = 500,
                genres = genre,
            ),
        )
        .Items.orEmpty()
        .map { it.toAlbum() }

    override suspend fun genreTracks(genre: String): List<Track> = ensureApi()
        .items(
            itemQuery(
                includeTypes = "Audio",
                recursive = true,
                sortBy = "SortName",
                sortOrder = "Ascending",
                limit = 500,
                genres = genre,
            ),
        )
        .Items.orEmpty()
        .map { it.toTrack() }

    // ------------------------------------------------------------ playlists

    override suspend fun playlists(): List<Playlist> = ensureApi()
        .items(
            itemQuery(
                includeTypes = "Playlist",
                recursive = true,
                sortBy = "SortName",
                sortOrder = "Ascending",
                limit = 300,
            ),
        )
        .Items.orEmpty()
        .map { it.toPlaylist() }

    override suspend fun playlistTracks(playlistId: String): List<Track> = ensureApi()
        .items(
            itemQuery(
                parentId = playlistId,
                includeTypes = "Audio",
                recursive = false,
                sortBy = "SortName",
                sortOrder = "Ascending",
                limit = 2000,
            ),
        )
        .Items.orEmpty()
        .map { it.toTrack() }

    override suspend fun createPlaylist(name: String, itemIds: List<String>): Playlist? {
        val current = requireSession()
        val ids = itemIds.takeIf { it.isNotEmpty() }?.joinToString(",")
        val created = ensureApi().createPlaylist(name.trim(), ids, current.userId)
        if (created.Id.isBlank()) return null
        return Playlist(id = created.Id, name = name.trim(), trackCount = itemIds.size)
    }

    override suspend fun renamePlaylist(playlistId: String, name: String): Boolean =
        runCatching { ensureApi().updatePlaylist(playlistId, name.trim()) }.isSuccess

    override suspend fun deletePlaylist(playlistId: String): Boolean =
        runCatching { ensureApi().deletePlaylist(playlistId) }.isSuccess

    override suspend fun addToPlaylist(playlistId: String, itemIds: List<String>): Boolean {
        if (itemIds.isEmpty()) return false
        val current = requireSession()
        return runCatching {
            ensureApi().addPlaylistItems(playlistId, itemIds.joinToString(","), current.userId)
        }.isSuccess
    }

    override suspend fun removeFromPlaylist(playlistId: String, itemIds: List<String>): Boolean {
        if (itemIds.isEmpty()) return false
        return runCatching {
            ensureApi().removePlaylistItems(playlistId, itemIds.joinToString(","))
        }.isSuccess
    }

    override suspend fun songs(parentId: String?, limit: Int): List<Track> = ensureApi()
        .items(
            itemQuery(
                parentId = parentId,
                includeTypes = "Audio",
                recursive = true,
                sortBy = "SortName",
                sortOrder = "Ascending",
                limit = limit,
            ),
        )
        .Items.orEmpty()
        .map { it.toTrack() }

    override suspend fun instantMix(seedIds: List<String>, limit: Int): List<Track> {
        if (seedIds.isEmpty()) return emptyList()
        val api = ensureApi()
        val current = session ?: throw NotAuthenticatedException()
        val collected = LinkedHashMap<String, Track>()
        // Ask per seed and merge: one seed already yields a coherent direction,
        // several seeds average it out without the server having to support it.
        for (seed in seedIds.take(3)) {
            val result = runCatching {
                api.instantMix(
                    seed,
                    mapOf(
                        "userId" to current.userId,
                        "limit" to (limit / seedIds.take(3).size).coerceAtLeast(8).toString(),
                        "Fields" to ITEM_FIELDS,
                        "EnableImageTypes" to "Primary",
                        "ImageTypeLimit" to "1",
                    ),
                )
            }.getOrNull() ?: continue
            result.Items.orEmpty().forEach { dto ->
                if (dto.Id.isNotBlank() && dto.Id !in seedIds) collected[dto.Id] = dto.toTrack()
            }
        }
        return collected.values.take(limit)
    }

    override suspend fun favoriteTracks(limit: Int): List<Track> = ensureApi()
        .items(
            itemQuery(
                includeTypes = "Audio",
                recursive = true,
                sortBy = "SortName",
                sortOrder = "Ascending",
                limit = limit,
                filters = "IsFavorite",
            ),
        )
        .Items.orEmpty()
        .map { it.toTrack() }

    override suspend fun search(term: String): SearchResults {
        val query = term.trim()
        if (query.length < 2) return SearchResults()

        val items = ensureApi().items(
            itemQuery(
                searchTerm = query,
                includeTypes = "MusicAlbum,MusicArtist,Audio",
                recursive = true,
                sortBy = "SortName",
                limit = 60,
            ),
        ).Items.orEmpty()

        return SearchResults(
            albums = items.filter { it.Type == "MusicAlbum" }.map { it.toAlbum() },
            artists = items.filter { it.Type == "MusicArtist" }.map { it.toArtist() },
            tracks = items.filter { it.Type == "Audio" }.map { it.toTrack() },
        )
    }

    override suspend fun setFavorite(itemId: String, favorite: Boolean): Boolean {
        val current = requireSession()
        val result = if (favorite) {
            ensureApi().addFavorite(current.userId, itemId)
        } else {
            ensureApi().removeFavorite(current.userId, itemId)
        }
        return result.IsFavorite == true
    }

    // ------------------------------------------------------------ playback

    override suspend fun playbackSource(track: Track): PlaybackSource {
        val current = session ?: throw NotAuthenticatedException()
        val base = baseUrl().ifBlank { current.serverUrl }
        val container = track.container?.lowercase()
        // Frugal mode + mobile data: force a low-bitrate transcode to save data.
        val frugal = settingsStore.frugalMode.value &&
            networkStatus.value.transport == NetworkTransport.CELLULAR
        val canDirectPlay = settingsStore.forceTranscode.value.not() &&
            !frugal &&
            (container.isNullOrBlank() || container in DIRECT_PLAY_CONTAINERS)

        return if (canDirectPlay) {
            val url = "${base}Audio/${track.id}/stream" +
                "?static=true&MediaSourceId=${track.id}" +
                "&DeviceId=${current.deviceId}&api_key=${current.token}"
            PlaybackSource(url = url, mimeType = null, transcoded = false)
        } else {
            val maxBitrate = if (frugal) 128_000 else 320_000
            val url = "${base}Audio/${track.id}/universal" +
                "?UserId=${current.userId}&DeviceId=${current.deviceId}" +
                "&MaxStreamingBitrate=$maxBitrate" +
                "&Container=opus,mp3,aac,m4a,m4b,flac,ogg,wav" +
                "&TranscodingContainer=ts&TranscodingProtocol=hls&AudioCodec=aac" +
                "&api_key=${current.token}&EnableRedirection=true"
            PlaybackSource(url = url, mimeType = "application/x-mpegURL", transcoded = true)
        }
    }

    /**
     * A single progressive file, never a live HLS session: either the untouched
     * original or a server-side transcode at the configured bitrate.
     */
    override suspend fun downloadSource(track: Track): PlaybackSource {
        val current = session ?: throw NotAuthenticatedException()
        val base = baseUrl().ifBlank { current.serverUrl }
        val quality = settingsStore.downloadQuality.value
        if (quality == DownloadQuality.ORIGINAL) {
            val url = "${base}Audio/${track.id}/stream" +
                "?static=true&MediaSourceId=${track.id}" +
                "&DeviceId=${current.deviceId}&api_key=${current.token}"
            return PlaybackSource(url = url, mimeType = null, transcoded = false)
        }

        val codec = when (settingsStore.downloadFormat.value) {
            DownloadFormat.MP3 -> "mp3"
            DownloadFormat.AAC -> "aac"
        }
        val url = "${base}Audio/${track.id}/universal" +
            "?UserId=${current.userId}&DeviceId=${current.deviceId}" +
            "&MaxStreamingBitrate=${quality.kbps * 1000}" +
            "&AudioCodec=$codec&TranscodingContainer=$codec&TranscodingProtocol=http" +
            "&api_key=${current.token}"
        return PlaybackSource(
            url = url,
            mimeType = if (codec == "mp3") "audio/mpeg" else "audio/mp4",
            transcoded = true,
        )
    }

    override suspend fun reportPlaybackStart(itemId: String, playSessionId: String, positionTicks: Long) {
        ensureApi().reportPlaybackStart(
            PlaybackReportDto(
                ItemId = itemId,
                MediaSourceId = itemId,
                PlaySessionId = playSessionId,
                PositionTicks = positionTicks,
                CanSeek = true,
                IsPaused = false,
                IsMuted = false,
                VolumeLevel = 100,
                PlayMethod = "DirectPlay",
                RepeatMode = "RepeatNone",
            ),
        )
    }

    override suspend fun reportPlaybackProgress(
        itemId: String,
        playSessionId: String,
        positionTicks: Long,
        isPaused: Boolean,
        repeatMode: String,
    ) {
        ensureApi().reportPlaybackProgress(
            PlaybackReportDto(
                ItemId = itemId,
                MediaSourceId = itemId,
                PlaySessionId = playSessionId,
                PositionTicks = positionTicks,
                CanSeek = true,
                IsPaused = isPaused,
                IsMuted = false,
                VolumeLevel = 100,
                PlayMethod = "DirectPlay",
                RepeatMode = repeatMode,
            ),
        )
    }

    override suspend fun reportPlaybackStopped(itemId: String, playSessionId: String, positionTicks: Long) {
        ensureApi().reportPlaybackStopped(
            PlaybackReportDto(
                ItemId = itemId,
                MediaSourceId = itemId,
                PlaySessionId = playSessionId,
                PositionTicks = positionTicks,
            ),
        )
    }

    // -------------------------------------------------------------- query

    private fun itemQuery(
        parentId: String? = null,
        includeTypes: String? = null,
        recursive: Boolean = true,
        sortBy: String? = null,
        sortOrder: String? = null,
        searchTerm: String? = null,
        limit: Int? = null,
        startIndex: Int? = null,
        filters: String? = null,
        albumArtistIds: String? = null,
        artistIds: String? = null,
        isPlayed: Boolean? = null,
        genres: String? = null,
    ): Map<String, String> = buildMap {
        parentId?.takeIf { it.isNotBlank() }?.let { put("ParentId", it) }
        includeTypes?.let { put("IncludeItemTypes", it) }
        put("Recursive", recursive.toString())
        sortBy?.let { put("SortBy", it) }
        sortOrder?.let { put("SortOrder", it) }
        searchTerm?.let { put("SearchTerm", it) }
        limit?.let { put("Limit", it.toString()) }
        startIndex?.let { put("StartIndex", it.toString()) }
        filters?.let { put("Filters", it) }
        albumArtistIds?.let { put("AlbumArtistIds", it) }
        artistIds?.let { put("ArtistIds", it) }
        isPlayed?.let { put("IsPlayed", it.toString()) }
        genres?.let { put("Genres", it) }
        put("Fields", ITEM_FIELDS)
        put("ImageTypeLimit", "1")
        put("EnableImageTypes", "Primary")
        put("EnableTotalRecordCount", "true")
    }

    private fun artistQuery(parentId: String?, limit: Int?): Map<String, String> = buildMap {
        parentId?.takeIf { it.isNotBlank() }?.let { put("ParentId", it) }
        put("Recursive", "true")
        put("SortBy", "SortName")
        put("SortOrder", "Ascending")
        limit?.let { put("Limit", it.toString()) }
        put("Fields", "PrimaryImageAspectRatio,Overview,Genres")
        put("ImageTypeLimit", "1")
        put("EnableImageTypes", "Primary")
    }

    // ------------------------------------------------------------- mapper

    private fun absoluteImageUrl(itemId: String, tag: String?): String? {
        if (itemId.isBlank() || tag.isNullOrBlank()) return null
        val base = baseUrl().ifBlank { session?.serverUrl ?: return null }
        val token = session?.token ?: return null
        return "${base}Items/$itemId/Images/Primary" +
            "?maxHeight=700&maxWidth=700&quality=90&tag=$tag&api_key=$token"
    }

    private fun BaseItemDto.toAlbum(): Album = Album(
        id = Id,
        name = Name ?: "Unbekanntes Album",
        artist = AlbumArtist?.takeIf { it.isNotBlank() }
            ?: AlbumArtists?.firstOrNull()?.Name
            ?: Artists?.firstOrNull(),
        artistId = AlbumArtists?.firstOrNull()?.Id,
        year = ProductionYear,
        imageUrl = absoluteImageUrl(Id, ImageTags?.get("Primary")),
        trackCount = ChildCount,
        isFavorite = UserData?.IsFavorite == true,
        overview = Overview?.takeIf { it.isNotBlank() },
        genres = Genres.orEmpty(),
    )

    private fun BaseItemDto.toArtist(): Artist = Artist(
        id = Id,
        name = Name ?: "Unbekannter Interpret",
        imageUrl = absoluteImageUrl(Id, ImageTags?.get("Primary")),
        overview = Overview?.takeIf { it.isNotBlank() },
    )

    private fun BaseItemDto.toTrack(): Track {
        val media = MediaSources?.firstOrNull()
        val ownImage = absoluteImageUrl(Id, ImageTags?.get("Primary"))
        val albumImage = AlbumId?.let { absoluteImageUrl(it, AlbumPrimaryImageTag) }
        return Track(
            id = Id,
            title = Name ?: "Unbekannter Titel",
            albumId = AlbumId,
            albumName = Album,
            artistName = Artists?.firstOrNull() ?: AlbumArtist,
            durationMs = (RunTimeTicks ?: 0L) / TICKS_PER_MILLISECOND,
            indexNumber = IndexNumber,
            discNumber = ParentIndexNumber,
            container = media?.Container ?: Container,
            bitrate = media?.Bitrate ?: Bitrate,
            imageUrl = ownImage ?: albumImage,
            isFavorite = UserData?.IsFavorite == true,
            played = UserData?.Played == true,
        )
    }

    private fun BaseItemDto.toPlaylist(): Playlist = Playlist(
        id = Id,
        name = Name ?: "Unbenannte Playlist",
        trackCount = ChildCount,
        imageUrl = absoluteImageUrl(Id, ImageTags?.get("Primary")),
    )

    private companion object {
        const val TICKS_PER_MILLISECOND = 10_000L
        const val QUICK_CONNECT_INTERVAL_MS = 2_000L
        const val QUICK_CONNECT_POLLS = 90

        const val ITEM_FIELDS =
            "PrimaryImageAspectRatio,ProductionYear,AlbumArtist,Artists,RunTimeTicks," +
                "ChildCount,MediaSources,Container,Bitrate,Genres,Overview"

        val DIRECT_PLAY_CONTAINERS = setOf(
            "mp3", "aac", "m4a", "m4b", "mp4", "flac", "ogg", "opus", "wav", "webm", "mka", "alac",
        )
    }
}
