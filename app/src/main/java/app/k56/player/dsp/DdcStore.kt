package app.k56.player.dsp

import android.content.Context
import android.net.Uri
import android.os.Environment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * ViPER DDC library. Bundled profiles ship as assets so the app has a working
 * set on first start; the ViPER4Android `DDC` folders are scanned as well, and
 * a SAF-picked file can be imported when scoped storage blocks direct reads.
 */
class DdcStore(context: Context) {

    private val appContext = context.applicationContext

    val directory: File get() = File(appContext.filesDir, "ddc").apply { mkdirs() }

    fun imported(): List<File> =
        directory.listFiles { file -> file.isFile && file.extension.equals("vdc", true) }
            ?.sortedBy { it.name.lowercase() }
            .orEmpty()

    /** Copies the bundled curves once; later edits by the user are preserved. */
    suspend fun ensureBundled(): Int = withContext(Dispatchers.IO) {
        val marker = File(directory, ".bundled_v1")
        if (marker.exists()) return@withContext 0
        var added = 0
        runCatching {
            appContext.assets.list("ddc")?.forEach { name ->
                val target = File(directory, name)
                if (!target.exists()) {
                    appContext.assets.open("ddc/$name").use { input ->
                        target.outputStream().use { output -> input.copyTo(output) }
                    }
                    added++
                }
            }
        }
        marker.writeText("ok")
        added
    }

    private fun candidateDirs(): List<File> {
        val storage = Environment.getExternalStorageDirectory()
        return listOf(
            File(storage, "ViPER4Android/DDC"),
            File(storage, "ViPERFX/DDC"),
            File(storage, "Android/data/com.pittvandewitt.viperfx/files/DDC"),
            File(storage, "Android/data/ru.tech.im.viperfx/files/DDC"),
            File(storage, "Android/data/com.vipercn.viper4android_v2/files/DDC"),
        ).distinct()
    }

    suspend fun scanAndImport(): Int = withContext(Dispatchers.IO) {
        var added = 0
        for (dir in candidateDirs()) {
            val files = runCatching {
                dir.listFiles { file -> file.isFile && file.extension.equals("vdc", true) }
            }.getOrNull().orEmpty()
            for (source in files) {
                val target = File(directory, source.name)
                if (target.exists() && target.length() == source.length()) continue
                val ok = runCatching {
                    source.copyTo(target, overwrite = true)
                    DdcProfile.parse(target) != null
                }.getOrDefault(false)
                if (ok) added++ else runCatching { target.delete() }
            }
        }
        added
    }

    suspend fun importFromUri(context: Context, uri: Uri): File? = withContext(Dispatchers.IO) {
        val name = (uri.lastPathSegment?.substringAfterLast('/') ?: "profile.vdc")
            .let { if (it.endsWith(".vdc", true)) it else "$it.vdc" }
        val target = File(directory, name)
        val ok = runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            } ?: return@runCatching false
            DdcProfile.parse(target) != null
        }.getOrDefault(false)
        if (ok) target else {
            runCatching { target.delete() }
            null
        }
    }

    fun delete(file: File) {
        if (file.parentFile == directory) runCatching { file.delete() }
    }
}
