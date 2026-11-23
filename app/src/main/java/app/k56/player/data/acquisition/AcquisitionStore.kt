package app.k56.player.data.acquisition

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Connection to an acquisition manager (currently Lidarr). Kept in its own
 * preferences file: it holds an API key and has nothing to do with the media
 * server session, so it survives sign-out.
 */
data class AcquisitionConfig(
    val enabled: Boolean = false,
    val baseUrl: String = "",
    val apiKey: String = "",
    val qualityProfileId: Int = 0,
    val metadataProfileId: Int = 0,
    val rootFolderPath: String = "",
) {
    val isComplete: Boolean
        get() = baseUrl.isNotBlank() && apiKey.isNotBlank()
}

class AcquisitionStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("k56_acquisition", Context.MODE_PRIVATE)

    private val _config = MutableStateFlow(read())
    val config: StateFlow<AcquisitionConfig> = _config.asStateFlow()

    fun save(config: AcquisitionConfig) {
        prefs.edit()
            .putBoolean(KEY_ENABLED, config.enabled)
            .putString(KEY_URL, config.baseUrl)
            .putString(KEY_API_KEY, config.apiKey)
            .putInt(KEY_QUALITY_PROFILE, config.qualityProfileId)
            .putInt(KEY_METADATA_PROFILE, config.metadataProfileId)
            .putString(KEY_ROOT_FOLDER, config.rootFolderPath)
            .apply()
        _config.value = config
    }

    fun reload() {
        _config.value = read()
    }

    /** Highest Lidarr history id we have already reported, so imports are not announced twice. */
    fun lastHistoryId(): Int = prefs.getInt(KEY_LAST_HISTORY, 0)

    fun setLastHistoryId(id: Int) {
        prefs.edit().putInt(KEY_LAST_HISTORY, id).apply()
    }

    /** Album ids that were in the download queue at the last watcher look. */
    fun queuedAlbumIds(): Set<Int> = prefs
        .getStringSet(KEY_QUEUE_IDS, emptySet())
        .orEmpty()
        .mapNotNull { it.toIntOrNull() }
        .toSet()

    fun setQueuedAlbumIds(ids: Set<Int>) {
        prefs.edit()
            .putStringSet(KEY_QUEUE_IDS, ids.map { it.toString() }.toSet())
            .apply()
    }

    private fun read() = AcquisitionConfig(
        enabled = prefs.getBoolean(KEY_ENABLED, false),
        baseUrl = prefs.getString(KEY_URL, "").orEmpty(),
        apiKey = prefs.getString(KEY_API_KEY, "").orEmpty(),
        qualityProfileId = prefs.getInt(KEY_QUALITY_PROFILE, 0),
        metadataProfileId = prefs.getInt(KEY_METADATA_PROFILE, 0),
        rootFolderPath = prefs.getString(KEY_ROOT_FOLDER, "").orEmpty(),
    )

    private companion object {
        const val KEY_ENABLED = "enabled"
        const val KEY_URL = "base_url"
        const val KEY_API_KEY = "api_key"
        const val KEY_QUALITY_PROFILE = "quality_profile_id"
        const val KEY_METADATA_PROFILE = "metadata_profile_id"
        const val KEY_ROOT_FOLDER = "root_folder_path"
        const val KEY_LAST_HISTORY = "last_history_id"
        const val KEY_QUEUE_IDS = "queue_album_ids"
    }
}
