package app.k56.player.core.net

import android.os.SystemClock
import app.k56.player.core.model.Session
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Picks the reachable base URL for a session.
 *
 * Rules (no location permission involved):
 *  - only LAN address configured -> LAN
 *  - only external address configured -> external
 *  - "prefer external" enabled -> external
 *  - cellular -> external
 *  - Wi-Fi/Ethernet -> LAN if it answers a cheap TCP/HTTP probe, else external
 *
 * The probe result is cached for a minute to avoid a request before every API
 * call, and re-evaluated when the network transport changes because the
 * transport is part of the decision.
 */
class EndpointResolver(
    private val networkMonitor: NetworkMonitor,
    private val sessionProvider: () -> Session?,
) {

    private val probeClient = OkHttpClient.Builder()
        .connectTimeout(1200, TimeUnit.MILLISECONDS)
        .readTimeout(1200, TimeUnit.MILLISECONDS)
        .callTimeout(1800, TimeUnit.MILLISECONDS)
        .build()

    @Volatile
    private var cachedInternalUrl: String? = null

    @Volatile
    private var cachedAtMs: Long = 0L

    @Volatile
    private var cachedTransport: NetworkTransport? = null

    @Volatile
    private var failedInternalUrl: String? = null

    @Volatile
    private var failedAtMs: Long = 0L

    suspend fun resolve(): String? = withContext(Dispatchers.IO) {
        val session = sessionProvider() ?: return@withContext null
        val internal = session.internalUrl?.takeIf { it.isNotBlank() }
        val external = session.externalUrl?.takeIf { it.isNotBlank() } ?: session.serverUrl

        if (internal == null) return@withContext external
        if (external == null) return@withContext internal
        if (session.preferExternal) return@withContext external

        when (networkMonitor.status.value.transport) {
            NetworkTransport.CELLULAR -> external
            NetworkTransport.WIFI, NetworkTransport.ETHERNET, NetworkTransport.OTHER ->
                if (probe(internal)) internal else external

            NetworkTransport.OFFLINE -> internal
        }
    }

    private fun probe(baseUrl: String): Boolean {
        val transport = networkMonitor.status.value.transport
        val now = SystemClock.elapsedRealtime()
        if (cachedInternalUrl == baseUrl &&
            cachedTransport == transport &&
            now - cachedAtMs < PROBE_TTL_MS
        ) {
            return true
        }
        if (failedInternalUrl == baseUrl && now - failedAtMs < PROBE_FAILURE_TTL_MS) {
            return false
        }

        val reachable = try {
            val request = Request.Builder().url(baseUrl).get().build()
            probeClient.newCall(request).execute().use { true }
        } catch (_: Exception) {
            false
        }

        if (reachable) {
            cachedInternalUrl = baseUrl
            cachedAtMs = now
            cachedTransport = transport
            failedInternalUrl = null
        } else {
            cachedInternalUrl = null
            cachedTransport = transport
            cachedAtMs = 0L
            failedInternalUrl = baseUrl
            failedAtMs = now
        }
        return reachable
    }

    private companion object {
        const val PROBE_TTL_MS = 60_000L
        const val PROBE_FAILURE_TTL_MS = 20_000L
    }
}
