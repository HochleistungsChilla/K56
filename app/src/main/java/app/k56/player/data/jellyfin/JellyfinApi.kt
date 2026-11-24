package app.k56.player.data.jellyfin

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

    /** Who the stored token belongs to; the cheapest proof that auth still works. */
    @GET("Users/Me")
    suspend fun me(): UserDto

    // Present in Jellyfin 10.10 and still in current releases; the
    // per-user route `Users/{userId}/Views` was dropped later on.
    @GET("UserViews")
    suspend fun userViews(@Query("userId") userId: String): QueryResultDto

    @GET("Items")
    suspend fun items(@QueryMap query: Map<String, String>): QueryResultDto

    @GET("Items/{itemId}/InstantMix")
    suspend fun instantMix(@Path("itemId") itemId: String, @QueryMap query: Map<String, String>): QueryResultDto

    @GET("Items/{itemId}")
    suspend fun item(
        @Path("itemId") itemId: String,
        @Query("userId") userId: String?,
    ): BaseItemDto

    @GET("Artists")
    suspend fun artists(@QueryMap query: Map<String, String>): QueryResultDto

    @GET("Genres")
    suspend fun genres(@QueryMap query: Map<String, String>): QueryResultDto

    @POST("Playlists")
    suspend fun createPlaylist(
        @Query("name") name: String,
        @Query("ids") ids: String?,
        @Query("userId") userId: String,
    ): PlaylistCreationResultDto

    @POST("Playlists/{playlistId}")
    suspend fun updatePlaylist(
        @Path("playlistId") playlistId: String,
        @Query("name") name: String,
    )

    @DELETE("Playlists/{playlistId}")
    suspend fun deletePlaylist(@Path("playlistId") playlistId: String)

    @POST("Playlists/{playlistId}/Items")
    suspend fun addPlaylistItems(
        @Path("playlistId") playlistId: String,
        @Query("ids") ids: String,
        @Query("userId") userId: String,
    )

    @DELETE("Playlists/{playlistId}/Items")
    suspend fun removePlaylistItems(
        @Path("playlistId") playlistId: String,
        @Query("ids") ids: String,
    )

    @POST("Sessions/Playing")
    suspend fun reportPlaybackStart(@Body body: PlaybackReportDto)

    @POST("Sessions/Playing/Progress")
    suspend fun reportPlaybackProgress(@Body body: PlaybackReportDto)

    @POST("Sessions/Playing/Stopped")
    suspend fun reportPlaybackStopped(@Body body: PlaybackReportDto)

    @POST("UserFavoriteItems/{itemId}")
    suspend fun addFavorite(
        @Query("userId") userId: String,
        @Path("itemId") itemId: String,
    ): UserItemDataDto

    @DELETE("UserFavoriteItems/{itemId}")
    suspend fun removeFavorite(
        @Query("userId") userId: String,
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
