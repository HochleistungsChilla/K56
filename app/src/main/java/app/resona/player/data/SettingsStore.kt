package app.resona.player.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsStore(context: Context) {

    private val prefs = context.getSharedPreferences("resona_settings", Context.MODE_PRIVATE)

    private val _forceTranscode = MutableStateFlow(prefs.getBoolean(KEY_FORCE_TRANSCODE, false))
    val forceTranscode: StateFlow<Boolean> = _forceTranscode.asStateFlow()

    private val _preferExternal = MutableStateFlow(prefs.getBoolean(KEY_PREFER_EXTERNAL, false))
    val preferExternal: StateFlow<Boolean> = _preferExternal.asStateFlow()

    fun setForceTranscode(value: Boolean) {
        prefs.edit().putBoolean(KEY_FORCE_TRANSCODE, value).apply()
        _forceTranscode.value = value
    }

    fun setPreferExternal(value: Boolean) {
        prefs.edit().putBoolean(KEY_PREFER_EXTERNAL, value).apply()
        _preferExternal.value = value
    }

    private companion object {
        const val KEY_FORCE_TRANSCODE = "force_transcode"
        const val KEY_PREFER_EXTERNAL = "prefer_external"
    }
}
