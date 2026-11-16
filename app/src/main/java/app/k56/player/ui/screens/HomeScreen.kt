package app.k56.player.ui.screens

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.k56.player.R
import app.k56.player.core.model.Album
import app.k56.player.core.model.Artist
import app.k56.player.core.model.Track
import app.k56.player.data.MediaRepository
import app.k56.player.data.SettingsStore
import app.k56.player.ui.appViewModel
import app.k56.player.ui.components.ArtistCard
import app.k56.player.ui.components.EmptyState
import app.k56.player.ui.components.ErrorState
import app.k56.player.ui.components.HorizontalAlbumRow
import app.k56.player.ui.components.LoadingState
import app.k56.player.ui.components.SectionHeader
import app.k56.player.ui.userMessage
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

class HomeViewModel(
    private val repository: MediaRepository,
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
    var loading by mutableStateOf(true)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    val serverName: String =
        repository.sessionFlow.value?.serverName ?: appContext.getString(R.string.settings_server)
    val serverVersion: String = repository.sessionFlow.value?.serverVersion.orEmpty()
    val backendLabel: String =
        repository.sessionFlow.value?.backend?.label ?: appContext.getString(R.string.settings_server)

    init {
        load()
    }

    fun load() {
        loading = true
        error = null
        viewModelScope.launch {
            try {
                coroutineScope {
                    val libraryId = repository.defaultLibraryId()
                    val recent = async { repository.recentAlbums(limit = 24) }
                    val albums = async { repository.albums(libraryId, limit = 300) }
                    val artistJob = async { repository.artists(libraryId, limit = 40) }
                    val songsJob = async { repository.songs(libraryId, limit = 600) }
                    recentAlbums = recent.await()
                    allAlbums = albums.await()
                    artists = artistJob.await()
                    songs = songsJob.await()
                }
            } catch (e: Exception) {
                error = e.userMessage(appContext)
            } finally {
                loading = false
            }
        }
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

@Composable
fun HomeScreen(
    repository: MediaRepository,
    settingsStore: SettingsStore,
    onOpenAlbum: (String) -> Unit,
    onOpenArtist: (String) -> Unit,
    onPlay: (List<Track>, Int) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val vm = appViewModel { HomeViewModel(repository, context.applicationContext) }
    val sections by settingsStore.homeSections.collectAsStateWithLifecycle()
    var editOpen by remember { mutableStateOf(false) }

    when {
        vm.loading -> LoadingState()

        vm.error != null -> ErrorState(vm.error!!, vm::load)

        else -> LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 20.dp),
        ) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 8.dp, top = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.home_greeting),
                            style = MaterialTheme.typography.headlineMedium,
                        )
                        Text(
                            "${vm.serverName} · ${vm.backendLabel} ${vm.serverVersion}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
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
                        item { HorizontalAlbumRow(vm.recentAlbums, onOpenAlbum) }
                    }

                    "random_albums" -> if (vm.allAlbums.isNotEmpty()) {
                        item { SectionHeader(stringResource(R.string.home_random_albums)) }
                        item { HorizontalAlbumRow(vm.allAlbums.shuffled().take(24), onOpenAlbum) }
                    }

                    "random_artists" -> if (vm.artists.isNotEmpty()) {
                        item { SectionHeader(stringResource(R.string.home_random_artists)) }
                        item {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                items(vm.artists.shuffled().take(20), key = { it.id }) { artist ->
                                    ArtistCard(
                                        artist = artist,
                                        onClick = { onOpenArtist(artist.id) },
                                        modifier = Modifier.width(118.dp),
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
private fun HomeSectionsDialog(
    current: List<String>,
    onDismiss: () -> Unit,
    onApply: (List<String>) -> Unit,
) {
    val catalog = listOf(
        "recent" to stringResource(R.string.home_recently_added),
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
