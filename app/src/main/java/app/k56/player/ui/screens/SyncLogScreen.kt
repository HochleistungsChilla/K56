package app.k56.player.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.k56.player.R
import app.k56.player.data.library.SyncLog
import app.k56.player.ui.components.EmptyState

/**
 * The sync log. Text is selectable, and the whole buffer can be copied or
 * shared — handy when a sync fails on someone else's server.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncLogScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val entries by SyncLog.entries.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.sync_log_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.common_back),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { copyToClipboard(context) }) {
                        Icon(
                            imageVector = Icons.Rounded.ContentCopy,
                            contentDescription = stringResource(R.string.sync_log_copy),
                        )
                    }
                    IconButton(onClick = { share(context) }) {
                        Icon(
                            imageVector = Icons.Rounded.Share,
                            contentDescription = stringResource(R.string.sync_log_share),
                        )
                    }
                    IconButton(onClick = { SyncLog.clear() }) {
                        Icon(
                            imageVector = Icons.Rounded.DeleteOutline,
                            contentDescription = stringResource(R.string.sync_log_clear),
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                },
            )
        },
    ) { padding ->
        if (entries.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                EmptyState(
                    title = stringResource(R.string.sync_log_empty_title),
                    subtitle = stringResource(R.string.sync_log_empty_body),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        } else {
            SelectionContainer {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 8.dp,
                        bottom = 24.dp,
                    ),
                ) {
                    items(entries) { entry ->
                        Row(Modifier.fillMaxWidth()) {
                            Text(
                                text = entry.format(),
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                ),
                                color = when (entry.level) {
                                    SyncLog.Level.ERROR -> MaterialTheme.colorScheme.error
                                    SyncLog.Level.WARN -> MaterialTheme.colorScheme.tertiary
                                    SyncLog.Level.INFO -> MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun copyToClipboard(context: Context) {
    val text = SyncLog.asText().ifBlank { "—" }
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    clipboard?.setPrimaryClip(ClipData.newPlainText("K56 sync log", text))
}

private fun share(context: Context) {
    val text = SyncLog.asText().ifBlank { "—" }
    // Share a real .txt file: chat apps then send it as a document, not a wall of text.
    val dir = java.io.File(context.cacheDir, "debug-logs").apply { mkdirs() }
    val file = java.io.File(dir, "k56-debug-${System.currentTimeMillis()}.txt")
    runCatching { file.writeText(text) }.onFailure { return }
    val uri = androidx.core.content.FileProvider.getUriForFile(
        context,
        context.packageName + ".fileprovider",
        file,
    )
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, "K56 debug log")
        putExtra(Intent.EXTRA_TEXT, "K56 debug log")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, null))
}
