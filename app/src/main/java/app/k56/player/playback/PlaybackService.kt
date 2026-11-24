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
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import app.k56.player.K56App
import app.k56.player.core.model.PlaybackSource
import app.k56.player.core.net.NetworkTransport
import app.k56.player.core.net.DiagnosticsDataSourceFactory
import app.k56.player.dsp.DspProcessor
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
    private var mediaSession: MediaSession? = null

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var progressJob: Job? = null

    private var playSessionId: String = UUID.randomUUID().toString()
    private var currentItemId: String? = null

    /**
     * Item whose start report is still owed. A media item transition happens
     * before a single byte is decoded, so reporting from there produced
     * "playing" entries for tracks that never made a sound.
     */
    private var pendingStartId: String? = null

    /** Last few tracks, used as seeds for the similarity-based continuation. */
    private val recentIds = ArrayDeque<String>()
    private var autoplayBusy = false
    private var autoplayExhausted = false

    private val appContainer get() = (application as K56App).container
    private val repository get() = appContainer.repository

    override fun onCreate() {
        super.onCreate()

        player = ExoPlayer.Builder(this)
            .setRenderersFactory(DspRenderersFactory(this))
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
            .setLoadControl(buildLoadControl())
            .build()

        mediaSession = MediaSession.Builder(this, player).build()

        player.addListener(object : Player.Listener {

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val id = mediaItem?.mediaId?.takeIf { it.isNotBlank() } ?: return
                if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED) {
                    autoplayExhausted = false
                }
                if (id != currentItemId) {
                    rememberSeed(id)
                    currentItemId = id
                    // Bookkeeping only: a large library can hold the write lock
                    // for a while during a sync, and this listener runs on the
                    // main thread.
                    serviceScope.launch {
                        appContainer.offlineStore.markStreamed(id)
                        appContainer.libraryDb.recordPlay(id)
                    }
                    resumeIfAvailable(id)
                    pendingStartId = id
                    extendWithSimilar(autoStart = false)
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) {
                    pendingStartId?.let { id ->
                        pendingStartId = null
                        reportStart(id)
                    }
                    startProgressLoop()
                } else {
                    stopProgressLoop()
                    saveResume()
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    reportStopped()
                    if (appContainer.settingsStore.circularQueue.value && player.mediaItemCount > 0) {
                        // Circular queue: wrap back to the start instead of stopping.
                        player.seekTo(0, 0L)
                        player.prepare()
                        player.play()
                    } else {
                        // Queue ran dry: continue in the same direction instead of stopping.
                        extendWithSimilar(autoStart = true)
                    }
                }
            }
        })
    }

    private fun rememberSeed(id: String) {
        recentIds.remove(id)
        recentIds.addLast(id)
        while (recentIds.size > 5) recentIds.removeFirst()
    }

    /** Continues the current track from its remembered resume point when valid. */
    private fun resumeIfAvailable(id: String) {
        val point = appContainer.resumeStore.read(id) ?: return
        val thresholdMs = appContainer.settingsStore.resumeThresholdSeconds.value * 1000L
        if (point.positionMs < thresholdMs) return
        if (point.durationMs > 0 && point.durationMs - point.positionMs < thresholdMs) return
        player.seekTo(point.positionMs)
    }

    /** Persists the current position so the next open can resume from it. */
    private fun saveResume() {
        val id = currentItemId ?: return
        val title = runCatching { player.currentMediaItem?.mediaMetadata?.title?.toString() }
            .getOrNull().orEmpty()
        val positionMs = runCatching { player.currentPosition.coerceAtLeast(0L) }.getOrDefault(0L)
        val durationMs = runCatching { player.duration }.getOrDefault(0L)
        val thresholdMs = appContainer.settingsStore.resumeThresholdSeconds.value * 1000L
        appContainer.resumeStore.save(id, title, positionMs, durationMs, thresholdMs)
    }

    /**
     * Appends tracks that continue the current direction. Jellyfin's InstantMix
     * is seeded with the last few titles, so a Eurodance track leads to more
     * Eurodance — no random picks, no shuffle.
     */
    private fun extendWithSimilar(autoStart: Boolean) {
        if (!appContainer.settingsStore.autoplay.value) return
        if (autoplayBusy || autoplayExhausted) return
        if (recentIds.isEmpty()) return
        if (!autoStart && player.mediaItemCount - player.currentMediaItemIndex > 2) return

        autoplayBusy = true
        val seeds = recentIds.toList().takeLast(3).reversed()
        serviceScope.launch {
            try {
                val existing = (0 until player.mediaItemCount)
                    .mapNotNull { player.getMediaItemAt(it).mediaId }
                    .toSet()
                val fresh = repository.instantMix(seeds, 25)
                    .filter { it.id !in existing }
                    .take(if (frugalLookahead()) 2 else 12)
                if (fresh.isEmpty()) {
                    autoplayExhausted = true
                    return@launch
                }
                val items = fresh.mapNotNull { track ->
                    val offline = appContainer.offlineStore.find(track.id)
                    if (offline != null) {
                        PlaybackQueue.mediaItem(
                            track,
                            PlaybackSource(
                                url = offline.uri(this@PlaybackService).toString(),
                                mimeType = null,
                                transcoded = false,
                            ),
                        )
                    } else {
                        runCatching { repository.playbackSource(track) }.getOrNull()
                            ?.let { PlaybackQueue.mediaItem(track, it) }
                    }
                }
                if (items.isEmpty()) {
                    autoplayExhausted = true
                    return@launch
                }
                withContext(Dispatchers.Main) {
                    player.addMediaItems(items)
                    if (autoStart) {
                        player.prepare()
                        player.play()
                    }
                }
            } catch (_: Exception) {
                autoplayExhausted = true
            } finally {
                autoplayBusy = false
            }
        }
    }

    /** Frugal mode on mobile data: keep the lookahead to one or two titles. */
    private fun frugalLookahead(): Boolean =
        appContainer.settingsStore.frugalMode.value &&
            appContainer.repository.networkStatus.value.transport == NetworkTransport.CELLULAR

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (!player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
        super.onTaskRemoved(rootIntent)
    }

    /**
     * Builds the audio sink with the DSP chain inserted after decoding, which is
     * the same point in the pipeline where V4A and JamesDSP hook in.
     */
    private inner class DspRenderersFactory(context: android.content.Context) :
        DefaultRenderersFactory(context) {
        override fun buildAudioSink(
            context: android.content.Context,
            enableFloatOutput: Boolean,
            enableAudioTrackPlaybackParams: Boolean,
        ): AudioSink = DefaultAudioSink.Builder(context)
            .setAudioProcessors(arrayOf(DspProcessor(appContainer.dspState)))
            .setEnableFloatOutput(true)
            .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
            .build()
    }

    /**
     * Frugal mode keeps the prebuffer small so a metered connection does not
     * pull more than a few seconds ahead of the current position.
     */
    private fun buildLoadControl(): DefaultLoadControl {
        if (!appContainer.settingsStore.frugalMode.value) return DefaultLoadControl()
        return DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                DefaultLoadControl.DEFAULT_MIN_BUFFER_MS,
                DefaultLoadControl.DEFAULT_MAX_BUFFER_MS / 4,
                DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_MS / 2,
                DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS / 2,
            )
            .build()
    }

    /** Upstream HTTP wrapped in the shared bounded cache (see MediaCache). */
    private fun buildCachedDataSourceFactory(): DataSource.Factory {
        val upstream = DiagnosticsDataSourceFactory(
            DefaultHttpDataSource.Factory()
                .setDefaultRequestProperties(repository.playbackHeaders())
                .setAllowCrossProtocolRedirects(true)
        )

        val cache = appContainer.mediaCache.get() ?: return upstream
        return CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(upstream)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }

    override fun onDestroy() {
        saveResume()
        reportStopped()
        progressJob?.cancel()
        serviceScope.cancel()
        mediaSession?.release()
        mediaSession = null
        player.release()
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
        pendingStartId = null
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
                        durationMs = runCatching { player.duration }.getOrDefault(0L),
                        title = runCatching { player.currentMediaItem?.mediaMetadata?.title?.toString() }
                            .getOrNull().orEmpty(),
                        paused = !player.isPlaying,
                        repeat = player.repeatMode.toJellyfinRepeatMode(),
                    )
                }
                appContainer.resumeStore.save(
                    itemId,
                    snapshot.title,
                    snapshot.positionMs,
                    snapshot.durationMs,
                    appContainer.settingsStore.resumeThresholdSeconds.value * 1000L,
                )
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
        val durationMs: Long,
        val title: String,
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
