package app.nadelton.player.data.plex

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/** Plex Media Server API client (JSON, token auth). */
class PlexClient(
    private val serverUrl: String,
    private val token: String,
    private val clientId: String,
) {

    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .build()

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
    }

    suspend fun <T> get(
        path: String,
        params: Map<String, String> = emptyMap(),
        deserializer: DeserializationStrategy<T>,
    ): T = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(urlFor(path, params))
            .header("Accept", "application/json")
            .header("X-Plex-Token", token)
            .header("X-Plex-Client-Identifier", clientId)
            .get()
            .build()

        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Plex antwortet mit HTTP ${response.code}.")
            json.decodeFromString(deserializer, response.body?.string().orEmpty())
        }
    }

    suspend fun ping(path: String, params: Map<String, String> = emptyMap()) {
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(urlFor(path, params))
                .header("X-Plex-Token", token)
                .header("X-Plex-Client-Identifier", clientId)
                .get()
                .build()
            http.newCall(request).execute().use { }
        }
    }

    fun streamUrl(streamKey: String): String {
        val base = serverUrl.trimEnd('/')
        val key = if (streamKey.startsWith("/")) streamKey else "/$streamKey"
        return "$base$key?X-Plex-Token=${encode(token)}"
    }

    fun imageUrl(thumb: String?, width: Int = 700, height: Int = 700): String? {
        if (thumb.isNullOrBlank()) return null
        val base = serverUrl.trimEnd('/')
        val path = if (thumb.startsWith("/")) thumb else "/$thumb"
        return "$base$path?X-Plex-Token=${encode(token)}&width=$width&height=$height"
    }

    suspend fun timeline(
        ratingKey: String,
        state: String,
        timeMs: Long,
        durationMs: Long,
    ) {
        ping(
            path = "/:/timeline",
            params = mapOf(
                "ratingKey" to ratingKey,
                "key" to "/library/metadata/$ratingKey",
                "state" to state,
                "time" to timeMs.toString(),
                "duration" to durationMs.toString(),
            ),
        )
    }

    private fun urlFor(path: String, params: Map<String, String>): String {
        val base = serverUrl.trimEnd('/')
        val normalizedPath = if (path.startsWith("/")) path else "/$path"
        val query = buildList {
            add("X-Plex-Token" to token)
            params.forEach { (key, value) -> add(key to value) }
        }.joinToString("&") { (key, value) ->
            "${encode(key)}=${encode(value)}"
        }
        return "$base$normalizedPath?$query"
    }

    private fun encode(value: String): String =
        URLEncoder.encode(value, Charsets.UTF_8.name())
}
