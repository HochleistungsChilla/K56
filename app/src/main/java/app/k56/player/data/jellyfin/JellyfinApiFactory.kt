package app.k56.player.data.jellyfin

import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import app.k56.player.core.net.DiagnosticsInterceptor
import app.k56.player.core.net.ServerUrl
import java.util.concurrent.TimeUnit

object JellyfinApiFactory {

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
    }

    /**
     * Accepts "host:8096", "http://host:8096", a trailing slash, or a pasted
     * web UI URL ("https://host/jellyfin/web/index.html").
     */
    fun normalizeServerUrl(raw: String): String = ServerUrl.normalize(raw)

    fun create(
        baseUrl: String,
        deviceId: String,
        clientVersion: String,
        token: String?,
    ): JellyfinApi {
        val client = OkHttpClient.Builder()
            .addInterceptor(DiagnosticsInterceptor())
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val builder = chain.request().newBuilder()
                    .header(
                        "Authorization",
                        "MediaBrowser Client=\"K56\", Device=\"Android\", " +
                            "DeviceId=\"$deviceId\", Version=\"$clientVersion\"",
                    )
                    .header(
                        "X-Emby-Authorization",
                        "MediaBrowser Client=\"K56\", Device=\"Android\", " +
                            "DeviceId=\"$deviceId\", Version=\"$clientVersion\"",
                    )
                if (!token.isNullOrBlank()) {
                    builder.header("X-Emby-Token", token)
                }
                chain.proceed(builder.build())
            }
            .build()

        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(JellyfinApi::class.java)
    }
}
