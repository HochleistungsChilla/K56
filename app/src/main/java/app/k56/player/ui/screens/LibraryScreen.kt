package app.k56.player.ui.screens

import android.content.Context
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.widget.Toast
import kotlinx.coroutines.launch
import app.k56.player.R
import app.k56.player.core.model.Album
import app.k56.player.core.model.Artist
import app.k56.player.core.model.Track
import app.k56.player.core.net.NetworkTransport
import app.k56.player.data.MediaRepository
import app.k56.player.data.SettingsStore
import app.k56.player.data.offline.OfflineStore
import app.k56.player.ui.appViewModel
import app.k56.player.ui.components.AlbumCard
import app.k56.player.ui.components.ArtistCard
import app.k56.player.ui.components.ErrorState
import app.k56.player.ui.components.EmptyState
import app.k56.player.ui.components.LoadingState
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
    var loading by mutableStateOf(true)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    init {
        load()
    }

    /** Toggle a track's favourite flag on the server and update the local list. */
    fun toggleTrackFavorite(track: Track) {
        val wanted = !track.isFavorite
        songs = songs.map { if (it.id == track.id) it.copy(isFavorite = wanted) else it }
        // Favourites live in their own list so the tab is not limited to the
        // songs the library page happened to load.
        favorites = if (wanted) {
            (favorites.filterNot { it.id == track.id } + track.copy(isFavorite = true))
                .sortedBy { it.title.lowercase() }
        } else {
            favorites.filterNot { it.id == track.id }
        }
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
        favorites = if (favorite) {
            (favorites.filterNot { it.id == track.id } + track.copy(isFavorite = true))
                .sortedBy { it.title.lowercase() }
        } else {
            favorites.filterNot { it.id == track.id }
        }
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

    fun load() {
        loading = true
        error = null
        viewModelScope.launch {
            try {
                coroutineScope {
                    val libraryId = repository.defaultLibraryId()
                    val albumsJob = async { repository.albums(libraryId, limit = 300) }
                    val artistsJob = async { repository.artists(libraryId, limit = 300) }
                    val songsJob = async { repository.songs(libraryId, limit = 600) }
                    val favoritesJob = async { repository.favoriteTracks() }
                    albums = albumsJob.await()
                    artists = artistsJob.await()
                    songs = songsJob.await()
                    favorites = favoritesJob.await()
                }
            } catch (e: Exception) {
                error = e.userMessage(appContext)
            } finally {
                loading = false
            }
        }
    }
}

@Composable
fun LibraryScreen(
    repository: MediaRepository,
    offlineStore: OfflineStore,
    settingsStore: SettingsStore,
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
    val vm = appViewModel { LibraryViewModel(repository, context.applicationContext, offlineStore, settingsStore) }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var sortMode by rememberSaveable { mutableIntStateOf(0) }
    var onlyOffline by rememberSaveable { mutableStateOf(false) }
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
    val scope = rememberCoroutineScope()
    val offline by offlineStore.tracks.collectAsState()
    val downloads by offlineStore.progress.collectAsState()
    val downloadedIds = remember(offline) { offline.map { it.id }.toSet() }
    val net by repository.networkStatus.collectAsState()
    val onWifi = net.transport == app.k56.player.core.net.NetworkTransport.WIFI ||
        net.transport == app.k56.player.core.net.NetworkTransport.ETHERNET
    fun sortKey(value: String?): String {
        val lower = (value ?: "").lowercase()
        return if (ignoreArticles) lower.replace(ARTICLE_PREFIX, "") else lower
    }

    // One track, on demand from a row.
    val downloadOne: (Track) -> Unit = { track ->
        scope.launch {
            if (wifiOnlyDownloads && !onWifi) {
                Toast.makeText(context, R.string.download_wifi_only, Toast.LENGTH_SHORT).show()
                return@launch
            }
            try {
                val source = repository.downloadSource(track)
                offlineStore.download(track, source.url, repository.playbackHeaders())
            } catch (e: Exception) {
                Toast.makeText(context, e.userMessage(context), Toast.LENGTH_LONG).show()
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = tab) {
            listOf(
                stringResource(R.string.library_tab_albums),
                stringResource(R.string.library_tab_artists),
                stringResource(R.string.library_tab_tracks),
                stringResource(R.string.library_tab_favorites),
            ).forEachIndexed { index, label ->
                Tab(
                    selected = tab == index,
                    onClick = { tab = index },
                    text = { Text(label) },
                )
            }
        }

        if (showItemCount && !vm.loading) {
            Text(
                text = when (tab) {
                    0 -> context.resources.getQuantityString(R.plurals.plural_albums, vm.albums.size, vm.albums.size)
                    1 -> context.resources.getQuantityString(R.plurals.plural_artists, vm.artists.size, vm.artists.size)
                    2 -> context.resources.getQuantityString(R.plurals.plural_tracks, vm.songs.size, vm.songs.size)
                    else -> context.resources.getQuantityString(R.plurals.plural_tracks, vm.favorites.size, vm.favorites.size)
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
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
                        isFavorite = album.isFavorite,
                        onToggleFavorite = { vm.toggleAlbumFavorite(album) },
                    )
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
                    )
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
                            isFavorite = track.isFavorite,
                            onToggleFavorite = { vm.toggleTrackFavorite(track) },
                            isDownloaded = track.id in downloadedIds,
                            downloadProgress = downloads[track.id],
                            onDownload = if (track.id in downloadedIds) null else { { downloadOne(track) } },
                            onPlayNext = { onPlayNext(track) },
                            onAddToQueue = { onAddToQueue(track) },
                        )
                    }
                }
            }

            else -> {
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
                                    onClick = {
                                        scope.launch {
                                            favorites.filter { it.id !in downloadedIds }.forEach { track ->
                                                try {
                                                    val source = repository.downloadSource(track)
                                                    offlineStore.download(track, source.url, repository.playbackHeaders())
                                                } catch (_: Exception) {
                                                    // keep going; a single bad file must not stop the batch
                                                }
                                            }
                                        }
                                    },
                                ) {
                                    Text(stringResource(R.string.offline_download_all, missing))
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
                                    downloadProgress = downloads[track.id],
                                    onDownload = if (track.id in downloadedIds) null else {
                                        { downloadOne(track) }
                                    },
                                    onPlayNext = { onPlayNext(track) },
                                    onAddToQueue = { onAddToQueue(track) },
                                )
                            }
                        }
                    }
                }
            }
        }
        } // Column

        if (selectionMode) {
            SelectionBar(
                count = selectedTracks.size + selectedAlbums.size + selectedArtists.size,
                onPlay = {
                    scope.launch {
                        val list = resolveSelection()
                        if (list.isNotEmpty()) onPlay(list, 0)
                        clearSelection()
                    }
                },
                onShuffle = {
                    scope.launch {
                        val list = resolveSelection().shuffled()
                        if (list.isNotEmpty()) onPlay(list, 0)
                        clearSelection()
                    }
                },
                onQueue = {
                    scope.launch {
                        resolveSelection().forEach { onAddToQueue(it) }
                        clearSelection()
                    }
                },
                onDownload = {
                    scope.launch {
                        resolveSelection().forEach { downloadOne(it) }
                        clearSelection()
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
    }
}

@Composable
private fun SelectionBar(
    count: Int,
    onPlay: () -> Unit,
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
