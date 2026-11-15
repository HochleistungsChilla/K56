package app.nadelton.player.data.abs

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Audiobookshelf REST client. Media endpoints accept the token as a query
 * parameter, which lets ExoPlayer and Coil stream without custom headers.
 */
class AbsClient(
    private val baseUrl: String,
    private val token: String? = null,
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

    suspend fun login(username: String, password: String): AbsLoginResponse = withContext(Dispatchers.IO) {
        val body = json.encodeToString(AbsLoginRequest.serializer(), AbsLoginRequest(username, password))
        val request = Request.Builder()
            .url(urlFor("/login", emptyMap()))
            .post(body.toRequestBody(JSON_MEDIA_TYPE))
            .header("Content-Type", "application/json")
            .build()

        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Audiobookshelf antwortet mit HTTP ${response.code}.")
            json.decodeFromString(AbsLoginResponse.serializer(), response.body?.string().orEmpty())
        }
    }

    suspend fun status(): AbsStatus = get(
        path = "/status",
        deserializer = AbsStatus.serializer(),
    )

    suspend fun libraries(): AbsLibrariesResponse = get(
        path = "/api/libraries",
        deserializer = AbsLibrariesResponse.serializer(),
    )

    suspend fun items(
        libraryId: String,
        limit: Int = 500,
        page: Int = 0,
    ): AbsItemsResponse = get(
        path = "/api/libraries/$libraryId/items",
        params = mapOf(
            "limit" to limit.toString(),
            "page" to page.toString(),
            "sort" to "addedAt",
            "desc" to "1",
        ),
        deserializer = AbsItemsResponse.serializer(),
    )

    suspend fun item(itemId: String): AbsItemResponse = get(
        path = "/api/items/$itemId",
        deserializer = AbsItemResponse.serializer(),
    )

    suspend fun reportProgress(
        itemId: String,
        positionSeconds: Double,
        durationSeconds: Double? = null,
    ) {
        val progress = if (durationSeconds != null && durationSeconds > 0) {
            (positionSeconds / durationSeconds).coerceIn(0.0, 1.0)
        } else {
            null
        }
        postJson(
            path = "/api/me/progress/$itemId",
            body = json.encodeToString(
                AbsProgressRequest.serializer(),
                AbsProgressRequest(
                    currentTime = positionSeconds,
                    duration = durationSeconds,
                    progress = progress,
                    isFinished = false,
                ),
            ),
        )
    }

    fun coverUrl(coverPath: String?): String? {
        if (coverPath.isNullOrBlank()) return null
        return "${baseUrl.trimEnd('/')}${if (coverPath.startsWith("/")) coverPath else "/$coverPath"}" +
            "?token=${encode(token.orEmpty())}"
    }

    fun streamUrl(contentUrl: String): String {
        val path = if (contentUrl.startsWith("/")) contentUrl else "/$contentUrl"
        return "${baseUrl.trimEnd('/')}$path?token=${encode(token.orEmpty())}"
    }

    private suspend fun <T> get(
        path: String,
        params: Map<String, String> = emptyMap(),
        deserializer: DeserializationStrategy<T>,
    ): T = withContext(Dispatchers.IO) {
        val builder = Request.Builder()
            .url(urlFor(path, params))
            .header("Accept", "application/json")
        if (!token.isNullOrBlank()) builder.header("Authorization", "Bearer $token")

        http.newCall(builder.get().build()).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Audiobookshelf antwortet mit HTTP ${response.code}.")
            json.decodeFromString(deserializer, response.body?.string().orEmpty())
        }
    }

    private suspend fun postJson(path: String, body: String) {
        withContext(Dispatchers.IO) {
            val builder = Request.Builder()
                .url(urlFor(path, emptyMap()))
                .post(body.toRequestBody(JSON_MEDIA_TYPE))
                .header("Content-Type", "application/json")
            if (!token.isNullOrBlank()) builder.header("Authorization", "Bearer $token")

            http.newCall(builder.build()).execute().use { response ->
                if (!response.isSuccessful) throw IOException("Audiobookshelf antwortet mit HTTP ${response.code}.")
            }
        }
    }

    private fun urlFor(path: String, params: Map<String, String>): String {
        val base = baseUrl.trimEnd('/')
        val normalized = if (path.startsWith("/")) path else "/$path"
        if (params.isEmpty()) return "$base$normalized"
        val query = params.entries.joinToString("&") { (key, value) ->
            "${encode(key)}=${encode(value)}"
        }
        return "$base$normalized?$query"
    }

    private fun encode(value: String): String =
        URLEncoder.encode(value, Charsets.UTF_8.name())

    private companion object {
        val JSON_MEDIA_TYPE = "application/json".toMediaType()
    }
}
