package app.k56.player.data

import android.content.Context
import app.k56.player.core.model.BackendKind
import app.k56.player.core.model.Session
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

/**
 * Local-only session persistence.
 *
 * A production build would seal the token with the Android Keystore; this MVP
 * keeps the same on-device-only guarantee with private SharedPreferences.
 */
class SessionStore(context: Context) {

    private val prefs = context.getSharedPreferences("k56_session", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    /** Stable per-install device id, required by Jellyfin's session API. */
    val deviceId: String = prefs.getString(KEY_DEVICE_ID, null)
        ?: UUID.randomUUID().toString().also { prefs.edit().putString(KEY_DEVICE_ID, it).apply() }

    private val _session = MutableStateFlow(read())
    val session: StateFlow<Session?> = _session.asStateFlow()

    private fun read(): Session? {
        val serverUrl = prefs.getString(KEY_SERVER_URL, null) ?: return null
        val token = prefs.getString(KEY_TOKEN, null) ?: return null
        val backend = runCatching {
            BackendKind.valueOf(prefs.getString(KEY_BACKEND, null).orEmpty())
        }.getOrDefault(BackendKind.JELLYFIN)
        val extra = runCatching {
            json.decodeFromString<Map<String, String>>(prefs.getString(KEY_EXTRA, null) ?: "{}")
        }.getOrDefault(emptyMap())

        return Session(
            backend = backend,
            serverUrl = serverUrl,
            internalUrl = prefs.getString(KEY_INTERNAL_URL, null),
            externalUrl = prefs.getString(KEY_EXTERNAL_URL, null),
            preferExternal = prefs.getBoolean(KEY_PREFER_EXTERNAL, false),
            serverName = prefs.getString(KEY_SERVER_NAME, null).orEmpty(),
            serverVersion = prefs.getString(KEY_SERVER_VERSION, null).orEmpty(),
            serverId = prefs.getString(KEY_SERVER_ID, null).orEmpty(),
            userId = prefs.getString(KEY_USER_ID, null).orEmpty(),
            userName = prefs.getString(KEY_USER_NAME, null).orEmpty(),
            token = token,
            deviceId = deviceId,
            extra = extra,
        )
    }

    fun save(session: Session) {
        prefs.edit()
            .putString(KEY_BACKEND, session.backend.name)
            .putString(KEY_SERVER_URL, session.serverUrl)
            .putString(KEY_INTERNAL_URL, session.internalUrl)
            .putString(KEY_EXTERNAL_URL, session.externalUrl)
            .putBoolean(KEY_PREFER_EXTERNAL, session.preferExternal)
            .putString(KEY_SERVER_NAME, session.serverName)
            .putString(KEY_SERVER_VERSION, session.serverVersion)
            .putString(KEY_SERVER_ID, session.serverId)
            .putString(KEY_USER_ID, session.userId)
            .putString(KEY_USER_NAME, session.userName)
            .putString(KEY_TOKEN, session.token)
            .putString(KEY_EXTRA, json.encodeToString(session.extra))
            .apply()
        _session.value = session
    }

    fun clear() {
        // Keep the device id: re-login on the same install should look like the same device.
        prefs.edit()
            .remove(KEY_BACKEND)
            .remove(KEY_SERVER_URL)
            .remove(KEY_INTERNAL_URL)
            .remove(KEY_EXTERNAL_URL)
            .remove(KEY_PREFER_EXTERNAL)
            .remove(KEY_SERVER_NAME)
            .remove(KEY_SERVER_VERSION)
            .remove(KEY_SERVER_ID)
            .remove(KEY_USER_ID)
            .remove(KEY_USER_NAME)
            .remove(KEY_TOKEN)
            .remove(KEY_EXTRA)
            .apply()
        _session.value = null
    }

    private companion object {
        const val KEY_DEVICE_ID = "device_id"
        const val KEY_BACKEND = "backend"
        const val KEY_SERVER_URL = "server_url"
        const val KEY_INTERNAL_URL = "internal_url"
        const val KEY_EXTERNAL_URL = "external_url"
        const val KEY_PREFER_EXTERNAL = "prefer_external"
        const val KEY_SERVER_NAME = "server_name"
        const val KEY_SERVER_VERSION = "server_version"
        const val KEY_SERVER_ID = "server_id"
        const val KEY_USER_ID = "user_id"
        const val KEY_USER_NAME = "user_name"
        const val KEY_TOKEN = "token"
        const val KEY_EXTRA = "extra_json"
    }
}
