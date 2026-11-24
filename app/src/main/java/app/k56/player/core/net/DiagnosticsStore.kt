package app.k56.player.core.net

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Coarse reason for a failed request, so the UI can name it without the payload. */
enum class DiagnosticsErrorKind { TIMEOUT, DNS, TLS, HTTP, IO }

/** Who issued the request: the API client or the media data source. */
enum class DiagnosticsSource { API, MEDIA }

/**
 * One finished request. [path] is the encoded path only — the query string is
 * dropped on purpose, because tokens and API keys live there.
 *
 * [queryNames] carries the parameter *names* of a failed request and nothing
 * else: a server answering `400` is unreadable without knowing which parameters
 * it rejected, and names cannot carry a credential.
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

    private val _entries = MutableStateFlow<List<DiagnosticsEntry>>(emptyList())

    /** Newest last. */
    val entries: StateFlow<List<DiagnosticsEntry>> = _entries.asStateFlow()

    fun record(entry: DiagnosticsEntry) {
        _entries.update { current ->
            val next = current + entry
            if (next.size <= CAPACITY) next else next.takeLast(CAPACITY)
        }
    }

    fun clear() {
        _entries.value = emptyList()
    }
}
