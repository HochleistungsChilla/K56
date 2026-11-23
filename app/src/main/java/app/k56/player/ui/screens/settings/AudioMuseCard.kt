package app.k56.player.ui.screens.settings

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import app.k56.player.data.audiomuse.AudioMuseApi
import app.k56.player.data.audiomuse.AudioMuseConfig
import app.k56.player.data.audiomuse.AudioMuseException
import app.k56.player.data.audiomuse.AudioMuseStats
import app.k56.player.data.audiomuse.AudioMuseStore
import app.k56.player.data.audiomuse.AudioMuseTrack
import kotlinx.coroutines.launch
import java.text.NumberFormat

/**
 * Connection data for an AudioMuse-AI instance plus a throwaway sonic search,
 * so the link can be verified before the library screens use it. Read-only:
 * every call is a search or a stats read.
 */
@Composable
fun AudioMuseCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val store = remember { AudioMuseStore(context.applicationContext) }
    val scope = rememberCoroutineScope()
    val saved by store.config.collectAsState(initial = store.config.value)

    var url by remember(saved) { mutableStateOf(saved.baseUrl) }
    var token by remember(saved) { mutableStateOf(saved.token) }

    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }
    var stats by remember { mutableStateOf<AudioMuseStats?>(null) }

    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<AudioMuseTrack>>(emptyList()) }
    var searched by remember { mutableStateOf(false) }

    fun draft() = AudioMuseConfig(baseUrl = url.trim(), token = token.trim())

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(20.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                stringResource(R.string.audiomuse_title),
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                stringResource(R.string.audiomuse_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text(stringResource(R.string.audiomuse_url)) },
                placeholder = { Text("http://192.168.0.10:8000") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = token,
                onValueChange = { token = it },
                label = { Text(stringResource(R.string.audiomuse_token)) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(10.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                OutlinedButton(
                    enabled = !busy && draft().isComplete,
                    onClick = {
                        busy = true
                        message = null
                        stats = null
                        val api = AudioMuseApi(draft())
                        scope.launch {
                            try {
                                stats = api.stats()
                                isError = false
                                message = context.getString(R.string.audiomuse_connected)
                            } catch (e: AudioMuseException) {
                                isError = true
                                message = e.message
                            } finally {
                                busy = false
                            }
                        }
                    },
                ) {
                    Text(stringResource(R.string.audiomuse_test))
                }
                Button(
                    enabled = !busy,
                    onClick = {
                        store.save(draft())
                        isError = false
                        message = context.getString(R.string.audiomuse_saved)
                    },
                ) {
                    Text(stringResource(R.string.audiomuse_save))
                }
                if (busy) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
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

            stats?.let { value ->
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    StatChip(
                        if (value.clapEnabled == false) {
                            stringResource(R.string.audiomuse_chip_clap_off)
                        } else {
                            stringResource(R.string.audiomuse_chip_clap_on)
                        },
                    )
                    value.embeddings?.let {
                        StatChip(
                            stringResource(
                                R.string.audiomuse_chip_embeddings,
                                NumberFormat.getIntegerInstance().format(it),
                            ),
                        )
                    }
                    value.lastRefresh?.takeIf { it.isNotBlank() }?.let {
                        StatChip(stringResource(R.string.audiomuse_chip_refresh, it))
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text(stringResource(R.string.audiomuse_search_hint)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.size(8.dp))
                Button(
                    enabled = !busy && query.isNotBlank() && saved.isComplete,
                    onClick = {
                        busy = true
                        message = null
                        val api = AudioMuseApi(saved)
                        scope.launch {
                            try {
                                results = api.search(query.trim(), limit = 8)
                                searched = true
                                isError = false
                            } catch (e: AudioMuseException) {
                                results = emptyList()
                                searched = false
                                isError = true
                                message = e.message
                            } finally {
                                busy = false
                            }
                        }
                    },
                ) {
                    Text(stringResource(R.string.audiomuse_search_action))
                }
            }

            if (searched) {
                Spacer(Modifier.height(8.dp))
                if (results.isEmpty()) {
                    Text(
                        stringResource(R.string.audiomuse_no_results),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text(
                        stringResource(R.string.audiomuse_results, results.size),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    results.forEach { track ->
                        Text(
                            buildString {
                                append(track.title)
                                if (track.artist.isNotBlank()) append(" — ").append(track.artist)
                                track.similarity?.let {
                                    append("  ·  ")
                                    append(String.format(java.util.Locale.US, "%.2f", it))
                                }
                            },
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatChip(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = RoundedCornerShape(50),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}
