package app.k56.player.data.acquisition.lidarr

import app.k56.player.core.net.ServerUrl
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

object LidarrApiFactory {

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
    }

    /**
     * Accepts "host:8686", "http://host:8686", a trailing slash, or the URL of
     * the Lidarr web UI.
     */
    fun normalizeUrl(raw: String): String = ServerUrl.normalize(raw)

    fun create(baseUrl: String, apiKey: String): LidarrApi {
        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                chain.proceed(
                    chain.request().newBuilder()
                        .header("X-Api-Key", apiKey)
                        .header("Accept", "application/json")
                        .build(),
                )
            }
            .build()

        return Retrofit.Builder()
            .baseUrl(if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/")
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(LidarrApi::class.java)
    }
}
