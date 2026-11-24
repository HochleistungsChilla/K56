package app.k56.player.data.offline

import android.content.Context
import app.k56.player.core.model.Track
import org.json.JSONArray
import org.json.JSONObject
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
 */
class PlaylistCacheStore(context: Context) {

    private val dir = File(context.applicationContext.filesDir, "playlist-cache")

    fun save(playlistId: String, name: String, tracks: List<Track>) {
        if (playlistId.isBlank() || tracks.isEmpty()) return
        runCatching {
            dir.mkdirs()
            val payload = JSONObject()
                .put("name", name)
                .put("savedAt", System.currentTimeMillis())
                .put("tracks", JSONArray().apply {
                    tracks.forEach { put(it.toJson()) }
                })
            val target = fileFor(playlistId)
            val tmp = File(dir, "${target.name}.tmp")
            tmp.writeText(payload.toString())
            if (!tmp.renameTo(target)) {
                target.writeText(payload.toString())
                tmp.delete()
            }
        }
    }

    fun load(playlistId: String): CachedPlaylist? = runCatching {
        val file = fileFor(playlistId)
        if (!file.isFile) return null
        val payload = JSONObject(file.readText())
        val array = payload.optJSONArray("tracks") ?: return null
        val tracks = (0 until array.length()).mapNotNull { index ->
            array.optJSONObject(index)?.toTrack()
        }
        if (tracks.isEmpty()) return null
        CachedPlaylist(
            name = payload.optString("name"),
            tracks = tracks,
            savedAt = payload.optLong("savedAt"),
        )
    }.getOrNull()

    fun clear(playlistId: String) {
        runCatching { fileFor(playlistId).delete() }
    }

    private fun fileFor(playlistId: String): File {
        // Ids come from the server; keep the name filesystem-safe anyway.
        val safe = playlistId.replace(Regex("[^A-Za-z0-9._-]"), "_").take(80)
        return File(dir, "$safe.json")
    }

    private fun Track.toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("title", title)
        .put("albumId", albumId)
        .put("albumName", albumName)
        .put("artistName", artistName)
        .put("durationMs", durationMs)
        .put("indexNumber", indexNumber)
        .put("discNumber", discNumber)
        .put("container", container)
        .put("bitrate", bitrate)
        .put("imageUrl", imageUrl)
        .put("streamKey", streamKey)
        .put("isFavorite", isFavorite)

    private fun JSONObject.toTrack(): Track? {
        val id = optString("id").takeIf { it.isNotBlank() } ?: return null
        return Track(
            id = id,
            title = optString("title"),
            albumId = optStringOrNull("albumId"),
            albumName = optStringOrNull("albumName"),
            artistName = optStringOrNull("artistName"),
            durationMs = optLong("durationMs"),
            indexNumber = optIntOrNull("indexNumber"),
            discNumber = optIntOrNull("discNumber"),
            container = optStringOrNull("container"),
            bitrate = optIntOrNull("bitrate"),
            imageUrl = optStringOrNull("imageUrl"),
            streamKey = optStringOrNull("streamKey"),
            isFavorite = optBoolean("isFavorite"),
        )
    }

    private fun JSONObject.optStringOrNull(key: String): String? =
        if (isNull(key)) null else optString(key).takeIf { it.isNotEmpty() }

    private fun JSONObject.optIntOrNull(key: String): Int? =
        if (isNull(key)) null else optInt(key)
}
