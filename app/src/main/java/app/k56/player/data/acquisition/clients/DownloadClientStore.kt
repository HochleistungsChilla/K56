package app.k56.player.data.acquisition.clients

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class DownloadClientKind { NONE, SABNZBD, QBITTORRENT }

data class DownloadClientConfig(
    val kind: DownloadClientKind = DownloadClientKind.NONE,
    val baseUrl: String = "",
    val username: String = "",
    val passwd: String = "",
    val key: String = "",
    val category: String = "",
) {
    val isComplete: Boolean
        get() = when (kind) {
            DownloadClientKind.NONE -> false
            DownloadClientKind.SABNZBD -> baseUrl.isNotBlank() && key.isNotBlank()
            DownloadClientKind.QBITTORRENT -> baseUrl.isNotBlank()
        }

    /** The adapter for this configuration, or null when nothing is configured. */
    fun createClient(): DownloadClient? = when (kind) {
        DownloadClientKind.NONE -> null
        DownloadClientKind.SABNZBD -> SabnzbdClient(
            SabnzbdConfig(baseUrl = baseUrl, key = key, category = category),
        )
        DownloadClientKind.QBITTORRENT -> QbittorrentClient(
            QbittorrentConfig(
                baseUrl = baseUrl,
                username = username,
                passwd = passwd,
                category = category,
            ),
        )
    }
}

class DownloadClientStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("k56_download_client", Context.MODE_PRIVATE)

    private val _config = MutableStateFlow(read())
    val config: StateFlow<DownloadClientConfig> = _config.asStateFlow()

    fun save(config: DownloadClientConfig) {
        prefs.edit()
            .putString(KEY_KIND, config.kind.name)
            .putString(KEY_URL, config.baseUrl)
            .putString(KEY_USER, config.username)
            .putString(KEY_PASS, config.passwd)
            .putString(KEY_KEY, config.key)
            .putString(KEY_CATEGORY, config.category)
            .apply()
        _config.value = config
    }

    private fun read() = DownloadClientConfig(
        kind = runCatching {
            DownloadClientKind.valueOf(prefs.getString(KEY_KIND, null) ?: "NONE")
        }.getOrDefault(DownloadClientKind.NONE),
        baseUrl = prefs.getString(KEY_URL, "").orEmpty(),
        username = prefs.getString(KEY_USER, "").orEmpty(),
        passwd = prefs.getString(KEY_PASS, "").orEmpty(),
        key = prefs.getString(KEY_KEY, "").orEmpty(),
        category = prefs.getString(KEY_CATEGORY, "").orEmpty(),
    )

    private companion object {
        const val KEY_KIND = "kind"
        const val KEY_URL = "base_url"
        const val KEY_USER = "username"
        const val KEY_PASS = "passwd"
        const val KEY_KEY = "key"
        const val KEY_CATEGORY = "category"
    }
}
