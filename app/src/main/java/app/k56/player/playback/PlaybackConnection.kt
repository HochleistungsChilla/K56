package app.k56.player.playback

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import app.k56.player.core.model.PlaybackSource
import app.k56.player.core.model.Track
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Owns the UI-side MediaController connection to [PlaybackService]. */
class PlaybackConnection(context: Context) {

    private val appContext = context.applicationContext

    private val future = MediaController.Builder(
        appContext,
        SessionToken(appContext, ComponentName(appContext, PlaybackService::class.java)),
    ).buildAsync()

    private val _controller = MutableStateFlow<MediaController?>(null)
    val controller: StateFlow<MediaController?> = _controller.asStateFlow()

    init {
        future.addListener(
            { _controller.value = runCatching { future.get() }.getOrNull() },
            MoreExecutors.directExecutor(),
        )
    }

    fun release() {
        _controller.value = null
        MediaController.releaseFuture(future)
    }
}

object PlaybackQueue {

    const val KEY_FAVORITE = "k56_favorite"

    fun mediaItem(track: Track, source: PlaybackSource): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle(track.title)
            .setArtist(track.artistName)
            .setAlbumTitle(track.albumName)
            .setExtras(android.os.Bundle().apply { putBoolean(KEY_FAVORITE, track.isFavorite) })
            .apply { track.imageUrl?.let { setArtworkUri(Uri.parse(it)) } }
            .build()

        return MediaItem.Builder()
            .setMediaId(track.id)
            .setUri(source.url)
            .apply { source.mimeType?.let { setMimeType(it) } }
            .setMediaMetadata(metadata)
            .build()
    }

    /** Copies [item] and flips its favorite flag in the media metadata extras. */
    fun withFavorite(item: MediaItem, favorite: Boolean): MediaItem {
        val md = item.mediaMetadata
        val extras = md.extras ?: android.os.Bundle()
        extras.putBoolean(KEY_FAVORITE, favorite)
        return item.buildUpon().setMediaMetadata(md.buildUpon().setExtras(extras).build()).build()
    }
}

data class PlaybackUiState(
    val hasItem: Boolean = false,
    val mediaId: String = "",
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val artworkUri: String? = null,
    val isPlaying: Boolean = false,
    val isFavorite: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val bufferedMs: Long = 0L,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val shuffle: Boolean = false,
)
