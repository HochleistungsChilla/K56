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
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.k56.player.R
import app.k56.player.BuildConfig
import app.k56.player.core.model.BackendKind
import app.k56.player.core.net.NetworkTransport
import app.k56.player.data.MediaRepository
import app.k56.player.ui.theme.ThemeController
import app.k56.player.ui.theme.ThemeMode

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
                Text(
                    stringResource(R.string.settings_theme_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.height(10.dp))
                val context = LocalContext.current
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
