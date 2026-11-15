package app.nadelton.player.data.subsonic

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class SubsonicException(val code: Int, message: String) : IllegalStateException(message)

/**
 * Thin, dependency-light Subsonic client. Works with Subsonic, OpenSubsonic and
 * Navidrome (which implements the same API). Token auth is used, so the user's
 * password is never persisted.
 */
class SubsonicClient(
    private val baseUrl: String,
    private val username: String,
    private val token: String,
    private val salt: String,
) {

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    suspend fun call(method: String, params: Map<String, String> = emptyMap()): SubsonicResponse {
        val request = Request.Builder()
            .url(apiUrl(method, params, includeJson = true))
            .get()
            .build()

        return withContext(Dispatchers.IO) {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                val body = response.body?.string().orEmpty()
                val envelope = runCatching {
                    json.decodeFromString<SubsonicEnvelope>(body)
                }.getOrElse {
                    throw IllegalStateException("Unerwartete Antwort auf $method.")
                }
                val payload = envelope.response
                if (payload.status != "ok") {
                    throw SubsonicException(
                        payload.error?.code ?: -1,
                        payload.error?.message ?: "Subsonic-Fehler",
                    )
                }
                payload
            }
        }
    }

    suspend fun ping(): SubsonicResponse = call("ping")

    suspend fun scrobble(trackId: String, submission: Boolean) {
        call(
            "scrobble",
            mapOf(
                "id" to trackId,
                "submission" to submission.toString(),
                "time" to System.currentTimeMillis().toString(),
            ),
        )
    }

    fun streamUrl(trackId: String, transcode: Boolean): String {
        val params = if (transcode) {
            mapOf("id" to trackId, "format" to "mp3", "maxBitRate" to "320")
        } else {
            mapOf("id" to trackId, "format" to "raw")
        }
        return apiUrl("stream", params, includeJson = false)
    }

    fun coverUrl(coverArtId: String, size: Int = 700): String =
        apiUrl("getCoverArt", mapOf("id" to coverArtId, "size" to size.toString()), includeJson = false)

    fun scrobbleUrl(trackId: String, submission: Boolean): String =
        apiUrl(
            "scrobble",
            mapOf(
                "id" to trackId,
                "submission" to submission.toString(),
                "time" to System.currentTimeMillis().toString(),
            ),
            includeJson = false,
        )

    private fun apiUrl(
        method: String,
        params: Map<String, String>,
        includeJson: Boolean,
    ): String {
        val query = LinkedHashMap<String, String>().apply {
            put("u", username)
            put("t", token)
            put("s", salt)
            put("v", API_VERSION)
            put("c", "Nadelton")
            if (includeJson) put("f", "json")
            putAll(params)
        }
        val encoded = query.entries.joinToString("&") { (key, value) ->
            "$key=${URLEncoder.encode(value, Charsets.UTF_8.name())}"
        }
        return "${baseUrl}rest/$method.view?$encoded"
    }

    private companion object {
        const val API_VERSION = "1.16.1"
    }
}
