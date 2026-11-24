package app.k56.player.data.acquisition.clients

import app.k56.player.core.net.parseBaseUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl
import app.k56.player.core.net.DiagnosticsInterceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

data class SabnzbdConfig(
    val baseUrl: String = "",
    val key: String = "",
    val category: String = "",
)

/**
 * SABnzbd's API takes the key as a query parameter — there is no header form.
 * The app never logs request URLs, so the key stays on the device.
 */
class SabnzbdClient(
    private val config: SabnzbdConfig,
) : DownloadClient {

    override val label: String = "SABnzbd"

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
    }

    private val http = OkHttpClient.Builder()
            .addInterceptor(DiagnosticsInterceptor())
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    /**
     * A URL typed by a human. parseBaseUrl rejects "http://" and hosts with a
     * space with IllegalArgumentException; that must become our own error, not
     * an exception escaping the coroutine.
     */
    private fun requireBase(): HttpUrl = try {
        parseBaseUrl(config.baseUrl)
    } catch (e: IllegalArgumentException) {
        throw DownloadClientException("SABnzbd URL is not valid", e)
    }

    private suspend fun call(mode: String, extra: Map<String, String> = emptyMap()): String =
        withContext(Dispatchers.IO) {
            val url = requireBase().newBuilder()
                .addPathSegments("api")
                .addQueryParameter("mode", mode)
                .addQueryParameter("output", "json")
                .addQueryParameter("apikey", config.key)
                .apply { extra.forEach { (k, v) -> addQueryParameter(k, v) } }
                .build()
            try {
                http.newCall(Request.Builder().url(url).build()).execute().use { response ->
                    if (response.code == 401 || response.code == 403) {
                        throw DownloadClientException("SABnzbd rejected the API key")
                    }
                    if (!response.isSuccessful) {
                        throw DownloadClientException("SABnzbd error ${response.code}")
                    }
                    response.body?.string().orEmpty()
                }
            } catch (e: IOException) {
                throw DownloadClientException("SABnzbd is not reachable: ${e.message}", e)
            }
        }

    @Serializable
    private data class VersionDto(val version: String? = null)

    override suspend fun test(): String {
        // `mode=version` is served without authentication, so it cannot prove the
        // key. The queue does: it returns 403 with a wrong key.
        call("queue")
        val parsed = runCatching {
            json.decodeFromString<VersionDto>(call("version"))
        }.getOrNull()
        return parsed?.version ?: "SABnzbd"
    }

    @Serializable
    private data class AddDto(
        val status: Boolean = false,
        @SerialName("nzo_ids") val nzoIds: List<String> = emptyList(),
    )

    override suspend fun add(url: String, category: String?): Boolean {
        if (url.isBlank()) throw DownloadClientException("Nothing to add")
        val params = buildMap {
            put("name", url)
            val cat = category?.takeIf { it.isNotBlank() } ?: config.category.takeIf { it.isNotBlank() }
            cat?.let { put("cat", it) }
        }
        // Fetch first, then parse: a wrong key or an unreachable host must
        // surface, not turn into "the client said no".
        val raw = call("addurl", params)
        val parsed = runCatching { json.decodeFromString<AddDto>(raw) }.getOrNull()
        return parsed?.status == true
    }

    @Serializable
    private data class SlotDto(
        @SerialName("nzo_id") val nzoId: String = "",
        val filename: String = "",
        val status: String = "",
        val percentage: String = "0",
        val timeleft: String? = null,
        val mb: String? = null,
        val mbleft: String? = null,
    )

    @Serializable
    private data class QueueInner(val slots: List<SlotDto> = emptyList())

    @Serializable
    private data class QueueDto(val queue: QueueInner? = null)

    override suspend fun tasks(): List<DownloadTask> {
        val raw = call("queue")
        val parsed = runCatching { json.decodeFromString<QueueDto>(raw) }.getOrNull()
        return parsed?.queue?.slots.orEmpty().map { slot ->
            val percent = slot.percentage.replace(',', '.').toFloatOrNull() ?: 0f
            val totalMb = slot.mb?.toFloatOrNull()
            DownloadTask(
                id = slot.nzoId,
                name = slot.filename,
                progress = (percent / 100f).coerceIn(0f, 1f),
                state = slot.status,
                etaSeconds = parseEta(slot.timeleft),
                sizeBytes = totalMb?.let { (it * 1024 * 1024).toLong() },
                speedBytesPerSecond = null,
                error = if (slot.status.contains("fail", ignoreCase = true)) slot.status else null,
            )
        }
    }

    private fun parseEta(raw: String?): Long? {
        val parts = raw?.split(':') ?: return null
        if (parts.size != 3) return null
        val h = parts[0].toLongOrNull() ?: return null
        val m = parts[1].toLongOrNull() ?: return null
        val s = parts[2].toLongOrNull() ?: return null
        return h * 3600 + m * 60 + s
    }

    override suspend fun pause(id: String) {
        call("queue", mapOf("name" to "pause"))
    }

    override suspend fun resume(id: String) {
        call("queue", mapOf("name" to "resume"))
    }
}
