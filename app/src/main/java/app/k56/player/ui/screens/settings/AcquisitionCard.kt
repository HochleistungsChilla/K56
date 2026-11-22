package app.k56.player.ui.screens.settings

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.k56.player.R
import app.k56.player.data.acquisition.AcquisitionConfig
import app.k56.player.data.acquisition.AcquisitionStore
import app.k56.player.data.acquisition.lidarr.AlbumCandidate
import app.k56.player.data.acquisition.lidarr.LidarrConnection
import app.k56.player.data.acquisition.lidarr.LidarrException
import app.k56.player.data.acquisition.lidarr.LidarrService
import app.k56.player.data.acquisition.lidarr.RequestOutcome
import kotlinx.coroutines.launch

/**
 * Settings card for the acquisition manager (Lidarr). Self-contained: it owns
 * its [AcquisitionStore], talks to [LidarrService] directly, and carries the
 * search/request dialog, so the host screen only needs to place it.
 */
@Composable
fun AcquisitionCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val store = remember { AcquisitionStore(context.applicationContext) }
    val scope = rememberCoroutineScope()
    val saved by store.config.collectAsState(initial = store.config.value)

    var url by remember(saved) { mutableStateOf(saved.baseUrl) }
    var apiKey by remember(saved) { mutableStateOf(saved.apiKey) }
    var enabled by remember(saved) { mutableStateOf(saved.enabled) }
    var qualityProfileId by remember(saved) { mutableStateOf(saved.qualityProfileId) }
    var metadataProfileId by remember(saved) { mutableStateOf(saved.metadataProfileId) }
    var rootFolder by remember(saved) { mutableStateOf(saved.rootFolderPath) }

    var testing by remember { mutableStateOf(false) }
    var connection by remember { mutableStateOf<LidarrConnection?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }
    var searchOpen by remember { mutableStateOf(false) }
    var requestsOpen by remember { mutableStateOf(false) }

    fun draft() = AcquisitionConfig(
        enabled = enabled,
        baseUrl = url.trim(),
        apiKey = apiKey.trim(),
        qualityProfileId = qualityProfileId,
        metadataProfileId = metadataProfileId,
        rootFolderPath = rootFolder,
    )

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(20.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.acquisition_title),
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        stringResource(R.string.acquisition_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = enabled, onCheckedChange = { enabled = it })
            }

            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text(stringResource(R.string.acquisition_url)) },
                placeholder = { Text("http://192.168.0.10:8686") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it },
                label = { Text(stringResource(R.string.acquisition_api_key)) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    enabled = !testing && url.isNotBlank() && apiKey.isNotBlank(),
                    onClick = {
                        testing = true
                        message = null
                        val config = draft()
                        scope.launch {
                            try {
                                val found = LidarrService(config).test()
                                connection = found
                                if (qualityProfileId == 0) {
                                    qualityProfileId = found.qualityProfiles.firstOrNull()?.id ?: 0
                                }
                                if (metadataProfileId == 0) {
                                    metadataProfileId = found.metadataProfiles.firstOrNull()?.id ?: 0
                                }
                                if (rootFolder.isBlank()) {
                                    rootFolder = found.rootFolders.firstOrNull()?.path.orEmpty()
                                }
                                isError = false
                                message = context.getString(
                                    R.string.acquisition_connected,
                                    found.version ?: "?",
                                )
                            } catch (e: LidarrException) {
                                connection = null
                                isError = true
                                message = e.message
                            } finally {
                                testing = false
                            }
                        }
                    },
                ) {
                    Text(stringResource(R.string.acquisition_test))
                }
                if (testing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp).padding(top = 2.dp),
                        strokeWidth = 2.dp,
                    )
                }
            }

            val found = connection
            if (found != null) {
                if (found.qualityProfiles.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    ChipRow(
                        label = stringResource(R.string.acquisition_quality_profile),
                        items = found.qualityProfiles.map { it.id to (it.name ?: "#${it.id}") },
                        selected = qualityProfileId,
                        onSelect = { qualityProfileId = it },
                    )
                }
                if (found.metadataProfiles.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    ChipRow(
                        label = stringResource(R.string.acquisition_metadata_profile),
                        items = found.metadataProfiles.map { it.id to (it.name ?: "#${it.id}") },
                        selected = metadataProfileId,
                        onSelect = { metadataProfileId = it },
                    )
                }
                if (found.rootFolders.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    ChipRow(
                        label = stringResource(R.string.acquisition_root_folder),
                        items = found.rootFolders.mapIndexed { index, folder ->
                            (index + 1) to folder.path.orEmpty()
                        },
                        selected = found.rootFolders.indexOfFirst { it.path == rootFolder } + 1,
                        onSelect = { index ->
                            rootFolder = found.rootFolders.getOrNull(index - 1)?.path.orEmpty()
                        },
                    )
                }
            }

            message?.let {
                Spacer(Modifier.height(8.dp))
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isError) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    enabled = url.isNotBlank() && apiKey.isNotBlank(),
                    onClick = {
                        store.save(draft())
                        isError = false
                        message = context.getString(R.string.acquisition_saved)
                    },
                ) {
                    Text(stringResource(R.string.acquisition_save))
                }
                OutlinedButton(
                    enabled = url.isNotBlank() && apiKey.isNotBlank(),
                    onClick = { searchOpen = true },
                ) {
                    Text(stringResource(R.string.acquisition_search))
                }
                OutlinedButton(
                    enabled = url.isNotBlank() && apiKey.isNotBlank(),
                    onClick = { requestsOpen = true },
                ) {
                    Text(stringResource(R.string.acquisition_requests))
                }
            }
        }
    }

    if (searchOpen) {
        SearchDialog(
            config = draft(),
            onDismiss = { searchOpen = false },
        )
    }

    if (requestsOpen) {
        RequestsDialog(
            config = draft(),
            onDismiss = { requestsOpen = false },
        )
    }
}

@Composable
private fun SearchDialog(
    config: AcquisitionConfig,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    var results by remember { mutableStateOf<List<AlbumCandidate>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirm by remember { mutableStateOf<AlbumCandidate?>(null) }
    var sending by remember { mutableStateOf(false) }
    var outcome by remember { mutableStateOf<RequestOutcome?>(null) }

    fun search() {
        val term = query.trim()
        if (term.isBlank()) return
        searching = true
        error = null
        outcome = null
        scope.launch {
            try {
                results = LidarrService(config).lookupAlbums(term)
            } catch (e: LidarrException) {
                error = e.message
                results = emptyList()
            } finally {
                searching = false
            }
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
                Text(
                    stringResource(R.string.acquisition_search_title),
                    style = MaterialTheme.typography.titleLarge,
                )
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text(stringResource(R.string.acquisition_search_hint)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    )
                    Spacer(Modifier.size(8.dp))
                    Button(onClick = { search() }, enabled = !searching && query.isNotBlank()) {
                        Text(stringResource(R.string.acquisition_search_action))
                    }
                }

                if (searching) {
                    Spacer(Modifier.height(12.dp))
                    CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                }
                error?.let {
                    Spacer(Modifier.height(10.dp))
                    Text(it, color = MaterialTheme.colorScheme.error)
                }
                outcome?.let {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        stringResource(R.string.acquisition_request_sent, it.albumTitle),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (it.artistAdded) {
                        Text(
                            stringResource(R.string.acquisition_artist_added),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (!searching && results.isEmpty() && error == null && outcome == null) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        stringResource(R.string.acquisition_search_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Spacer(Modifier.height(8.dp))
                LazyColumn(Modifier.weight(1f)) {
                    items(results, key = { it.foreignAlbumId }) { candidate ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable(enabled = !sending) { confirm = candidate },
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Text(
                                    candidate.title,
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                Text(
                                    buildString {
                                        append(candidate.artistName)
                                        candidate.year?.let { append(" · ").append(it) }
                                        candidate.trackCount?.let { append(" · ").append(it).append(" tracks") }
                                        if (candidate.artistInLidarr) append(" · in Lidarr")
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
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

    confirm?.let { candidate ->
        AlertDialog(
            onDismissRequest = { if (!sending) confirm = null },
            title = { Text(stringResource(R.string.acquisition_confirm_title)) },
            text = {
                Column {
                    Text(candidate.title, style = MaterialTheme.typography.titleMedium)
                    Text(
                        candidate.artistName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    candidate.year?.let {
                        Text(
                            it.toString(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !sending,
                    onClick = {
                        sending = true
                        error = null
                        scope.launch {
                            try {
                                outcome = LidarrService(config).request(candidate)
                                // A requested album no longer needs to be offered.
                                results = results.filterNot { it.foreignAlbumId == candidate.foreignAlbumId }
                            } catch (e: LidarrException) {
                                error = e.message
                            } finally {
                                sending = false
                                confirm = null
                            }
                        }
                    },
                ) {
                    Text(stringResource(if (sending) R.string.acquisition_sending else R.string.acquisition_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirm = null }, enabled = !sending) {
                    Text(stringResource(R.string.acquisition_cancel))
                }
            },
        )
    }
}

@Composable
private fun ChipRow(
    label: String,
    items: List<Pair<Int, String>>,
    selected: Int,
    onSelect: (Int) -> Unit,
) {
    Column {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items.forEach { (id, name) ->
                FilterChip(
                    selected = selected == id,
                    onClick = { onSelect(id) },
                    label = { Text(name) },
                )
            }
        }
    }
}
