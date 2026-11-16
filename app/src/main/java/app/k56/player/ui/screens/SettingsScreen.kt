package app.k56.player.ui.screens

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.k56.player.R
import app.k56.player.BuildConfig
import app.k56.player.core.model.BackendKind
import app.k56.player.core.net.NetworkTransport
import app.k56.player.data.MediaRepository
import app.k56.player.data.DownloadFormat
import app.k56.player.data.DownloadQuality
import app.k56.player.data.SettingsStore
import app.k56.player.data.StorageLocation
import app.k56.player.data.offline.OfflineStore
import kotlinx.coroutines.launch
import app.k56.player.ui.formatBytes
import app.k56.player.ui.theme.ThemeController
import app.k56.player.ui.theme.ThemeMode

@Composable
fun SettingsScreen(
    repository: MediaRepository,
    offlineStore: OfflineStore,
    settingsStore: SettingsStore,
    onOpenDsp: () -> Unit = {},
    onOpenCache: () -> Unit = {},
) {
    val context = LocalContext.current
    val session by repository.sessionFlow.collectAsStateWithLifecycle()
    val offlineTracks by offlineStore.tracks.collectAsStateWithLifecycle()
    val storageLocation by settingsStore.storageLocation.collectAsStateWithLifecycle()
    val cacheLimitMb by settingsStore.cacheLimitMb.collectAsStateWithLifecycle()
    val autoplay by settingsStore.autoplay.collectAsStateWithLifecycle()
    val resumeThresholdSeconds by settingsStore.resumeThresholdSeconds.collectAsStateWithLifecycle()
    val keepScreenOn by settingsStore.keepScreenOn.collectAsStateWithLifecycle()
    val showTrackNumbers by settingsStore.showTrackNumbers.collectAsStateWithLifecycle()
    val libraryColumns by settingsStore.libraryColumns.collectAsStateWithLifecycle()
    val wifiOnlyDownloads by settingsStore.wifiOnlyDownloads.collectAsStateWithLifecycle()
    val ignoreArticles by settingsStore.ignoreArticles.collectAsStateWithLifecycle()
    val downloadQuality by settingsStore.downloadQuality.collectAsStateWithLifecycle()
    val downloadFormat by settingsStore.downloadFormat.collectAsStateWithLifecycle()
    val hasSdCard = remember { SettingsStore.externalRoot(context) != null }
    val forceTranscode by repository.forceTranscode.collectAsStateWithLifecycle()
    val preferExternal by repository.preferExternal.collectAsStateWithLifecycle()
    val networkStatus by repository.networkStatus.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
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
                Text(stringResource(R.string.offline_section), style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
                InfoRow(stringResource(R.string.offline_tracks), offlineTracks.size.toString())
                InfoRow("Speicher", formatBytes(offlineTracks.sumOf { it.bytes }))

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

private fun themeLabel(mode: ThemeMode): Int = when (mode) {
    ThemeMode.System -> R.string.theme_system
    ThemeMode.Light -> R.string.theme_light
    ThemeMode.Dark -> R.string.theme_dark
    ThemeMode.Matrix -> R.string.theme_matrix
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(96.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
    }
}
