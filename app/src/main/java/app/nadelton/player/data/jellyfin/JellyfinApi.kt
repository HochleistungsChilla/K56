package app.nadelton.player.data.jellyfin

import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.QueryMap

/**
 * Minimal, documented Jellyfin REST surface (10.10.x). Everything goes through
 * the X-Emby-Token header except media URLs, where Jellyfin expects api_key.
 */
interface JellyfinApi {

    @POST("Users/AuthenticateByName")
    suspend fun authenticate(@Body body: LoginRequest): AuthResultDto

    @GET("System/Info/Public")
    suspend fun publicSystemInfo(): PublicSystemInfoDto

    @GET("Users/{userId}/Views")
    suspend fun userViews(@Path("userId") userId: String): QueryResultDto

    @GET("Items")
    suspend fun items(@QueryMap query: Map<String, String>): QueryResultDto

    @GET("Items/{itemId}")
    suspend fun item(
        @Path("itemId") itemId: String,
        @Query("userId") userId: String?,
    ): BaseItemDto

    @GET("Artists")
    suspend fun artists(@QueryMap query: Map<String, String>): QueryResultDto

    @POST("Sessions/Playing")
    suspend fun reportPlaybackStart(@Body body: PlaybackReportDto)

    @POST("Sessions/Playing/Progress")
    suspend fun reportPlaybackProgress(@Body body: PlaybackReportDto)

    @POST("Sessions/Playing/Stopped")
    suspend fun reportPlaybackStopped(@Body body: PlaybackReportDto)

    @POST("Users/{userId}/FavoriteItems/{itemId}")
    suspend fun addFavorite(
        @Path("userId") userId: String,
        @Path("itemId") itemId: String,
    ): UserItemDataDto

    @DELETE("Users/{userId}/FavoriteItems/{itemId}")
    suspend fun removeFavorite(
        @Path("userId") userId: String,
        @Path("itemId") itemId: String,
    ): UserItemDataDto

    @GET("QuickConnect/Enabled")
    suspend fun quickConnectEnabled(): Boolean

    @POST("QuickConnect/Initiate")
    suspend fun quickConnectInitiate(): QuickConnectResultDto

    @GET("QuickConnect/Connect")
    suspend fun quickConnectPoll(@Query("secret") secret: String): QuickConnectResultDto

    @POST("Users/AuthenticateWithQuickConnect")
    suspend fun authenticateWithQuickConnect(@Body body: QuickConnectAuthDto): AuthResultDto
}
