package app.k56.player.data

import android.content.Context
import android.os.Environment
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
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

/** Locked screen orientation. */
enum class ScreenOrientation { AUTO, PORTRAIT, LANDSCAPE }

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

    /** Show the artist · album subtitle line under each track title. */
    private val _showTrackSubtitle = MutableStateFlow(prefs.getBoolean(KEY_SHOW_SUBTITLE, true))
    val showTrackSubtitle: StateFlow<Boolean> = _showTrackSubtitle.asStateFlow()

    /** Show the item count of each media list. */
    private val _showItemCount = MutableStateFlow(prefs.getBoolean(KEY_SHOW_ITEM_COUNT, false))
    val showItemCount: StateFlow<Boolean> = _showItemCount.asStateFlow()

    /** Locked screen orientation. */
    private val _screenOrientation = MutableStateFlow(
        runCatching { ScreenOrientation.valueOf(prefs.getString(KEY_ORIENTATION, null) ?: "AUTO") }
            .getOrDefault(ScreenOrientation.AUTO),
    )
    val screenOrientation: StateFlow<ScreenOrientation> = _screenOrientation.asStateFlow()

    /** Spacing between library grid columns, in dp. */
    private val _gridSpacing = MutableStateFlow(prefs.getInt(KEY_GRID_SPACING, 12))
    val gridSpacing: StateFlow<Int> = _gridSpacing.asStateFlow()

    private val _wifiOnlyDownloads = MutableStateFlow(prefs.getBoolean(KEY_WIFI_ONLY, true))
    val wifiOnlyDownloads: StateFlow<Boolean> = _wifiOnlyDownloads.asStateFlow()

    /** Download every favorited track automatically so it is available offline. */
    private val _autoCacheFavorites = MutableStateFlow(prefs.getBoolean(KEY_AUTO_CACHE_FAVORITES, false))
    val autoCacheFavorites: StateFlow<Boolean> = _autoCacheFavorites.asStateFlow()

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

    /** Wrap around to the first track when the queue reaches the end. */
    private val _circularQueue = MutableStateFlow(prefs.getBoolean(KEY_CIRCULAR_QUEUE, false))
    val circularQueue: StateFlow<Boolean> = _circularQueue.asStateFlow()

    /** Cache cap in MiB: -1 unlimited, 0 disabled, otherwise LRU size. */
    private val _cacheLimitMb = MutableStateFlow(prefs.getInt(KEY_CACHE_LIMIT, 512))
    val cacheLimitMb: StateFlow<Int> = _cacheLimitMb.asStateFlow()

    /** Minimum played seconds before a track position is remembered for resume. */
    private val _resumeThresholdSeconds = MutableStateFlow(prefs.getInt(KEY_RESUME_THRESHOLD, 15))
    val resumeThresholdSeconds: StateFlow<Int> = _resumeThresholdSeconds.asStateFlow()

    /** Now-playing options. */
    private val _showNowPlayingFavorite = MutableStateFlow(prefs.getBoolean(KEY_NP_FAVORITE, true))
    val showNowPlayingFavorite: StateFlow<Boolean> = _showNowPlayingFavorite.asStateFlow()

    private val _fitCover = MutableStateFlow(prefs.getBoolean(KEY_NP_FIT_COVER, false))
    val fitCover: StateFlow<Boolean> = _fitCover.asStateFlow()

    private val _compactControls = MutableStateFlow(prefs.getBoolean(KEY_NP_COMPACT, false))
    val compactControls: StateFlow<Boolean> = _compactControls.asStateFlow()

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

    fun setShowTrackSubtitle(value: Boolean) {
        prefs.edit().putBoolean(KEY_SHOW_SUBTITLE, value).apply()
        _showTrackSubtitle.value = value
    }

    fun setShowItemCount(value: Boolean) {
        prefs.edit().putBoolean(KEY_SHOW_ITEM_COUNT, value).apply()
        _showItemCount.value = value
    }

    fun setScreenOrientation(value: ScreenOrientation) {
        prefs.edit().putString(KEY_ORIENTATION, value.name).apply()
        _screenOrientation.value = value
    }

    fun setGridSpacing(value: Int) {
        prefs.edit().putInt(KEY_GRID_SPACING, value).apply()
        _gridSpacing.value = value
    }

    /** Serializes every setting to a JSON object. Contains no tokens or secrets. */
    fun exportJson(): String {
        val obj = JSONObject()
        prefs.all.forEach { (key, value) -> obj.put(key, value) }
        return obj.toString()
    }

    /** Replaces every setting from a JSON export and refreshes the in-memory state. */
    fun importJson(json: String) {
        val obj = runCatching { JSONObject(json) }.getOrNull() ?: return
        val editor = prefs.edit().clear()
        obj.keys().forEach { key ->
            when (val value = obj.opt(key)) {
                is Boolean -> editor.putBoolean(key, value)
                is Int -> editor.putInt(key, value)
                is Long -> editor.putLong(key, value)
                is String -> editor.putString(key, value)
                else -> Unit
            }
        }
        editor.apply()
        reload()
    }

    /** Re-reads every value so imported settings take effect immediately. */
    private fun reload() {
        _forceTranscode.value = prefs.getBoolean(KEY_FORCE_TRANSCODE, false)
        _preferExternal.value = prefs.getBoolean(KEY_PREFER_EXTERNAL, false)
        _storageLocation.value = runCatching {
            StorageLocation.valueOf(prefs.getString(KEY_STORAGE, null) ?: "INTERNAL")
        }.getOrDefault(StorageLocation.INTERNAL)
        _downloadQuality.value = runCatching {
            DownloadQuality.valueOf(prefs.getString(KEY_DOWNLOAD_QUALITY, null) ?: "ORIGINAL")
        }.getOrDefault(DownloadQuality.ORIGINAL)
        _downloadFormat.value = runCatching {
            DownloadFormat.valueOf(prefs.getString(KEY_DOWNLOAD_FORMAT, null) ?: "AAC")
        }.getOrDefault(DownloadFormat.AAC)
        _keepScreenOn.value = prefs.getBoolean(KEY_KEEP_SCREEN_ON, true)
        _showTrackNumbers.value = prefs.getBoolean(KEY_TRACK_NUMBERS, true)
        _showTrackSubtitle.value = prefs.getBoolean(KEY_SHOW_SUBTITLE, true)
        _showItemCount.value = prefs.getBoolean(KEY_SHOW_ITEM_COUNT, false)
        _wifiOnlyDownloads.value = prefs.getBoolean(KEY_WIFI_ONLY, true)
        _autoCacheFavorites.value = prefs.getBoolean(KEY_AUTO_CACHE_FAVORITES, false)
        _ignoreArticles.value = prefs.getBoolean(KEY_IGNORE_ARTICLES, true)
        _homeSections.value = (prefs.getString(KEY_HOME_SECTIONS, null) ?: DEFAULT_HOME_SECTIONS)
            .split(',')
            .filter { it.isNotBlank() }
        _libraryColumns.value = prefs.getInt(KEY_COLUMNS, 0)
        _autoplay.value = prefs.getBoolean(KEY_AUTOPLAY, true)
        _circularQueue.value = prefs.getBoolean(KEY_CIRCULAR_QUEUE, false)
        _cacheLimitMb.value = prefs.getInt(KEY_CACHE_LIMIT, 512)
        _resumeThresholdSeconds.value = prefs.getInt(KEY_RESUME_THRESHOLD, 15)
        _screenOrientation.value = runCatching {
            ScreenOrientation.valueOf(prefs.getString(KEY_ORIENTATION, null) ?: "AUTO")
        }.getOrDefault(ScreenOrientation.AUTO)
        _gridSpacing.value = prefs.getInt(KEY_GRID_SPACING, 12)
    }

    fun setWifiOnlyDownloads(value: Boolean) {
        prefs.edit().putBoolean(KEY_WIFI_ONLY, value).apply()
        _wifiOnlyDownloads.value = value
    }

    fun setAutoCacheFavorites(value: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_CACHE_FAVORITES, value).apply()
        _autoCacheFavorites.value = value
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

    fun setCircularQueue(value: Boolean) {
        prefs.edit().putBoolean(KEY_CIRCULAR_QUEUE, value).apply()
        _circularQueue.value = value
    }

    fun setCacheLimitMb(value: Int) {
        prefs.edit().putInt(KEY_CACHE_LIMIT, value).apply()
        _cacheLimitMb.value = value
    }

    fun setResumeThresholdSeconds(value: Int) {
        prefs.edit().putInt(KEY_RESUME_THRESHOLD, value).apply()
        _resumeThresholdSeconds.value = value
    }

    fun setShowNowPlayingFavorite(value: Boolean) {
        prefs.edit().putBoolean(KEY_NP_FAVORITE, value).apply()
        _showNowPlayingFavorite.value = value
    }

    fun setFitCover(value: Boolean) {
        prefs.edit().putBoolean(KEY_NP_FIT_COVER, value).apply()
        _fitCover.value = value
    }

    fun setCompactControls(value: Boolean) {
        prefs.edit().putBoolean(KEY_NP_COMPACT, value).apply()
        _compactControls.value = value
    }

    companion object {
        private const val KEY_FORCE_TRANSCODE = "force_transcode"
        private const val KEY_PREFER_EXTERNAL = "prefer_external"
        private const val KEY_STORAGE = "storage_location"
        private const val KEY_CACHE_LIMIT = "cache_limit_mb"
        private const val KEY_AUTOPLAY = "autoplay"
        private const val KEY_CIRCULAR_QUEUE = "circular_queue"
        private const val KEY_KEEP_SCREEN_ON = "keep_screen_on"
        private const val KEY_TRACK_NUMBERS = "track_numbers"
        private const val KEY_SHOW_SUBTITLE = "show_subtitle"
        private const val KEY_SHOW_ITEM_COUNT = "show_item_count"
        private const val KEY_ORIENTATION = "screen_orientation"
        private const val KEY_GRID_SPACING = "grid_spacing"
        private const val KEY_COLUMNS = "library_columns"
        private const val KEY_HOME_SECTIONS = "home_sections"
        private const val KEY_WIFI_ONLY = "wifi_only_downloads"
        private const val KEY_AUTO_CACHE_FAVORITES = "auto_cache_favorites"
        private const val KEY_IGNORE_ARTICLES = "ignore_articles"
        private const val KEY_DOWNLOAD_QUALITY = "download_quality"
        private const val KEY_DOWNLOAD_FORMAT = "download_format"
        private const val KEY_RESUME_THRESHOLD = "resume_threshold_seconds"
        private const val KEY_NP_FAVORITE = "np_favorite"
        private const val KEY_NP_FIT_COVER = "np_fit_cover"
        private const val KEY_NP_COMPACT = "np_compact"

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
