package app.k56.player.ui.screens

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import android.widget.Toast
import app.k56.player.R
import app.k56.player.core.model.Album
import app.k56.player.core.model.Artist
import app.k56.player.core.model.Playlist
import app.k56.player.core.model.Track
import app.k56.player.core.net.NetworkTransport
import app.k56.player.data.HomeGreeting
import app.k56.player.data.MediaRepository
import app.k56.player.data.ResumeStore
import app.k56.player.data.SettingsStore
import app.k56.player.data.library.LibraryDb
import app.k56.player.data.offline.OfflineStore
import app.k56.player.data.offline.DownloadRequest
import app.k56.player.ui.appViewModel
import app.k56.player.ui.components.ArtistCard
import app.k56.player.ui.components.Artwork
import app.k56.player.ui.components.EmptyState
import app.k56.player.ui.components.ErrorState
import app.k56.player.ui.components.HorizontalAlbumRow
import app.k56.player.ui.components.LoadingState
import app.k56.player.ui.components.SectionHeader
import app.k56.player.ui.components.TrackRow
import app.k56.player.ui.userMessage
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

class HomeViewModel(
    private val repository: MediaRepository,
    private val libraryDb: LibraryDb,
    private val resumeStore: ResumeStore,
    private val appContext: Context,
) : ViewModel() {

    var recentAlbums by mutableStateOf<List<Album>>(emptyList())
        private set
    var allAlbums by mutableStateOf<List<Album>>(emptyList())
        private set
    var artists by mutableStateOf<List<Artist>>(emptyList())
        private set
    var songs by mutableStateOf<List<Track>>(emptyList())
        private set
    var continueItems by mutableStateOf<List<ResumeItem>>(emptyList())
        private set
    var recentPlayed by mutableStateOf<List<Track>>(emptyList())
        private set
    var favorites by mutableStateOf<List<Track>>(emptyList())
        private set
    var playlists by mutableStateOf<List<Playlist>>(emptyList())
        private set
    var genres by mutableStateOf<List<String>>(emptyList())
        private set
    var loading by mutableStateOf(true)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    val serverName: String =
        repository.sessionFlow.value?.serverName ?: appContext.getString(R.string.settings_server)
    val userName: String = repository.sessionFlow.value?.userName.orEmpty()
    val serverVersion: String = repository.sessionFlow.value?.serverVersion.orEmpty()
    val backendLabel: String =
        repository.sessionFlow.value?.backend?.label ?: appContext.getString(R.string.settings_server)

    init {
        load()
    }

    fun load() {
        loading = true
        error = null
        refreshLocal()
        viewModelScope.launch {
            try {
                coroutineScope {
                    val libraryId = repository.defaultLibraryId()
                    val recent = async { repository.recentAlbums(limit = 24) }
                    val albums = async { repository.albums(libraryId, limit = 300) }
                    val artistJob = async { repository.artists(libraryId, limit = 40) }
                    val songsJob = async { repository.songs(libraryId, limit = 600) }
                    val playlistsJob = async { repository.playlists() }
                    recentAlbums = recent.await()
                    allAlbums = albums.await()
                    artists = artistJob.await()
                    songs = songsJob.await()
                    playlists = playlistsJob.await()
                }
            } catch (e: Exception) {
                error = e.userMessage(appContext)
            } finally {
                loading = false
            }
        }
    }

    /** Refreshes the local (device) sections: resume points and play history. */
    fun refreshLocal() {
        continueItems = resumeStore.recent(6).mapNotNull { point ->
            val track = libraryDb.trackById(point.itemId) ?: Track(
                id = point.itemId,
                title = point.title,
                albumId = null,
                albumName = null,
                artistName = null,
                durationMs = point.durationMs,
                indexNumber = null,
                discNumber = null,
                container = null,
                bitrate = null,
                imageUrl = null,
            )
            ResumeItem(track, point.positionMs, point.durationMs)
        }
        recentPlayed = libraryDb.recentPlays(12)
        favorites = libraryDb.favoriteTracks(12)
        genres = libraryDb.allGenres().take(12)
    }

    /** Title mix: a wide random slice of the library. */
    fun titleMix(): List<Track> = songs.shuffled().take(60)

    /** Album mix: one random album, played in order. */
    suspend fun albumMix(): List<Track> {
        val album = allAlbums.randomOrNull() ?: return emptyList()
        return runCatching { repository.albumDetails(album.id).tracks }.getOrDefault(emptyList())
    }

    /** Artist mix: everything from one random artist. */
    suspend fun artistMix(): List<Track> {
        val artist = artists.randomOrNull() ?: return emptyList()
        val details = runCatching { repository.artistDetails(artist.id) }.getOrNull() ?: return emptyList()
        val tracks = mutableListOf<Track>()
        details.albums.take(6).forEach { album ->
            runCatching { repository.albumDetails(album.id).tracks }.getOrNull()?.let { tracks += it }
        }
        return tracks
    }
}

/** A resumed track with where it was left off, for the "continue" row. */
data class ResumeItem(
    val track: Track,
    val positionMs: Long,
    val durationMs: Long,
)

@Composable
fun HomeScreen(
    repository: MediaRepository,
    settingsStore: SettingsStore,
    offlineStore: OfflineStore,
    libraryDb: LibraryDb,
    resumeStore: ResumeStore,
    onOpenAlbum: (String) -> Unit,
    onOpenArtist: (String) -> Unit,
    onOpenGenre: (String) -> Unit,
    onOpenPlaylist: (String) -> Unit,
    onOpenLibrary: () -> Unit,
    onPlay: (List<Track>, Int) -> Unit,
    onAddToQueue: (Track) -> Unit = {},
    gridSpacing: Int = 12,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val vm = appViewModel { HomeViewModel(repository, libraryDb, resumeStore, context.applicationContext) }
    val sections by settingsStore.homeSections.collectAsStateWithLifecycle()
    var editOpen by remember { mutableStateOf(false) }

    val net by repository.networkStatus.collectAsStateWithLifecycle()
    val onWifi = net.transport == NetworkTransport.WIFI || net.transport == NetworkTransport.ETHERNET
    val wifiOnlyDownloads = settingsStore.wifiOnlyDownloads.value
    val offlineTracks by offlineStore.tracks.collectAsStateWithLifecycle()
    val downloadedIds = offlineTracks.map { it.id }.toSet()
    val downloads by offlineStore.progress.collectAsStateWithLifecycle()
    fun enqueueTracks(tracks: List<Track>) {
        scope.launch {
            val requests = tracks.mapNotNull { track ->
                if (offlineStore.isDownloaded(track.id)) return@mapNotNull null
                val source = runCatching { repository.downloadSource(track) }.getOrNull()
                    ?: return@mapNotNull null
                DownloadRequest(track, source.url, repository.playbackHeaders())
            }
            if (requests.isNotEmpty()) offlineStore.enqueue(requests)
        }
    }

    val downloadOne: (Track) -> Unit = { track -> enqueueTracks(listOf(track)) }

    fun downloadAlbum(albumId: String) {
        scope.launch {
            val tracks = runCatching { repository.albumDetails(albumId).tracks }.getOrDefault(emptyList())
            enqueueTracks(tracks)
        }
    }

    val selectedAlbums = remember { mutableStateListOf<String>() }
    val selectedArtists = remember { mutableStateListOf<String>() }
    val selectionMode = selectedAlbums.isNotEmpty() || selectedArtists.isNotEmpty()

    fun toggleAlbum(id: String) {
        if (!selectedAlbums.remove(id)) selectedAlbums.add(id)
    }

    fun toggleArtist(id: String) {
        if (!selectedArtists.remove(id)) selectedArtists.add(id)
    }

    fun clearSelection() {
        selectedAlbums.clear()
        selectedArtists.clear()
    }

    suspend fun resolveSelection(): List<Track> {
        val out = LinkedHashMap<String, Track>()
        for (albumId in selectedAlbums) {
            runCatching { repository.albumDetails(albumId) }.getOrNull()
                ?.tracks?.forEach { out[it.id] = it }
        }
        for (artistId in selectedArtists) {
            runCatching { repository.artistDetails(artistId) }.getOrNull()
                ?.albums?.take(6)?.forEach { album ->
                    runCatching { repository.albumDetails(album.id) }.getOrNull()
                        ?.tracks?.forEach { out[it.id] = it }
                }
        }
        return out.values.toList()
    }

    BackHandler(enabled = selectionMode) { clearSelection() }

    Box(Modifier.fillMaxSize()) {
        when {
            vm.loading -> LoadingState()

            vm.error != null -> ErrorState(vm.error!!, vm::load)

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = if (selectionMode) 96.dp else 20.dp),
            ) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 8.dp, top = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        val greeting by settingsStore.homeGreeting.collectAsStateWithLifecycle()
                        val headline: String? = when (greeting) {
                            HomeGreeting.OFF -> null
                            HomeGreeting.PLAIN -> stringResource(R.string.home_greeting)
                            HomeGreeting.SERVER -> vm.serverName
                            HomeGreeting.PERSONAL -> if (vm.userName.isBlank()) {
                                stringResource(R.string.home_greeting)
                            } else {
                                stringResource(
                                    R.string.home_greeting_personal,
                                    stringResource(timeOfDayGreeting()),
                                    vm.userName,
                                )
                            }
                        }
                        if (headline != null) {
                            Text(
                                headline,
                                style = MaterialTheme.typography.headlineMedium,
                            )
                        }
                        val subtitle = if (greeting == HomeGreeting.SERVER) {
                            "${vm.backendLabel} ${vm.serverVersion}"
                        } else {
                            "${vm.serverName} · ${vm.backendLabel} ${vm.serverVersion}"
                        }
                        Text(
                            subtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { vm.load() }) {
                        Icon(Icons.Rounded.Refresh, contentDescription = stringResource(R.string.action_refresh))
                    }
                    IconButton(onClick = { editOpen = true }) {
                        Icon(Icons.Rounded.Edit, contentDescription = stringResource(R.string.home_edit))
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AssistChip(
                        onClick = { onPlay(vm.songs.shuffled(), 0) },
                        label = { Text(stringResource(R.string.home_shuffle)) },
                    )
                    AssistChip(
                        onClick = { onPlay(vm.titleMix(), 0) },
                        label = { Text(stringResource(R.string.mix_titles)) },
                    )
                    AssistChip(
                        onClick = {
                            scope.launch {
                                val tracks = vm.albumMix()
                                if (tracks.isNotEmpty()) onPlay(tracks, 0)
                            }
                        },
                        label = { Text(stringResource(R.string.mix_album)) },
                    )
                    AssistChip(
                        onClick = {
                            scope.launch {
                                val tracks = vm.artistMix()
                                if (tracks.isNotEmpty()) onPlay(tracks, 0)
                            }
                        },
                        label = { Text(stringResource(R.string.mix_artist)) },
                    )
                }
            }

            sections.forEach { key ->
                when (key) {
                    "recent" -> if (vm.recentAlbums.isNotEmpty()) {
                        item { SectionHeader(stringResource(R.string.home_recently_added)) }
                        item {
                            HorizontalAlbumRow(
                                albums = vm.recentAlbums,
                                onOpenAlbum = onOpenAlbum,
                                spacing = gridSpacing.dp,
                                onAlbumLongClick = { toggleAlbum(it) },
                                selectedIds = selectedAlbums.toSet(),
                                selectionMode = selectionMode,
                                onDownloadAlbum = { downloadAlbum(it) },
                            )
                        }
                    }

                    "random_albums" -> if (vm.allAlbums.isNotEmpty()) {
                        item { SectionHeader(stringResource(R.string.home_random_albums)) }
                        item {
                            HorizontalAlbumRow(
                                albums = vm.allAlbums.shuffled().take(24),
                                onOpenAlbum = onOpenAlbum,
                                spacing = gridSpacing.dp,
                                onAlbumLongClick = { toggleAlbum(it) },
                                selectedIds = selectedAlbums.toSet(),
                                selectionMode = selectionMode,
                                onDownloadAlbum = { downloadAlbum(it) },
                            )
                        }
                    }

                    "random_artists" -> if (vm.artists.isNotEmpty()) {
                        item { SectionHeader(stringResource(R.string.home_random_artists)) }
                        item {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(gridSpacing.dp),
                            ) {
                                items(vm.artists.shuffled().take(20), key = { it.id }) { artist ->
                                    ArtistCard(
                                        artist = artist,
                                        onClick = {
                                            if (selectionMode) toggleArtist(artist.id)
                                            else onOpenArtist(artist.id)
                                        },
                                        onLongClick = { toggleArtist(artist.id) },
                                        selected = artist.id in selectedArtists,
                                        modifier = Modifier.width(118.dp),
                                    )
                                }
                            }
                        }
                    }

                    "continue" -> if (vm.continueItems.isNotEmpty()) {
                        item { SectionHeader(stringResource(R.string.home_continue)) }
                        item {
                            val items = vm.continueItems
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(gridSpacing.dp),
                            ) {
                                items(items, key = { it.track.id }) { resume ->
                                    ContinueCard(
                                        resume = resume,
                                        onClick = { onPlay(items.map { it.track }, items.indexOf(resume)) },
                                    )
                                }
                            }
                        }
                    }

                    "recent_played" -> if (vm.recentPlayed.isNotEmpty()) {
                        item { SectionHeader(stringResource(R.string.home_recent_played)) }
                        item {
                            Column(Modifier.padding(horizontal = 16.dp)) {
                                vm.recentPlayed.forEachIndexed { index, track ->
                                    TrackRow(
                                        track = track,
                                        onClick = { onPlay(vm.recentPlayed, index) },
                                        isDownloaded = track.id in downloadedIds,
                                        downloadProgress = downloads[track.id],
                                        onDownload = if (track.id in downloadedIds) null else { { downloadOne(track) } },
                                        onAddToQueue = { onAddToQueue(track) },
                                    )
                                }
                            }
                        }
                    }

                    "favorites" -> if (vm.favorites.isNotEmpty()) {
                        item {
                            SectionHeader(
                                stringResource(R.string.library_tab_favorites),
                                onShowAll = onOpenLibrary,
                            )
                        }
                        item {
                            Column(Modifier.padding(horizontal = 16.dp)) {
                                vm.favorites.forEachIndexed { index, track ->
                                    TrackRow(
                                        track = track,
                                        onClick = { onPlay(vm.favorites, index) },
                                        isDownloaded = track.id in downloadedIds,
                                        downloadProgress = downloads[track.id],
                                        onDownload = if (track.id in downloadedIds) null else { { downloadOne(track) } },
                                        onAddToQueue = { onAddToQueue(track) },
                                    )
                                }
                            }
                        }
                    }

                    "playlists" -> if (vm.playlists.isNotEmpty()) {
                        item {
                            SectionHeader(
                                stringResource(R.string.library_tab_playlists),
                                onShowAll = onOpenLibrary,
                            )
                        }
                        item {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(gridSpacing.dp),
                            ) {
                                items(vm.playlists.take(12), key = { it.id }) { playlist ->
                                    PlaylistCard(playlist, onClick = { onOpenPlaylist(playlist.id) })
                                }
                            }
                        }
                    }

                    "genres" -> if (vm.genres.isNotEmpty()) {
                        item {
                            SectionHeader(
                                stringResource(R.string.library_tab_genres),
                                onShowAll = onOpenLibrary,
                            )
                        }
                        item {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                items(vm.genres, key = { it }) { genre ->
                                    FilterChip(
                                        selected = false,
                                        onClick = { onOpenGenre(genre) },
                                        label = { Text(genre) },
                                    )
                                }
                            }
                        }
                    }

                    "songs" -> if (vm.songs.isNotEmpty()) {
                        item { SectionHeader(stringResource(R.string.home_random_songs)) }
                        item {
                            Column(Modifier.padding(horizontal = 16.dp)) {
                                vm.songs.shuffled().take(10).forEachIndexed { index, track ->
                                    TrackRowCompact(track) { onPlay(vm.songs.shuffled(), 0) }
                                    if (index < 9) Spacer(Modifier.height(2.dp))
                                }
                            }
                        }
                    }
                }
            }

            if (vm.recentAlbums.isEmpty() && vm.artists.isEmpty()) {
                item {
                    EmptyState(
                        title = stringResource(R.string.home_empty_title),
                        subtitle = stringResource(R.string.home_empty_body),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            }
        }

        if (selectionMode) {
            HomeSelectionBar(
                count = selectedAlbums.size + selectedArtists.size,
                onPlay = {
                    scope.launch {
                        val list = resolveSelection()
                        clearSelection()
                        if (list.isNotEmpty()) onPlay(list, 0)
                    }
                },
                onShuffle = {
                    scope.launch {
                        val list = resolveSelection().shuffled()
                        clearSelection()
                        if (list.isNotEmpty()) onPlay(list, 0)
                    }
                },
                onSelectAll = {
                    selectedAlbums.clear()
                    selectedAlbums.addAll(vm.allAlbums.map { it.id })
                    selectedArtists.clear()
                    selectedArtists.addAll(vm.artists.map { it.id })
                },
                onQueue = {
                    scope.launch {
                        val list = resolveSelection()
                        clearSelection()
                        list.forEach { onAddToQueue(it) }
                    }
                },
                onDownload = {
                    scope.launch {
                        val list = resolveSelection()
                        clearSelection()
                        enqueueTracks(list)
                    }
                },
                onCancel = { clearSelection() },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }

    if (editOpen) {
        HomeSectionsDialog(
            current = sections,
            onDismiss = { editOpen = false },
            onApply = {
                settingsStore.applyHomeSections(it)
                editOpen = false
            },
        )
    }
}

@Composable
private fun TrackRowCompact(track: Track, onClick: () -> Unit) {
    androidx.compose.material3.TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Text(
            text = track.title,
            maxLines = 1,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
private fun ContinueCard(resume: ResumeItem, onClick: () -> Unit) {
    val progress = if (resume.durationMs > 0) {
        (resume.positionMs.toFloat() / resume.durationMs).coerceIn(0f, 1f)
    } else {
        0f
    }
    Column(
        modifier = Modifier
            .width(150.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(2.dp),
    ) {
        Artwork(
            url = resume.track.imageUrl,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
            shape = RoundedCornerShape(14.dp),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = resume.track.title,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = resume.track.artistName.orEmpty(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp)),
        )
    }
}

@Composable
private fun PlaylistCard(playlist: Playlist, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(150.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(2.dp),
    ) {
        Artwork(
            url = playlist.imageUrl,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
            shape = RoundedCornerShape(14.dp),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = playlist.name,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (playlist.trackCount != null) {
            Text(
                text = stringResource(R.string.home_track_count, playlist.trackCount),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun HomeSectionsDialog(
    current: List<String>,
    onDismiss: () -> Unit,
    onApply: (List<String>) -> Unit,
) {
    val catalog = listOf(
        "recent" to stringResource(R.string.home_recently_added),
        "continue" to stringResource(R.string.home_continue),
        "recent_played" to stringResource(R.string.home_recent_played),
        "favorites" to stringResource(R.string.library_tab_favorites),
        "playlists" to stringResource(R.string.library_tab_playlists),
        "genres" to stringResource(R.string.library_tab_genres),
        "random_albums" to stringResource(R.string.home_random_albums),
        "random_artists" to stringResource(R.string.home_random_artists),
        "songs" to stringResource(R.string.home_random_songs),
    )
    // Keep the saved order, append the ones the user had hidden.
    val order = remember {
        (current + catalog.map { it.first }).distinct().toMutableList()
    }
    val enabled = remember { current.toMutableSet() }
    var tick by remember { mutableStateOf(0) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.home_edit)) },
        text = {
            Column {
                Text(
                    stringResource(R.string.home_edit_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                order.forEachIndexed { index, key ->
                    val label = catalog.firstOrNull { it.first == key }?.second ?: key
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = key in enabled,
                            onCheckedChange = { on ->
                                if (on) enabled += key else enabled -= key
                                tick++
                            },
                        )
                        Text(label, modifier = Modifier.weight(1f))
                        IconButton(
                            enabled = index > 0,
                            onClick = {
                                val moved = order.removeAt(index)
                                order.add(index - 1, moved)
                                tick++
                            },
                        ) {
                            Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = null)
                        }
                        IconButton(
                            enabled = index < order.lastIndex,
                            onClick = {
                                val moved = order.removeAt(index)
                                order.add(index + 1, moved)
                                tick++
                            },
                        ) {
                            Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = null)
                        }
                    }
                }
                // Reading tick keeps recomposition tied to the mutable state above.
                if (tick < 0) Text("")
            }
        },
        confirmButton = {
            TextButton(onClick = { onApply(order.filter { it in enabled }) }) {
                Text(stringResource(R.string.settings_edit_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.settings_edit_cancel))
            }
        },
    )
}

@Composable
private fun HomeSelectionBar(
    count: Int,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    onSelectAll: () -> Unit,
    onQueue: () -> Unit,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 6.dp,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.selection_count, count),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onSelectAll) {
                    Text(stringResource(R.string.selection_all))
                }
                IconButton(onClick = onCancel) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.selection_cancel),
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                HomeSelectionAction(Icons.Rounded.PlayArrow, stringResource(R.string.common_play), onPlay)
                HomeSelectionAction(Icons.Rounded.Shuffle, stringResource(R.string.player_shuffle), onShuffle)
                HomeSelectionAction(Icons.Rounded.Add, stringResource(R.string.action_add_queue), onQueue)
                HomeSelectionAction(Icons.Rounded.Download, stringResource(R.string.offline_download), onDownload)
            }
        }
    }
}

@Composable
private fun HomeSelectionAction(
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

/** Greeting that fits the current local hour. */
private fun timeOfDayGreeting(): Int {
    val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
    return when (hour) {
        in 5..10 -> R.string.home_greeting_morning
        in 11..17 -> R.string.home_greeting_day
        in 18..22 -> R.string.home_greeting_evening
        else -> R.string.home_greeting_night
    }
}
