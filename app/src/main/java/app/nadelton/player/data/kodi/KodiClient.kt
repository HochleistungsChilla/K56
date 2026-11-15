package app.nadelton.player.data.kodi

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Kodi JSON-RPC client. Works with an optional HTTP basic auth header and
 * resolves playable files through Files.PrepareDownload.
 */
class KodiClient(
    private val baseUrl: String,
    private val basicAuth: String?,
) {

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
    }

    suspend fun ping() {
        withContext(Dispatchers.IO) {
            val response = http.newCall(request("JSONRPC.Ping", buildJsonObject {})).execute()
            response.use {
                if (!it.isSuccessful) throw IOException("Kodi antwortet mit HTTP ${it.code}.")
            }
        }
    }

    suspend fun <T> call(
        method: String,
        params: JsonObject = buildJsonObject { },
        deserializer: DeserializationStrategy<T>,
    ): T? = withContext(Dispatchers.IO) {
        http.newCall(request(method, params)).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Kodi antwortet mit HTTP ${response.code}.")
            val root = json.parseToJsonElement(response.body?.string().orEmpty()).jsonObject
            root["error"]?.let { error ->
                val message = error.jsonObject["message"]?.jsonPrimitive?.content
                    ?: "Unbekannter Kodi-Fehler"
                throw IllegalStateException("Kodi: $message")
            }
            root["result"]?.let { json.decodeFromJsonElement(deserializer, it) }
        }
    }

    fun imageUrl(thumbnail: String?): String? {
        if (thumbnail.isNullOrBlank()) return null
        val encoded = URLEncoder.encode(thumbnail, Charsets.UTF_8.name())
        return "${baseUrl}image/$encoded"
    }

    fun fileUrl(path: String): String {
        if (path.startsWith("http://", ignoreCase = true) ||
            path.startsWith("https://", ignoreCase = true)
        ) {
            return path
        }
        return "${baseUrl}${path.trimStart('/')}"
    }

    private fun request(method: String, params: JsonObject): Request {
        val payload = buildJsonObject {
            put("jsonrpc", "2.0")
            put("id", "nadelton")
            put("method", method)
            put("params", params)
        }
        val builder = Request.Builder()
            .url("${baseUrl}jsonrpc")
            .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
            .header("Content-Type", "application/json")
        if (!basicAuth.isNullOrBlank()) {
            builder.header("Authorization", "Basic $basicAuth")
        }
        return builder.build()
    }

    private companion object {
        val JSON_MEDIA_TYPE = "application/json".toMediaType()
    }
}
