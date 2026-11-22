package app.k56.player.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import app.k56.player.R
import app.k56.player.data.acquisition.clients.DownloadClientConfig
import app.k56.player.data.acquisition.clients.DownloadClientException
import app.k56.player.data.acquisition.clients.DownloadClientKind
import app.k56.player.data.acquisition.clients.DownloadClientStore
import kotlinx.coroutines.launch

/**
 * B4: connection data for the download client plus a hand-over field, so a
 * magnet/NZB/URL can be pushed without leaving the app. Never deletes.
 */
@Composable
fun DownloadClientCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val store = remember { DownloadClientStore(context.applicationContext) }
    val scope = rememberCoroutineScope()
    val saved by store.config.collectAsState(initial = store.config.value)

    var kind by remember(saved) { mutableStateOf(saved.kind) }
    var url by remember(saved) { mutableStateOf(saved.baseUrl) }
    var user by remember(saved) { mutableStateOf(saved.username) }
    var passwd by remember(saved) { mutableStateOf(saved.passwd) }
    var key by remember(saved) { mutableStateOf(saved.key) }
    var category by remember(saved) { mutableStateOf(saved.category) }
    var link by remember { mutableStateOf("") }

    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }

    fun draft() = DownloadClientConfig(
        kind = kind,
        baseUrl = url.trim(),
        username = user.trim(),
        passwd = passwd,
        key = key,
        category = category.trim(),
    )

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(20.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                stringResource(R.string.download_client_title),
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                stringResource(R.string.download_client_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                DownloadClientKind.entries.forEach { entry ->
                    FilterChip(
                        selected = kind == entry,
                        onClick = {
                            kind = entry
                            message = null
                        },
                        label = { Text(kindLabel(entry)) },
                    )
                }
            }

            if (kind != DownloadClientKind.NONE) {
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text(stringResource(R.string.download_client_url)) },
                    placeholder = { Text("http://192.168.0.10:8080") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth(),
                )

                when (kind) {
                    DownloadClientKind.SABNZBD -> {
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = key,
                            onValueChange = { key = it },
                            label = { Text(stringResource(R.string.download_client_api_key)) },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    DownloadClientKind.QBITTORRENT -> {
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = user,
                            onValueChange = { user = it },
                            label = { Text(stringResource(R.string.download_client_user)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = passwd,
                            onValueChange = { passwd = it },
                            label = { Text(stringResource(R.string.download_client_password)) },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    DownloadClientKind.NONE -> Unit
                }

                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = category,
                    onValueChange = { category = it },
                    label = { Text(stringResource(R.string.download_client_category)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        enabled = !busy && draft().isComplete,
                        onClick = {
                            busy = true
                            message = null
                            val config = draft()
                            scope.launch {
                                try {
                                    val client = config.createClient()
                                        ?: throw DownloadClientException("No client configured")
                                    val version = client.test()
                                    isError = false
                                    message = context.getString(
                                        R.string.download_client_connected,
                                        client.label,
                                        version,
                                    )
                                } catch (e: DownloadClientException) {
                                    isError = true
                                    message = e.message
                                } finally {
                                    busy = false
                                }
                            }
                        },
                    ) {
                        Text(stringResource(R.string.download_client_test))
                    }
                    Button(
                        enabled = !busy,
                        onClick = {
                            store.save(draft())
                            isError = false
                            message = context.getString(R.string.download_client_saved)
                        },
                    ) {
                        Text(stringResource(R.string.download_client_save))
                    }
                    if (busy) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    }
                }

                Spacer(Modifier.height(14.dp))
                Text(
                    stringResource(R.string.download_client_hand_over),
                    style = MaterialTheme.typography.titleSmall,
                )
                Spacer(Modifier.height(6.dp))
                Row {
                    OutlinedTextField(
                        value = link,
                        onValueChange = { link = it },
                        placeholder = { Text(stringResource(R.string.download_client_link_hint)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    )
                    Spacer(Modifier.size(8.dp))
                    Button(
                        enabled = !busy && link.isNotBlank() && saved.isComplete,
                        onClick = {
                            busy = true
                            message = null
                            val config = saved
                            scope.launch {
                                try {
                                    val client = config.createClient()
                                        ?: throw DownloadClientException("No client configured")
                                    val ok = client.add(link.trim(), config.category.ifBlank { null })
                                    isError = !ok
                                    message = context.getString(
                                        if (ok) R.string.download_client_sent
                                        else R.string.download_client_rejected,
                                    )
                                    if (ok) link = ""
                                } catch (e: DownloadClientException) {
                                    isError = true
                                    message = e.message
                                } finally {
                                    busy = false
                                }
                            }
                        },
                    ) {
                        Text(stringResource(R.string.download_client_send))
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
            }
        }
    }
}

@Composable
private fun kindLabel(kind: DownloadClientKind): String = when (kind) {
    DownloadClientKind.NONE -> stringResource(R.string.download_client_none)
    DownloadClientKind.SABNZBD -> "SABnzbd"
    DownloadClientKind.QBITTORRENT -> "qBittorrent"
}
