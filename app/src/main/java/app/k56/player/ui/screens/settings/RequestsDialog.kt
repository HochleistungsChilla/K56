package app.k56.player.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.k56.player.R
import app.k56.player.data.acquisition.AcquisitionConfig
import app.k56.player.data.acquisition.AcquisitionStore
import app.k56.player.data.acquisition.lidarr.LidarrAlbumDto
import app.k56.player.data.acquisition.lidarr.LidarrException
import app.k56.player.data.acquisition.lidarr.LidarrHistoryRecordDto
import app.k56.player.data.acquisition.lidarr.LidarrQueueRecordDto
import app.k56.player.data.acquisition.lidarr.LidarrService
import app.k56.player.data.library.LibraryDb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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
@Composable
fun RequestsDialog(
    config: AcquisitionConfig,
    onDismiss: () -> Unit,
    onLibraryChanged: () -> Unit = {},
) {
    val context = LocalContext.current
    val store = remember { AcquisitionStore(context.applicationContext) }
    val scope = rememberCoroutineScope()
    var tab by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(false) }
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
        error = null
        val service = LidarrService(config)
        try {
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
        } finally {
            loading = false
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.acquisition_requests),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f),
                    )
                    if (loading) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        OutlinedButton(onClick = { reloadKey++ }) {
                            Text(stringResource(R.string.requests_refresh))
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
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
                    Spacer(Modifier.height(10.dp))
                    Text(it, color = MaterialTheme.colorScheme.error)
                }

                if (importedCount > 0) {
                    Spacer(Modifier.height(10.dp))
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
                            // TODO(i18n): move to strings.xml once the UI session has committed its strings.
                            label = { Text("Only my artists") },
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
                if (!loading && error == null && empty) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        stringResource(R.string.requests_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Spacer(Modifier.height(8.dp))
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
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
                }

                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.acquisition_close))
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
        Text(album.title.orEmpty(), style = MaterialTheme.typography.titleSmall)
        Text(
            buildString {
                append(album.artist?.artistName.orEmpty())
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
            record.album?.title ?: record.title.orEmpty(),
            style = MaterialTheme.typography.titleSmall,
        )
        Text(
            buildString {
                append(record.artist?.artistName ?: record.album?.artist?.artistName.orEmpty())
                record.trackedDownloadState?.let { append(" · ").append(it.lowercase()) }
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
            record.album?.title ?: record.sourceTitle.orEmpty(),
            style = MaterialTheme.typography.titleSmall,
        )
        Text(
            buildString {
                append(record.artist?.artistName ?: record.album?.artist?.artistName.orEmpty())
                record.eventType?.let { append(" · ").append(it.lowercase()) }
                record.date?.let { append(" · ").append(it.take(16).replace('T', ' ')) }
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
