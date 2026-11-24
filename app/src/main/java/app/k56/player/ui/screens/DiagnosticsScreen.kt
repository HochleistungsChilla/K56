package app.k56.player.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.k56.player.R
import app.k56.player.core.net.DiagnosticsEntry
import app.k56.player.core.net.DiagnosticsErrorKind
import app.k56.player.core.net.DiagnosticsStore
import app.k56.player.ui.components.EmptyState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The last requests the app made, straight from [DiagnosticsStore].
 *
 * Only method, path (no query string), status, duration and a coarse error class
 * are kept by the store, so the copied text is safe to paste into a support thread.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val stored by DiagnosticsStore.entries.collectAsStateWithLifecycle()
    // The store appends newest last; the screen reads better newest first.
    val entries = remember(stored) { stored.asReversed() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.diagnostics_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.common_back),
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { copyDiagnostics(context, entries) },
                        enabled = entries.isNotEmpty(),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ContentCopy,
                            contentDescription = stringResource(R.string.diagnostics_copy),
                        )
                    }
                    IconButton(
                        onClick = { DiagnosticsStore.clear() },
                        enabled = entries.isNotEmpty(),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.DeleteOutline,
                            contentDescription = stringResource(R.string.diagnostics_clear),
                        )
                    }
                },
            )
        },
    ) { padding ->
        if (entries.isEmpty()) {
            EmptyState(
                title = stringResource(R.string.diagnostics_empty_title),
                subtitle = stringResource(R.string.diagnostics_empty_body),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            )
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(entries, key = { "${it.atEpochMs}-${it.method}-${it.path}-${it.status}" }) { entry ->
                DiagnosticsRow(entry)
            }
        }
    }
}

@Composable
private fun DiagnosticsRow(entry: DiagnosticsEntry) {
    val statusColor = when {
        entry.errorKind != null -> MaterialTheme.colorScheme.error
        entry.status in 200..299 -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.error
    }
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = entry.method,
                style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(52.dp),
            )
            Text(
                text = entry.status?.toString() ?: entry.errorKind?.name ?: "—",
                style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace),
                color = statusColor,
                modifier = Modifier.width(64.dp),
            )
            Text(
                text = "${entry.durationMs} ms",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(64.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = timeOnly(entry.atEpochMs),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = entry.path,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

private fun timeOnly(at: Long): String =
    SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(at))

private fun kindText(kind: DiagnosticsErrorKind?): String = when (kind) {
    DiagnosticsErrorKind.TIMEOUT -> "timeout"
    DiagnosticsErrorKind.DNS -> "dns"
    DiagnosticsErrorKind.TLS -> "tls"
    DiagnosticsErrorKind.HTTP -> "http"
    DiagnosticsErrorKind.IO -> "io"
    null -> "ok"
}

private fun copyDiagnostics(context: Context, entries: List<DiagnosticsEntry>) {
    if (entries.isEmpty()) return
    val text = buildString {
        appendLine("K56 diagnostics")
        appendLine("${entries.size} requests, newest first")
        entries.forEach { e ->
            append(e.method)
            append(' ')
            append(e.status?.toString() ?: kindText(e.errorKind))
            append(' ')
            append(e.path)
            append(' ')
            append(e.durationMs)
            appendLine(" ms")
        }
    }
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("K56 diagnostics", text))
}
