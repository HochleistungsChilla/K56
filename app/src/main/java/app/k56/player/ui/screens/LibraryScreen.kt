package app.k56.player.ui.screens

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import android.widget.Toast
import kotlinx.coroutines.launch
import app.k56.player.R
import app.k56.player.core.model.Album
import app.k56.player.core.model.Artist
import app.k56.player.core.model.LibraryCounts
import app.k56.player.core.model.Playlist
import app.k56.player.core.model.Track
import app.k56.player.core.net.NetworkTransport
import app.k56.player.data.MediaRepository
import app.k56.player.data.SettingsStore
import app.k56.player.data.offline.OfflineStore
import app.k56.player.data.offline.DownloadRequest
import app.k56.player.data.library.LibraryDb
import app.k56.player.data.library.SyncLog
import app.k56.player.ui.appViewModel
import app.k56.player.ui.components.AlbumCard
import app.k56.player.ui.components.ArtistCard
import app.k56.player.ui.components.ErrorState
import app.k56.player.ui.components.EmptyState
import app.k56.player.ui.components.HorizontalAlbumRow
import app.k56.player.ui.components.LoadingState
import app.k56.player.ui.components.SectionHeader
import app.k56.player.ui.components.TrackRow
import app.k56.player.ui.userMessage
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

class LibraryViewModel(
    private val repository: MediaRepository,
    private val appContext: Context,
    private val offlineStore: OfflineStore,
    private val settingsStore: SettingsStore,
) : ViewModel() {

    var albums by mutableStateOf<List<Album>>(emptyList())
        private set
    var artists by mutableStateOf<List<Artist>>(emptyList())
        private set
    var songs by mutableStateOf<List<Track>>(emptyList())
        private set
    var favorites by mutableStateOf<List<Track>>(emptyList())
        private set
    var genres by mutableStateOf<List<String>>(emptyList())
        private set
    var selectedGenre by mutableStateOf<String?>(null)
        private set
    var genreAlbums by mutableStateOf<List<Album>>(emptyList())
        private set
    var genreTracks by mutableStateOf<List<Track>>(emptyList())
        private set
    var genreOffline by mutableStateOf(false)
        private set
    var playlists by mutableStateOf<List<Playlist>>(emptyList())
        private set
    var selectedPlaylist by mutableStateOf<Playlist?>(null)
        private set
    var playlistTracks by mutableStateOf<List<Track>>(emptyList())
        private set
    var counts by mutableStateOf(LibraryCounts())
        private set
    var loading by mutableStateOf(true)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    /** True when the lists come from the on-device copy instead of the server. */
    var offlineCopy by mutableStateOf(false)
        private set

    private val libraryDb = LibraryDb.get(appContext)
    val indexLastSync: Long get() = libraryDb.lastSync()

    var favoriteArtistIds by mutableStateOf(libraryDb.favoriteArtistIds())
        private set
    var trackExceptionIds by mutableStateOf(libraryDb.trackExceptions())
        private set
    private var serverFavorites by mutableStateOf<List<Track>>(emptyList())

    init {
        load()
    }

    fun openGenre(name: String) {
        selectedGenre = name
        genreAlbums = emptyList()
        genreTracks = emptyList()
        genreOffline = false
        viewModelScope.launch {
            try {
                genreAlbums = repository.genreAlbums(name)
                genreTracks = repository.genreTracks(name)
            } catch (e: Exception) {
                val albums = libraryDb.albumsByGenre(name)
                val tracks = libraryDb.tracksByGenre(name)
                if (albums.isNotEmpty() || tracks.isNotEmpty()) {
                    genreAlbums = albums
                    genreTracks = tracks
                    genreOffline = true
                    error = null
                } else {
                    error = e.userMessage(appContext)
                }
            }
        }
    }

    fun closeGenre() {
        selectedGenre = null
        genreAlbums = emptyList()
        genreTracks = emptyList()
        genreOffline = false
    }

    fun refreshPlaylists() {
        viewModelScope.launch {
            runCatching { repository.playlists() }.onSuccess { playlists = it }
        }
    }

    fun openPlaylist(playlist: Playlist) {
        selectedPlaylist = playlist
        playlistTracks = emptyList()
        viewModelScope.launch {
            try {
                playlistTracks = repository.playlistTracks(playlist.id)
            } catch (e: Exception) {
                error = e.userMessage(appContext)
            }
        }
    }

    fun closePlaylist() {
        selectedPlaylist = null
        playlistTracks = emptyList()
    }

    fun createPlaylist(name: String, itemIds: List<String>, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                val created = repository.createPlaylist(name, itemIds)
                if (created != null) {
                    playlists = (playlists + created).sortedBy { it.name.lowercase() }
                }
                onDone(created != null)
            } catch (e: Exception) {
                error = e.userMessage(appContext)
                onDone(false)
            }
        }
    }

    fun renamePlaylist(playlistId: String, name: String) {
        viewModelScope.launch {
            if (repository.renamePlaylist(playlistId, name)) {
                playlists = playlists.map { if (it.id == playlistId) it.copy(name = name) else it }
                selectedPlaylist?.let { if (it.id == playlistId) selectedPlaylist = it.copy(name = name) }
            }
        }
    }

    fun deletePlaylist(playlistId: String) {
        viewModelScope.launch {
            if (repository.deletePlaylist(playlistId)) {
                playlists = playlists.filterNot { it.id == playlistId }
                if (selectedPlaylist?.id == playlistId) closePlaylist()
            }
        }
    }

    fun addToPlaylist(playlistId: String, itemIds: List<String>) {
        viewModelScope.launch {
            runCatching { repository.addToPlaylist(playlistId, itemIds) }
            playlists = playlists.map { p ->
                if (p.id == playlistId) p.copy(trackCount = (p.trackCount ?: 0) + itemIds.size) else p
            }
        }
    }

    fun removeFromPlaylist(track: Track) {
        val playlist = selectedPlaylist ?: return
        playlistTracks = playlistTracks.filterNot { it.id == track.id }
        viewModelScope.launch {
            if (repository.removeFromPlaylist(playlist.id, listOf(track.id))) {
                playlists = playlists.map { p ->
                    if (p.id == playlist.id) p.copy(trackCount = ((p.trackCount ?: 1) - 1).coerceAtLeast(0)) else p
                }
            } else {
                playlistTracks = (playlistTracks + track).sortedBy { it.title.lowercase() }
            }
        }
    }

    private fun trackArtistId(track: Track): String? =
        track.artistName?.let { name -> artists.firstOrNull { it.name == name }?.id }

    fun effectiveTrackFavorite(track: Track): Boolean {
        if (track.isFavorite) return true
        val artistId = trackArtistId(track) ?: return false
        return artistId in favoriteArtistIds && track.id !in trackExceptionIds
    }

    fun effectiveAlbumFavorite(album: Album): Boolean =
        album.isFavorite || (album.artistId != null && album.artistId in favoriteArtistIds)

    fun effectiveArtistFavorite(artist: Artist): Boolean =
        artist.isFavorite || artist.id in favoriteArtistIds

    /** Rebuilds the favourites tab: own favourites plus artist-derived ones. */
    private fun refreshFavorites() {
        val derived = songs.filter { track ->
            !track.isFavorite &&
                trackArtistId(track)?.let { it in favoriteArtistIds } == true &&
                track.id !in trackExceptionIds
        }
        val merged = linkedMapOf<String, Track>()
        (serverFavorites + derived).forEach { merged[it.id] = it }
        favorites = merged.values.sortedBy { it.title.lowercase() }
    }

    /** Toggle a track's favourite flag; artist-derived favourites use a local exception. */
    fun toggleTrackFavorite(track: Track) {
        val artistFavorited = trackArtistId(track)?.let { it in favoriteArtistIds } == true
        val wanted = !effectiveTrackFavorite(track)

        // A track that is favourite only because its artist is: toggle the
        // exception locally instead of writing the server flag.
        if (artistFavorited && !track.isFavorite) {
            if (wanted) libraryDb.removeTrackException(track.id)
            else libraryDb.addTrackException(track.id)
            trackExceptionIds = libraryDb.trackExceptions()
            refreshFavorites()
            return
        }

        songs = songs.map { if (it.id == track.id) it.copy(isFavorite = wanted) else it }
        serverFavorites = if (wanted) {
            (serverFavorites.filterNot { it.id == track.id } + track.copy(isFavorite = true))
        } else {
            serverFavorites.filterNot { it.id == track.id }
        }
        refreshFavorites()
        viewModelScope.launch {
            try {
                val actual = repository.setFavorite(track.id, wanted)
                if (actual != wanted) applyFavorite(track, actual)
                if (actual) cacheFavorite(track)
            } catch (e: Exception) {
                applyFavorite(track, !wanted)
                error = e.userMessage(appContext)
            }
        }
    }

    private fun onWifi(): Boolean {
        val transport = repository.networkStatus.value.transport
        return transport == NetworkTransport.WIFI || transport == NetworkTransport.ETHERNET
    }

    /** Auto-cache a newly favorited track, honoring the Wi-Fi-only rule. */
    private suspend fun cacheFavorite(track: Track) {
        if (!settingsStore.autoCacheFavorites.value) return
        if (settingsStore.wifiOnlyDownloads.value && !onWifi()) return
        if (offlineStore.isDownloaded(track.id)) return
        runCatching {
            val source = repository.downloadSource(track)
            offlineStore.download(track, source.url, repository.playbackHeaders())
        }
    }

    private fun applyFavorite(track: Track, favorite: Boolean) {
        songs = songs.map { if (it.id == track.id) it.copy(isFavorite = favorite) else it }
        serverFavorites = if (favorite) {
            (serverFavorites.filterNot { it.id == track.id } + track.copy(isFavorite = true))
        } else {
            serverFavorites.filterNot { it.id == track.id }
        }
        refreshFavorites()
    }

    fun toggleAlbumFavorite(album: Album) {
        val wanted = !album.isFavorite
        albums = albums.map { if (it.id == album.id) it.copy(isFavorite = wanted) else it }
        viewModelScope.launch {
            try {
                val actual = repository.setFavorite(album.id, wanted)
                if (actual != wanted) {
                    albums = albums.map { if (it.id == album.id) it.copy(isFavorite = actual) else it }
                }
            } catch (e: Exception) {
                albums = albums.map { if (it.id == album.id) it.copy(isFavorite = !wanted) else it }
                error = e.userMessage(appContext)
            }
        }
    }

    fun toggleArtistFavorite(artist: Artist) {
        val wanted = !effectiveArtistFavorite(artist)
        libraryDb.setArtistFavorite(artist.id, wanted)
        favoriteArtistIds = libraryDb.favoriteArtistIds()
        artists = artists.map { if (it.id == artist.id) it.copy(isFavorite = wanted) else it }
        refreshFavorites()
        viewModelScope.launch {
            try {
                repository.setFavorite(artist.id, wanted)
                SyncLog.info("Interpret-Favorit \"${artist.name}\" gesetzt (lokal + Server)")
            } catch (e: Exception) {
                SyncLog.info(
                    "Interpret-Favorit \"${artist.name}\": Server nicht unterstützt (${e.javaClass.simpleName})",
                )
            }
        }
    }

    /**
     * Runs a selection action in the ViewModel scope so it survives the
     * selection bar disappearing; cancellation never reaches the user.
     */
    fun runAction(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (cancel: kotlinx.coroutines.CancellationException) {
                throw cancel
            } catch (e: Exception) {
                error = e.userMessage(appContext)
            }
        }
    }

    fun load() {
        error = null
        // The on-device index makes the library open instantly; the server is
        // only asked for the light things (favorites, playlists, counts).
        val hasIndex = !libraryDb.isEmpty()
        if (hasIndex) {
            readFromIndex()
            loading = false
        } else {
            loading = true
        }

        viewModelScope.launch {
            try {
                coroutineScope {
                    val libraryId = repository.defaultLibraryId()
                    val favoritesJob = async { repository.favoriteTracks(-1) }
                    val playlistsJob = async { repository.playlists() }
                    val countsJob = async { repository.libraryCounts(libraryId) }
                    serverFavorites = favoritesJob.await()
                    playlists = playlistsJob.await()
                    val serverCounts = countsJob.await()
                    if (serverCounts.songs > 0) counts = serverCounts
                }
                if (!hasIndex) {
                    // First run without an index: one full live load.
                    coroutineScope {
                        val libraryId = repository.defaultLibraryId()
                        val albumsJob = async { repository.albums(libraryId, limit = -1) }
                        val artistsJob = async { repository.artists(libraryId, limit = -1) }
                        val songsJob = async { repository.songs(libraryId, limit = -1) }
                        val genresJob = async { repository.genres(libraryId) }
                        albums = albumsJob.await()
                        artists = artistsJob.await()
                        songs = songsJob.await()
                        genres = genresJob.await()
                    }
                }
                offlineCopy = false
            } catch (e: Exception) {
                if (!hasIndex) {
                    val cachedSongs = libraryDb.allTracks()
                    if (cachedSongs.isNotEmpty()) {
                        readFromIndex()
                        offlineCopy = true
                    } else {
                        error = e.userMessage(appContext)
                    }
                } else {
                    // Index is there — stay on it, no error noise.
                    offlineCopy = true
                }
            } finally {
                refreshFavorites()
                loading = false
            }
        }
    }

    /** Fills every list from the SQLite index. Cheap enough for the main thread. */
    fun readFromIndex() {
        albums = libraryDb.allAlbums()
        artists = libraryDb.allArtists()
        songs = libraryDb.allTracks()
        genres = libraryDb.allGenres()
        counts = libraryDb.counts()
        refreshFavorites()
    }
}

@Composable
fun LibraryScreen(
    repository: MediaRepository,
    offlineStore: OfflineStore,
    settingsStore: SettingsStore,
    librarySync: app.k56.player.data.library.LibrarySync,
    onOpenAlbum: (String) -> Unit,
    onOpenArtist: (String) -> Unit,
    onPlay: (List<Track>, Int) -> Unit,
    onPlayNext: (Track) -> Unit = {},
    onAddToQueue: (Track) -> Unit = {},
    showTrackNumbers: Boolean = true,
    columns: Int = 0,
    wifiOnlyDownloads: Boolean = false,
    ignoreArticles: Boolean = true,
    showSubtitle: Boolean = true,
    showItemCount: Boolean = false,
    gridSpacing: Int = 12,
) {
    val context = LocalContext.current
    val syncState by librarySync.state.collectAsStateWithLifecycle()
    val vm = appViewModel { LibraryViewModel(repository, context.applicationContext, offlineStore, settingsStore) }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var sortMode by rememberSaveable { mutableIntStateOf(0) }
    var onlyOffline by rememberSaveable { mutableStateOf(false) }
    var addToPlaylistTrack by remember { mutableStateOf<Track?>(null) }
    var contextAlbum by remember { mutableStateOf<Album?>(null) }
    var contextArtist by remember { mutableStateOf<Artist?>(null) }
    val selectedTracks = remember { mutableStateListOf<String>() }
    val selectedAlbums = remember { mutableStateListOf<String>() }
    val selectedArtists = remember { mutableStateListOf<String>() }
    val selectionMode = selectedTracks.isNotEmpty() || selectedAlbums.isNotEmpty() || selectedArtists.isNotEmpty()

    fun toggleTrack(id: String) {
        if (!selectedTracks.remove(id)) selectedTracks.add(id)
    }

    suspend fun resolveSelection(): List<Track> {
        val out = LinkedHashMap<String, Track>()
        (vm.songs + vm.favorites).forEach { if (it.id in selectedTracks) out[it.id] = it }
        for (albumId in selectedAlbums) {
            runCatching { repository.albumDetails(albumId) }.getOrNull()
                ?.tracks?.forEach { out[it.id] = it }
        }
        for (artistId in selectedArtists) {
            val details = runCatching { repository.artistDetails(artistId) }.getOrNull() ?: continue
            details.albums.take(25).forEach { album ->
                runCatching { repository.albumDetails(album.id) }.getOrNull()
                    ?.tracks?.forEach { out[it.id] = it }
            }
        }
        return out.values.toList()
    }

    fun clearSelection() {
        selectedTracks.clear()
        selectedAlbums.clear()
        selectedArtists.clear()
    }

    suspend fun resolveAlbum(albumId: String): List<Track> =
        runCatching { repository.albumDetails(albumId) }.getOrNull()?.tracks ?: emptyList()

    suspend fun resolveArtist(artistId: String): List<Track> {
        val details = runCatching { repository.artistDetails(artistId) }.getOrNull() ?: return emptyList()
        val out = LinkedHashMap<String, Track>()
        for (album in details.albums.take(25)) {
            runCatching { repository.albumDetails(album.id) }.getOrNull()
                ?.tracks?.forEach { out[it.id] = it }
        }
        return out.values.toList()
    }
    val scope = rememberCoroutineScope()
    val offline by offlineStore.tracks.collectAsState()
    val downloads by offlineStore.progress.collectAsState()
    val downloadQueue by offlineStore.queue.collectAsState()
    val downloadedIds = remember(offline) { offline.map { it.id }.toSet() }
    val streamed by offlineStore.streamed.collectAsState()
    val cachedIds = remember(streamed, downloadedIds) { streamed - downloadedIds }
    val albumOfflineCount = remember(offline) {
        offline.mapNotNull { it.albumId }.groupingBy { it }.eachCount()
    }
    val net by repository.networkStatus.collectAsState()
    val onWifi = net.transport == app.k56.player.core.net.NetworkTransport.WIFI ||
        net.transport == app.k56.player.core.net.NetworkTransport.ETHERNET
    fun sortKey(value: String?): String {
        val lower = (value ?: "").lowercase()
        return if (ignoreArticles) lower.replace(ARTICLE_PREFIX, "") else lower
    }

    // Resolve sources, then hand the whole batch to the concurrent queue.
    fun enqueueTracks(tracks: List<Track>) {
        scope.launch {
            if (wifiOnlyDownloads && !onWifi) {
                Toast.makeText(context, R.string.download_wifi_only, Toast.LENGTH_SHORT).show()
                return@launch
            }
            val requests = tracks.mapNotNull { track ->
                if (offlineStore.isDownloaded(track.id)) return@mapNotNull null
                val source = runCatching { repository.downloadSource(track) }.getOrNull()
                    ?: return@mapNotNull null
                DownloadRequest(track, source.url, repository.playbackHeaders())
            }
            if (requests.isNotEmpty()) offlineStore.enqueue(requests)
        }
    }

    // One track, on demand from a row.
    val downloadOne: (Track) -> Unit = { track -> enqueueTracks(listOf(track)) }

    Box(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = tab) {
            listOf(
                stringResource(R.string.library_tab_albums),
                stringResource(R.string.library_tab_artists),
                stringResource(R.string.library_tab_tracks),
                stringResource(R.string.library_tab_favorites),
                stringResource(R.string.library_tab_genres),
                stringResource(R.string.library_tab_playlists),
            ).forEachIndexed { index, label ->
                Tab(
                    selected = tab == index,
                    onClick = { tab = index },
                    text = { Text(label) },
                )
            }
        }

        if (!vm.loading && tab <= 3) {
            val loaded = when (tab) {
                0 -> vm.albums.size
                1 -> vm.artists.size
                2 -> vm.songs.size
                else -> vm.favorites.size
            }
            val total = when (tab) {
                0 -> vm.counts.albums
                1 -> vm.counts.artists
                2 -> vm.counts.songs
                else -> 0
            }
            val text = when {
                total > 0 -> stringResource(R.string.library_loaded_count, loaded, total)
                tab == 0 -> context.resources.getQuantityString(R.plurals.plural_albums, loaded, loaded)
                tab == 1 -> context.resources.getQuantityString(R.plurals.plural_artists, loaded, loaded)
                else -> context.resources.getQuantityString(R.plurals.plural_tracks, loaded, loaded)
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }

        if (syncState.running) {
            Text(
                text = stringResource(
                    R.string.library_syncing,
                    syncPhaseLabel(syncState.phase),
                    syncState.loaded,
                    syncState.total,
                ),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
            )
            LinearProgressIndicator(
                progress = {
                    if (syncState.total > 0) {
                        syncState.loaded.toFloat() / syncState.total
                    } else {
                        0f
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .height(2.dp),
            )
        } else if (syncState.error != null) {
            Text(
                text = stringResource(R.string.library_sync_failed),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { librarySync.start(force = true) }
                    .padding(horizontal = 16.dp, vertical = 2.dp),
            )
        }

        if (!vm.loading && vm.offlineCopy) {
            val lastSync = remember(vm.indexLastSync) {
                if (vm.indexLastSync > 0) {
                    java.text.DateFormat
                        .getDateTimeInstance(
                            java.text.DateFormat.SHORT,
                            java.text.DateFormat.SHORT,
                        )
                        .format(java.util.Date(vm.indexLastSync))
                } else {
                    "—"
                }
            }
            Text(
                text = stringResource(R.string.library_offline_copy, lastSync),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
            )
        }

        if (tab >= 2) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilterChip(
                    selected = sortMode == 0,
                    onClick = { sortMode = 0 },
                    label = { Text(stringResource(R.string.sort_title)) },
                )
                FilterChip(
                    selected = sortMode == 1,
                    onClick = { sortMode = 1 },
                    label = { Text(stringResource(R.string.sort_artist)) },
                )
                FilterChip(
                    selected = onlyOffline,
                    onClick = { onlyOffline = !onlyOffline },
                    label = { Text(stringResource(R.string.filter_offline)) },
                )
            }
        }

        val error = vm.error
        when {
            vm.loading -> LoadingState(Modifier.weight(1f))

            error != null -> ErrorState(error, vm::load, Modifier.weight(1f))

            tab == 0 -> LazyVerticalGrid(
                columns = if (columns > 0) GridCells.Fixed(columns) else GridCells.Adaptive(148.dp),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(gridSpacing.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                items(vm.albums, key = { it.id }) { album ->
                    Box {
                        AlbumCard(
                            album = album,
                            onClick = {
                                if (selectionMode) {
                                    if (!selectedAlbums.remove(album.id)) selectedAlbums.add(album.id)
                                } else {
                                    onOpenAlbum(album.id)
                                }
                            },
                            onLongClick = {
                                if (!selectedAlbums.remove(album.id)) selectedAlbums.add(album.id)
                            },
                            selected = album.id in selectedAlbums,
                            isFavorite = vm.effectiveAlbumFavorite(album),
                            onToggleFavorite = { vm.toggleAlbumFavorite(album) },
                            offlineCount = albumOfflineCount[album.id],
                            trackCount = album.trackCount,
                            onMenu = { contextAlbum = album },
                        )
                        DropdownMenu(
                            expanded = contextAlbum?.id == album.id,
                            onDismissRequest = { contextAlbum = null },
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_select)) },
                                onClick = {
                                    if (!selectedAlbums.remove(album.id)) selectedAlbums.add(album.id)
                                    contextAlbum = null
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.common_play)) },
                                onClick = {
                                    scope.launch {
                                        val tracks = resolveAlbum(album.id)
                                        if (tracks.isNotEmpty()) onPlay(tracks, 0)
                                    }
                                    contextAlbum = null
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.player_shuffle)) },
                                onClick = {
                                    scope.launch {
                                        val tracks = resolveAlbum(album.id).shuffled()
                                        if (tracks.isNotEmpty()) onPlay(tracks, 0)
                                    }
                                    contextAlbum = null
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_add_queue)) },
                                onClick = {
                                    scope.launch { resolveAlbum(album.id).forEach { onAddToQueue(it) } }
                                    contextAlbum = null
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.offline_download)) },
                                onClick = {
                                    scope.launch { resolveAlbum(album.id).forEach { downloadOne(it) } }
                                    contextAlbum = null
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_open)) },
                                onClick = {
                                    onOpenAlbum(album.id)
                                    contextAlbum = null
                                },
                            )
                        }
                    }
                }
            }

            tab == 1 -> LazyVerticalGrid(
                columns = if (columns > 0) GridCells.Fixed(columns) else GridCells.Adaptive(126.dp),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(gridSpacing.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                items(vm.artists, key = { it.id }) { artist ->
                    Box {
                        ArtistCard(
                            artist = artist,
                            onClick = {
                                if (selectionMode) {
                                    if (!selectedArtists.remove(artist.id)) selectedArtists.add(artist.id)
                                } else {
                                    onOpenArtist(artist.id)
                                }
                            },
                            onLongClick = {
                                if (!selectedArtists.remove(artist.id)) selectedArtists.add(artist.id)
                            },
                            selected = artist.id in selectedArtists,
                            isFavorite = vm.effectiveArtistFavorite(artist),
                            onToggleFavorite = { vm.toggleArtistFavorite(artist) },
                            onMenu = { contextArtist = artist },
                        )
                        DropdownMenu(
                            expanded = contextArtist?.id == artist.id,
                            onDismissRequest = { contextArtist = null },
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_select)) },
                                onClick = {
                                    if (!selectedArtists.remove(artist.id)) selectedArtists.add(artist.id)
                                    contextArtist = null
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.common_play)) },
                                onClick = {
                                    scope.launch {
                                        val tracks = resolveArtist(artist.id)
                                        if (tracks.isNotEmpty()) onPlay(tracks, 0)
                                    }
                                    contextArtist = null
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.player_shuffle)) },
                                onClick = {
                                    scope.launch {
                                        val tracks = resolveArtist(artist.id).shuffled()
                                        if (tracks.isNotEmpty()) onPlay(tracks, 0)
                                    }
                                    contextArtist = null
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_add_queue)) },
                                onClick = {
                                    scope.launch { resolveArtist(artist.id).forEach { onAddToQueue(it) } }
                                    contextArtist = null
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.offline_download)) },
                                onClick = {
                                    scope.launch { resolveArtist(artist.id).forEach { downloadOne(it) } }
                                    contextArtist = null
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_open)) },
                                onClick = {
                                    onOpenArtist(artist.id)
                                    contextArtist = null
                                },
                            )
                        }
                    }
                }
            }

            tab == 2 -> run {
                val songs = vm.songs
                    .filter { !onlyOffline || it.id in downloadedIds }
                    .let { list ->
                        if (sortMode == 0) list.sortedBy { sortKey(it.title) }
                        else list.sortedBy { sortKey(it.artistName) }
                    }
                LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(bottom = 20.dp),
            ) {
                    itemsIndexed(songs, key = { _, track -> track.id }) { index, track ->
                        TrackRow(
                            track = track,
                            onClick = {
                                if (selectionMode) toggleTrack(track.id) else onPlay(songs, index)
                            },
                            onLongClick = { toggleTrack(track.id) },
                            selected = track.id in selectedTracks,
                            index = if (showTrackNumbers) index + 1 else null,
                            showSubtitle = showSubtitle,
                            isFavorite = vm.effectiveTrackFavorite(track),
                            onToggleFavorite = { vm.toggleTrackFavorite(track) },
                            isDownloaded = track.id in downloadedIds,
                            isCached = track.id in cachedIds,
                            downloadProgress = downloads[track.id],
                            onDownload = if (track.id in downloadedIds) null else { { downloadOne(track) } },
                            onPlayNext = { onPlayNext(track) },
                            onAddToQueue = { onAddToQueue(track) },
                            onAddToPlaylist = { addToPlaylistTrack = track },
                        )
                    }
                }
            }

            tab == 3 -> {
                val favorites = vm.favorites
                    .filter { !onlyOffline || it.id in downloadedIds }
                    .let { list ->
                        if (sortMode == 0) list.sortedBy { sortKey(it.title) }
                        else list.sortedBy { sortKey(it.artistName) }
                    }
                if (favorites.isEmpty()) {
                    EmptyState(
                        title = stringResource(R.string.library_tab_favorites),
                        subtitle = stringResource(R.string.library_empty_favorites),
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    val missing = favorites.count { it.id !in downloadedIds }
                    val downloaded = favorites.count { it.id in downloadedIds }
                    Column(Modifier.weight(1f)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = stringResource(
                                    R.string.offline_favorites_status,
                                    downloadedIds.count { id -> favorites.any { it.id == id } },
                                    favorites.size,
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(
                                onClick = {
                                    if (favorites.isNotEmpty()) onPlay(favorites, 0)
                                },
                            ) {
                                Icon(
                                    Icons.Rounded.PlayArrow,
                                    contentDescription = stringResource(R.string.common_play),
                                )
                            }
                            IconButton(
                                onClick = {
                                    if (favorites.isNotEmpty()) onPlay(favorites.shuffled(), 0)
                                },
                            ) {
                                Icon(
                                    Icons.Rounded.Shuffle,
                                    contentDescription = stringResource(R.string.player_shuffle),
                                )
                            }
                            if (downloaded > 0 && missing == 0) {
                                OutlinedButton(
                                    onClick = {
                                        scope.launch {
                                            favorites.filter { it.id in downloadedIds }.forEach { track ->
                                                try {
                                                    // Replace the old file so the new quality actually applies.
                                                    offlineStore.delete(track.id)
                                                    val source = repository.downloadSource(track)
                                                    offlineStore.download(track, source.url, repository.playbackHeaders())
                                                } catch (_: Exception) {
                                                    // one failure must not abort the batch
                                                }
                                            }
                                        }
                                    },
                                ) {
                                    Text(stringResource(R.string.offline_reload))
                                }
                            }
                            if (missing > 0) {
                                Button(
                                    onClick = { enqueueTracks(favorites.filter { it.id !in downloadedIds }) },
                                ) {
                                    Text(stringResource(R.string.offline_download_all, missing))
                                }
                            }
                            if (downloadQueue.running) {
                                Spacer(Modifier.height(8.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        stringResource(
                                            R.string.download_queue_progress,
                                            downloadQueue.done,
                                            downloadQueue.total,
                                            downloadQueue.active,
                                        ),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.weight(1f),
                                    )
                                    TextButton(onClick = { offlineStore.cancelAll() }) {
                                        Text(stringResource(R.string.download_queue_cancel))
                                    }
                                }
                            }
                        }
                        LazyColumn(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            contentPadding = PaddingValues(bottom = 20.dp),
                        ) {
                            itemsIndexed(favorites, key = { _, track -> track.id }) { index, track ->
                                TrackRow(
                                    track = track,
                                    onClick = {
                                        if (selectionMode) toggleTrack(track.id)
                                        else onPlay(favorites, index)
                                    },
                                    onLongClick = { toggleTrack(track.id) },
                                    selected = track.id in selectedTracks,
                                    index = if (showTrackNumbers) index + 1 else null,
                                    showSubtitle = showSubtitle,
                                    isFavorite = true,
                                    onToggleFavorite = { vm.toggleTrackFavorite(track) },
                                    isDownloaded = track.id in downloadedIds,
                                    isCached = track.id in cachedIds,
                                    downloadProgress = downloads[track.id],
                                    onDownload = if (track.id in downloadedIds) null else {
                                        { downloadOne(track) }
                                    },
                                    onPlayNext = { onPlayNext(track) },
                                    onAddToQueue = { onAddToQueue(track) },
                                    onAddToPlaylist = { addToPlaylistTrack = track },
                                )
                            }
                        }
                    }
                }
            }

            tab == 4 -> {
                GenreContent(
                    vm = vm,
                    onOpenAlbum = onOpenAlbum,
                    onPlay = onPlay,
                )
            }

            tab == 5 -> {
                PlaylistContent(
                    vm = vm,
                    onPlay = onPlay,
                    onPlayNext = onPlayNext,
                    onAddToQueue = onAddToQueue,
                )
            }
        }
        } // Column

        if (selectionMode) {
            SelectionBar(
                count = selectedTracks.size + selectedAlbums.size + selectedArtists.size,
                onPlay = {
                    vm.runAction {
                        val list = resolveSelection()
                        if (list.isNotEmpty()) onPlay(list, 0)
                        clearSelection()
                    }
                },
                onMix = {
                    vm.runAction {
                        settingsStore.setAutoplay(true)
                        val list = resolveSelection().shuffled()
                        if (list.isNotEmpty()) onPlay(list, 0)
                        clearSelection()
                    }
                },
                onShuffle = {
                    vm.runAction {
                        val list = resolveSelection().shuffled()
                        if (list.isNotEmpty()) onPlay(list, 0)
                        clearSelection()
                    }
                },
                onQueue = {
                    vm.runAction {
                        resolveSelection().forEach { onAddToQueue(it) }
                        clearSelection()
                    }
                },
                onDownload = {
                    vm.runAction {
                        val list = resolveSelection()
                        clearSelection()
                        enqueueTracks(list)
                    }
                },
                onDelete = {
                    scope.launch {
                        resolveSelection().forEach { offlineStore.delete(it.id) }
                        clearSelection()
                    }
                },
                onSelectAll = {
                    when (tab) {
                        0 -> {
                            selectedAlbums.clear()
                            selectedAlbums.addAll(vm.albums.map { it.id })
                        }
                        1 -> {
                            selectedArtists.clear()
                            selectedArtists.addAll(vm.artists.map { it.id })
                        }
                        2 -> {
                            selectedTracks.clear()
                            selectedTracks.addAll(vm.songs.map { it.id })
                        }
                        else -> {
                            selectedTracks.clear()
                            selectedTracks.addAll(vm.favorites.map { it.id })
                        }
                    }
                },
                onCancel = { clearSelection() },
            )
        }

        val playlistTarget = addToPlaylistTrack
        if (playlistTarget != null) {
            PlaylistPickerDialog(
                playlists = vm.playlists,
                onPick = { playlist ->
                    vm.addToPlaylist(playlist.id, listOf(playlistTarget.id))
                    addToPlaylistTrack = null
                },
                onCreateNew = { name ->
                    vm.createPlaylist(name, listOf(playlistTarget.id)) { ok ->
                        if (ok) addToPlaylistTrack = null
                    }
                },
                onDismiss = { addToPlaylistTrack = null },
            )
        }
    }
}

@Composable
private fun SelectionBar(
    count: Int,
    onPlay: () -> Unit,
    onMix: () -> Unit,
    onShuffle: () -> Unit,
    onQueue: () -> Unit,
    onDownload: () -> Unit,
    onDelete: () -> Unit,
    onSelectAll: () -> Unit,
    onCancel: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 6.dp,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.selection_count, count),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onSelectAll) {
                    Text(stringResource(R.string.selection_all), style = MaterialTheme.typography.labelLarge)
                }
                IconButton(onClick = onCancel) {
                    Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.selection_cancel))
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                SelectionAction(Icons.Rounded.PlayArrow, stringResource(R.string.common_play), onPlay)
                SelectionAction(Icons.Rounded.AutoAwesome, stringResource(R.string.selection_mix), onMix)
                SelectionAction(Icons.Rounded.Shuffle, stringResource(R.string.player_shuffle), onShuffle)
                SelectionAction(Icons.Rounded.Add, stringResource(R.string.action_add_queue), onQueue)
                SelectionAction(Icons.Rounded.Download, stringResource(R.string.offline_download), onDownload)
                SelectionAction(Icons.Rounded.DeleteOutline, stringResource(R.string.cache_delete_one), onDelete)
            }
        }
    }
}

@Composable
private fun SelectionAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(onClick = onClick) {
            Icon(icon, contentDescription = label)
        }
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

private val ARTICLE_PREFIX = Regex("""^(the|a|an|die|der|das|le|la|les|el|los|il)\s+""")

@Composable
private fun syncPhaseLabel(phase: String): String = when (phase) {
    "albums" -> stringResource(R.string.sync_phase_albums)
    "tracks" -> stringResource(R.string.sync_phase_tracks)
    "artists" -> stringResource(R.string.sync_phase_artists)
    "genres" -> stringResource(R.string.sync_phase_genres)
    "writing" -> stringResource(R.string.sync_phase_writing)
    "incremental" -> stringResource(R.string.sync_phase_incremental)
    "start" -> stringResource(R.string.sync_phase_start)
    else -> phase
}

@Composable
private fun GenreContent(
    vm: LibraryViewModel,
    onOpenAlbum: (String) -> Unit,
    onPlay: (List<Track>, Int) -> Unit,
) {
    val genre = vm.selectedGenre
    if (genre == null) {
        if (vm.genres.isEmpty()) {
            EmptyState(
                title = stringResource(R.string.library_tab_genres),
                subtitle = stringResource(R.string.genre_empty),
                modifier = Modifier.fillMaxWidth(),
            )
            return
        }
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(vertical = 8.dp),
        ) {
            items(vm.genres, key = { it }) { name ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { vm.openGenre(name) }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = name,
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        }
        return
    }

    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = vm::closeGenre) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.common_back))
            }
            Text(genre, style = MaterialTheme.typography.titleLarge)
        }
        if (vm.genreOffline) {
            Text(
                text = stringResource(R.string.offline_copy),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(bottom = 20.dp),
        ) {
            if (vm.genreAlbums.isNotEmpty()) {
                item { SectionHeader(stringResource(R.string.library_tab_albums)) }
                item {
                    HorizontalAlbumRow(
                        albums = vm.genreAlbums,
                        onOpenAlbum = onOpenAlbum,
                    )
                }
            }
            if (vm.genreTracks.isNotEmpty()) {
                item { SectionHeader(stringResource(R.string.library_tab_tracks)) }
                itemsIndexed(vm.genreTracks, key = { _, track -> track.id }) { index, track ->
                    TrackRow(
                        track = track,
                        onClick = { onPlay(vm.genreTracks, index) },
                        showArtwork = true,
                    )
                }
            }
        }
    }
}

@Composable
private fun PlaylistContent(
    vm: LibraryViewModel,
    onPlay: (List<Track>, Int) -> Unit,
    onPlayNext: (Track) -> Unit,
    onAddToQueue: (Track) -> Unit,
) {
    val playlist = vm.selectedPlaylist
    var showCreate by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<Playlist?>(null) }
    var deleteTarget by remember { mutableStateOf<Playlist?>(null) }

    if (playlist == null) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.playlist_offline_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Button(onClick = { showCreate = true }) {
                    Icon(Icons.Rounded.Add, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.playlist_new))
                }
            }
            if (vm.playlists.isEmpty()) {
                EmptyState(
                    title = stringResource(R.string.library_tab_playlists),
                    subtitle = stringResource(R.string.playlist_empty),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(bottom = 20.dp),
                ) {
                    items(vm.playlists, key = { it.id }) { p ->
                        PlaylistRow(
                            playlist = p,
                            onClick = { vm.openPlaylist(p) },
                            onRename = { renameTarget = p },
                            onDelete = { deleteTarget = p },
                        )
                    }
                }
            }
        }
    } else {
        Column(Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = vm::closePlaylist) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.common_back))
                }
                Text(
                    text = playlist.name,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                IconButton(
                    onClick = { if (vm.playlistTracks.isNotEmpty()) onPlay(vm.playlistTracks, 0) },
                ) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = stringResource(R.string.common_play))
                }
                Box {
                    var menuOpen by remember { mutableStateOf(false) }
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.action_more))
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.playlist_rename)) },
                            onClick = { menuOpen = false; renameTarget = playlist },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.playlist_delete)) },
                            onClick = { menuOpen = false; deleteTarget = playlist },
                        )
                    }
                }
            }
            if (vm.playlistTracks.isEmpty()) {
                EmptyState(
                    title = stringResource(R.string.library_tab_playlists),
                    subtitle = stringResource(R.string.playlist_empty),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(bottom = 20.dp),
                ) {
                    itemsIndexed(vm.playlistTracks, key = { _, track -> track.id }) { index, track ->
                        TrackRow(
                            track = track,
                            onClick = { onPlay(vm.playlistTracks, index) },
                            showArtwork = true,
                            onPlayNext = { onPlayNext(track) },
                            onAddToQueue = { onAddToQueue(track) },
                            onRemoveFromPlaylist = { vm.removeFromPlaylist(track) },
                        )
                    }
                }
            }
        }
    }

    if (showCreate) {
        PlaylistNameDialog(
            title = stringResource(R.string.playlist_new),
            initial = "",
            onConfirm = { name -> vm.createPlaylist(name, emptyList()) { showCreate = false } },
            onDismiss = { showCreate = false },
        )
    }
    renameTarget?.let { p ->
        PlaylistNameDialog(
            title = stringResource(R.string.playlist_rename),
            initial = p.name,
            onConfirm = { name -> vm.renamePlaylist(p.id, name); renameTarget = null },
            onDismiss = { renameTarget = null },
        )
    }
    deleteTarget?.let { p ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(R.string.playlist_delete)) },
            text = { Text(p.name) },
            confirmButton = {
                TextButton(onClick = { vm.deletePlaylist(p.id); deleteTarget = null }) {
                    Text(stringResource(R.string.playlist_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text(stringResource(R.string.common_cancel)) }
            },
        )
    }
}

@Composable
private fun PlaylistRow(
    playlist: Playlist,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = playlist.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val count = playlist.trackCount
            if (count != null) {
                Text(
                    text = pluralStringResource(R.plurals.plural_tracks, count, count),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.action_more))
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.playlist_rename)) },
                    onClick = { menuOpen = false; onRename() },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.playlist_delete)) },
                    onClick = { menuOpen = false; onDelete() },
                )
            }
        }
    }
}

@Composable
private fun PlaylistNameDialog(
    title: String,
    initial: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.playlist_name)) },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = { onConfirm(name.trim()) }) {
                Text(stringResource(R.string.common_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        },
    )
}

@Composable
private fun PlaylistPickerDialog(
    playlists: List<Playlist>,
    onPick: (Playlist) -> Unit,
    onCreateNew: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var creating by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.action_add_playlist)) },
        text = {
            if (creating) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.playlist_name)) },
                    singleLine = true,
                )
            } else if (playlists.isEmpty()) {
                Text(stringResource(R.string.playlist_empty))
            } else {
                Column {
                    playlists.forEach { p ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPick(p) }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(p.name, style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (creating) {
                TextButton(enabled = name.isNotBlank(), onClick = { onCreateNew(name.trim()) }) {
                    Text(stringResource(R.string.common_save))
                }
            } else {
                TextButton(onClick = { creating = true }) { Text(stringResource(R.string.playlist_new)) }
            }
        },
        dismissButton = {
            TextButton(onClick = { if (creating) { creating = false } else onDismiss() }) {
                Text(stringResource(R.string.common_cancel))
            }
        },
    )
}
