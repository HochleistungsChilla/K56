package app.k56.player.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.k56.player.R
import app.k56.player.core.model.Track
import app.k56.player.data.MediaRepository
import app.k56.player.data.offline.OfflineStore
import app.k56.player.ui.appViewModel
import app.k56.player.ui.components.EmptyState
import app.k56.player.ui.components.LoadingState
import app.k56.player.ui.components.TrackRow
import app.k56.player.ui.enqueueDownloads
import kotlinx.coroutines.launch

class PlaylistViewModel(
    private val repository: MediaRepository,
    private val playlistId: String,
) : ViewModel() {

    var name by mutableStateOf("")
        private set
    var tracks by mutableStateOf<List<Track>>(emptyList())
        private set
    var loading by mutableStateOf(true)
        private set

    init {
        load()
    }

    fun load() {
        loading = true
        viewModelScope.launch {
            name = runCatching {
                repository.playlists().firstOrNull { it.id == playlistId }?.name.orEmpty()
            }.getOrDefault("")
            tracks = runCatching { repository.playlistTracks(playlistId) }.getOrDefault(emptyList())
            loading = false
        }
    }
}

@Composable
fun PlaylistScreen(
    repository: MediaRepository,
    offlineStore: OfflineStore,
    playlistId: String,
    onBack: () -> Unit,
    onPlay: (List<Track>, Int) -> Unit,
    onPlayNext: (Track) -> Unit = {},
    onAddToQueue: (Track) -> Unit = {},
) {
    val vm = appViewModel { PlaylistViewModel(repository, playlistId) }
    val scope = rememberCoroutineScope()
    val offline by offlineStore.tracks.collectAsState()
    val downloadedIds = offline.map { it.id }.toSet()
    val downloads by offlineStore.progress.collectAsState()

    fun downloadTracks(list: List<Track>) {
        scope.launch { enqueueDownloads(list, repository, offlineStore) }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.common_back))
            }
            Text(
                text = vm.name.ifBlank { stringResource(R.string.library_tab_playlists) },
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            IconButton(
                onClick = { downloadTracks(vm.tracks) },
                enabled = vm.tracks.isNotEmpty(),
            ) {
                Icon(Icons.Rounded.Download, contentDescription = stringResource(R.string.offline_download))
            }
            IconButton(
                onClick = { if (vm.tracks.isNotEmpty()) onPlay(vm.tracks, 0) },
                enabled = vm.tracks.isNotEmpty(),
            ) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = stringResource(R.string.common_play))
            }
        }

        when {
            vm.loading -> LoadingState(Modifier.weight(1f))

            vm.tracks.isEmpty() -> EmptyState(
                title = stringResource(R.string.library_tab_playlists),
                subtitle = stringResource(R.string.playlist_empty),
                modifier = Modifier.weight(1f),
            )

            else -> LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 20.dp),
            ) {
                itemsIndexed(vm.tracks, key = { _, track -> track.id }) { index, track ->
                    TrackRow(
                        track = track,
                        onClick = { onPlay(vm.tracks, index) },
                        isDownloaded = track.id in downloadedIds,
                        downloadProgress = downloads[track.id],
                        onDownload = if (track.id in downloadedIds) null else { { downloadTracks(listOf(track)) } },
                        onPlayNext = { onPlayNext(track) },
                        onAddToQueue = { onAddToQueue(track) },
                    )
                }
            }
        }
    }
}
