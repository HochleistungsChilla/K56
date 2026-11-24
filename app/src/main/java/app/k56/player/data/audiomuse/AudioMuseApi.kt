package app.k56.player.data.audiomuse

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import app.k56.player.core.net.DiagnosticsInterceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

data class AudioMuseConfig(
    val baseUrl: String = "",
    val token: String = "",
) {
    val isComplete: Boolean
        get() = baseUrl.isNotBlank() && token.isNotBlank()
}

/** One track returned by AudioMuse. [itemId] is the media server item id. */
data class AudioMuseTrack(
    val itemId: String,
    val title: String,
    val artist: String,
    val album: String?,
    val similarity: Double?,
)

data class AudioMuseArtist(
    val name: String,
    val artistId: String?,
    val divergence: Double?,
)

/** `/api/clap/stats`, used as the connection/health test. */
data class AudioMuseStats(
    val clapEnabled: Boolean?,
    val embeddings: Int?,
    val lastRefresh: String?,
)

class AudioMuseException(
    message: String,
    val statusCode: Int? = null,
    cause: Throwable? = null,
) : Exception(message, cause) {
    val isAuthFailure: Boolean get() = statusCode == 401 || statusCode == 403
}

/**
 * Thin client for an AudioMuse-AI instance. Read-only by design: the app only
 * searches and reads stats, never analyses, imports or edits anything.
 *
 * The response shapes are read defensively from the JSON tree instead of
 * strict DTOs, so an instance that adds fields (or answers with a different
 * nesting) still works; a shape error surfaces the server's own error text.
 *
 * The token travels as `Authorization: Bearer …` and is never logged, never
 * put into an exception message and never part of a URL.
 */
class AudioMuseApi(private val config: AudioMuseConfig) {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val http = OkHttpClient.Builder()
            .addInterceptor(DiagnosticsInterceptor())
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private fun base(): String {
        val raw = config.baseUrl.trim()
        if (raw.isBlank()) throw AudioMuseException("AudioMuse URL is empty")
        val withScheme = if (raw.contains("://")) raw else "http://$raw"
        // Only drop trailing slashes when a host is actually there: "http://"
        // must stay broken so it reaches the URL check in [call].
        return if (withScheme.length > withScheme.indexOf("://") + 3) {
            withScheme.trimEnd('/')
        } else {
            withScheme
        }
    }

    private suspend fun get(path: String, query: Map<String, String> = emptyMap()): String =
        call(path, query, null)

    private suspend fun post(path: String, body: JsonObject): String = call(path, emptyMap(), body)

    private suspend fun call(
        path: String,
        query: Map<String, String>,
        body: JsonObject?,
    ): String = withContext(Dispatchers.IO) {
        val token = config.token.trim()
        if (token.isBlank()) throw AudioMuseException("AudioMuse token is empty")
        // The URL field is free text: `toHttpUrl` throws IllegalArgumentException
        // on a typo like "http://" or a space in the host. That must surface as
        // a message in the card, never escape from a coroutine.
        val request = try {
            // Parse the base on its own first: appending a path to a broken
            // base like "http://" would otherwise yield "http:///api/…", which
            // OkHttp happily reads as host "api" instead of failing.
            val url = base().toHttpUrl().newBuilder()
                .addPathSegments(path.trimStart('/'))
                .apply { query.forEach { (k, v) -> addQueryParameter(k, v) } }
                .build()
            Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $token")
                .header("Accept", "application/json")
                .apply {
                    if (body != null) {
                        post(body.toString().toRequestBody(JSON_MEDIA))
                    }
                }
                .build()
        } catch (e: IllegalArgumentException) {
            throw AudioMuseException("AudioMuse URL is not valid", null, e)
        }
        try {
            http.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) throw failure(response.code, text)
                text
            }
        } catch (e: IOException) {
            throw AudioMuseException("AudioMuse is not reachable: ${e.message}", null)
        }
    }

    /**
     * Turns a non-2xx answer into a message that is useful to a human without
     * echoing anything secret. AudioMuse answers errors as
     * `{error_code, error_class, error_message, error}`; older routes only set
     * `error`.
     */
    private fun failure(code: Int, text: String): AudioMuseException {
        val detail = runCatching {
            val obj = json.parseToJsonElement(text) as? JsonObject ?: return@runCatching null
            obj.string("error_message")
                ?: obj.string("message")
                ?: obj.string("error")
                ?: obj.string("error_class")
        }.getOrNull()?.takeIf { it.isNotBlank() }
        return when {
            code == 401 || code == 403 ->
                AudioMuseException("AudioMuse: token missing or wrong", code)
            code == 503 ->
                AudioMuseException(
                    detail ?: "AudioMuse is still analysing the library",
                    code,
                )
            else -> AudioMuseException(
                buildString {
                    append("AudioMuse error ")
                    append(code)
                    if (detail != null) append(": ").append(detail.take(180))
                },
                code,
            )
        }
    }

    /** Health test: a wrong token answers 401, so this proves the credential. */
    suspend fun stats(): AudioMuseStats {
        val root = parseObject(get("/api/clap/stats"))
        return AudioMuseStats(
            clapEnabled = root.bool("clap_enabled"),
            embeddings = root.int("num_embeddings") ?: root.int("count"),
            lastRefresh = root.string("last_refresh"),
        )
    }

    /** Free-text sonic search, e.g. "calm piano at night". */
    suspend fun search(query: String, limit: Int = 30): List<AudioMuseTrack> {
        if (query.isBlank()) return emptyList()
        val body = buildJsonObject {
            put("query", query)
            put("limit", limit.coerceAtLeast(1))
        }
        return tracks(parseObject(post("/api/clap/search", body)))
    }

    /** Seed-track search: library items close to [itemId]. */
    suspend fun searchBySong(itemId: String, limit: Int = 30): List<AudioMuseTrack> {
        if (itemId.isBlank()) return emptyList()
        val body = buildJsonObject {
            put("item_id", itemId)
            put("limit", limit.coerceAtLeast(1))
        }
        return tracks(parseObject(post("/api/clap/search_by_song", body)))
    }

    /** Similar artists for a name; AudioMuse answers a plain array. */
    suspend fun similarArtists(artist: String, limit: Int = 10): List<AudioMuseArtist> {
        if (artist.isBlank()) return emptyList()
        val element = parse(get("/api/similar_artists", mapOf("artist" to artist, "n" to "$limit")))
        val array = element as? JsonArray ?: return emptyList()
        return array.mapNotNull { row ->
            val obj = row as? JsonObject ?: return@mapNotNull null
            val name = obj.string("artist") ?: obj.string("name") ?: return@mapNotNull null
            AudioMuseArtist(
                name = name,
                artistId = obj.string("artist_id"),
                divergence = obj.double("divergence"),
            )
        }
    }

    private fun tracks(root: JsonObject): List<AudioMuseTrack> {
        val array = root.array("results") ?: root.array("tracks") ?: return emptyList()
        return array.mapNotNull { row ->
            val obj = row as? JsonObject ?: return@mapNotNull null
            val id = obj.string("item_id") ?: obj.string("id") ?: return@mapNotNull null
            AudioMuseTrack(
                itemId = id,
                title = obj.string("title") ?: obj.string("name").orEmpty(),
                artist = obj.string("author") ?: obj.string("artist").orEmpty(),
                album = obj.string("album"),
                similarity = obj.double("similarity") ?: obj.double("score"),
            )
        }
    }

    private fun parse(text: String): JsonElement = runCatching {
        json.parseToJsonElement(text)
    }.getOrElse {
        throw AudioMuseException("AudioMuse answered with something that is not JSON")
    }

    private fun parseObject(text: String): JsonObject =
        parse(text) as? JsonObject
            ?: throw AudioMuseException("AudioMuse answered with an unexpected shape")

    private companion object {
        val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

        fun JsonObject.string(key: String): String? =
            (this[key] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content

        fun JsonObject.int(key: String): Int? =
            string(key)?.toDoubleOrNull()?.toInt()

        fun JsonObject.double(key: String): Double? =
            string(key)?.toDoubleOrNull()

        fun JsonObject.bool(key: String): Boolean? =
            (this[key] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content
                ?.let { if (it == "true") true else if (it == "false") false else null }

        fun JsonObject.array(key: String): JsonArray? = this[key] as? JsonArray
    }
}
