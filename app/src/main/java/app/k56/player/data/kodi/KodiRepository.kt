package app.k56.player.data.kodi

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
import app.k56.player.core.net.ServerUrl
import app.k56.player.data.MediaRepository
import app.k56.player.data.NotAuthenticatedException
import app.k56.player.data.SessionStore
import app.k56.player.data.SettingsStore
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.Base64

/**
 * Kodi adapter (JSON-RPC). Kodi has no user library accounts; HTTP basic auth
 * is optional and stored only as an on-device header value.
 */
class KodiRepository(
    private val sessionStore: SessionStore,
    private val settingsStore: SettingsStore,
    networkMonitor: NetworkMonitor,
) : MediaRepository {

    @Volatile
    private var session: Session? = sessionStore.session.value?.takeIf { it.backend == BackendKind.KODI }

    @Volatile
    private var client: KodiClient? = null

    @Volatile
    private var clientBase: String? = null

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
        require(backend == BackendKind.KODI) { "Kodi-Adapter kann ${backend.label} nicht anbinden." }
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
                val probe = KodiClient(base, basicAuth)
                probe.ping()
                val properties = probe.call(
                    method = "Application.GetProperties",
                    params = buildJsonObject {
                        put("properties", buildJsonArray {
                            add("version")
                        })
                    },
                    deserializer = KodiAppProperties.serializer(),
                )

                val result = Session(
                    backend = BackendKind.KODI,
                    serverUrl = base,
                    internalUrl = internal,
                    externalUrl = external,
                    preferExternal = settingsStore.preferExternal.value,
                    serverName = properties?.name?.takeIf { it.isNotBlank() }
                        ?: ServerUrl.host(base),
                    serverVersion = properties?.version?.label.orEmpty(),
                    serverId = "",
                    userId = "",
                    userName = username.ifBlank { "Kodi" },
                    token = "kodi",
                    deviceId = sessionStore.deviceId,
                    extra = basicAuth?.let { mapOf(EXTRA_BASIC_AUTH to it) }.orEmpty(),
                )
                persist(result)
                return result
            } catch (e: Exception) {
                lastError = e
            }
        }
        throw lastError ?: IllegalStateException("Kodi nicht erreichbar.")
    }

    override suspend fun quickConnect(
        backend: BackendKind,
        internalUrl: String,
        externalUrl: String?,
        onCode: (String) -> Unit,
    ): Session = throw IllegalStateException("Quick Connect gibt es bei Kodi nicht.")

    override fun logout() {
        sessionStore.clear()
        session = null
        client = null
        clientBase = null
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
    }

    private suspend fun ensureClient(): KodiClient {
        val current = requireSession()
        val base = resolver.resolve() ?: current.serverUrl
        val existing = client
        if (existing != null && clientBase == base) return existing
        val created = KodiClient(base, current.extra[EXTRA_BASIC_AUTH])
        client = created
        clientBase = base
        return created
    }

    private fun requireSession(): Session = session ?: throw NotAuthenticatedException()

    // ------------------------------------------------------------- library

    override suspend fun libraries(): List<LibraryView> {
        requireSession()
        return listOf(LibraryView(LIBRARY_ID, "Kodi-Musikbibliothek", "music"))
    }

    override suspend fun defaultLibraryId(): String = LIBRARY_ID

    override suspend fun recentAlbums(limit: Int): List<Album> =
        albumList(limit = limit, start = 0, sortBy = "DateCreated", sortOrder = "Descending")

    override suspend fun albums(
        parentId: String?,
        sortBy: String,
        sortOrder: String,
        limit: Int,
        startIndex: Int,
    ): List<Album> = albumList(limit, startIndex, sortBy, sortOrder)

    private suspend fun albumList(
        limit: Int,
        start: Int,
        sortBy: String,
        sortOrder: String,
    ): List<Album> {
        val client = ensureClient()
        val method = if (sortBy.equals("DateCreated", ignoreCase = true)) "dateadded" else "label"
        val order = if (sortOrder.equals("Descending", ignoreCase = true)) "descending" else "ascending"
        val result = client.call(
            method = "AudioLibrary.GetAlbums",
            params = buildJsonObject {
                put("properties", buildJsonArray {
                    add("title")
                    add("artist")
                    add("artistid")
                    add("year")
                    add("thumbnail")
                })
                put("limits", buildJsonObject {
                    put("start", start)
                    put("end", start + limit)
                })
                put("sort", buildJsonObject {
                    put("method", method)
                    put("order", order)
                })
            },
            deserializer = KodiAlbumsResult.serializer(),
        )
        return result?.albums.orEmpty().map { it.toAlbum(client) }
    }

    override suspend fun artists(parentId: String?, limit: Int): List<Artist> {
        val client = ensureClient()
        val result = client.call(
            method = "AudioLibrary.GetArtists",
            params = buildJsonObject {
                put("properties", buildJsonArray { add("thumbnail") })
                put("limits", buildJsonObject {
                    put("start", 0)
                    put("end", limit)
                })
            },
            deserializer = KodiArtistsResult.serializer(),
        )
        return result?.artists.orEmpty().map { it.toArtist(client) }
    }

    override suspend fun albumDetails(albumId: String): AlbumDetails {
        val client = ensureClient()
        val albumKey = albumId.toIntOrNull() ?: throw IllegalStateException("Invalid Kodi album id.")
        val details = client.call(
            method = "AudioLibrary.GetAlbumDetails",
            params = buildJsonObject {
                put("albumid", albumKey)
                put("properties", buildJsonArray {
                    add("title")
                    add("artist")
                    add("year")
                    add("thumbnail")
                })
            },
            deserializer = KodiAlbumDetailsResult.serializer(),
        )?.albumdetails

        val songs = songsFor(filter = buildJsonObject { put("albumid", albumKey) })
        return AlbumDetails(
            album = details?.toAlbum(client) ?: Album(
                id = albumId,
                name = "Unbekanntes Album",
                artist = null,
                year = null,
                imageUrl = null,
                trackCount = songs.size,
            ),
            tracks = songs,
        )
    }

    override suspend fun artistDetails(artistId: String): ArtistDetails {
        val client = ensureClient()
        val artistKey = artistId.toIntOrNull() ?: throw IllegalStateException("Invalid Kodi artist id.")
        val details = client.call(
            method = "AudioLibrary.GetArtistDetails",
            params = buildJsonObject {
                put("artistid", artistKey)
                put("properties", buildJsonArray { add("thumbnail") })
            },
            deserializer = KodiArtistDetailsResult.serializer(),
        )?.artistdetails

        val albums = client.call(
            method = "AudioLibrary.GetAlbums",
            params = buildJsonObject {
                put("properties", buildJsonArray {
                    add("title")
                    add("artist")
                    add("artistid")
                    add("year")
                    add("thumbnail")
                })
                put("filter", buildJsonObject { put("artistid", artistKey) })
            },
            deserializer = KodiAlbumsResult.serializer(),
        )?.albums.orEmpty().map { it.toAlbum(client) }

        return ArtistDetails(
            artist = details?.toArtist(client) ?: Artist(artistId, "Unbekannter Interpret", null),
            albums = albums,
        )
    }

    override suspend fun songs(parentId: String?, limit: Int): List<Track> =
        songsFor(
            filter = null,
            limits = buildJsonObject {
                put("start", 0)
                put("end", limit)
            },
        )

    override suspend fun search(term: String): SearchResults {
        val query = term.trim()
        if (query.length < 2) return SearchResults()
        val client = ensureClient()

        val albumFilter = buildJsonObject {
            put("field", "album")
            put("operator", "contains")
            put("value", query)
        }
        val artistFilter = buildJsonObject {
            put("field", "artist")
            put("operator", "contains")
            put("value", query)
        }
        val songFilter = buildJsonObject {
            put("field", "title")
            put("operator", "contains")
            put("value", query)
        }

        val albums = client.call(
            method = "AudioLibrary.GetAlbums",
            params = buildJsonObject {
                put("properties", buildJsonArray {
                    add("title")
                    add("artist")
                    add("artistid")
                    add("year")
                    add("thumbnail")
                })
                put("filter", albumFilter)
                put("limits", buildJsonObject {
                    put("start", 0)
                    put("end", 20)
                })
            },
            deserializer = KodiAlbumsResult.serializer(),
        )?.albums.orEmpty().map { it.toAlbum(client) }

        val artists = client.call(
            method = "AudioLibrary.GetArtists",
            params = buildJsonObject {
                put("properties", buildJsonArray { add("thumbnail") })
                put("filter", artistFilter)
                put("limits", buildJsonObject {
                    put("start", 0)
                    put("end", 20)
                })
            },
            deserializer = KodiArtistsResult.serializer(),
        )?.artists.orEmpty().map { it.toArtist(client) }

        val tracks = songsFor(
            filter = songFilter,
            limits = buildJsonObject {
                put("start", 0)
                put("end", 30)
            },
        )

        return SearchResults(albums = albums, artists = artists, tracks = tracks)
    }

    override suspend fun setFavorite(itemId: String, favorite: Boolean): Boolean = favorite

    // ------------------------------------------------------------ playback

    override suspend fun playbackSource(track: Track): PlaybackSource {
        val file = track.streamKey
            ?: throw IllegalStateException("Kodi-Track ohne Dateipfad.")
        val client = ensureClient()
        val prepared = client.call(
            method = "Files.PrepareDownload",
            params = buildJsonObject { put("path", file) },
            deserializer = KodiPreparedDownload.serializer(),
        )
        val path = prepared?.details?.path
            ?: throw IllegalStateException("Kodi konnte keine Wiedergabe-URL erzeugen.")
        return PlaybackSource(
            url = client.fileUrl(path),
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

    private suspend fun songsFor(
        filter: JsonObject? = null,
        limits: JsonObject? = null,
    ): List<Track> {
        val client = ensureClient()
        val result = client.call(
            method = "AudioLibrary.GetSongs",
            params = buildJsonObject {
                put("properties", buildJsonArray {
                    add("title")
                    add("artist")
                    add("album")
                    add("albumid")
                    add("duration")
                    add("track")
                    add("disc")
                    add("file")
                    add("thumbnail")
                })
                filter?.let { put("filter", it) }
                limits?.let { put("limits", it) }
            },
            deserializer = KodiSongsResult.serializer(),
        )
        return result?.songs.orEmpty()
            .filter { !it.file.isNullOrBlank() }
            .sortedWith(compareBy({ it.disc ?: 0 }, { it.track ?: 0 }))
            .map { it.toTrack(client) }
    }

    // ------------------------------------------------------------- mapper

    private fun KodiAlbum.toAlbum(client: KodiClient): Album = Album(
        id = albumid.toString(),
        name = label ?: "Unbekanntes Album",
        artist = artist?.firstOrNull(),
        artistId = artistid?.firstOrNull()?.toString(),
        year = year,
        imageUrl = client.imageUrl(thumbnail),
        trackCount = null,
    )

    private fun KodiArtist.toArtist(client: KodiClient): Artist = Artist(
        id = artistid.toString(),
        name = label ?: "Unbekannter Interpret",
        imageUrl = client.imageUrl(thumbnail),
    )

    private fun KodiSong.toTrack(client: KodiClient): Track = Track(
        id = songid.toString(),
        title = title ?: label ?: "Unbekannter Titel",
        albumId = albumid?.toString(),
        albumName = album,
        artistName = artist?.firstOrNull(),
        durationMs = (duration ?: 0) * 1000L,
        indexNumber = track,
        discNumber = disc,
        container = file?.substringAfterLast('.', "")?.lowercase()?.takeIf { it.isNotBlank() },
        bitrate = null,
        imageUrl = client.imageUrl(thumbnail),
        streamKey = file,
    )

    private companion object {
        const val LIBRARY_ID = "music"
        const val EXTRA_BASIC_AUTH = "kodi_basic_auth"
    }
}
