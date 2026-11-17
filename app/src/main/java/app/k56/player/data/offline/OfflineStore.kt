package app.k56.player.data.offline

import android.content.Context
import android.net.Uri
import app.k56.player.core.model.Track
import app.k56.player.core.net.NetworkMonitor
import app.k56.player.core.net.NetworkTransport
import app.k56.player.data.DownloadFormat
import app.k56.player.data.DownloadQuality
import app.k56.player.data.SettingsStore
import app.k56.player.data.StorageLocation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
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

/** A queued download: the track plus the resolved source to fetch. */
data class DownloadRequest(
    val track: Track,
    val url: String,
    val headers: Map<String, String> = emptyMap(),
)

/** Lifecycle of one queued download. */
enum class DownloadState { QUEUED, ACTIVE, DONE, FAILED }

/**
 * One persisted queue entry. `url`/`headers` are extra fields beyond the
 * display metadata: they are the resolved source, so a restart can resume the
 * download without a fresh server round-trip.
 */
data class QueueEntry(
    val trackId: String,
    val title: String,
    val artist: String?,
    val album: String?,
    val albumId: String?,
    val durationMs: Long,
    val imageUrl: String?,
    val quality: String,
    val format: String,
    val state: DownloadState,
    val attempts: Int,
    val bytes: Long,
    val totalBytes: Long,
    val createdAt: Long,
    val url: String,
    val headers: Map<String, String>,
) {
    fun toRequest(): DownloadRequest = DownloadRequest(
        track = Track(
            id = trackId,
            title = title,
            albumId = albumId,
            albumName = album,
            artistName = artist,
            durationMs = durationMs,
            indexNumber = null,
            discNumber = null,
            container = null,
            bitrate = null,
            imageUrl = imageUrl,
            isFavorite = true,
        ),
        url = url,
        headers = headers,
    )
}

/** Aggregate state of the whole download queue, for a single progress line. */
data class DownloadQueueState(
    val total: Int = 0,
    val done: Int = 0,
    val active: Int = 0,
    val failed: Int = 0,
) {
    val running: Boolean get() = total > 0 && done + failed < total
}

/**
 * Downloads tracks into app storage and keeps an index of what is on disk.
 * The pending queue is persisted next to the downloads so an app kill and a
 * network change never leave half-finished work behind.
 */
class OfflineStore(
    context: Context,
    private val settingsStore: SettingsStore,
    private val networkMonitor: NetworkMonitor,
) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("k56_offline", Context.MODE_PRIVATE)

    private val _tracks = MutableStateFlow(readIndex())
    val tracks: StateFlow<List<OfflineTrack>> = _tracks.asStateFlow()

    private val _progress = MutableStateFlow<Map<String, Float>>(emptyMap())
    val progress: StateFlow<Map<String, Float>> = _progress.asStateFlow()

    private val _active = MutableStateFlow<Set<String>>(emptySet())
    val active: StateFlow<Set<String>> = _active.asStateFlow()

    private val _streamed = MutableStateFlow(readStreamed())
    val streamed: StateFlow<Set<String>> = _streamed.asStateFlow()

    private val cancelled = ConcurrentHashMap.newKeySet<String>()
    private val processingIds = ConcurrentHashMap.newKeySet<String>()

    /** The whole ordered queue: queued, active, done and failed entries. */
    private val _entries = MutableStateFlow<List<QueueEntry>>(emptyList())
    val entries: StateFlow<List<QueueEntry>> = _entries.asStateFlow()

    private val _paused = MutableStateFlow(false)
    val paused: StateFlow<Boolean> = _paused.asStateFlow()

    /** Set when a restart resumed queued work — drives a subtle startup notice. */
    private val _resumed = MutableStateFlow(false)
    val resumed: StateFlow<Boolean> = _resumed.asStateFlow()

    private val _queue = MutableStateFlow(DownloadQueueState())
    val queue: StateFlow<DownloadQueueState> = _queue.asStateFlow()

    private val downloadScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var queueVersion = 0
    @Volatile private var persistScheduled = false

    init {
        downloadScope.launch {
            cleanupParts()
            restore()
        }
        downloadScope.launch {
            // Recompute the aggregate when the network changes (wifi-only pause).
            networkMonitor.status.collect { refreshAggregate() }
        }
    }

    fun isDownloaded(id: String): Boolean = _tracks.value.any { it.id == id }

    /** Record a track that just streamed — a proxy for the rolling play cache. */
    fun markStreamed(id: String) {
        if (id.isBlank() || id in _streamed.value) return
        val updated = _streamed.value.toMutableSet().apply { add(id) }
        if (updated.size > MAX_STREAMED) {
            val iterator = updated.iterator()
            repeat(updated.size - MAX_STREAMED) {
                if (iterator.hasNext()) {
                    iterator.next()
                    iterator.remove()
                }
            }
        }
        _streamed.value = updated
        prefs.edit().putStringSet(KEY_STREAMED, updated).apply()
    }

    fun find(id: String): OfflineTrack? = _tracks.value.firstOrNull { it.id == id }

    fun cancel(id: String) {
        cancelled += id
    }

    /** Queues a batch; up to [SettingsStore.concurrentDownloads] run at once. */
    fun enqueue(requests: List<DownloadRequest>) {
        val existing = _entries.value.map { it.trackId }.toSet()
        val now = System.currentTimeMillis()
        val fresh = requests.distinctBy { it.track.id }.mapNotNull { req ->
            if (isDownloaded(req.track.id) || req.track.id in existing) return@mapNotNull null
            QueueEntry(
                trackId = req.track.id,
                title = req.track.title,
                artist = req.track.artistName,
                album = req.track.albumName,
                albumId = req.track.albumId,
                durationMs = req.track.durationMs,
                imageUrl = req.track.imageUrl,
                quality = settingsStore.downloadQuality.value.name,
                format = settingsStore.downloadFormat.value.name,
                state = DownloadState.QUEUED,
                attempts = 0,
                bytes = 0,
                totalBytes = 0,
                createdAt = now,
                url = req.url,
                headers = req.headers,
            )
        }
        if (fresh.isEmpty()) return
        _entries.value = _entries.value + fresh
        persistQueue()
        refreshAggregate()
        pump(fresh.map { it.trackId })
    }

    /** Loads the persisted queue, reconciles it with disk, and resumes. */
    private fun restore() {
        val saved = readQueue()
        if (saved.isEmpty()) return
        val reconciled = saved.map { entry ->
            if (isDownloaded(entry.trackId)) {
                entry.copy(state = DownloadState.DONE)
            } else {
                entry.copy(state = DownloadState.QUEUED, attempts = 0)
            }
        }
        _entries.value = reconciled
        persistQueue()
        refreshAggregate()
        val toResume = reconciled.filter { it.state == DownloadState.QUEUED }
        if (toResume.isNotEmpty()) {
            _resumed.value = true
            pump(toResume.map { it.trackId })
        }
    }

    /** Launches a worker per queued id, capped by the concurrency setting. */
    private fun pump(trackIds: List<String>) {
        val version = queueVersion
        val permits = settingsStore.concurrentDownloads.value.coerceIn(1, 5)
        val semaphore = Semaphore(permits)
        trackIds.forEach { trackId ->
            if (!processingIds.add(trackId)) return@forEach
            downloadScope.launch {
                try {
                    semaphore.withPermit {
                        if (version != queueVersion) return@withPermit
                        val entry = _entries.value.firstOrNull { it.trackId == trackId }
                            ?: return@withPermit
                        if (entry.state != DownloadState.QUEUED) return@withPermit
                        waitForAllowed(version)
                        if (version != queueVersion) return@withPermit
                        setState(trackId, DownloadState.ACTIVE)
                        refreshAggregate()
                        val result = downloadWithAttempts(entry.toRequest())
                        if (result != null) {
                            setState(trackId, DownloadState.DONE) {
                                it.copy(bytes = result.bytes, attempts = it.attempts + 1)
                            }
                        } else {
                            setState(trackId, DownloadState.FAILED) {
                                it.copy(attempts = it.attempts + 1)
                            }
                        }
                        persistQueue()
                        refreshAggregate()
                    }
                } finally {
                    processingIds.remove(trackId)
                }
            }
        }
    }

    private fun setState(
        trackId: String,
        state: DownloadState,
        transform: (QueueEntry) -> QueueEntry = { it },
    ) {
        _entries.value = _entries.value.map { entry ->
            if (entry.trackId == trackId) transform(entry).copy(state = state) else entry
        }
    }

    private fun refreshAggregate() {
        val list = _entries.value
        _queue.value = DownloadQueueState(
            total = list.size,
            done = list.count { it.state == DownloadState.DONE },
            active = list.count { it.state == DownloadState.ACTIVE },
            failed = list.count { it.state == DownloadState.FAILED },
        )
    }

    /** Pauses the queue: running downloads finish, new ones wait. */
    fun pause() {
        _paused.value = true
    }

    fun resume() {
        _paused.value = false
    }

    /** Re-queues every failed entry (attempts reset). */
    fun retryFailed() {
        val failed = _entries.value.filter { it.state == DownloadState.FAILED }
        if (failed.isEmpty()) return
        _entries.value = _entries.value.map {
            if (it.state == DownloadState.FAILED) {
                it.copy(state = DownloadState.QUEUED, attempts = 0)
            } else {
                it
            }
        }
        persistQueue()
        refreshAggregate()
        pump(failed.map { it.trackId })
    }

    /** Removes finished entries from the list (they are never persisted anyway). */
    fun clearFinished() {
        _entries.value = _entries.value.filterNot { it.state == DownloadState.DONE }
        persistQueue()
        refreshAggregate()
    }

    /** Stops queued and in-flight downloads, keeping done + failed for the UI. */
    fun cancelAll() {
        queueVersion++
        _active.value.toList().forEach { cancelled += it }
        _entries.value = _entries.value.filterNot {
            it.state == DownloadState.QUEUED || it.state == DownloadState.ACTIVE
        }
        persistQueue()
        refreshAggregate()
    }

    /** Three attempts, so a transient error does not fail the batch. */
    private suspend fun downloadWithAttempts(request: DownloadRequest): OfflineTrack? {
        repeat(MAX_ATTEMPTS) {
            val result = try {
                download(request.track, request.url, request.headers)
            } catch (_: Exception) {
                null
            }
            if (result != null) return result
            if (request.track.id in cancelled) return null
        }
        return null
    }

    /** Waits until downloads are allowed: not paused, and Wi-Fi if the rule is on. */
    private suspend fun waitForAllowed(version: Int) {
        while (version == queueVersion) {
            val status = networkMonitor.status.value
            if (!_paused.value && networkAllows(status)) return
            // React to a pause-toggle or a network change without polling.
            combine(_paused, networkMonitor.status) { p, s -> p to s }
                .first { (p, s) -> !p && networkAllows(s) }
        }
    }

    private fun networkAllows(status: app.k56.player.core.net.NetworkStatus): Boolean {
        if (!settingsStore.wifiOnlyDownloads.value) return status.online
        return status.transport == NetworkTransport.WIFI || status.transport == NetworkTransport.ETHERNET
    }

    /** Removes left-over `.part` files from a previous run. */
    private fun cleanupParts() {
        listOf(StorageLocation.INTERNAL, StorageLocation.EXTERNAL).forEach { location ->
            runCatching {
                directoryFor(appContext, location).listFiles()
                    ?.filter { it.name.endsWith(".part") }
                    ?.forEach { it.delete() }
            }
        }
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

    private fun readStreamed(): Set<String> =
        prefs.getStringSet(KEY_STREAMED, emptySet()) ?: emptySet()

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

    // ------------------------------------------------------ queue persistence

    private val queueFile: File
        get() = File(directoryFor(appContext, StorageLocation.INTERNAL), "queue.json")

    /** Debounced write: at most one flush per [PERSIST_DEBOUNCE_MS], no per-byte writes. */
    private fun persistQueue() {
        if (persistScheduled) return
        persistScheduled = true
        downloadScope.launch {
            delay(PERSIST_DEBOUNCE_MS)
            persistScheduled = false
            writeQueueNow()
        }
    }

    private fun writeQueueNow() {
        val array = JSONArray()
        // Only pending work is persisted; done entries are pruned so the file
        // never grows without bound.
        _entries.value.filter { it.state != DownloadState.DONE }.forEach { array.put(it.toJson()) }
        val file = queueFile
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(array.toString())
        if (!tmp.renameTo(file)) {
            file.delete()
            tmp.renameTo(file)
        }
    }

    private fun readQueue(): List<QueueEntry> {
        val file = queueFile
        if (!file.exists()) return emptyList()
        return runCatching {
            val array = JSONArray(file.readText())
            (0 until array.length()).mapNotNull { index ->
                array.optJSONObject(index)?.toQueueEntry()
            }
        }.getOrDefault(emptyList())
    }

    private fun QueueEntry.toJson(): JSONObject = JSONObject().apply {
        put("trackId", trackId)
        put("title", title)
        put("artist", artist ?: JSONObject.NULL)
        put("album", album ?: JSONObject.NULL)
        put("albumId", albumId ?: JSONObject.NULL)
        put("durationMs", durationMs)
        put("imageUrl", imageUrl ?: JSONObject.NULL)
        put("quality", quality)
        put("format", format)
        put("state", state.name)
        put("attempts", attempts)
        put("bytes", bytes)
        put("totalBytes", totalBytes)
        put("createdAt", createdAt)
        put("url", url)
        val h = JSONObject()
        headers.forEach { (k, v) -> h.put(k, v) }
        put("headers", h)
    }

    private fun JSONObject.toQueueEntry(): QueueEntry? {
        val id = optString("trackId").takeIf { it.isNotBlank() } ?: return null
        val resolvedUrl = optString("url")
        if (resolvedUrl.isBlank()) return null
        val state = runCatching { DownloadState.valueOf(optString("state", "QUEUED")) }
            .getOrDefault(DownloadState.QUEUED)
        val headers = optJSONObject("headers")?.let { h ->
            buildMap { h.keys().forEach { key -> put(key, h.optString(key)) } }
        } ?: emptyMap()
        return QueueEntry(
            trackId = id,
            title = optString("title").ifBlank { "Unbekannter Titel" },
            artist = optStringOrNull("artist"),
            album = optStringOrNull("album"),
            albumId = optStringOrNull("albumId"),
            durationMs = optLong("durationMs"),
            imageUrl = optStringOrNull("imageUrl"),
            quality = optString("quality", "ORIGINAL"),
            format = optString("format", "MP3"),
            state = state,
            attempts = optInt("attempts"),
            bytes = optLong("bytes"),
            totalBytes = optLong("totalBytes"),
            createdAt = optLong("createdAt"),
            url = resolvedUrl,
            headers = headers,
        )
    }

    companion object {
        private const val KEY_INDEX = "index"
        private const val KEY_STREAMED = "streamed"
        private const val MAX_STREAMED = 2000
        private const val MAX_ATTEMPTS = 3
        private const val PERSIST_DEBOUNCE_MS = 500L

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
