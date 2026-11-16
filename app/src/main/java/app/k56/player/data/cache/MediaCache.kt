package app.k56.player.data.cache

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import app.k56.player.data.SettingsStore
import app.k56.player.data.StorageLocation
import java.io.File

/**
 * Single owner of the Media3 stream cache. Both the playback service and the
 * cache manager screen talk to this instance; Media3 allows exactly one
 * SimpleCache per directory, so it must never be created twice.
 */
@UnstableApi
class MediaCache(private val context: Context, private val settingsStore: SettingsStore) {

    private var instance: SimpleCache? = null

    /** Null when caching is switched off. */
    @Synchronized
    fun get(): Cache? {
        val limitMb = settingsStore.cacheLimitMb.value
        if (limitMb == 0) return null
        instance?.let { return it }
        return runCatching {
            val evictor = if (limitMb < 0) {
                NoOpCacheEvictor() // grow until the volume is full
            } else {
                LeastRecentlyUsedCacheEvictor(limitMb.toLong() * 1024L * 1024L)
            }
            SimpleCache(directory(), evictor, StandaloneDatabaseProvider(context))
                .also { instance = it }
        }.getOrNull()
    }

    @Synchronized
    fun release() {
        runCatching { instance?.release() }
        instance = null
    }

    /** Bytes currently on disk, readable even before the cache is opened. */
    fun sizeBytes(): Long {
        instance?.let { cache ->
            val measured = runCatching { cache.cacheSpace }.getOrNull()
            if (measured != null && measured > 0) return measured
        }
        return directory().walkTopDown()
            .filter { it.isFile }
            .sumOf { it.length() }
    }

    /** Track count of the stream cache; 0 while it is not open. */
    fun entryCount(): Int = runCatching { instance?.keys?.size ?: 0 }.getOrDefault(0)

    /**
     * Empties the stream cache. Deleting through the cache keeps its index
     * consistent; a plain file wipe would leave stale rows behind.
     */
    fun clear() {
        val cache = instance
        if (cache == null) {
            directory().walkTopDown().filter { it.isFile }.forEach { runCatching { it.delete() } }
            return
        }
        runCatching {
            cache.keys.toList().forEach { key -> cache.removeResource(key) }
        }
    }

    fun directory(): File {
        val base = if (settingsStore.storageLocation.value == StorageLocation.EXTERNAL) {
            val root = SettingsStore.externalRoot(context)
            val matching = root?.let { r ->
                context.externalCacheDirs.firstOrNull { it != null && it.absolutePath.startsWith(r.absolutePath) }
            }
            matching ?: context.externalCacheDir ?: context.cacheDir
        } else {
            context.cacheDir
        }
        return File(base ?: context.cacheDir, "media_cache")
    }
}
