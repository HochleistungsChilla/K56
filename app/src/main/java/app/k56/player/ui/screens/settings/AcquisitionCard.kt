package app.k56.player.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import app.k56.player.R
import app.k56.player.data.acquisition.AcquisitionConfig
import app.k56.player.data.acquisition.AcquisitionStore
import app.k56.player.data.acquisition.lidarr.LidarrConnection
import app.k56.player.data.acquisition.lidarr.LidarrException
import app.k56.player.data.acquisition.lidarr.LidarrService
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.launch

/**
 * Settings card for the acquisition manager (Lidarr). Self-contained: it owns
 * its [AcquisitionStore] and talks to [LidarrService] directly, so the host
 * screen only needs to place it.
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
                        scope.launch {
                            try {
                                val found = LidarrService(draft()).test()
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
                        modifier = Modifier.height(20.dp).padding(top = 2.dp),
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
        }
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
