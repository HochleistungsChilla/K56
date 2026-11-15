package app.resona.player.data.plex

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * plex.tv account flow: create a PIN, user enters it on plex.tv/link, poll
 * until it carries an auth token, then enumerate the account's servers.
 */
class PlexTvClient(
    private val clientId: String,
    private val clientVersion: String,
) {

    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
    }

    suspend fun createPin(): PlexPin = execute(
        url = "https://plex.tv/api/v2/pins?strong=true",
        method = "POST",
        token = null,
        deserializer = PlexPin.serializer(),
    )

    suspend fun pollPin(pinId: Long): PlexPin = execute(
        url = "https://plex.tv/api/v2/pins/$pinId",
        method = "GET",
        token = null,
        deserializer = PlexPin.serializer(),
    )

    suspend fun resources(accountToken: String): List<PlexResource> = execute(
        url = "https://plex.tv/api/v2/resources?includeHttps=1&includeRelay=1",
        method = "GET",
        token = accountToken,
        deserializer = ListSerializer(PlexResource.serializer()),
    )

    private suspend fun <T> execute(
        url: String,
        method: String,
        token: String?,
        deserializer: DeserializationStrategy<T>,
    ): T = withContext(Dispatchers.IO) {
        val builder = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("X-Plex-Product", "Resona")
            .header("X-Plex-Version", clientVersion)
            .header("X-Plex-Client-Identifier", clientId)
            .header("X-Plex-Platform", "Android")
            .header("X-Plex-Device", "Android")
            .header("X-Plex-Device-Name", "Resona")

        if (!token.isNullOrBlank()) builder.header("X-Plex-Token", token)

        if (method == "POST") {
            builder.post(ByteArray(0).toRequestBody("application/json".toMediaType()))
        }

        http.newCall(builder.build()).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Plex.tv antwortet mit HTTP ${response.code}.")
            val body = response.body?.string().orEmpty()
            json.decodeFromString(deserializer, body)
        }
    }
}
