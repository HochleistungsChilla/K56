package app.k56.player.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import app.k56.player.R
import app.k56.player.data.PlayerBackground
import app.k56.player.data.PlayerStyle
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import app.k56.player.ui.components.Artwork
import app.k56.player.ui.components.EmptyState
import app.k56.player.ui.components.ScreenHeader
import app.k56.player.ui.components.SectionHeader
import app.k56.player.ui.formatDuration
import app.k56.player.ui.rememberPlaybackUiState
import androidx.core.graphics.drawable.toBitmap
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    controller: MediaController?,
    onBack: () -> Unit,
    onOpenDsp: () -> Unit = {},
    keepScreenOn: Boolean = false,
    showFavorite: Boolean = true,
    fitCover: Boolean = false,
    compactControls: Boolean = false,
    showShuffle: Boolean = true,
    showRepeat: Boolean = true,
    showQueue: Boolean = true,
    showDsp: Boolean = true,
    showSleepTimer: Boolean = true,
    playerStyle: PlayerStyle = PlayerStyle.STANDARD,
    coverCornerRadius: Int = 24,
    playerBackground: PlayerBackground = PlayerBackground.GRADIENT,
    animatedProgress: Boolean = false,
    onToggleFavorite: (String, Boolean) -> Unit = { _, _ -> },
    onMoveQueueItem: (Int, Int) -> Unit = { _, _ -> },
    onRemoveQueueItem: (Int) -> Unit = {},
    onClearQueue: () -> Unit = {},
) {
    val view = androidx.compose.ui.platform.LocalView.current
    androidx.compose.runtime.DisposableEffect(keepScreenOn) {
        val previous = view.keepScreenOn
        view.keepScreenOn = keepScreenOn
        onDispose { view.keepScreenOn = previous }
    }
    val state = rememberPlaybackUiState(controller)

    val context = LocalContext.current
    var albumColor by remember { mutableStateOf<Color?>(null) }
    LaunchedEffect(state.artworkUri, playerBackground) {
        albumColor = null
        if (playerBackground != PlayerBackground.ALBUM_COLOR) return@LaunchedEffect
        val url = state.artworkUri ?: return@LaunchedEffect
        albumColor = runCatching {
            val request = ImageRequest.Builder(context)
                .data(url)
                .size(8, 8)
                .allowHardware(false)
                .build()
            val result = context.imageLoader.execute(request)
            (result as? SuccessResult)?.drawable?.toBitmap()?.let { bitmap ->
                var r = 0; var g = 0; var b = 0; var n = 0
                for (x in 0 until bitmap.width) {
                    for (y in 0 until bitmap.height) {
                        val px = bitmap.getPixel(x, y)
                        r += (px shr 16) and 0xFF
                        g += (px shr 8) and 0xFF
                        b += px and 0xFF
                        n++
                    }
                }
                if (n > 0) Color(r / n, g / n, b / n) else null
            }
        }.getOrNull()
    }

    if (!state.hasItem) {
        Column(Modifier.fillMaxSize()) {
            ScreenHeader(title = stringResource(R.string.player_now_playing), onBack = onBack)
            EmptyState(
                title = stringResource(R.string.player_nothing_title),
                subtitle = stringResource(R.string.player_nothing_body),
                modifier = Modifier.weight(1f),
            )
        }
        return
    }

    var dragPosition by remember { mutableStateOf<Float?>(null) }
    var showQueue by remember { mutableStateOf(false) }
    var sleepMinutes by remember { mutableStateOf(0) }
    var timerMenu by remember { mutableStateOf(false) }
    var dragOffset by remember { mutableStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }
    val settle by animateFloatAsState(targetValue = dragOffset, label = "player-swipe")

    LaunchedEffect(sleepMinutes) {
        if (sleepMinutes > 0) {
            delay(sleepMinutes * 60_000L)
            controller?.pause()
            sleepMinutes = 0
        }
    }
    val durationMs = state.durationMs.coerceAtLeast(1L)
    val animatedPosition by animateFloatAsState(
        targetValue = state.positionMs.coerceIn(0L, durationMs).toFloat(),
        animationSpec = tween(durationMillis = 400),
        label = "progress",
    )
    val sliderPosition = dragPosition
        ?: if (animatedProgress) animatedPosition
        else state.positionMs.coerceIn(0L, durationMs).toFloat()

    val playerBg: Modifier = when (playerBackground) {
        PlayerBackground.SOLID -> Modifier.background(MaterialTheme.colorScheme.background)
        PlayerBackground.ALBUM_COLOR -> {
            val c = albumColor
            if (c != null) {
                Modifier.background(
                    Brush.verticalGradient(listOf(c.copy(alpha = 0.9f), MaterialTheme.colorScheme.background)),
                )
            } else {
                Modifier.background(MaterialTheme.colorScheme.background)
            }
        }
        else -> Modifier.background(
            Brush.verticalGradient(
                listOf(
                    MaterialTheme.colorScheme.surfaceContainerHigh,
                    MaterialTheme.colorScheme.background,
                ),
            ),
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { translationY = if (dragging) dragOffset else settle }
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart = { dragging = true },
                    onDragEnd = {
                        dragging = false
                        if (dragOffset > 260f) onBack() else dragOffset = 0f
                    },
                    onDragCancel = {
                        dragging = false
                        dragOffset = 0f
                    },
                    onVerticalDrag = { _, delta ->
                        dragOffset = (dragOffset + delta).coerceAtLeast(0f)
                    },
                )
            }
            .then(playerBg)
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.Rounded.KeyboardArrowDown,
                    contentDescription = stringResource(R.string.player_close),
                )
            }
            Text(
                text = stringResource(R.string.player_now_playing),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
            )
            if (showFavorite) {
                IconButton(onClick = { onToggleFavorite(state.mediaId, state.isFavorite) }) {
                    Icon(
                        imageVector = if (state.isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        contentDescription = stringResource(R.string.player_favorite),
                        tint = if (state.isFavorite) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (showDsp) {
                IconButton(onClick = onOpenDsp) {
                    Icon(
                        Icons.Rounded.Tune,
                        contentDescription = stringResource(R.string.dsp_title),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (showSleepTimer) {
                Box {
                    IconButton(onClick = { timerMenu = true }) {
                        Icon(
                            Icons.Rounded.Timer,
                            contentDescription = stringResource(R.string.player_sleep_timer),
                            tint = if (sleepMinutes > 0) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    DropdownMenu(expanded = timerMenu, onDismissRequest = { timerMenu = false }) {
                        listOf(0, 15, 30, 45, 60).forEach { minutes ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        if (minutes == 0) stringResource(R.string.player_sleep_off)
                                        else "$minutes min",
                                    )
                                },
                                onClick = {
                                    sleepMinutes = minutes
                                    timerMenu = false
                                },
                            )
                        }
                    }
                }
            }
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
                modifier = if (playerStyle == PlayerStyle.LARGE_COVER) {
                    Modifier.fillMaxSize()
                } else {
                    Modifier
                        .fillMaxWidth(0.92f)
                        .aspectRatio(1f)
                },
                shape = RoundedCornerShape(coverCornerRadius.dp),
                contentScale = if (fitCover) ContentScale.Fit else ContentScale.Crop,
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

        if (showQueue) {
            OutlinedButton(
                onClick = { showQueue = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Rounded.QueueMusic, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.player_queue_count, controller?.mediaItemCount ?: 0))
            }

            Spacer(Modifier.height(6.dp))
        }

        val playButtonSize = if (compactControls) 56.dp else 72.dp
        val playIconSize = if (compactControls) 30.dp else 38.dp
        val skipIconSize = if (compactControls) 28.dp else 36.dp
        val skipSpacing = if (compactControls) 4.dp else 8.dp

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showShuffle) {
                IconButton(onClick = { controller?.shuffleModeEnabled = !state.shuffle }) {
                    Icon(
                        Icons.Rounded.Shuffle,
                        contentDescription = stringResource(R.string.player_shuffle),
                        tint = if (state.shuffle) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { controller?.seekToPreviousMediaItem() }) {
                Icon(
                    Icons.Rounded.SkipPrevious,
                    contentDescription = stringResource(R.string.player_previous),
                    modifier = Modifier.size(skipIconSize),
                )
            }
            Spacer(Modifier.size(skipSpacing))
            FilledIconButton(
                onClick = {
                    if (state.isPlaying) controller?.pause() else controller?.play()
                },
                modifier = Modifier.size(playButtonSize),
            ) {
                Icon(
                    imageVector = if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = stringResource(
                        if (state.isPlaying) R.string.common_pause else R.string.common_play,
                    ),
                    modifier = Modifier.size(playIconSize),
                )
            }
            Spacer(Modifier.size(skipSpacing))
            IconButton(onClick = { controller?.seekToNextMediaItem() }) {
                Icon(
                    Icons.Rounded.SkipNext,
                    contentDescription = stringResource(R.string.player_next),
                    modifier = Modifier.size(skipIconSize),
                )
            }
            Spacer(Modifier.weight(1f))
            if (showRepeat) {
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
                        contentDescription = stringResource(R.string.player_repeat),
                        tint = if (state.repeatMode == Player.REPEAT_MODE_OFF) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                    )
                }
            }
        }

        Spacer(Modifier.height(18.dp))
    }

    if (showQueue) {
        ModalBottomSheet(onDismissRequest = { showQueue = false }) {
            val count = controller?.mediaItemCount ?: 0
            val currentIndex = controller?.currentMediaItemIndex ?: -1
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
            ) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(end = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SectionHeader(stringResource(R.string.player_queue), modifier = Modifier.weight(1f))
                        TextButton(onClick = { onClearQueue(); showQueue = false }, enabled = count > 0) {
                            Text(stringResource(R.string.queue_clear))
                        }
                    }
                }
                items(count) { index ->
                    val item = controller?.getMediaItemAt(index)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                controller?.seekTo(index, 0L)
                                showQueue = false
                            }
                            .padding(horizontal = 20.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = (index + 1).toString(),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.width(28.dp),
                        )
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = item?.mediaMetadata?.title?.toString().orEmpty(),
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (index == currentIndex) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = item?.mediaMetadata?.artist?.toString().orEmpty(),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        IconButton(
                            onClick = { onMoveQueueItem(index, index - 1) },
                            enabled = index > 0,
                        ) {
                            Icon(
                                Icons.Rounded.ArrowUpward,
                                contentDescription = stringResource(R.string.queue_move_up),
                            )
                        }
                        IconButton(
                            onClick = { onMoveQueueItem(index, index + 1) },
                            enabled = index < count - 1,
                        ) {
                            Icon(
                                Icons.Rounded.ArrowDownward,
                                contentDescription = stringResource(R.string.queue_move_down),
                            )
                        }
                        IconButton(onClick = { onRemoveQueueItem(index) }) {
                            Icon(
                                Icons.Rounded.DeleteOutline,
                                contentDescription = stringResource(R.string.queue_remove),
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }
        }
    }
}
