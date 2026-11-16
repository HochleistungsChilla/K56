package app.k56.player.data.library

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * In-memory ring buffer for the library sync. Deliberately tiny and local:
 * nothing leaves the device unless the user copies or shares the text.
 */
object SyncLog {

    data class Entry(
        val at: Long,
        val level: Level,
        val message: String,
    ) {
        fun format(): String =
            "${TIME.format(Date(at))}  ${level.tag}  $message"
    }

    enum class Level(val tag: String) {
        INFO("INFO"),
        WARN("WARN"),
        ERROR("ERR "),
    }

    private const val MAX_ENTRIES = 400

    private val _entries = MutableStateFlow<List<Entry>>(emptyList())
    val entries: StateFlow<List<Entry>> = _entries.asStateFlow()

    fun info(message: String) = add(Level.INFO, message)

    fun warn(message: String) = add(Level.WARN, message)

    fun error(message: String) = add(Level.ERROR, message)

    fun clear() {
        _entries.value = emptyList()
    }

    /** Plain-text dump for copy/share. */
    fun asText(): String = _entries.value.joinToString("\n") { it.format() }

    private fun add(level: Level, message: String) {
        val next = (_entries.value + Entry(System.currentTimeMillis(), level, message))
            .takeLast(MAX_ENTRIES)
        _entries.value = next
    }

    private val TIME = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
}
