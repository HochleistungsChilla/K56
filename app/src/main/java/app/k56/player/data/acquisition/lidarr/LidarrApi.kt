package app.k56.player.data.acquisition.lidarr

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Query

/**
 * Lidarr v1 REST surface. Authentication is the `X-Api-Key` header, added by
 * [LidarrApiFactory]; the key itself never appears in a URL or a log line.
 */
interface LidarrApi {

    @GET("api/v1/system/status")
    suspend fun status(): LidarrStatusDto

    @GET("api/v1/qualityprofile")
    suspend fun qualityProfiles(): List<LidarrProfileDto>

    @GET("api/v1/metadataprofile")
    suspend fun metadataProfiles(): List<LidarrProfileDto>

    @GET("api/v1/rootfolder")
    suspend fun rootFolders(): List<LidarrRootFolderDto>

    @GET("api/v1/artist")
    suspend fun artists(): List<LidarrArtistDto>

    @GET("api/v1/artist/lookup")
    suspend fun lookupArtist(@Query("term") term: String): List<LidarrArtistDto>

    @POST("api/v1/artist")
    suspend fun addArtist(@Body body: LidarrAddArtistRequest): LidarrArtistDto

    @GET("api/v1/album")
    suspend fun albumsByArtist(@Query("artistId") artistId: Int): List<LidarrAlbumDto>

    @GET("api/v1/album/lookup")
    suspend fun lookupAlbum(@Query("term") term: String): List<LidarrAlbumDto>

    @PUT("api/v1/album/monitor")
    suspend fun monitorAlbums(@Body body: LidarrMonitoredRequest)

    @POST("api/v1/command")
    suspend fun command(@Body body: LidarrCommandRequest): LidarrCommandDto

    @GET("api/v1/queue")
    suspend fun queue(
        @Query("page") page: Int = 1,
        @Query("pageSize") pageSize: Int = 50,
        @Query("includeUnknownArtistItems") includeUnknown: Boolean = true,
    ): LidarrQueueDto

    @GET("api/v1/wanted/missing")
    suspend fun wantedMissing(
        @Query("page") page: Int = 1,
        @Query("pageSize") pageSize: Int = 50,
        @Query("monitored") monitored: Boolean = true,
    ): LidarrAlbumPageDto

    @GET("api/v1/history")
    suspend fun history(
        @Query("page") page: Int = 1,
        @Query("pageSize") pageSize: Int = 50,
        @Query("sortKey") sortKey: String = "date",
        @Query("sortDirection") sortDirection: String = "descending",
    ): LidarrHistoryPageDto
}
