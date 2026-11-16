package app.k56.player.data.offline

import android.content.Context
import android.net.Uri
import app.k56.player.core.model.Track
import app.k56.player.data.DownloadFormat
import app.k56.player.data.DownloadQuality
import app.k56.player.data.SettingsStore
import app.k56.player.data.StorageLocation
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
    val storage: StorageLocation = StorageLocation.INTERNAL,
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

    fun file(context: Context): File =
        File(OfflineStore.directoryFor(context, storage), fileName)

    fun uri(context: Context): Uri = Uri.fromFile(file(context))
}

/**
 * Downloads tracks into app storage and keeps an index of what is on disk.
 * Each entry remembers where it was written, so switching between internal
 * storage and the SD card never orphans existing files.
 */
class OfflineStore(context: Context, private val settingsStore: SettingsStore) {

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

    /** Bytes used in one storage location, for the settings summary. */
    fun bytesIn(location: StorageLocation): Long =
        _tracks.value.filter { it.storage == location }.sumOf { it.bytes }

    /**
     * Streams [url] to disk with progress. Returns the stored entry, or null when
     * the transfer was cancelled. Throws on HTTP errors so the caller can surface
     * a message.
     */
    suspend fun download(
        track: Track,
        url: String,
        headers: Map<String, String> = emptyMap(),
    ): OfflineTrack? = withContext(Dispatchers.IO) {
        if (isDownloaded(track.id)) return@withContext find(track.id)

        var location = settingsStore.storageLocation.value
        var dir = directoryFor(appContext, location)
        // No card present (or not writable): fall back to internal storage instead of failing.
        if (!dir.exists() && !dir.mkdirs()) {
            location = StorageLocation.INTERNAL
            dir = directoryFor(appContext, location).apply { mkdirs() }
        }

        // A transcoded download needs a container extension so players and
        // external apps can identify it; originals keep their server bytes.
        val suffix = if (settingsStore.downloadQuality.value == DownloadQuality.ORIGINAL) {
            ""
        } else {
            when (settingsStore.downloadFormat.value) {
                DownloadFormat.MP3 -> ".mp3"
                DownloadFormat.AAC -> ".m4a"
            }
        }
        val target = File(dir, fileNameFor(track.id) + suffix)
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
                storage = location,
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
        runCatching { File(directoryFor(appContext, entry.storage), entry.fileName).delete() }
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
                val storage = runCatching {
                    StorageLocation.valueOf(o.optString("storage", "INTERNAL"))
                }.getOrDefault(StorageLocation.INTERNAL)
                // Drop entries whose file disappeared (cleaner run, card removed).
                if (!File(directoryFor(appContext, storage), fileName).exists()) return@mapNotNull null
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
                    storage = storage,
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
                    put("storage", entry.storage.name)
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

        fun directoryFor(context: Context, location: StorageLocation): File {
            val base = when (location) {
                StorageLocation.INTERNAL -> File(context.filesDir, "offline")
                StorageLocation.EXTERNAL ->
                    SettingsStore.externalRoot(context)?.let { File(it, "offline") }
                        ?: File(context.filesDir, "offline")
            }
            return base
        }

        /** File names must not leak into path traversal; ids are server GUIDs. */
        fun fileNameFor(id: String): String = id.replace(Regex("[^A-Za-z0-9._-]"), "_")
    }
}
