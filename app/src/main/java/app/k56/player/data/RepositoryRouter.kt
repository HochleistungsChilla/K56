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
import app.k56.player.core.model.isJellyfinFamily
import app.k56.player.core.model.isSubsonicFamily
import app.k56.player.core.net.NetworkMonitor
import app.k56.player.core.net.NetworkStatus
import app.k56.player.data.abs.AbsRepository
import app.k56.player.data.jellyfin.JellyfinRepository
import app.k56.player.data.kodi.KodiRepository
import app.k56.player.data.local.LocalRepository
import app.k56.player.data.webdav.WebDavRepository
import app.k56.player.data.plex.PlexRepository
import app.k56.player.data.subsonic.SubsonicRepository
import kotlinx.coroutines.flow.StateFlow

/**
 * Routes every call to the adapter selected by the persisted session backend.
 * Adding a source means adding an adapter here, not touching the UI.
 */
class RepositoryRouter(
    context: android.content.Context,
    private val sessionStore: SessionStore,
    private val settingsStore: SettingsStore,
    networkMonitor: NetworkMonitor,
) : MediaRepository {

    private val jellyfin = JellyfinRepository(sessionStore, settingsStore, networkMonitor)
    private val subsonic = SubsonicRepository(sessionStore, settingsStore, networkMonitor)
    private val plex = PlexRepository(sessionStore, settingsStore, networkMonitor)
    private val abs = AbsRepository(sessionStore, settingsStore, networkMonitor)
    private val local = LocalRepository(context.applicationContext, sessionStore, settingsStore, networkMonitor)
    private val kodi = KodiRepository(sessionStore, settingsStore, networkMonitor)
    private val webdav = WebDavRepository(sessionStore, settingsStore, networkMonitor)

    override val sessionFlow: StateFlow<Session?> = sessionStore.session
    override val forceTranscode: StateFlow<Boolean> = settingsStore.forceTranscode
    override val preferExternal: StateFlow<Boolean> = settingsStore.preferExternal
    override val networkStatus: StateFlow<NetworkStatus> = networkMonitor.status

    private fun delegateOrNull(): MediaRepository? {
        val backend = sessionStore.session.value?.backend ?: return null
        return when {
            backend.isJellyfinFamily -> jellyfin
            backend.isSubsonicFamily -> subsonic
            backend == BackendKind.PLEX -> plex
            backend == BackendKind.AUDIOBOOKSHELF -> abs
            backend == BackendKind.LOCAL -> local
            backend == BackendKind.KODI -> kodi
            backend == BackendKind.WEBDAV -> webdav
            else -> null
        }
    }

    private fun delegate(): MediaRepository = delegateOrNull() ?: throw NotAuthenticatedException()

    override fun supportsQuickConnect(backend: BackendKind): Boolean =
        backend == BackendKind.JELLYFIN

    override suspend fun login(
        backend: BackendKind,
        internalUrl: String,
        externalUrl: String?,
        username: String,
        password: String,
    ): Session = when {
        backend.isJellyfinFamily -> jellyfin.login(backend, internalUrl, externalUrl, username, password)
        backend.isSubsonicFamily -> subsonic.login(backend, internalUrl, externalUrl, username, password)
        backend == BackendKind.PLEX -> plex.login(backend, internalUrl, externalUrl, username, password)
        backend == BackendKind.AUDIOBOOKSHELF -> abs.login(backend, internalUrl, externalUrl, username, password)
        backend == BackendKind.LOCAL -> local.login(backend, internalUrl, externalUrl, username, password)
        backend == BackendKind.KODI -> kodi.login(backend, internalUrl, externalUrl, username, password)
        backend == BackendKind.WEBDAV -> webdav.login(backend, internalUrl, externalUrl, username, password)
        else -> throw UnsupportedBackendException(backend)
    }

    override suspend fun plexSignIn(onCode: (String) -> Unit): Session = plex.plexSignIn(onCode)

    override suspend fun discoverLocalServers(): List<DiscoveredServer> =
        jellyfin.discoverLocalServers()

    override suspend fun quickConnect(
        backend: BackendKind,
        internalUrl: String,
        externalUrl: String?,
        onCode: (String) -> Unit,
    ): Session = jellyfin.quickConnect(backend, internalUrl, externalUrl, onCode)

    override fun logout() {
        jellyfin.logout()
        subsonic.logout()
        plex.logout()
        abs.logout()
        local.logout()
        kodi.logout()
        webdav.logout()
    }

    override fun setForceTranscode(value: Boolean) {
        settingsStore.setForceTranscode(value)
    }

    override fun setPreferExternal(value: Boolean) {
        settingsStore.setPreferExternal(value)
        delegateOrNull()?.setPreferExternal(value)
    }

    override suspend fun libraries(): List<LibraryView> = delegate().libraries()

    override suspend fun defaultLibraryId(): String? = delegate().defaultLibraryId()

    override suspend fun recentAlbums(limit: Int): List<Album> = delegate().recentAlbums(limit)

    override suspend fun albums(
        parentId: String?,
        sortBy: String,
        sortOrder: String,
        limit: Int,
        startIndex: Int,
    ): List<Album> = delegate().albums(parentId, sortBy, sortOrder, limit, startIndex)

    override suspend fun artists(parentId: String?, limit: Int): List<Artist> =
        delegate().artists(parentId, limit)

    override suspend fun albumDetails(albumId: String): AlbumDetails =
        delegate().albumDetails(albumId)

    override suspend fun artistDetails(artistId: String): ArtistDetails =
        delegate().artistDetails(artistId)

    override suspend fun songs(parentId: String?, limit: Int): List<Track> =
        delegate().songs(parentId, limit)

    override suspend fun search(term: String): SearchResults = delegate().search(term)

    override suspend fun setFavorite(itemId: String, favorite: Boolean): Boolean =
        delegate().setFavorite(itemId, favorite)

    override suspend fun playbackSource(track: Track): PlaybackSource = delegate().playbackSource(track)

    override suspend fun downloadSource(track: Track): PlaybackSource = delegate().downloadSource(track)

    override fun playbackHeaders(): Map<String, String> =
        delegateOrNull()?.playbackHeaders().orEmpty()

    override suspend fun reportPlaybackStart(itemId: String, playSessionId: String, positionTicks: Long) =
        delegate().reportPlaybackStart(itemId, playSessionId, positionTicks)

    override suspend fun reportPlaybackProgress(
        itemId: String,
        playSessionId: String,
        positionTicks: Long,
        isPaused: Boolean,
        repeatMode: String,
    ) = delegate().reportPlaybackProgress(itemId, playSessionId, positionTicks, isPaused, repeatMode)

    override suspend fun reportPlaybackStopped(itemId: String, playSessionId: String, positionTicks: Long) =
        delegate().reportPlaybackStopped(itemId, playSessionId, positionTicks)
}
