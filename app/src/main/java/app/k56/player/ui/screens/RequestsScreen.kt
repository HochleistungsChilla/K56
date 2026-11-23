package app.k56.player.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.k56.player.R
import app.k56.player.data.acquisition.AcquisitionStore
import app.k56.player.data.acquisition.lidarr.LidarrAlbumDto
import app.k56.player.data.acquisition.lidarr.LidarrException
import app.k56.player.data.acquisition.lidarr.LidarrHistoryRecordDto
import app.k56.player.data.acquisition.lidarr.LidarrQueueRecordDto
import app.k56.player.data.acquisition.lidarr.LidarrService
import app.k56.player.data.library.LibraryDb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private enum class RequestsTab(val label: Int) {
    WANTED(R.string.requests_wanted),
    QUEUE(R.string.requests_queue),
    HISTORY(R.string.requests_history),
}

/**
 * A2: what Lidarr is still missing, what it is downloading, and what it
 * finished. Read-only — nothing here changes the server.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RequestsScreen(
    onBack: () -> Unit,
    onLibraryChanged: () -> Unit = {},
) {
    val context = LocalContext.current
    val store = remember { AcquisitionStore(context.applicationContext) }
    val config by store.config.collectAsStateWithLifecycle()
    var tab by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(false) }
    var refreshing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var wanted by remember { mutableStateOf<List<LidarrAlbumDto>>(emptyList()) }
    var queue by remember { mutableStateOf<List<LidarrQueueRecordDto>>(emptyList()) }
    var history by remember { mutableStateOf<List<LidarrHistoryRecordDto>>(emptyList()) }
    var reloadKey by remember { mutableIntStateOf(0) }
    var importedCount by remember { mutableIntStateOf(0) }
    // C1a: show only the gaps of artists that are already in the local library.
    var onlyMine by remember { mutableStateOf(true) }
    var myArtists by remember { mutableStateOf<Set<String>?>(null) }
    val libraryDb = remember { LibraryDb.get(context.applicationContext) }

    LaunchedEffect(Unit) {
        myArtists = withContext(Dispatchers.IO) {
            runCatching {
                libraryDb.allArtists().mapNotNull { it.name.trim().lowercase().ifBlank { null } }.toSet()
            }.getOrDefault(emptySet())
        }
    }

    val shownWanted = remember(wanted, onlyMine, myArtists) {
        val names = myArtists
        if (!onlyMine || names == null) wanted
        else wanted.filter { album ->
            val artist = album.artist?.artistName?.trim()?.lowercase() ?: return@filter false
            artist in names
        }
    }

    LaunchedEffect(tab, reloadKey) {
        loading = true
        refreshing = true
        error = null
        try {
            if (!config.isComplete) {
                error = context.getString(R.string.requests_not_configured)
                return@LaunchedEffect
            }
            val service = LidarrService(config)
            when (RequestsTab.entries[tab]) {
                RequestsTab.WANTED -> wanted = service.wanted().records
                RequestsTab.QUEUE -> queue = service.queue().records
                RequestsTab.HISTORY -> {
                    val records = service.history().records
                    history = records
                    val lastSeen = store.lastHistoryId()
                    if (lastSeen == 0) {
                        // First look at this server: remember where we are, announce nothing.
                        store.setLastHistoryId(records.maxOfOrNull { it.id ?: 0 } ?: 0)
                        importedCount = 0
                    } else {
                        importedCount = records.count { record ->
                            (record.id ?: 0) > lastSeen &&
                                record.eventType?.contains("imported", ignoreCase = true) == true
                        }
                    }
                }
            }
        } catch (e: LidarrException) {
            error = e.message
        } catch (e: IllegalArgumentException) {
            error = e.message
        } finally {
            loading = false
            refreshing = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.requests_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.acquisition_close),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
        ) {
            TabRow(selectedTabIndex = tab) {
                RequestsTab.entries.forEachIndexed { index, entry ->
                    Tab(
                        selected = tab == index,
                        onClick = { tab = index },
                        text = { Text(stringResource(entry.label)) },
                    )
                }
            }

            error?.let {
                Spacer(Modifier.height(12.dp))
                Text(
                    it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            if (importedCount > 0) {
                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(R.string.requests_imported, importedCount),
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedButton(
                    onClick = {
                        val newest = history.maxOfOrNull { it.id ?: 0 } ?: 0
                        store.setLastHistoryId(newest)
                        importedCount = 0
                        onLibraryChanged()
                    },
                ) {
                    Text(stringResource(R.string.requests_update_library))
                }
            }

            if (RequestsTab.entries[tab] == RequestsTab.WANTED && wanted.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FilterChip(
                        selected = onlyMine,
                        onClick = { onlyMine = !onlyMine },
                        label = { Text(stringResource(R.string.requests_only_my_artists)) },
                    )
                    Spacer(Modifier.size(8.dp))
                    Text(
                        "${shownWanted.size} / ${wanted.size}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            val empty = when (RequestsTab.entries[tab]) {
                RequestsTab.WANTED -> shownWanted.isEmpty()
                RequestsTab.QUEUE -> queue.isEmpty()
                RequestsTab.HISTORY -> history.isEmpty()
            }

            Spacer(Modifier.height(8.dp))
            PullToRefreshBox(
                isRefreshing = refreshing,
                onRefresh = { reloadKey++ },
                modifier = Modifier.weight(1f),
            ) {
                if (!loading && error == null && empty) {
                    Box(Modifier.fillMaxSize()) {
                        Text(
                            stringResource(R.string.requests_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(24.dp),
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        when (RequestsTab.entries[tab]) {
                            RequestsTab.WANTED -> items(shownWanted, key = { it.id ?: -1 }) { album ->
                                WantedRow(album)
                            }
                            RequestsTab.QUEUE -> items(queue, key = { it.id ?: -1 }) { record ->
                                QueueRow(record)
                            }
                            RequestsTab.HISTORY -> items(history, key = { it.id ?: -1 }) { record ->
                                HistoryRow(record)
                            }
                        }
                        item { Spacer(Modifier.height(12.dp)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun WantedRow(album: LidarrAlbumDto) {
    val have = album.statistics?.trackFileCount ?: 0
    val total = album.statistics?.totalTrackCount ?: 0
    Column(Modifier.fillMaxWidth()) {
        Text(
            buildString {
                album.artist?.artistName?.takeIf { it.isNotBlank() }?.let { append(it).append(" — ") }
                append(album.title.orEmpty())
            },
            style = MaterialTheme.typography.titleSmall,
        )
        Text(
            buildString {
                append(stringResource(R.string.requests_status_missing))
                album.releaseDate?.take(4)?.let { append(" · ").append(it) }
                if (total > 0) append(" · ").append("$have/$total")
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun QueueRow(record: LidarrQueueRecordDto) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            buildString {
                val artist = record.artist?.artistName ?: record.album?.artist?.artistName
                artist?.takeIf { it.isNotBlank() }?.let { append(it).append(" — ") }
                append(record.album?.title ?: record.title.orEmpty())
            },
            style = MaterialTheme.typography.titleSmall,
        )
        Text(
            buildString {
                record.trackedDownloadState?.let { append(it.lowercase()) }
                record.status?.let { if (isNotEmpty()) append(" · "); append(it.lowercase()) }
                record.timeleft?.let { append(" · ").append(it) }
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        record.progress?.let { progress ->
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp),
            )
        }
        record.errorMessage?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun HistoryRow(record: LidarrHistoryRecordDto) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            buildString {
                val artist = record.artist?.artistName ?: record.album?.artist?.artistName
                artist?.takeIf { it.isNotBlank() }?.let { append(it).append(" — ") }
                append(record.album?.title ?: record.sourceTitle.orEmpty())
            },
            style = MaterialTheme.typography.titleSmall,
        )
        Text(
            buildString {
                record.eventType?.let { append(it.lowercase()) }
                record.date?.let { if (isNotEmpty()) append(" · "); append(it.take(16).replace('T', ' ')) }
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
