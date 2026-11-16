package app.k56.player.data

import android.content.Context
import android.os.Environment
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/** Where downloads and the streaming cache are written. */
enum class StorageLocation { INTERNAL, EXTERNAL }

/** Download quality. ORIGINAL copies the file untouched; the rest transcode. */
enum class DownloadQuality(val kbps: Int) {
    ORIGINAL(0),
    KBPS_320(320),
    KBPS_192(192),
    KBPS_128(128),
}

/** Container used when a download is transcoded. */
enum class DownloadFormat { AAC, MP3 }

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

    private val _downloadQuality = MutableStateFlow(
        runCatching { DownloadQuality.valueOf(prefs.getString(KEY_DOWNLOAD_QUALITY, null) ?: "ORIGINAL") }
            .getOrDefault(DownloadQuality.ORIGINAL),
    )
    val downloadQuality: StateFlow<DownloadQuality> = _downloadQuality.asStateFlow()

    private val _downloadFormat = MutableStateFlow(
        runCatching { DownloadFormat.valueOf(prefs.getString(KEY_DOWNLOAD_FORMAT, null) ?: "AAC") }
            .getOrDefault(DownloadFormat.AAC),
    )
    val downloadFormat: StateFlow<DownloadFormat> = _downloadFormat.asStateFlow()

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

    fun setDownloadQuality(value: DownloadQuality) {
        prefs.edit().putString(KEY_DOWNLOAD_QUALITY, value.name).apply()
        _downloadQuality.value = value
    }

    fun setDownloadFormat(value: DownloadFormat) {
        prefs.edit().putString(KEY_DOWNLOAD_FORMAT, value.name).apply()
        _downloadFormat.value = value
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
        private const val KEY_DOWNLOAD_QUALITY = "download_quality"
        private const val KEY_DOWNLOAD_FORMAT = "download_format"

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
