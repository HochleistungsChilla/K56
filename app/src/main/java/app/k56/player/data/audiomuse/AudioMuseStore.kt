package app.k56.player.data.audiomuse

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Connection to an AudioMuse-AI instance. Own preferences file: it holds a
 * token and has nothing to do with the media-server session, so it survives
 * sign-out and is never mixed into `SettingsStore`.
 */
class AudioMuseStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("k56_audiomuse", Context.MODE_PRIVATE)

    private val _config = MutableStateFlow(read())
    val config: StateFlow<AudioMuseConfig> = _config.asStateFlow()

    fun save(config: AudioMuseConfig) {
        prefs.edit()
            .putString(KEY_URL, config.baseUrl.trim())
            .putString(KEY_TOKEN, config.token.trim())
            .apply()
        _config.value = config.copy(
            baseUrl = config.baseUrl.trim(),
            token = config.token.trim(),
        )
    }

    fun reload() {
        _config.value = read()
    }

    /** A client for the saved configuration, or null when no URL is set. */
    fun createApi(): AudioMuseApi? {
        val saved = _config.value
        if (saved.baseUrl.isBlank()) return null
        return AudioMuseApi(saved)
    }

    private fun read() = AudioMuseConfig(
        baseUrl = prefs.getString(KEY_URL, "").orEmpty(),
        token = (prefs.getString(KEY_TOKEN, "") ?: "").trim(),
    )

    private companion object {
        const val KEY_URL = "base_url"
        const val KEY_TOKEN = "token"
    }
}
