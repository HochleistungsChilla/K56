package app.k56.player.data.acquisition.clients

import app.k56.player.core.net.HostPolicy
import app.k56.player.core.net.InsecureTransportException
import app.k56.player.core.net.parseBaseUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import app.k56.player.core.net.DiagnosticsInterceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

data class SlskdConfig(
    val baseUrl: String = "",
    val apiKey: String = "",
)

/**
 * slskd (Soulseek daemon), API v0. Authentication is the `X-API-Key` header,
 * so the key never appears in a URL or a log line.
 *
 * The API surface is deliberately small: a version call, a transfer list and
 * cancel. slskd has no pause/resume and no URL hand-over — its transfers are
 * driven by Soulseek searches (usually from Lidarr).
 */
class SlskdClient(
    private val config: SlskdConfig,
) : DownloadClient {

    override val label: String = "slskd"

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
    }

    private val http = OkHttpClient.Builder()
            .addInterceptor(DiagnosticsInterceptor())
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()

    /** See SabnzbdClient.requireBase. */
    private fun requireBase(): HttpUrl = try {
        val parsed = parseBaseUrl(config.baseUrl)
        // An API key must not travel in the clear to a public host.
        HostPolicy.requireAllowed(parsed.toString())
        parsed
    } catch (e: IllegalArgumentException) {
        throw DownloadClientException("slskd URL is not valid", e)
    } catch (e: InsecureTransportException) {
        throw DownloadClientException(e.message ?: "HTTPS required", e)
    }

    private suspend fun get(path: String): String = withContext(Dispatchers.IO) {
        if (config.apiKey.isBlank()) throw DownloadClientException("slskd API key is empty")
        val request = Request.Builder()
            .url(requireBase().newBuilder().addPathSegments(path.trimStart('/')).build())
            .header("X-API-Key", config.apiKey)
            .header("Accept", "application/json")
            .build()
        try {
            http.newCall(request).execute().use { response ->
                when {
                    response.code == 401 || response.code == 403 ->
                        throw DownloadClientException("slskd rejected the API key")
                    response.code == 404 ->
                        throw DownloadClientException("Not found: is this really a slskd URL?")
                    !response.isSuccessful ->
                        throw DownloadClientException("slskd error ${response.code}")
                    else -> response.body?.string().orEmpty()
                }
            }
        } catch (e: IOException) {
            throw DownloadClientException("slskd is not reachable: ${e.message}", e)
        }
    }

    /** GET /api/v0/application/version answers a JSON string, e.g. "0.26.2". */
    override suspend fun test(): String {
        val raw = get("/api/v0/application/version").trim()
        val version = runCatching { json.decodeFromString<String>(raw) }
            .getOrElse { raw }
        return version.ifBlank { "slskd" }
    }

    @Serializable
    private data class TransferDto(
        val id: String = "",
        val filename: String = "",
        val size: Long = 0L,
        val bytesTransferred: Long = 0L,
        val averageSpeed: Double? = null,
        val state: String? = null,
        val placeInQueue: Int? = null,
        val exception: String? = null,
    )

    @Serializable
    private data class DirectoryDto(
        val directory: String? = null,
        val fileCount: Int = 0,
        val files: List<TransferDto> = emptyList(),
    )

    @Serializable
    private data class UserDto(
        val username: String? = null,
        val directories: List<DirectoryDto> = emptyList(),
    )

    /** GET /api/v0/transfers/downloads groups every download by peer. */
    override suspend fun tasks(): List<DownloadTask> {
        val raw = get("/api/v0/transfers/downloads")
        val users = runCatching { json.decodeFromString<List<UserDto>>(raw) }
            .getOrElse { throw DownloadClientException("Unexpected slskd response") }
        return users.flatMap { user ->
            user.directories.flatMap { directory ->
                directory.files.map { file -> file.toTask() }
            }
        }.sortedWith(compareBy({ it.finished }, { it.name.lowercase() }))
    }

    private fun TransferDto.toTask(): DownloadTask {
        val progress = if (size > 0L) {
            (bytesTransferred.toFloat() / size.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }
        return DownloadTask(
            id = id,
            name = filename
                .substringAfterLast('\\')
                .substringAfterLast('/')
                .ifBlank { filename },
            progress = progress,
            state = state.orEmpty(),
            sizeBytes = size.takeIf { it > 0L },
            transferredBytes = bytesTransferred,
            speedBytesPerSecond = averageSpeed?.toLong()?.takeIf { it > 0L },
            error = exception?.takeIf { it.isNotBlank() },
        )
    }

    /** slskd cannot take a link: downloads come from Soulseek searches. */
    override suspend fun add(url: String, category: String?): Boolean =
        throw DownloadClientException(
            "slskd downloads come from Soulseek searches; links cannot be handed over",
        )

    /** slskd's API has no pause; cancel is the only transfer action it offers. */
    override suspend fun pause(id: String) = throw DownloadClientException(
        "slskd cannot pause a transfer; cancel it in slskd instead",
    )

    override suspend fun resume(id: String) = throw DownloadClientException(
        "slskd cannot resume a transfer; start it again in slskd",
    )
}
