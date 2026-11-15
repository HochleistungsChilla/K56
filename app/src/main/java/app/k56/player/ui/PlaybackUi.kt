package app.k56.player.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import app.k56.player.playback.PlaybackUiState
import kotlinx.coroutines.delay

/**
 * Bridges MediaController callbacks and position polling into one Compose state
 * object. Position is sampled; everything else comes from player events.
 */
@Composable
fun rememberPlaybackUiState(controller: MediaController?): PlaybackUiState {
    var state by remember { mutableStateOf(PlaybackUiState()) }

    DisposableEffect(controller) {
        if (controller == null) {
            state = PlaybackUiState()
            return@DisposableEffect onDispose { }
        }
        val listener = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                state = player.toUiState()
            }
        }
        controller.addListener(listener)
        state = controller.toUiState()
        onDispose { controller.removeListener(listener) }
    }

    LaunchedEffect(controller) {
        if (controller == null) return@LaunchedEffect
        while (true) {
            state = state.copy(
                positionMs = controller.currentPosition.coerceAtLeast(0L),
                durationMs = controller.duration.takeIf { it > 0L } ?: 0L,
                bufferedMs = controller.bufferedPosition.coerceAtLeast(0L),
            )
            delay(400)
        }
    }

    return state
}

private fun Player.toUiState(): PlaybackUiState {
    val metadata = currentMediaItem?.mediaMetadata
    return PlaybackUiState(
        hasItem = currentMediaItem != null,
        title = metadata?.title?.toString().orEmpty(),
        artist = metadata?.artist?.toString().orEmpty(),
        album = metadata?.albumTitle?.toString().orEmpty(),
        artworkUri = metadata?.artworkUri?.toString(),
        isPlaying = isPlaying,
        positionMs = currentPosition.coerceAtLeast(0L),
        durationMs = duration.takeIf { it > 0L } ?: 0L,
        bufferedMs = bufferedPosition.coerceAtLeast(0L),
        repeatMode = repeatMode,
        shuffle = shuffleModeEnabled,
    )
}
