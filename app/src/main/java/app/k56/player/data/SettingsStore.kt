package app.k56.player.data

import android.content.Context
import android.os.Environment
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/** Where downloads and the streaming cache are written. */
enum class StorageLocation { INTERNAL, EXTERNAL }

class SettingsStore(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("k56_settings", Context.MODE_PRIVATE)

    private val _forceTranscode = MutableStateFlow(prefs.getBoolean(KEY_FORCE_TRANSCODE, false))
    val forceTranscode: StateFlow<Boolean> = _forceTranscode.asStateFlow()

    private val _preferExternal = MutableStateFlow(prefs.getBoolean(KEY_PREFER_EXTERNAL, false))
    val preferExternal: StateFlow<Boolean> = _preferExternal.asStateFlow()

    private val _storageLocation = MutableStateFlow(
        runCatching { StorageLocation.valueOf(prefs.getString(KEY_STORAGE, null) ?: "INTERNAL") }
            .getOrDefault(StorageLocation.INTERNAL),
    )
    val storageLocation: StateFlow<StorageLocation> = _storageLocation.asStateFlow()

    /** Cache cap in MiB: -1 unlimited, 0 disabled, otherwise LRU size. */
    private val _cacheLimitMb = MutableStateFlow(prefs.getInt(KEY_CACHE_LIMIT, 512))
    val cacheLimitMb: StateFlow<Int> = _cacheLimitMb.asStateFlow()

    fun setForceTranscode(value: Boolean) {
        prefs.edit().putBoolean(KEY_FORCE_TRANSCODE, value).apply()
        _forceTranscode.value = value
    }

    fun setPreferExternal(value: Boolean) {
        prefs.edit().putBoolean(KEY_PREFER_EXTERNAL, value).apply()
        _preferExternal.value = value
    }

    fun setStorageLocation(value: StorageLocation) {
        prefs.edit().putString(KEY_STORAGE, value.name).apply()
        _storageLocation.value = value
    }

    fun setCacheLimitMb(value: Int) {
        prefs.edit().putInt(KEY_CACHE_LIMIT, value).apply()
        _cacheLimitMb.value = value
    }

    companion object {
        private const val KEY_FORCE_TRANSCODE = "force_transcode"
        private const val KEY_PREFER_EXTERNAL = "prefer_external"
        private const val KEY_STORAGE = "storage_location"
        private const val KEY_CACHE_LIMIT = "cache_limit_mb"

        /** The removable card, or null when the device has none. */
        fun externalRoot(context: Context): File? {
            val dirs = context.getExternalFilesDirs(null)
            val candidate = dirs.drop(1).firstOrNull { it != null } ?: return null
            val usable = Environment.isExternalStorageRemovable(candidate) ||
                !candidate.absolutePath.contains("emulated")
            return candidate.takeIf { usable }
        }

        fun freeBytes(dir: File): Long = runCatching { dir.usableSpace }.getOrDefault(0L)
    }
}
