package app.k56.player.playback

import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import app.k56.player.K56App
import app.k56.player.data.SettingsStore
import app.k56.player.data.StorageLocation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * Foreground media playback. ExoPlayer + MediaSession live here so playback and
 * the system media notification survive the UI.
 */
@androidx.annotation.OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {

    private lateinit var player: ExoPlayer
    private lateinit var mediaCache: SimpleCache
    private var mediaSession: MediaSession? = null

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var progressJob: Job? = null

    private var playSessionId: String = UUID.randomUUID().toString()
    private var currentItemId: String? = null

    private val appContainer get() = (application as K56App).container
    private val repository get() = appContainer.repository

    override fun onCreate() {
        super.onCreate()

        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(
                    buildCachedDataSourceFactory(),
                ),
            )
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true)
            .build()

        mediaSession = MediaSession.Builder(this, player).build()

        player.addListener(object : Player.Listener {

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val id = mediaItem?.mediaId?.takeIf { it.isNotBlank() } ?: return
                if (id != currentItemId) {
                    currentItemId = id
                    reportStart(id)
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) startProgressLoop() else stopProgressLoop()
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) reportStopped()
            }
        })
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (!player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
        super.onTaskRemoved(rootIntent)
    }

    /** Upstream HTTP wrapped in a bounded on-disk cache (512 MiB, LRU). */
    private fun buildCachedDataSourceFactory(): DataSource.Factory {
        val upstream = DefaultHttpDataSource.Factory()
            .setDefaultRequestProperties(repository.playbackHeaders())
            .setAllowCrossProtocolRedirects(true)

        // 0 MiB means "no cache at all": hand back the plain network source.
        val limitMb = appContainer.settingsStore.cacheLimitMb.value
        if (limitMb == 0) return upstream

        if (!::mediaCache.isInitialized) {
            val evictor = if (limitMb < 0) {
                NoOpCacheEvictor() // grow until the volume is full
            } else {
                LeastRecentlyUsedCacheEvictor(limitMb.toLong() * 1024L * 1024L)
            }
            mediaCache = SimpleCache(cacheDirectory(), evictor, StandaloneDatabaseProvider(this))
        }
        return CacheDataSource.Factory()
            .setCache(mediaCache)
            .setUpstreamDataSourceFactory(upstream)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }

    /** Cache lives next to downloads: internal by default, SD when selected. */
    private fun cacheDirectory(): File {
        val location = appContainer.settingsStore.storageLocation.value
        val base = if (location == StorageLocation.EXTERNAL) {
            val root = SettingsStore.externalRoot(this)
            val matching = root?.let { r ->
                externalCacheDirs.firstOrNull { it != null && it.absolutePath.startsWith(r.absolutePath) }
            }
            matching ?: externalCacheDir ?: cacheDir
        } else {
            cacheDir
        }
        return File(base ?: cacheDir, "media_cache")
    }

    override fun onDestroy() {
        reportStopped()
        progressJob?.cancel()
        serviceScope.cancel()
        mediaSession?.release()
        mediaSession = null
        player.release()
        if (::mediaCache.isInitialized) mediaCache.release()
        super.onDestroy()
    }

    private fun reportStart(itemId: String) {
        val positionTicks = player.currentPosition.coerceAtLeast(0L) * TICKS_PER_MS
        val sessionId = playSessionId
        // Reporting outlives the service teardown on purpose.
        appContainer.appScope.launch {
            runCatching { repository.reportPlaybackStart(itemId, sessionId, positionTicks) }
        }
    }

    private fun reportStopped() {
        val itemId = currentItemId ?: return
        currentItemId = null
        val sessionId = playSessionId
        val positionTicks = runCatching { player.currentPosition.coerceAtLeast(0L) }
            .getOrDefault(0L) * TICKS_PER_MS
        appContainer.appScope.launch {
            runCatching { repository.reportPlaybackStopped(itemId, sessionId, positionTicks) }
        }
        playSessionId = UUID.randomUUID().toString()
    }

    private fun startProgressLoop() {
        if (progressJob?.isActive == true) return
        progressJob = serviceScope.launch {
            while (isActive) {
                delay(PROGRESS_INTERVAL_MS)
                val itemId = currentItemId ?: continue
                val snapshot = withContext(Dispatchers.Main) {
                    ProgressSnapshot(
                        positionMs = player.currentPosition.coerceAtLeast(0L),
                        paused = !player.isPlaying,
                        repeat = player.repeatMode.toJellyfinRepeatMode(),
                    )
                }
                runCatching {
                    repository.reportPlaybackProgress(
                        itemId = itemId,
                        playSessionId = playSessionId,
                        positionTicks = snapshot.positionMs * TICKS_PER_MS,
                        isPaused = snapshot.paused,
                        repeatMode = snapshot.repeat,
                    )
                }
            }
        }
    }

    private fun stopProgressLoop() {
        progressJob?.cancel()
        progressJob = null
    }

    private data class ProgressSnapshot(
        val positionMs: Long,
        val paused: Boolean,
        val repeat: String,
    )

    private fun Int.toJellyfinRepeatMode(): String = when (this) {
        Player.REPEAT_MODE_ONE -> "RepeatOne"
        Player.REPEAT_MODE_ALL -> "RepeatAll"
        else -> "RepeatNone"
    }

    private companion object {
        const val PROGRESS_INTERVAL_MS = 10_000L
        const val TICKS_PER_MS = 10_000L
    }
}
