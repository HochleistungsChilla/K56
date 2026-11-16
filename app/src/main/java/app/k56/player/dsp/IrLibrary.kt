package app.k56.player.dsp

import android.content.Context
import android.net.Uri
import android.os.Environment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Impulse-response library. Kernels are ordinary WAV files, which is exactly
 * what ViPER4Android stores under `ViPER4Android/Kernel` with an `.irs` suffix.
 *
 * Scanning happens twice: once over the well-known V4A directories (works when
 * storage is readable) and, once the user picks a folder through SAF, on every
 * start from the persisted tree — so new kernels land in the list without any
 * further taps.
 */
class IrLibrary(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("k56_dsp", Context.MODE_PRIVATE)

    val directory: File get() = File(appContext.filesDir, "irs").apply { mkdirs() }

    /** Files currently imported, sorted by name. */
    fun imported(): List<File> =
        directory.listFiles { file -> file.isFile && file.extension.lowercase() in KERNEL_EXTENSIONS }
            ?.sortedBy { it.name.lowercase() }
            .orEmpty()

    /** Well-known kernel folders, most specific first. */
    private fun candidateDirs(): List<File> {
        val storage = Environment.getExternalStorageDirectory()
        val roots = listOf(
            File(storage, "ViPER4Android/Kernel"),
            File(storage, "ViPERFX/Kernel"),
            File(storage, "Android/data/com.pittvandewitt.viperfx/files/Kernel"),
            File(storage, "Android/data/ru.tech.im.viperfx/files/Kernel"),
            File(storage, "Android/data/com.vipercn.viper4android_v2/files/Kernel"),
        )
        val picked = prefs.getString(KEY_TREE, null)?.let { File(it) }
        return (listOfNotNull(picked) + roots).distinct()
    }

    fun hasFolderAccess(): Boolean = prefs.getString(KEY_TREE, null) != null

    /** Persists a SAF tree so later starts can rescan it. */
    fun rememberFolder(context: Context, uri: Uri) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
        val path = runCatching { uri.path }.getOrNull() ?: return
        // content://.../tree/primary%3AViPER4Android%2FKernel -> /storage/emulated/0/...
        val decoded = Uri.decode(path.substringAfter("/tree/"))
        val resolved = when {
            decoded.startsWith("primary:") ->
                File(Environment.getExternalStorageDirectory(), decoded.removePrefix("primary:"))
            decoded.contains(':') -> {
                val (volume, relative) = decoded.split(':', limit = 2)
                File("/storage/$volume", relative)
            }
            else -> File(decoded)
        }
        prefs.edit().putString(KEY_TREE, resolved.absolutePath).apply()
    }

    /**
     * Imports every readable kernel. Returns the number of newly added files.
     * Existing imports are skipped by name and size, so repeated scans are cheap.
     */
    suspend fun scanAndImport(): Int = withContext(Dispatchers.IO) {
        val existing = imported().associateBy { it.name }
        var added = 0
        for (dir in candidateDirs()) {
            val files = runCatching {
                dir.listFiles { file -> file.isFile && file.extension.lowercase() in KERNEL_EXTENSIONS }
            }.getOrNull().orEmpty()
            for (source in files) {
                val name = source.name
                val already = existing[name]
                if (already != null && already.length() == source.length()) continue
                val target = File(directory, name)
                val copied = runCatching {
                    source.inputStream().use { input ->
                        target.outputStream().use { output -> input.copyTo(output) }
                    }
                    // Reject files that only pretend to be WAV.
                    WavReader.read(target)
                    target.length()
                }.isSuccess
                if (copied) {
                    added++
                } else {
                    runCatching { target.delete() }
                }
            }
        }
        added
    }

    /** Copies one picker document into the library. Returns the stored file. */
    suspend fun importFromUri(context: Context, uri: Uri): File? = withContext(Dispatchers.IO) {
        val name = (uri.lastPathSegment?.substringAfterLast('/') ?: "kernel.irs")
            .let { if (it.contains('.')) it else "$it.irs" }
        val target = File(directory, name)
        val ok = runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            } ?: return@runCatching false
            WavReader.read(target)
            true
        }.getOrDefault(false)
        if (ok) target else {
            runCatching { target.delete() }
            null
        }
    }

    fun delete(file: File) {
        if (file.parentFile == directory) runCatching { file.delete() }
    }

    companion object {
        private const val KEY_TREE = "v4a_tree"
        private val KERNEL_EXTENSIONS = setOf("irs", "wav")
    }
}
