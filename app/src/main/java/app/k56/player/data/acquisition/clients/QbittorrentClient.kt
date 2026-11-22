package app.k56.player.data.acquisition.clients

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

data class QbittorrentConfig(
    val baseUrl: String = "",
    val username: String = "",
    val passwd: String = "",
    val category: String = "",
)

/**
 * qBittorrent Web API v2. Login stores a SID cookie; every later call sends it.
 * The API key equivalent here is the session cookie, so nothing is put in a URL.
 */
class QbittorrentClient(
    private val config: QbittorrentConfig,
) : DownloadClient {

    override val label: String = "qBittorrent"

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
    }

    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()

    private var sid: String? = null

    private fun base(): String {
        val raw = config.baseUrl.trim().trimEnd('/')
        if (raw.isBlank()) throw DownloadClientException("qBittorrent URL is empty")
        val withScheme = if (raw.startsWith("http://") || raw.startsWith("https://")) raw else "http://$raw"
        return withScheme
    }

    private suspend fun login(): String = withContext(Dispatchers.IO) {
        sid?.let { return@withContext it }
        val body = FormBody.Builder()
            .add("username", config.username)
            .add("password", config.passwd)
            .build()
        val request = Request.Builder()
            .url("${base()}/api/v2/auth/login")
            .post(body)
            .header("Referer", base())
            .build()
        try {
            http.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful || text.trim() != "Ok.") {
                    throw DownloadClientException("qBittorrent rejected the login")
                }
                val cookie = response.headers("Set-Cookie")
                    .firstOrNull { it.startsWith("SID=") }
                    ?.substringBefore(';')
                    ?: throw DownloadClientException("qBittorrent returned no session cookie")
                sid = cookie
                cookie
            }
        } catch (e: IOException) {
            throw DownloadClientException("qBittorrent is not reachable: ${e.message}", e)
        }
    }

    private suspend fun get(path: String): String = withContext(Dispatchers.IO) {
        val cookie = login()
        val request = Request.Builder()
            .url("${base()}$path")
            .header("Cookie", cookie)
            .header("Referer", base())
            .build()
        try {
            http.newCall(request).execute().use { response ->
                if (response.code == 403) {
                    sid = null
                    throw DownloadClientException("qBittorrent session expired")
                }
                if (!response.isSuccessful) {
                    throw DownloadClientException("qBittorrent error ${response.code}")
                }
                response.body?.string().orEmpty()
            }
        } catch (e: IOException) {
            throw DownloadClientException("qBittorrent is not reachable: ${e.message}", e)
        }
    }

    private suspend fun post(path: String, form: FormBody): String = withContext(Dispatchers.IO) {
        val cookie = login()
        val request = Request.Builder()
            .url("${base()}$path")
            .post(form)
            .header("Cookie", cookie)
            .header("Referer", base())
            .build()
        try {
            http.newCall(request).execute().use { response ->
                if (response.code == 403) {
                    sid = null
                    throw DownloadClientException("qBittorrent session expired")
                }
                if (!response.isSuccessful) {
                    throw DownloadClientException("qBittorrent error ${response.code}")
                }
                response.body?.string().orEmpty()
            }
        } catch (e: IOException) {
            throw DownloadClientException("qBittorrent is not reachable: ${e.message}", e)
        }
    }

    override suspend fun test(): String =
        get("/api/v2/app/version").trim().ifBlank { "qBittorrent" }

    override suspend fun add(url: String, category: String?): Boolean {
        if (url.isBlank()) throw DownloadClientException("Nothing to add")
        val form = FormBody.Builder().add("urls", url)
        val cat = category?.takeIf { it.isNotBlank() } ?: config.category.takeIf { it.isNotBlank() }
        cat?.let { form.add("category", it) }
        val response = post("/api/v2/torrents/add", form.build())
        // v2 answers "Ok." on success; older builds answer with an empty body.
        return !response.contains("Fails", ignoreCase = true)
    }

    @Serializable
    private data class TorrentDto(
        val hash: String = "",
        val name: String = "",
        val progress: Double = 0.0,
        val state: String = "",
        val eta: Long? = null,
        val size: Long? = null,
        val dlspeed: Long? = null,
    )

    override suspend fun tasks(): List<DownloadTask> {
        val raw = get("/api/v2/torrents/info")
        val parsed = runCatching { json.decodeFromString<List<TorrentDto>>(raw) }
            .getOrElse { throw DownloadClientException("Unexpected qBittorrent response") }
        return parsed.map { dto ->
            DownloadTask(
                id = dto.hash,
                name = dto.name,
                progress = dto.progress.toFloat().coerceIn(0f, 1f),
                state = dto.state,
                etaSeconds = dto.eta?.takeIf { it in 0..86_400 * 30L },
                sizeBytes = dto.size,
                speedBytesPerSecond = dto.dlspeed,
            )
        }
    }

    override suspend fun pause(id: String) {
        // v4.5 renamed pause/resume to stop/start; try the new name first.
        val form = { FormBody.Builder().add("hashes", id).build() }
        runCatching { post("/api/v2/torrents/stop", form()) }
            .onFailure { post("/api/v2/torrents/pause", form()) }
            .getOrThrow()
    }

    override suspend fun resume(id: String) {
        val form = { FormBody.Builder().add("hashes", id).build() }
        runCatching { post("/api/v2/torrents/start", form()) }
            .onFailure { post("/api/v2/torrents/resume", form()) }
            .getOrThrow()
    }

    /** True when the string looks like something a client can accept. */
    fun accepts(url: String): Boolean {
        val trimmed = url.trim()
        if (trimmed.startsWith("magnet:?")) return true
        val parsed = trimmed.toHttpUrlOrNull() ?: return false
        return parsed.scheme == "http" || parsed.scheme == "https"
    }
}
