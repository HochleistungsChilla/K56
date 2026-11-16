package app.k56.player.ui.screens

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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.k56.player.R
import app.k56.player.data.SettingsStore
import app.k56.player.data.StorageLocation
import app.k56.player.data.cache.MediaCache
import app.k56.player.data.offline.OfflineStore
import app.k56.player.ui.components.ScreenHeader
import app.k56.player.ui.formatBytes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Cache manager: what the stream cache holds, what is downloaded for offline
 * use, and one-tap ways to reclaim either. Sizes are measured off the UI thread.
 */
@Composable
fun CacheScreen(
    offlineStore: OfflineStore,
    mediaCache: MediaCache,
    settingsStore: SettingsStore,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val downloads by offlineStore.tracks.collectAsStateWithLifecycle()
    val location by settingsStore.storageLocation.collectAsStateWithLifecycle()

    var streamBytes by remember { mutableLongStateOf(0L) }
    var streamEntries by remember { mutableIntStateOf(0) }
    var refresh by remember { mutableIntStateOf(0) }

    LaunchedEffect(refresh, downloads) {
        val measured = withContext(Dispatchers.IO) {
            mediaCache.sizeBytes() to mediaCache.entryCount()
        }
        streamBytes = measured.first
        streamEntries = measured.second
    }

    val downloadBytes = downloads.sumOf { it.bytes }
    val freeBytes = SettingsStore.freeBytes(
        if (location == StorageLocation.EXTERNAL) {
            SettingsStore.externalRoot(context) ?: context.filesDir
        } else {
            context.filesDir
        },
    )

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(stringResource(R.string.cache_manager_title), onBack)

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            stringResource(R.string.cache_storage_title),
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Spacer(Modifier.height(8.dp))
                        CacheRow(
                            stringResource(R.string.storage_location),
                            stringResource(
                                if (location == StorageLocation.EXTERNAL) R.string.storage_external
                                else R.string.storage_internal,
                            ),
                        )
                        CacheRow(stringResource(R.string.cache_free), formatBytes(freeBytes))
                        CacheRow(stringResource(R.string.cache_total_used), formatBytes(downloadBytes + streamBytes))
                    }
                }
            }

            item {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    stringResource(R.string.cache_stream_title),
                                    style = MaterialTheme.typography.titleLarge,
                                )
                                Text(
                                    stringResource(R.string.cache_stream_body),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            IconButton(onClick = { refresh++ }) {
                                Icon(Icons.Rounded.Refresh, contentDescription = null)
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        CacheRow(stringResource(R.string.cache_size), formatBytes(streamBytes))
                        CacheRow(stringResource(R.string.cache_entry_count), streamEntries.toString())
                        Spacer(Modifier.height(10.dp))
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    withContext(Dispatchers.IO) { mediaCache.clear() }
                                    refresh++
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = streamBytes > 0L || streamEntries > 0,
                        ) {
                            Text(stringResource(R.string.cache_clear_stream))
                        }
                    }
                }
            }

            item {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            stringResource(R.string.cache_downloads_title),
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Spacer(Modifier.height(8.dp))
                        CacheRow(stringResource(R.string.offline_tracks), downloads.size.toString())
                        CacheRow(stringResource(R.string.cache_size), formatBytes(downloadBytes))
                        if (downloads.isNotEmpty()) {
                            Spacer(Modifier.height(10.dp))
                            OutlinedButton(
                                onClick = { downloads.map { it.id }.forEach(offlineStore::delete) },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Icon(Icons.Rounded.DeleteOutline, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.offline_clear))
                            }
                        }
                    }
                }
            }

            items(downloads, key = { it.id }) { entry ->
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(start = 14.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                entry.title,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                            )
                            Text(
                                listOfNotNull(entry.artistName, entry.albumName, formatBytes(entry.bytes))
                                    .joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                        IconButton(onClick = { offlineStore.delete(entry.id) }) {
                            Icon(
                                Icons.Rounded.DeleteOutline,
                                contentDescription = stringResource(R.string.cache_delete_one),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CacheRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
