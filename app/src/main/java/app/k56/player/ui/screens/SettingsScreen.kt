package app.k56.player.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Logout
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.widget.Toast
import app.k56.player.R
import app.k56.player.BuildConfig
import app.k56.player.core.model.BackendKind
import app.k56.player.core.net.NetworkTransport
import app.k56.player.data.MediaRepository
import app.k56.player.data.BackupCrypto
import app.k56.player.data.DownloadFormat
import app.k56.player.data.DownloadQuality
import app.k56.player.data.HomeGreeting
import app.k56.player.data.PlayerBackground
import app.k56.player.data.PlayerStyle
import app.k56.player.data.ScreenOrientation
import app.k56.player.data.SettingsStore
import app.k56.player.data.StorageLocation
import app.k56.player.data.library.LibraryDb
import app.k56.player.data.library.LibrarySync
import app.k56.player.data.offline.OfflineStore
import app.k56.player.data.offline.DownloadState
import app.k56.player.dsp.DspState
import kotlinx.coroutines.launch
import app.k56.player.ui.formatBytes
import app.k56.player.ui.theme.ThemeController
import app.k56.player.ui.theme.ThemeMode

@Composable
fun SettingsScreen(
    repository: MediaRepository,
    offlineStore: OfflineStore,
    settingsStore: SettingsStore,
    libraryDb: LibraryDb,
    librarySync: LibrarySync,
    dspState: DspState,
    onOpenDsp: () -> Unit = {},
    onOpenCache: () -> Unit = {},
    onOpenSyncLog: () -> Unit = {},
) {
    val context = LocalContext.current
    var batteryExempt by remember { mutableStateOf(false) }
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                val pm = context.getSystemService(android.content.Context.POWER_SERVICE)
                    as? android.os.PowerManager
                batteryExempt = pm?.isIgnoringBatteryOptimizations(context.packageName) == true
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val session by repository.sessionFlow.collectAsStateWithLifecycle()
    val offlineTracks by offlineStore.tracks.collectAsStateWithLifecycle()
    val downloadQueue by offlineStore.queue.collectAsStateWithLifecycle()
    val downloadPaused by offlineStore.paused.collectAsStateWithLifecycle()
    val queueEntries by offlineStore.entries.collectAsStateWithLifecycle()
    val downloads by offlineStore.progress.collectAsStateWithLifecycle()
    val syncState by librarySync.state.collectAsStateWithLifecycle()
    val storageLocation by settingsStore.storageLocation.collectAsStateWithLifecycle()
    val cacheLimitMb by settingsStore.cacheLimitMb.collectAsStateWithLifecycle()
    val autoplay by settingsStore.autoplay.collectAsStateWithLifecycle()
    val circularQueue by settingsStore.circularQueue.collectAsStateWithLifecycle()
    val resumeThresholdSeconds by settingsStore.resumeThresholdSeconds.collectAsStateWithLifecycle()
    val showNowPlayingFavorite by settingsStore.showNowPlayingFavorite.collectAsStateWithLifecycle()
    val showShuffleButton by settingsStore.showShuffleButton.collectAsStateWithLifecycle()
    val showRepeatButton by settingsStore.showRepeatButton.collectAsStateWithLifecycle()
    val showQueueButton by settingsStore.showQueueButton.collectAsStateWithLifecycle()
    val showDspButton by settingsStore.showDspButton.collectAsStateWithLifecycle()
    val showSleepTimerButton by settingsStore.showSleepTimerButton.collectAsStateWithLifecycle()
    val playerStyle by settingsStore.playerStyle.collectAsStateWithLifecycle()
    val coverCornerRadius by settingsStore.coverCornerRadius.collectAsStateWithLifecycle()
    val coverMarginDp by settingsStore.coverMarginDp.collectAsStateWithLifecycle()
    val playerBackground by settingsStore.playerBackground.collectAsStateWithLifecycle()
    val animatedProgress by settingsStore.animatedProgress.collectAsStateWithLifecycle()
    val fitCover by settingsStore.fitCover.collectAsStateWithLifecycle()
    val compactControls by settingsStore.compactControls.collectAsStateWithLifecycle()
    val keepScreenOn by settingsStore.keepScreenOn.collectAsStateWithLifecycle()
    val showTrackNumbers by settingsStore.showTrackNumbers.collectAsStateWithLifecycle()
    val showTrackSubtitle by settingsStore.showTrackSubtitle.collectAsStateWithLifecycle()
    val showItemCount by settingsStore.showItemCount.collectAsStateWithLifecycle()
    val screenOrientation by settingsStore.screenOrientation.collectAsStateWithLifecycle()
    val gridSpacing by settingsStore.gridSpacing.collectAsStateWithLifecycle()
    val homeGreeting by settingsStore.homeGreeting.collectAsStateWithLifecycle()
    val libraryColumns by settingsStore.libraryColumns.collectAsStateWithLifecycle()
    val wifiOnlyDownloads by settingsStore.wifiOnlyDownloads.collectAsStateWithLifecycle()
    val concurrentDownloads by settingsStore.concurrentDownloads.collectAsStateWithLifecycle()
    val autoCacheFavorites by settingsStore.autoCacheFavorites.collectAsStateWithLifecycle()
    val saveNowPlaying by settingsStore.saveNowPlaying.collectAsStateWithLifecycle()
    val ignoreArticles by settingsStore.ignoreArticles.collectAsStateWithLifecycle()
    val downloadQuality by settingsStore.downloadQuality.collectAsStateWithLifecycle()
    val downloadFormat by settingsStore.downloadFormat.collectAsStateWithLifecycle()
    val hasSdCard = remember { SettingsStore.externalRoot(context) != null }
    val forceTranscode by repository.forceTranscode.collectAsStateWithLifecycle()
    val frugalMode by settingsStore.frugalMode.collectAsStateWithLifecycle()
    val autoSync by settingsStore.autoSync.collectAsStateWithLifecycle()
    val preferExternal by repository.preferExternal.collectAsStateWithLifecycle()
    val networkStatus by repository.networkStatus.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    var exportPasswordOpen by remember { mutableStateOf(false) }
    var pendingExportPassword by remember { mutableStateOf<String?>(null) }
    var importPasswordOpen by remember { mutableStateOf(false) }
    var pendingImportText by remember { mutableStateOf<String?>(null) }

    val applyBackup: (String) -> Boolean = { text ->
        runCatching {
            val obj = org.json.JSONObject(text)
            val settingsObj = obj.optJSONObject("settings") ?: obj
            settingsStore.importJson(settingsObj.toString())
            obj.optJSONObject("dsp")?.let { dspState.importSelection(it) }
        }.isSuccess
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri != null) {
            val password = pendingExportPassword
            pendingExportPassword = null
            scope.launch {
                runCatching {
                    val full = org.json.JSONObject()
                        .put("settings", org.json.JSONObject(settingsStore.exportJson()))
                        .put("dsp", dspState.exportSelection())
                        .toString()
                    val payload = if (password.isNullOrEmpty()) full else BackupCrypto.encrypt(full, password)
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(payload.toByteArray())
                    }
                }.onSuccess {
                    Toast.makeText(context, R.string.backup_export_ok, Toast.LENGTH_SHORT).show()
                }.onFailure {
                    Toast.makeText(context, R.string.backup_export_failed, Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val text = runCatching {
                    context.contentResolver.openInputStream(uri)
                        ?.use { it.readBytes().decodeToString() }
                        .orEmpty()
                }.getOrNull()
                when {
                    text.isNullOrBlank() ->
                        Toast.makeText(context, R.string.backup_import_failed, Toast.LENGTH_LONG).show()
                    BackupCrypto.isEncrypted(text) -> {
                        pendingImportText = text
                        importPasswordOpen = true
                    }
                    else -> {
                        if (applyBackup(text)) {
                            Toast.makeText(context, R.string.backup_import_ok, Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, R.string.backup_import_failed, Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
        }
    }

    var editOpen by remember { mutableStateOf(false) }
    var editBusy by remember { mutableStateOf(false) }
    var editError by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
    ) {
        Text(stringResource(R.string.nav_settings), style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(18.dp))

        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(stringResource(R.string.settings_connection), style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
                InfoRow(stringResource(R.string.settings_type), session?.backend?.label ?: "—")
                InfoRow(stringResource(R.string.settings_server), session?.serverName ?: "—")
                InfoRow("LAN", session?.internalUrl ?: "—")
                InfoRow("Internet", session?.externalUrl ?: "—")
                InfoRow(stringResource(R.string.login_username), session?.userName ?: "—")
                InfoRow(stringResource(R.string.settings_version), session?.serverVersion ?: "—")
                InfoRow(
                    stringResource(R.string.settings_network),
                    when (networkStatus.transport) {
                        NetworkTransport.WIFI -> stringResource(R.string.net_wifi)
                        NetworkTransport.CELLULAR -> stringResource(R.string.net_cellular)
                        NetworkTransport.ETHERNET -> stringResource(R.string.net_ethernet)
                        NetworkTransport.OTHER -> stringResource(R.string.net_other)
                        NetworkTransport.OFFLINE -> stringResource(R.string.net_offline)
                    },
                )
                InfoRow(
                    stringResource(R.string.settings_route),
                    when {
                        session?.backend == BackendKind.LOCAL -> stringResource(R.string.route_local)
                        preferExternal -> stringResource(R.string.route_external_forced)
                        networkStatus.transport == NetworkTransport.CELLULAR ->
                            stringResource(R.string.route_external)
                        session?.externalUrl.isNullOrBlank() -> stringResource(R.string.login_lan_label)
                        session?.internalUrl.isNullOrBlank() -> stringResource(R.string.route_external)
                        else -> stringResource(R.string.route_lan_if_available)
                    },
                )
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = { editOpen = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Rounded.Edit, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.settings_edit_connection))
                }
            }
        }

        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 14.dp),
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(stringResource(R.string.sync_title), style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
                val counts = libraryDb.counts()
                val serverCounts = libraryDb.serverCounts()
                Text(
                    text = stringResource(
                        R.string.sync_counts,
                        counts.songs,
                        counts.albums,
                        counts.artists,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (serverCounts.songs > 0 && serverCounts.songs != counts.songs) {
                    Text(
                        text = stringResource(R.string.sync_status, counts.songs, serverCounts.songs),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                val lastSync = libraryDb.lastSync()
                if (lastSync > 0) {
                    Text(
                        text = stringResource(
                            R.string.sync_last,
                            java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
                                .format(java.util.Date(lastSync)),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (syncState.running) {
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text(
                        text = "${syncPhaseLabel(syncState.phase)}: ${syncState.loaded}/${syncState.total}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                syncState.error?.let { message ->
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = { librarySync.start(force = true) },
                    enabled = !syncState.running,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.sync_now))
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onOpenSyncLog,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.sync_log_open))
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.sync_legend),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.auto_sync_title), style = MaterialTheme.typography.titleMedium)
                        Text(
                            stringResource(R.string.auto_sync_body),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Switch(
                        checked = autoSync,
                        onCheckedChange = { settingsStore.setAutoSync(it) },
                    )
                }
            }
        }

        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 14.dp),
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    stringResource(R.string.battery_title),
                    style = MaterialTheme.typography.titleLarge,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(R.string.battery_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = { requestBatteryExemption(context) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !batteryExempt,
                ) {
                    Text(
                        stringResource(
                            if (batteryExempt) R.string.battery_done else R.string.battery_disable,
                        ),
                    )
                }
            }
        }

        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 14.dp),
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(stringResource(R.string.offline_section), style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
                InfoRow(stringResource(R.string.offline_tracks), offlineTracks.size.toString())
                InfoRow("Speicher", formatBytes(offlineTracks.sumOf { it.bytes }))

                if (queueEntries.isNotEmpty() || downloadQueue.running) {
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.download_queue_title), style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(6.dp))
                    val wifiWaiting = wifiOnlyDownloads &&
                        networkStatus.transport != NetworkTransport.WIFI &&
                        networkStatus.transport != NetworkTransport.ETHERNET
                    val pausedLabel = when {
                        downloadPaused -> stringResource(R.string.download_queue_paused)
                        wifiWaiting && downloadQueue.running -> stringResource(R.string.download_queue_waiting_wifi)
                        else -> null
                    }
                    Text(
                        buildString {
                            append(
                                stringResource(
                                    R.string.download_queue_progress,
                                    downloadQueue.done,
                                    downloadQueue.total,
                                    downloadQueue.active,
                                ),
                            )
                            if (pausedLabel != null) append(" · ").append(pausedLabel)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(6.dp))
                    queueEntries.forEach { entry ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp),
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(entry.title, maxLines = 1, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    queueStateLabel(entry.state),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (entry.state == DownloadState.ACTIVE) {
                                LinearProgressIndicator(
                                    progress = { downloads[entry.trackId] ?: 0f },
                                    modifier = Modifier
                                        .width(64.dp)
                                        .height(4.dp),
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Row {
                        TextButton(onClick = { if (downloadPaused) offlineStore.resume() else offlineStore.pause() }) {
                            Text(stringResource(if (downloadPaused) R.string.download_queue_resume else R.string.download_queue_pause))
                        }
                        if (downloadQueue.failed > 0) {
                            TextButton(onClick = { offlineStore.retryFailed() }) {
                                Text(stringResource(R.string.download_queue_retry))
                            }
                        }
                        if (downloadQueue.done > 0) {
                            TextButton(onClick = { offlineStore.clearFinished() }) {
                                Text(stringResource(R.string.download_queue_clear_finished))
                            }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(R.string.storage_location),
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = storageLocation == StorageLocation.INTERNAL,
                        onClick = { settingsStore.setStorageLocation(StorageLocation.INTERNAL) },
                        label = { Text(stringResource(R.string.storage_internal)) },
                    )
                    FilterChip(
                        selected = storageLocation == StorageLocation.EXTERNAL,
                        onClick = { if (hasSdCard) settingsStore.setStorageLocation(StorageLocation.EXTERNAL) },
                        enabled = hasSdCard,
                        label = { Text(stringResource(R.string.storage_external)) },
                    )
                }
                if (!hasSdCard) {
                    Text(
                        stringResource(R.string.storage_no_sd),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    val dir = app.k56.player.data.offline.OfflineStore
                        .directoryFor(context, storageLocation)
                    Text(
                        stringResource(
                            R.string.storage_free,
                            formatBytes(SettingsStore.freeBytes(dir)),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Spacer(Modifier.height(14.dp))
                Text(
                    stringResource(R.string.cache_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf(0, 128, 256, 512, 1024, -1).forEach { mb ->
                        FilterChip(
                            selected = cacheLimitMb == mb,
                            onClick = { settingsStore.setCacheLimitMb(mb) },
                            label = {
                                Text(
                                    when (mb) {
                                        0 -> stringResource(R.string.cache_off)
                                        -1 -> stringResource(R.string.cache_unlimited)
                                        else -> "$mb MiB"
                                    },
                                )
                            },
                        )
                    }
                }
                Text(
                    stringResource(R.string.cache_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(Modifier.height(14.dp))
                Text(
                    stringResource(R.string.download_quality_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    DownloadQuality.entries.forEach { entry ->
                        FilterChip(
                            selected = downloadQuality == entry,
                            onClick = { settingsStore.setDownloadQuality(entry) },
                            label = {
                                Text(
                                    when (entry) {
                                        DownloadQuality.ORIGINAL ->
                                            stringResource(R.string.quality_original)
                                        else -> "${entry.kbps} kbps"
                                    },
                                )
                            },
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    DownloadFormat.entries.forEach { entry ->
                        FilterChip(
                            selected = downloadFormat == entry,
                            onClick = { settingsStore.setDownloadFormat(entry) },
                            enabled = downloadQuality != DownloadQuality.ORIGINAL,
                            label = {
                                Text(
                                    when (entry) {
                                        DownloadFormat.AAC -> "AAC (m4a)"
                                        DownloadFormat.MP3 -> "MP3"
                                    },
                                )
                            },
                        )
                    }
                }
                Text(
                    text = if (downloadQuality == DownloadQuality.ORIGINAL) {
                        stringResource(R.string.download_quality_original_hint)
                    } else {
                        stringResource(R.string.download_quality_transcode_hint, downloadQuality.kbps)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.wifi_only_title), style = MaterialTheme.typography.titleMedium)
                        Text(
                            stringResource(R.string.wifi_only_body),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = wifiOnlyDownloads,
                        onCheckedChange = { settingsStore.setWifiOnlyDownloads(it) },
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.concurrent_downloads_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    (1..5).forEach { n ->
                        FilterChip(
                            selected = concurrentDownloads == n,
                            onClick = { settingsStore.setConcurrentDownloads(n) },
                            label = { Text("$n") },
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.auto_cache_favorites_title), style = MaterialTheme.typography.titleMedium)
                        Text(
                            stringResource(R.string.auto_cache_favorites_body),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = autoCacheFavorites,
                        onCheckedChange = { settingsStore.setAutoCacheFavorites(it) },
                    )
                }
                Spacer(Modifier.height(10.dp))
                OutlinedButton(onClick = onOpenCache, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.cache_manager_title))
                }
                if (offlineTracks.isEmpty()) {
                    Text(
                        stringResource(R.string.offline_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = { offlineTracks.map { it.id }.forEach(offlineStore::delete) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Rounded.DeleteOutline, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.offline_clear))
                    }
                }
            }
        }

        if (editOpen && session != null) {
            EditConnectionDialog(
                initialInternal = session?.internalUrl.orEmpty(),
                initialExternal = session?.externalUrl.orEmpty(),
                initialUser = session?.userName.orEmpty(),
                busy = editBusy,
                error = editError,
                onDismiss = {
                    if (!editBusy) {
                        editOpen = false
                        editError = null
                    }
                },
                onSave = { internal, external, user, pass ->
                    val backend = session?.backend
                    if (backend != null) {
                        editBusy = true
                        editError = null
                        scope.launch {
                            try {
                                repository.login(backend, internal, external, user, pass)
                                editOpen = false
                            } catch (e: Exception) {
                                editError = e.message ?: "Fehler"
                            } finally {
                                editBusy = false
                            }
                        }
                    }
                },
            )
        }

        Spacer(Modifier.height(14.dp))

        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.settings_prefer_external),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        stringResource(R.string.settings_prefer_external_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Switch(
                    checked = preferExternal,
                    onCheckedChange = { repository.setPreferExternal(it) },
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.settings_force_transcode),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        stringResource(R.string.settings_force_transcode_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Switch(
                    checked = forceTranscode,
                    onCheckedChange = { repository.setForceTranscode(it) },
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.frugal_mode_title),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        stringResource(R.string.frugal_mode_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Switch(
                    checked = frugalMode,
                    onCheckedChange = { settingsStore.setFrugalMode(it) },
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.autoplay_title), style = MaterialTheme.typography.titleMedium)
                        Text(
                            stringResource(R.string.autoplay_body),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = autoplay,
                        onCheckedChange = { settingsStore.setAutoplay(it) },
                    )
                }
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.circular_queue_title), style = MaterialTheme.typography.titleMedium)
                        Text(
                            stringResource(R.string.circular_queue_body),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = circularQueue,
                        onCheckedChange = { settingsStore.setCircularQueue(it) },
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(stringResource(R.string.resume_title), style = MaterialTheme.typography.titleLarge)
                Text(
                    stringResource(R.string.resume_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.save_np_title), style = MaterialTheme.typography.titleMedium)
                        Text(
                            stringResource(R.string.save_np_body),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = saveNowPlaying,
                        onCheckedChange = { settingsStore.setSaveNowPlaying(it) },
                    )
                }
                Spacer(Modifier.height(10.dp))
                Text(stringResource(R.string.resume_threshold), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf(0, 5, 15, 30, 60).forEach { seconds ->
                        FilterChip(
                            selected = resumeThresholdSeconds == seconds,
                            onClick = { settingsStore.setResumeThresholdSeconds(seconds) },
                            label = {
                                Text(
                                    if (seconds == 0) stringResource(R.string.resume_off)
                                    else stringResource(R.string.resume_seconds, seconds),
                                )
                            },
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    stringResource(R.string.display_title),
                    style = MaterialTheme.typography.titleLarge,
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.display_keep_on), style = MaterialTheme.typography.titleMedium)
                        Text(
                            stringResource(R.string.display_keep_on_body),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = keepScreenOn,
                        onCheckedChange = { settingsStore.setKeepScreenOn(it) },
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.display_numbers), style = MaterialTheme.typography.titleMedium)
                        Text(
                            stringResource(R.string.display_numbers_body),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = showTrackNumbers,
                        onCheckedChange = { settingsStore.setShowTrackNumbers(it) },
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.display_subtitle), style = MaterialTheme.typography.titleMedium)
                        Text(
                            stringResource(R.string.display_subtitle_body),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = showTrackSubtitle,
                        onCheckedChange = { settingsStore.setShowTrackSubtitle(it) },
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.display_item_count), style = MaterialTheme.typography.titleMedium)
                        Text(
                            stringResource(R.string.display_item_count_body),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = showItemCount,
                        onCheckedChange = { settingsStore.setShowItemCount(it) },
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.display_columns), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0, 2, 3, 4).forEach { value ->
                        FilterChip(
                            selected = libraryColumns == value,
                            onClick = { settingsStore.setLibraryColumns(value) },
                            label = {
                                Text(
                                    if (value == 0) stringResource(R.string.display_columns_auto)
                                    else value.toString(),
                                )
                            },
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(stringResource(R.string.orientation_title), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ScreenOrientation.entries.forEach { entry ->
                        FilterChip(
                            selected = screenOrientation == entry,
                            onClick = { settingsStore.setScreenOrientation(entry) },
                            label = { Text(stringResource(orientationLabel(entry))) },
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(stringResource(R.string.grid_spacing_title), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(4, 8, 12, 16).forEach { value ->
                        FilterChip(
                            selected = gridSpacing == value,
                            onClick = { settingsStore.setGridSpacing(value) },
                            label = { Text("$value dp") },
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    stringResource(R.string.np_options_title),
                    style = MaterialTheme.typography.titleLarge,
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.np_hide_favorite), style = MaterialTheme.typography.titleMedium)
                        Text(
                            stringResource(R.string.np_hide_favorite_body),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = showNowPlayingFavorite,
                        onCheckedChange = { settingsStore.setShowNowPlayingFavorite(it) },
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.np_fit_cover), style = MaterialTheme.typography.titleMedium)
                        Text(
                            stringResource(R.string.np_fit_cover_body),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = fitCover,
                        onCheckedChange = { settingsStore.setFitCover(it) },
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.np_compact), style = MaterialTheme.typography.titleMedium)
                        Text(
                            stringResource(R.string.np_compact_body),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = compactControls,
                        onCheckedChange = { settingsStore.setCompactControls(it) },
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(stringResource(R.string.np_buttons_title), style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
                PlayerButtonSwitch(stringResource(R.string.np_show_shuffle), showShuffleButton) { settingsStore.setShowShuffleButton(it) }
                PlayerButtonSwitch(stringResource(R.string.np_show_repeat), showRepeatButton) { settingsStore.setShowRepeatButton(it) }
                PlayerButtonSwitch(stringResource(R.string.np_show_queue), showQueueButton) { settingsStore.setShowQueueButton(it) }
                PlayerButtonSwitch(stringResource(R.string.np_show_dsp), showDspButton) { settingsStore.setShowDspButton(it) }
                PlayerButtonSwitch(stringResource(R.string.np_show_sleep), showSleepTimerButton) { settingsStore.setShowSleepTimerButton(it) }
            }
        }

        Spacer(Modifier.height(14.dp))

        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(stringResource(R.string.np_style_title), style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.np_style_label), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PlayerStyle.entries.forEach { entry ->
                        FilterChip(
                            selected = playerStyle == entry,
                            onClick = { settingsStore.setPlayerStyle(entry) },
                            label = { Text(stringResource(playerStyleLabel(entry))) },
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(stringResource(R.string.np_cover_radius), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0, 8, 16, 24, 28).forEach { value ->
                        FilterChip(
                            selected = coverCornerRadius == value,
                            onClick = { settingsStore.setCoverCornerRadius(value) },
                            label = { Text("$value dp") },
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(stringResource(R.string.np_cover_margin), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0, 8, 16, 24).forEach { value ->
                        FilterChip(
                            selected = coverMarginDp == value,
                            onClick = { settingsStore.setCoverMarginDp(value) },
                            label = { Text("$value dp") },
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(stringResource(R.string.np_background_title), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PlayerBackground.entries.forEach { entry ->
                        FilterChip(
                            selected = playerBackground == entry,
                            onClick = { settingsStore.setPlayerBackground(entry) },
                            label = { Text(stringResource(playerBackgroundLabel(entry))) },
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.np_animated_progress), style = MaterialTheme.typography.titleMedium)
                    }
                    Switch(
                        checked = animatedProgress,
                        onCheckedChange = { settingsStore.setAnimatedProgress(it) },
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.articles_title), style = MaterialTheme.typography.titleMedium)
                        Text(
                            stringResource(R.string.articles_body),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = ignoreArticles,
                        onCheckedChange = { settingsStore.setIgnoreArticles(it) },
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    stringResource(R.string.settings_theme_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ThemeMode.entries.forEach { entry ->
                        FilterChip(
                            selected = ThemeController.mode == entry,
                            onClick = { ThemeController.set(context, entry) },
                            label = { Text(stringResource(themeLabel(entry))) },
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    stringResource(R.string.settings_home_greeting),
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    HomeGreeting.entries.forEach { entry ->
                        FilterChip(
                            selected = homeGreeting == entry,
                            onClick = { settingsStore.setHomeGreeting(entry) },
                            label = { Text(stringResource(homeGreetingLabel(entry))) },
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(stringResource(R.string.dsp_title), style = MaterialTheme.typography.titleLarge)
                Text(
                    stringResource(R.string.dsp_settings_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                OutlinedButton(onClick = onOpenDsp, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.dsp_open))
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(stringResource(R.string.backup_title), style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { exportPasswordOpen = true },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.backup_export))
                    }
                    OutlinedButton(
                        onClick = { importLauncher.launch(arrayOf("application/json")) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.backup_import))
                    }
                }
            }
        }

        Spacer(Modifier.height(22.dp))

        OutlinedButton(
            onClick = { repository.logout() },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
        ) {
            Icon(Icons.Rounded.Logout, contentDescription = null)
            Spacer(Modifier.width(10.dp))
            Text(stringResource(R.string.settings_sign_out))
        }

        Spacer(Modifier.height(26.dp))

        Text(stringResource(R.string.settings_about_title), style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(R.string.settings_about_version, BuildConfig.VERSION_NAME),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(R.string.settings_about_body, BuildConfig.JELLYFIN_MIN_VERSION),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    if (exportPasswordOpen) {
        PasswordDialog(
            title = stringResource(R.string.backup_export),
            confirmLabel = stringResource(R.string.backup_export),
            onDismiss = { exportPasswordOpen = false },
            onConfirm = { password ->
                exportPasswordOpen = false
                pendingExportPassword = password
                exportLauncher.launch("k56-settings.json")
            },
        )
    }
    if (importPasswordOpen) {
        PasswordDialog(
            title = stringResource(R.string.backup_import),
            confirmLabel = stringResource(R.string.backup_import),
            onDismiss = {
                importPasswordOpen = false
                pendingImportText = null
            },
            onConfirm = { password ->
                importPasswordOpen = false
                val text = pendingImportText
                pendingImportText = null
                scope.launch {
                    val json = runCatching { BackupCrypto.decrypt(text.orEmpty(), password) }.getOrNull()
                    if (json == null || !applyBackup(json)) {
                        Toast.makeText(context, R.string.backup_import_failed, Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(context, R.string.backup_import_ok, Toast.LENGTH_SHORT).show()
                    }
                }
            },
        )
    }
}


@Composable
private fun EditConnectionDialog(
    initialInternal: String,
    initialExternal: String,
    initialUser: String,
    busy: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onSave: (String, String, String, String) -> Unit,
) {
    var internal by remember { mutableStateOf(initialInternal) }
    var external by remember { mutableStateOf(initialExternal) }
    var user by remember { mutableStateOf(initialUser) }
    var pass by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_edit_connection)) },
        text = {
            Column {
                OutlinedTextField(
                    value = internal,
                    onValueChange = { internal = it },
                    label = { Text(stringResource(R.string.login_lan_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = external,
                    onValueChange = { external = it },
                    label = { Text(stringResource(R.string.login_external_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = user,
                    onValueChange = { user = it },
                    label = { Text(stringResource(R.string.login_username)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = pass,
                    onValueChange = { pass = it },
                    label = { Text(stringResource(R.string.login_password)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (error != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(internal.trim(), external.trim(), user.trim(), pass) },
                enabled = !busy && (internal.isNotBlank() || external.isNotBlank()),
            ) {
                Text(stringResource(R.string.settings_edit_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) {
                Text(stringResource(R.string.settings_edit_cancel))
            }
        },
    )
}

private fun homeGreetingLabel(mode: HomeGreeting): Int = when (mode) {
    HomeGreeting.OFF -> R.string.home_greeting_off
    HomeGreeting.PLAIN -> R.string.home_greeting_plain
    HomeGreeting.SERVER -> R.string.home_greeting_server
    HomeGreeting.PERSONAL -> R.string.home_greeting_personal_label
}

private fun themeLabel(mode: ThemeMode): Int = when (mode) {
    ThemeMode.System -> R.string.theme_system
    ThemeMode.Light -> R.string.theme_light
    ThemeMode.Dark -> R.string.theme_dark
    ThemeMode.Matrix -> R.string.theme_matrix
}

private fun orientationLabel(mode: ScreenOrientation): Int = when (mode) {
    ScreenOrientation.AUTO -> R.string.orientation_auto
    ScreenOrientation.PORTRAIT -> R.string.orientation_portrait
    ScreenOrientation.LANDSCAPE -> R.string.orientation_landscape
}

private fun playerStyleLabel(style: PlayerStyle): Int = when (style) {
    PlayerStyle.STANDARD -> R.string.np_style_standard
    PlayerStyle.LARGE_COVER -> R.string.np_style_large
}

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

private fun playerBackgroundLabel(bg: PlayerBackground): Int = when (bg) {
    PlayerBackground.GRADIENT -> R.string.np_background_gradient
    PlayerBackground.SOLID -> R.string.np_background_solid
    PlayerBackground.ALBUM_COLOR -> R.string.np_background_album
}

@Composable
private fun PasswordDialog(
    title: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var password by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text(stringResource(R.string.backup_password)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(password) }) {
                Text(confirmLabel)
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
private fun PlayerButtonSwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun queueStateLabel(state: DownloadState): String = stringResource(
    when (state) {
        DownloadState.QUEUED -> R.string.download_state_queued
        DownloadState.ACTIVE -> R.string.download_state_active
        DownloadState.DONE -> R.string.download_state_done
        DownloadState.FAILED -> R.string.download_state_failed
    },
)

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 7.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(104.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

/** Opens the system dialog that exempts K56 from battery optimisation. */
private fun requestBatteryExemption(context: android.content.Context) {
    val powerManager = context.getSystemService(android.content.Context.POWER_SERVICE)
        as? android.os.PowerManager ?: return
    if (powerManager.isIgnoringBatteryOptimizations(context.packageName)) return
    val intent = android.content.Intent(
        android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
        android.net.Uri.parse("package:" + context.packageName),
    ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
}
