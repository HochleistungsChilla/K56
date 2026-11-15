package app.nadelton.player.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Logout
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.nadelton.player.BuildConfig
import app.nadelton.player.core.model.BackendKind
import app.nadelton.player.core.net.NetworkTransport
import app.nadelton.player.data.MediaRepository

@Composable
fun SettingsScreen(repository: MediaRepository) {
    val session by repository.sessionFlow.collectAsStateWithLifecycle()
    val forceTranscode by repository.forceTranscode.collectAsStateWithLifecycle()
    val preferExternal by repository.preferExternal.collectAsStateWithLifecycle()
    val networkStatus by repository.networkStatus.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
    ) {
        Text("Einstellungen", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(18.dp))

        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("Verbindung", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
                InfoRow("Typ", session?.backend?.label ?: "—")
                InfoRow("Server", session?.serverName ?: "—")
                InfoRow("LAN", session?.internalUrl ?: "—")
                InfoRow("Internet", session?.externalUrl ?: "—")
                InfoRow("Benutzer", session?.userName ?: "—")
                InfoRow("Version", session?.serverVersion ?: "—")
                InfoRow(
                    "Netz",
                    when (networkStatus.transport) {
                        NetworkTransport.WIFI -> "WLAN"
                        NetworkTransport.CELLULAR -> "Mobilfunk"
                        NetworkTransport.ETHERNET -> "Ethernet"
                        NetworkTransport.OTHER -> "anderes Netz"
                        NetworkTransport.OFFLINE -> "offline"
                    },
                )
                InfoRow(
                    "Route",
                    when {
                        session?.backend == BackendKind.LOCAL -> "lokal"
                        preferExternal -> "externe Adresse (erzwungen)"
                        networkStatus.transport == NetworkTransport.CELLULAR -> "externe Adresse"
                        session?.externalUrl.isNullOrBlank() -> "LAN-Adresse"
                        session?.internalUrl.isNullOrBlank() -> "externe Adresse"
                        else -> "LAN, falls erreichbar"
                    },
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
                    Text("Externe Adresse bevorzugen", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Aus: Im WLAN wird die LAN-Adresse genutzt, wenn sie erreichbar ist; " +
                            "im Mobilfunk immer die externe Adresse. Keine Standortberechtigung nötig.",
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
                    Text("Transcoding erzwingen", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "AAC/HLS statt Direct Play. Nützlich, wenn ein Codec auf dem Gerät nicht läuft.",
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

        Spacer(Modifier.height(22.dp))

        OutlinedButton(
            onClick = { repository.logout() },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
        ) {
            Icon(Icons.Rounded.Logout, contentDescription = null)
            Spacer(Modifier.width(10.dp))
            Text("Abmelden")
        }

        Spacer(Modifier.height(26.dp))

        Text("Über Nadelton", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(4.dp))
        Text(
            "Version ${BuildConfig.VERSION_NAME} · GPL-3.0-or-later",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Nadelton ist eine unabhängige, quelloffene Cleanroom-Neuentwicklung eines " +
                "Android-Musikplayers. Kein Code, keine Assets und keine Marke von Symfonium " +
                "wurden übernommen. Jellyfin-Mindestversion: ${BuildConfig.JELLYFIN_MIN_VERSION}.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
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
