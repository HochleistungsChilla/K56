package app.k56.player.data.offline

import android.content.Context
import android.net.Uri
import app.k56.player.core.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

/** One track that exists on this device and can be played without a server. */
data class OfflineTrack(
    val id: String,
    val title: String,
    val artistName: String?,
    val albumName: String?,
    val albumId: String?,
    val durationMs: Long,
    val imageUrl: String?,
    val fileName: String,
    val bytes: Long,
    val savedAt: Long,
) {
    fun toTrack(): Track = Track(
        id = id,
        title = title,
        albumId = albumId,
        albumName = albumName,
        artistName = artistName,
        durationMs = durationMs,
        indexNumber = null,
        discNumber = null,
        container = null,
        bitrate = null,
        imageUrl = imageUrl,
        streamKey = null,
        isFavorite = true,
        played = false,
    )

    fun file(context: Context): File = File(OfflineStore.directory(context), fileName)
    fun uri(context: Context): Uri = Uri.fromFile(file(context))
}

/**
 * Downloads tracks into the app-private directory and keeps an index of what is
 * on disk. Deliberately independent of the backend: it is handed a resolved
 * download URL, so it works for every adapter that can produce a static stream.
 */
class OfflineStore(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("k56_offline", Context.MODE_PRIVATE)

    private val _tracks = MutableStateFlow(readIndex())
    val tracks: StateFlow<List<OfflineTrack>> = _tracks.asStateFlow()

    private val _progress = MutableStateFlow<Map<String, Float>>(emptyMap())
    val progress: StateFlow<Map<String, Float>> = _progress.asStateFlow()

    private val _active = MutableStateFlow<Set<String>>(emptySet())
    val active: StateFlow<Set<String>> = _active.asStateFlow()

    private val cancelled = ConcurrentHashMap.newKeySet<String>()

    fun isDownloaded(id: String): Boolean = _tracks.value.any { it.id == id }

    fun find(id: String): OfflineTrack? = _tracks.value.firstOrNull { it.id == id }

    fun cancel(id: String) {
        cancelled += id
    }

    /**
     * Streams [url] to disk with progress. Returns the stored entry, or null when
     * the transfer was cancelled or failed. Throws on HTTP errors so the caller
     * can surface a message.
     */
    suspend fun download(
        track: Track,
        url: String,
        headers: Map<String, String> = emptyMap(),
    ): OfflineTrack? = withContext(Dispatchers.IO) {
        if (isDownloaded(track.id)) return@withContext find(track.id)
        val dir = directory(appContext).apply { mkdirs() }
        val target = File(dir, fileNameFor(track.id))
        val temp = File(dir, target.name + ".part")

        cancelled -= track.id
        _active.value = _active.value + track.id
        _progress.value = _progress.value + (track.id to 0f)
        try {
            var connection: HttpURLConnection? = null
            try {
                connection = (URL(url).openConnection() as HttpURLConnection).apply {
                    headers.forEach { (key, value) -> setRequestProperty(key, value) }
                    connectTimeout = 20_000
                    readTimeout = 30_000
                    instanceFollowRedirects = true
                    requestMethod = "GET"
                }
                val code = connection.responseCode
                if (code !in 200..299) {
                    throw IllegalStateException("HTTP $code")
                }
                val length = connection.contentLengthLong.coerceAtLeast(0L)
                connection.inputStream.use { input ->
                    temp.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var written = 0L
                        while (true) {
                            if (track.id in cancelled) break
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            written += read
                            if (length > 0) {
                                _progress.value = _progress.value + (track.id to (written.toFloat() / length))
                            }
                        }
                    }
                }
            } finally {
                connection?.disconnect()
            }

            if (track.id in cancelled) {
                temp.delete()
                return@withContext null
            }
            if (!temp.exists() || temp.length() == 0L) {
                temp.delete()
                throw IllegalStateException("Leere Datei")
            }
            if (target.exists()) target.delete()
            if (!temp.renameTo(target)) {
                temp.delete()
                throw IllegalStateException("Konnte Datei nicht ablegen")
            }

            val entry = OfflineTrack(
                id = track.id,
                title = track.title,
                artistName = track.artistName,
                albumName = track.albumName,
                albumId = track.albumId,
                durationMs = track.durationMs,
                imageUrl = track.imageUrl,
                fileName = target.name,
                bytes = target.length(),
                savedAt = System.currentTimeMillis(),
            )
            _tracks.value = (_tracks.value.filterNot { it.id == entry.id } + entry)
            writeIndex(_tracks.value)
            entry
        } finally {
            cancelled -= track.id
            _active.value = _active.value - track.id
            _progress.value = _progress.value - track.id
        }
    }

    /** Removes every track of one album; returns the number deleted. */
    fun deleteAlbum(albumId: String): Int {
        val doomed = _tracks.value.filter { it.albumId == albumId }
        doomed.forEach { delete(it.id) }
        return doomed.size
    }

    fun delete(id: String) {
        val entry = _tracks.value.firstOrNull { it.id == id } ?: return
        runCatching { File(directory(appContext), entry.fileName).delete() }
        _tracks.value = _tracks.value.filterNot { it.id == id }
        writeIndex(_tracks.value)
    }

    fun totalBytes(): Long = _tracks.value.sumOf { it.bytes }

    private fun readIndex(): List<OfflineTrack> {
        val raw = prefs.getString(KEY_INDEX, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { index ->
                val o = array.optJSONObject(index) ?: return@mapNotNull null
                val id = o.optString("id").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val fileName = o.optString("fileName")
                if (fileName.isBlank()) return@mapNotNull null
                // Drop entries whose file disappeared (cache cleaner, uninstall leftovers).
                if (!File(directory(appContext), fileName).exists()) return@mapNotNull null
                OfflineTrack(
                    id = id,
                    title = o.optString("title"),
                    artistName = o.optStringOrNull("artistName"),
                    albumName = o.optStringOrNull("albumName"),
                    albumId = o.optStringOrNull("albumId"),
                    durationMs = o.optLong("durationMs"),
                    imageUrl = o.optStringOrNull("imageUrl"),
                    fileName = fileName,
                    bytes = o.optLong("bytes"),
                    savedAt = o.optLong("savedAt"),
                )
            }
        }.getOrDefault(emptyList())
    }

    private fun writeIndex(entries: List<OfflineTrack>) {
        val array = JSONArray()
        entries.forEach { entry ->
            array.put(
                JSONObject().apply {
                    put("id", entry.id)
                    put("title", entry.title)
                    put("artistName", entry.artistName ?: JSONObject.NULL)
                    put("albumName", entry.albumName ?: JSONObject.NULL)
                    put("albumId", entry.albumId ?: JSONObject.NULL)
                    put("durationMs", entry.durationMs)
                    put("imageUrl", entry.imageUrl ?: JSONObject.NULL)
                    put("fileName", entry.fileName)
                    put("bytes", entry.bytes)
                    put("savedAt", entry.savedAt)
                },
            )
        }
        prefs.edit().putString(KEY_INDEX, array.toString()).apply()
    }

    private fun JSONObject.optStringOrNull(key: String): String? {
        if (isNull(key)) return null
        return optString(key).takeIf { it.isNotBlank() }
    }

    companion object {
        private const val KEY_INDEX = "index"

        fun directory(context: Context): File = File(context.filesDir, "offline")

        /** File names must not leak into path traversal; ids are server GUIDs. */
        fun fileNameFor(id: String): String = id.replace(Regex("[^A-Za-z0-9._-]"), "_")
    }
}
