package app.k56.player.data

import android.content.Context
import app.k56.player.core.model.Track
import org.json.JSONArray
import org.json.JSONObject

/**
 * Persists the current playback queue (full [Track] list plus the active index)
 * so a restart can rebuild "Now Playing" instead of starting empty. The
 * per-track resume position stays in [ResumeStore]; this store only cares about
 * which tracks make up the queue and which one was active.
 */
class NowPlayingStore(context: Context) {

    private val prefs = context.getSharedPreferences("k56_now_playing", Context.MODE_PRIVATE)

    data class SavedQueue(val tracks: List<Track>, val index: Int)

    fun save(tracks: List<Track>, index: Int) {
        if (tracks.isEmpty()) {
            clear()
            return
        }
        val array = JSONArray()
        tracks.forEach { track -> array.put(track.toJson()) }
        prefs.edit()
            .putString(KEY_TRACKS, array.toString())
            .putInt(KEY_INDEX, index.coerceIn(0, tracks.lastIndex))
            .apply()
    }

    fun read(): SavedQueue? {
        val raw = prefs.getString(KEY_TRACKS, null) ?: return null
        val tracks = runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { index ->
                array.optJSONObject(index)?.toTrack()
            }
        }.getOrDefault(emptyList())
        if (tracks.isEmpty()) return null
        val index = prefs.getInt(KEY_INDEX, 0).coerceIn(0, tracks.lastIndex)
        return SavedQueue(tracks, index)
    }

    fun clear() {
        prefs.edit().remove(KEY_TRACKS).remove(KEY_INDEX).apply()
    }

    private fun Track.toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("title", title)
        put("albumId", albumId ?: JSONObject.NULL)
        put("albumName", albumName ?: JSONObject.NULL)
        put("artistName", artistName ?: JSONObject.NULL)
        put("durationMs", durationMs)
        put("container", container ?: JSONObject.NULL)
        put("imageUrl", imageUrl ?: JSONObject.NULL)
        put("isFavorite", isFavorite)
    }

    private fun JSONObject.toTrack(): Track? {
        val id = optString("id").takeIf { it.isNotBlank() } ?: return null
        return Track(
            id = id,
            title = optString("title").ifBlank { "Unbekannter Titel" },
            albumId = optStringOrNull("albumId"),
            albumName = optStringOrNull("albumName"),
            artistName = optStringOrNull("artistName"),
            durationMs = optLong("durationMs"),
            indexNumber = null,
            discNumber = null,
            container = optStringOrNull("container"),
            bitrate = null,
            imageUrl = optStringOrNull("imageUrl"),
            isFavorite = optBoolean("isFavorite"),
            played = false,
        )
    }

    private fun JSONObject.optStringOrNull(key: String): String? {
        if (isNull(key)) return null
        return optString(key).takeIf { it.isNotBlank() }
    }

    private companion object {
        const val KEY_TRACKS = "tracks"
        const val KEY_INDEX = "index"
    }
}
