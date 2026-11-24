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
import android.content.Context
import app.k56.player.data.offline.OfflineStore
import app.k56.player.data.offline.PlaylistCacheStore
import app.k56.player.ui.appViewModel
import app.k56.player.ui.components.EmptyState
import app.k56.player.ui.components.LoadingState
import app.k56.player.ui.components.TrackRow
import app.k56.player.ui.enqueueDownloads
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PlaylistViewModel(
    private val repository: MediaRepository,
    private val playlistId: String,
    context: Context,
) : ViewModel() {

    var name by mutableStateOf("")
        private set
    var tracks by mutableStateOf<List<Track>>(emptyList())
        private set
    var loading by mutableStateOf(true)
        private set
    var offlineCopy by mutableStateOf(false)
        private set
    private var cachedAt by mutableStateOf(0L)

    private val cache = PlaylistCacheStore(context)

    /** When the offline copy was last refreshed, or 0 when it came from nowhere. */
    fun cachedAtLabel(): String? = if (cachedAt > 0) {
        java.text.DateFormat.getDateTimeInstance(
            java.text.DateFormat.SHORT,
            java.text.DateFormat.SHORT,
        ).format(java.util.Date(cachedAt))
    } else {
        null
    }

    init {
        load()
    }

    fun load() {
        loading = true
        viewModelScope.launch {
            val loadedName = runCatching {
                repository.playlists().firstOrNull { it.id == playlistId }?.name.orEmpty()
            }.getOrNull()
            val loadedTracks = runCatching { repository.playlistTracks(playlistId) }.getOrNull()

            if (loadedTracks != null) {
                name = loadedName.orEmpty()
                tracks = loadedTracks
                offlineCopy = false
                cachedAt = 0L
                if (loadedTracks.isNotEmpty()) {
                    withContext(Dispatchers.IO) {
                        cache.save(playlistId, name, loadedTracks)
                    }
                }
            } else {
                // Server unreachable: show the last known contents instead of an
                // empty screen, and say that this is an offline copy.
                val cached = withContext(Dispatchers.IO) { cache.load(playlistId) }
                if (cached != null) {
                    name = cached.name.ifBlank { name }
                    tracks = cached.tracks
                    cachedAt = cached.savedAt
                    offlineCopy = true
                } else {
                    name = loadedName.orEmpty()
                    tracks = emptyList()
                    offlineCopy = false
                }
            }
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
    val context = androidx.compose.ui.platform.LocalContext.current
    val vm = appViewModel { PlaylistViewModel(repository, playlistId, context.applicationContext) }
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

        if (vm.offlineCopy && !vm.loading) {
            Text(
                text = vm.cachedAtLabel()
                    ?.let { stringResource(R.string.library_offline_copy, it) }
                    ?: stringResource(R.string.offline_copy),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
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
