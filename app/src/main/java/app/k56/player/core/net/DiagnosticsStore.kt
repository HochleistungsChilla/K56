package app.k56.player.core.net

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Coarse reason for a failed request, so the UI can name it without the payload. */
enum class DiagnosticsErrorKind { TIMEOUT, DNS, TLS, HTTP, IO }

/** Who produced the entry: the API client, the media data source or the player. */
enum class DiagnosticsSource { API, MEDIA, PLAYER }

/**
 * One finished request. [path] is the encoded path only — the query string is
 * dropped on purpose, because tokens and API keys live there.
 *
 * [queryNames] carries the parameter *names* of a failed request and nothing
 * else: a server answering `400` is unreadable without knowing which parameters
 * it rejected, and names cannot carry a credential.
 *
 * [detail] is filled only in verbose mode: redacted query values
 * ([DiagnosticsRedaction]), or the player state for a [DiagnosticsSource.PLAYER]
 * entry.
 */
data class DiagnosticsEntry(
    val atEpochMs: Long,
    val method: String,
    val path: String,
    val status: Int?,
    val durationMs: Long,
    val errorKind: DiagnosticsErrorKind?,
    val source: DiagnosticsSource = DiagnosticsSource.API,
    val queryNames: List<String> = emptyList(),
    val detail: String? = null,
)

/**
 * In-memory ring buffer of recent HTTP activity, meant for the debug screen.
 *
 * Nothing here is written to disk, logcat or a crash report, and no header,
 * body or query value is stored: only method, path, status, duration and a
 * coarse error class.
 */
object DiagnosticsStore {

    const val CAPACITY = 200

    /** Verbose mode keeps a long history, so a playback start is never pushed out. */
    const val VERBOSE_CAPACITY = 2_000

    @Volatile
    var verbose: Boolean = false
        private set

    @Volatile
    private var capacity: Int = CAPACITY

    private val _entries = MutableStateFlow<List<DiagnosticsEntry>>(emptyList())

    /** Newest last. */
    val entries: StateFlow<List<DiagnosticsEntry>> = _entries.asStateFlow()

    /**
     * Switches the buffer size. Turning verbose off trims back to [CAPACITY]
     * immediately, so the setting costs nothing once it is off again.
     */
    fun setVerbose(enabled: Boolean) {
        verbose = enabled
        capacity = if (enabled) VERBOSE_CAPACITY else CAPACITY
        _entries.update { current ->
            if (current.size <= capacity) current else current.takeLast(capacity)
        }
    }

    fun record(entry: DiagnosticsEntry) {
        _entries.update { current ->
            val next = current + entry
            if (next.size <= capacity) next else next.takeLast(capacity)
        }
    }

    fun clear() {
        _entries.value = emptyList()
    }
}
