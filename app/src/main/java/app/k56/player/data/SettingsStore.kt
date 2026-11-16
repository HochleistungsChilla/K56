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

    /** Interface options. */
    private val _keepScreenOn = MutableStateFlow(prefs.getBoolean(KEY_KEEP_SCREEN_ON, true))
    val keepScreenOn: StateFlow<Boolean> = _keepScreenOn.asStateFlow()

    private val _showTrackNumbers = MutableStateFlow(prefs.getBoolean(KEY_TRACK_NUMBERS, true))
    val showTrackNumbers: StateFlow<Boolean> = _showTrackNumbers.asStateFlow()

    private val _wifiOnlyDownloads = MutableStateFlow(prefs.getBoolean(KEY_WIFI_ONLY, true))
    val wifiOnlyDownloads: StateFlow<Boolean> = _wifiOnlyDownloads.asStateFlow()

    private val _ignoreArticles = MutableStateFlow(prefs.getBoolean(KEY_IGNORE_ARTICLES, true))
    val ignoreArticles: StateFlow<Boolean> = _ignoreArticles.asStateFlow()

    /** Ordered home-page sections the user wants to see. */
    private val _homeSections = MutableStateFlow(
        (prefs.getString(KEY_HOME_SECTIONS, null) ?: DEFAULT_HOME_SECTIONS)
            .split(',')
            .filter { it.isNotBlank() },
    )
    val homeSections: StateFlow<List<String>> = _homeSections.asStateFlow()

    private val _libraryColumns = MutableStateFlow(prefs.getInt(KEY_COLUMNS, 0))
    val libraryColumns: StateFlow<Int> = _libraryColumns.asStateFlow()

    /** Keep the queue going with similar tracks when it runs out. */
    private val _autoplay = MutableStateFlow(prefs.getBoolean(KEY_AUTOPLAY, true))
    val autoplay: StateFlow<Boolean> = _autoplay.asStateFlow()

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

    fun setKeepScreenOn(value: Boolean) {
        prefs.edit().putBoolean(KEY_KEEP_SCREEN_ON, value).apply()
        _keepScreenOn.value = value
    }

    fun setShowTrackNumbers(value: Boolean) {
        prefs.edit().putBoolean(KEY_TRACK_NUMBERS, value).apply()
        _showTrackNumbers.value = value
    }

    fun setWifiOnlyDownloads(value: Boolean) {
        prefs.edit().putBoolean(KEY_WIFI_ONLY, value).apply()
        _wifiOnlyDownloads.value = value
    }

    fun setIgnoreArticles(value: Boolean) {
        prefs.edit().putBoolean(KEY_IGNORE_ARTICLES, value).apply()
        _ignoreArticles.value = value
    }

    fun applyHomeSections(value: List<String>) {
        prefs.edit().putString(KEY_HOME_SECTIONS, value.joinToString(",")).apply()
        _homeSections.value = value
    }

    fun setLibraryColumns(value: Int) {
        prefs.edit().putInt(KEY_COLUMNS, value).apply()
        _libraryColumns.value = value
    }

    fun setAutoplay(value: Boolean) {
        prefs.edit().putBoolean(KEY_AUTOPLAY, value).apply()
        _autoplay.value = value
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
        private const val KEY_AUTOPLAY = "autoplay"
        private const val KEY_KEEP_SCREEN_ON = "keep_screen_on"
        private const val KEY_TRACK_NUMBERS = "track_numbers"
        private const val KEY_COLUMNS = "library_columns"
        private const val KEY_HOME_SECTIONS = "home_sections"
        private const val KEY_WIFI_ONLY = "wifi_only_downloads"
        private const val KEY_IGNORE_ARTICLES = "ignore_articles"
        private const val KEY_DOWNLOAD_QUALITY = "download_quality"
        private const val KEY_DOWNLOAD_FORMAT = "download_format"

        val DEFAULT_HOME_SECTIONS = "recent,random_albums,random_artists"

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
