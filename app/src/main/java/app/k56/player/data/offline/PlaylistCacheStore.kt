package app.k56.player.data.offline

import android.content.Context
import app.k56.player.core.model.Track
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/** What a playlist looked like the last time it could be loaded. */
data class CachedPlaylist(
    val name: String,
    val tracks: List<Track>,
    val savedAt: Long,
)

/**
 * Keeps the last successful contents of a playlist on disk, so its screen can
 * still render while the server is unreachable.
 *
 * Playlists are not part of the offline library index: they change too often
 * and are too small to justify a table plus a sync step. One JSON file per
 * playlist, written atomically, is enough and stays out of `LibraryDb`.
 *
 * The on-disk shape is a plain kotlinx structure rather than `Track` itself, so
 * the format is explicit, tolerant to added fields, and unit-testable on the
 * JVM without an Android runtime.
 */
class PlaylistCacheStore(
    private val dir: File,
) {

    constructor(context: Context) : this(File(context.applicationContext.filesDir, "playlist-cache"))

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun save(playlistId: String, name: String, tracks: List<Track>) {
        if (playlistId.isBlank() || tracks.isEmpty()) return
        runCatching {
            dir.mkdirs()
            prune()
            val cachedTracks = tracks.map { it.toCached() }
            val target = fileFor(playlistId)
            // Opening the same playlist repeatedly must not rewrite the file -
            // and must keep the original timestamp, so the banner does not claim
            // a refresh that never happened.
            val existing = readPayload(target)
            if (existing != null && existing.name == name && existing.tracks == cachedTracks) {
                return
            }
            val text = json.encodeToString(
                CachedPayload.serializer(),
                CachedPayload(name = name, savedAt = System.currentTimeMillis(), tracks = cachedTracks),
            )
            val tmp = File(dir, "${target.name}.${System.nanoTime()}.tmp")
            tmp.writeText(text)
            if (!tmp.renameTo(target)) {
                target.writeText(text)
                tmp.delete()
            }
        }
    }

    fun load(playlistId: String): CachedPlaylist? = runCatching {
        val target = fileFor(playlistId)
        val payload = readPayload(target) ?: return null
        if (payload.tracks.isEmpty()) return null
        CachedPlaylist(
            name = payload.name,
            tracks = payload.tracks.map { it.toTrack() },
            savedAt = payload.savedAt,
        )
    }.getOrNull()

    fun clear(playlistId: String) {
        runCatching { fileFor(playlistId).delete() }
    }

    private fun readPayload(file: File): CachedPayload? {
        if (!file.isFile) return null
        return runCatching {
            json.decodeFromString(CachedPayload.serializer(), file.readText())
        }.getOrNull()
    }

    /** Files for playlists that no longer exist would otherwise stay forever. */
    private fun prune() {
        val now = System.currentTimeMillis()
        val cutoff = now - MAX_AGE_MS
        val partialCutoff = now - PARTIAL_MAX_AGE_MS
        dir.listFiles()?.forEach { file ->
            if (!file.isFile) return@forEach
            val modified = file.lastModified()
            val stalePartial = file.name.endsWith(".tmp") && modified < partialCutoff
            if (modified < cutoff || stalePartial) file.delete()
        }
    }

    private fun fileFor(playlistId: String): File {
        // Ids come from the server; keep the name filesystem-safe anyway.
        val safe = playlistId.replace(Regex("[^A-Za-z0-9._-]"), "_").take(80)
        return File(dir, "$safe.json")
    }

    private fun Track.toCached() = CachedTrack(
        id = id,
        title = title,
        albumId = albumId,
        albumName = albumName,
        artistName = artistName,
        durationMs = durationMs,
        indexNumber = indexNumber,
        discNumber = discNumber,
        container = container,
        bitrate = bitrate,
        imageUrl = imageUrl,
        streamKey = streamKey,
        isFavorite = isFavorite,
    )

    private fun CachedTrack.toTrack() = Track(
        id = id,
        title = title,
        albumId = albumId,
        albumName = albumName,
        artistName = artistName,
        durationMs = durationMs,
        indexNumber = indexNumber,
        discNumber = discNumber,
        container = container,
        bitrate = bitrate,
        imageUrl = imageUrl,
        streamKey = streamKey,
        isFavorite = isFavorite,
    )

    @Serializable
    private data class CachedPayload(
        val name: String = "",
        val savedAt: Long = 0L,
        val tracks: List<CachedTrack> = emptyList(),
    )

    @Serializable
    private data class CachedTrack(
        val id: String,
        val title: String = "",
        val albumId: String? = null,
        val albumName: String? = null,
        val artistName: String? = null,
        val durationMs: Long = 0L,
        val indexNumber: Int? = null,
        val discNumber: Int? = null,
        val container: String? = null,
        val bitrate: Int? = null,
        val imageUrl: String? = null,
        val streamKey: String? = null,
        val isFavorite: Boolean = false,
    )

    private companion object {
        const val MAX_AGE_MS = 30L * 24 * 60 * 60 * 1000
        const val PARTIAL_MAX_AGE_MS = 24L * 60 * 60 * 1000
    }
}
