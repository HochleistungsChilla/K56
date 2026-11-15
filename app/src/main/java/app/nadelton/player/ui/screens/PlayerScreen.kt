package app.nadelton.player.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import app.nadelton.player.ui.components.Artwork
import app.nadelton.player.ui.components.EmptyState
import app.nadelton.player.ui.components.ScreenHeader
import app.nadelton.player.ui.formatDuration
import app.nadelton.player.ui.rememberPlaybackUiState

@Composable
fun PlayerScreen(controller: MediaController?, onBack: () -> Unit) {
    val state = rememberPlaybackUiState(controller)

    if (!state.hasItem) {
        Column(Modifier.fillMaxSize()) {
            ScreenHeader(title = "Jetzt läuft", onBack = onBack)
            EmptyState(
                title = "Nichts läuft",
                subtitle = "Starte einen Titel aus deiner Bibliothek.",
                modifier = Modifier.weight(1f),
            )
        }
        return
    }

    var dragPosition by remember { mutableStateOf<Float?>(null) }
    val durationMs = state.durationMs.coerceAtLeast(1L)
    val sliderPosition = dragPosition ?: state.positionMs.coerceIn(0L, durationMs).toFloat()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.surfaceContainerHigh,
                        MaterialTheme.colorScheme.background,
                    ),
                ),
            )
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Player schließen")
            }
            Text(
                text = "Jetzt läuft",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.size(48.dp))
        }

        Spacer(Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center,
        ) {
            Artwork(
                url = state.artworkUri,
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .aspectRatio(1f),
                shape = RoundedCornerShape(24.dp),
            )
        }

        Spacer(Modifier.height(20.dp))

        Text(
            text = state.title,
            style = MaterialTheme.typography.headlineSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = listOf(state.artist, state.album)
                .filter { it.isNotBlank() }
                .joinToString(" · "),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(14.dp))

        Slider(
            value = sliderPosition,
            onValueChange = { dragPosition = it },
            onValueChangeFinished = {
                dragPosition?.let { controller?.seekTo(it.toLong()) }
                dragPosition = null
            },
            valueRange = 0f..durationMs.toFloat(),
        )
        Row(Modifier.fillMaxWidth()) {
            Text(
                text = formatDuration((dragPosition ?: state.positionMs.toFloat()).toLong()),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = formatDuration(state.durationMs),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { controller?.shuffleModeEnabled = !state.shuffle }) {
                Icon(
                    Icons.Rounded.Shuffle,
                    contentDescription = "Zufallswiedergabe",
                    tint = if (state.shuffle) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { controller?.seekToPreviousMediaItem() }) {
                Icon(
                    Icons.Rounded.SkipPrevious,
                    contentDescription = "Vorheriger Titel",
                    modifier = Modifier.size(36.dp),
                )
            }
            Spacer(Modifier.size(8.dp))
            FilledIconButton(
                onClick = {
                    if (state.isPlaying) controller?.pause() else controller?.play()
                },
                modifier = Modifier.size(72.dp),
            ) {
                Icon(
                    imageVector = if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = if (state.isPlaying) "Pause" else "Abspielen",
                    modifier = Modifier.size(38.dp),
                )
            }
            Spacer(Modifier.size(8.dp))
            IconButton(onClick = { controller?.seekToNextMediaItem() }) {
                Icon(
                    Icons.Rounded.SkipNext,
                    contentDescription = "Nächster Titel",
                    modifier = Modifier.size(36.dp),
                )
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = {
                controller?.repeatMode = when (state.repeatMode) {
                    Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                    Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                    else -> Player.REPEAT_MODE_OFF
                }
            }) {
                Icon(
                    imageVector = if (state.repeatMode == Player.REPEAT_MODE_ONE) {
                        Icons.Rounded.RepeatOne
                    } else {
                        Icons.Rounded.Repeat
                    },
                    contentDescription = "Wiederholen",
                    tint = if (state.repeatMode == Player.REPEAT_MODE_OFF) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                )
            }
        }

        Spacer(Modifier.height(18.dp))
    }
}
