package app.k56.player

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Writes uncaught exceptions to a small file, so a crash can be inspected after
 * the app restarted. The file is drained into the debug log on next launch.
 */
object CrashLog {

    private const val FILE_NAME = "crash-log.txt"
    private const val MAX_BYTES = 64 * 1024

    fun install(context: Context) {
        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                val entry = buildString {
                    append(TIME.format(Date()))
                    append("  ")
                    append(thread.name)
                    append('\n')
                    append(android.util.Log.getStackTraceString(error))
                    append("\n\n")
                }
                val file = File(appContext.filesDir, FILE_NAME)
                file.appendText(entry)
                if (file.length() > MAX_BYTES) {
                    file.writeText(entry)
                }
            }
            previous?.uncaughtException(thread, error)
        }
    }

    /** Returns the stored crashes and clears the file, so each one appears once. */
    fun drain(context: Context): String? {
        val file = File(context.filesDir, FILE_NAME)
        if (!file.exists()) return null
        val text = runCatching { file.readText() }.getOrNull().orEmpty()
        runCatching { file.delete() }
        return text.takeIf { it.isNotBlank() }
    }

    private val TIME = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
}
