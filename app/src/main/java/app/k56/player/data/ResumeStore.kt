package app.k56.player.data

import android.content.Context
import org.json.JSONObject

/**
 * A resume point for one track: where it was left off and what it was called.
 * Positions below the threshold (near the start) or near the end of the track
 * are dropped so a fresh start is not forced mid-song or right before the end.
 */
data class ResumePoint(
    val itemId: String,
    val title: String,
    val positionMs: Long,
    val durationMs: Long,
    val savedAt: Long,
)

/**
 * Per-item resume persistence in private SharedPreferences. Written from the
 * playback service so it survives the UI and process restarts; the queue itself
 * is intentionally not persisted here.
 */
class ResumeStore(context: Context) {

    private val prefs = context.getSharedPreferences("k56_resume", Context.MODE_PRIVATE)

    /** Remembers the position for [itemId]; near-start / near-end points are ignored. */
    fun save(itemId: String, title: String, positionMs: Long, durationMs: Long, thresholdMs: Long) {
        if (itemId.isBlank()) return
        if (positionMs < thresholdMs) return
        if (durationMs > 0 && durationMs - positionMs < thresholdMs) return
        val all = readAll().toMutableMap()
        all[itemId] = ResumePoint(itemId, title, positionMs, durationMs, System.currentTimeMillis())
        write(all)
    }

    fun read(itemId: String): ResumePoint? = readAll()[itemId]

    fun clear(itemId: String) {
        val all = readAll()
        if (itemId !in all) return
        write(all - itemId)
    }

    private fun readAll(): Map<String, ResumePoint> {
        val raw = prefs.getString(KEY_POINTS, null) ?: return emptyMap()
        return runCatching {
            val obj = JSONObject(raw)
            buildMap {
                obj.keys().forEach { itemId ->
                    val o = obj.optJSONObject(itemId) ?: return@forEach
                    put(
                        itemId,
                        ResumePoint(
                            itemId = itemId,
                            title = o.optString("title"),
                            positionMs = o.optLong("positionMs"),
                            durationMs = o.optLong("durationMs"),
                            savedAt = o.optLong("savedAt"),
                        ),
                    )
                }
            }
        }.getOrDefault(emptyMap())
    }

    private fun write(points: Map<String, ResumePoint>) {
        val obj = JSONObject()
        points.values.forEach { point ->
            obj.put(
                point.itemId,
                JSONObject().apply {
                    put("title", point.title)
                    put("positionMs", point.positionMs)
                    put("durationMs", point.durationMs)
                    put("savedAt", point.savedAt)
                },
            )
        }
        prefs.edit().putString(KEY_POINTS, obj.toString()).apply()
    }

    private companion object {
        const val KEY_POINTS = "points"
    }
}
