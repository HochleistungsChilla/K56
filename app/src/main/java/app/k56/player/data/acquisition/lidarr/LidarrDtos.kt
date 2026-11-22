package app.k56.player.data.acquisition.lidarr

import kotlinx.serialization.Serializable

/**
 * Lidarr v1 DTOs. Only the fields K56 actually reads are declared; the API
 * sends a lot more and unknown keys are ignored by the JSON parser.
 */

@Serializable
data class LidarrStatusDto(
    val appName: String? = null,
    val version: String? = null,
    val branch: String? = null,
    val isProduction: Boolean? = null,
)

@Serializable
data class LidarrImageDto(
    val coverType: String? = null,
    val url: String? = null,
    val remoteUrl: String? = null,
)

@Serializable
data class LidarrStatisticsDto(
    val albumCount: Int? = null,
    val trackFileCount: Int? = null,
    val trackCount: Int? = null,
    val totalTrackCount: Int? = null,
    val percentOfTracks: Double? = null,
    val sizeOnDisk: Long? = null,
)

@Serializable
data class LidarrArtistRefDto(
    val id: Int? = null,
    val artistName: String? = null,
    val foreignArtistId: String? = null,
)

@Serializable
data class LidarrArtistDto(
    val id: Int? = null,
    val artistName: String? = null,
    val foreignArtistId: String? = null,
    val monitored: Boolean? = null,
    val overview: String? = null,
    val images: List<LidarrImageDto> = emptyList(),
    val statistics: LidarrStatisticsDto? = null,
)

@Serializable
data class LidarrAlbumDto(
    val id: Int? = null,
    val title: String? = null,
    val disambiguation: String? = null,
    val overview: String? = null,
    val foreignAlbumId: String? = null,
    val monitored: Boolean? = null,
    val artistId: Int? = null,
    val artist: LidarrArtistRefDto? = null,
    val albumType: String? = null,
    val releaseDate: String? = null,
    val genres: List<String> = emptyList(),
    val images: List<LidarrImageDto> = emptyList(),
    val statistics: LidarrStatisticsDto? = null,
)

@Serializable
data class LidarrQueueRecordDto(
    val id: Int? = null,
    val albumId: Int? = null,
    val artistId: Int? = null,
    val title: String? = null,
    val status: String? = null,
    val trackedDownloadStatus: String? = null,
    val trackedDownloadState: String? = null,
    val size: Double? = null,
    val sizeleft: Double? = null,
    val timeleft: String? = null,
    val errorMessage: String? = null,
    val album: LidarrAlbumDto? = null,
    val artist: LidarrArtistRefDto? = null,
) {
    /** 0f..1f, or null when the server has not reported a size yet. */
    val progress: Float?
        get() {
            val total = size ?: return null
            val left = sizeleft ?: return null
            if (total <= 0.0) return null
            return ((total - left) / total).toFloat().coerceIn(0f, 1f)
        }
}

@Serializable
data class LidarrQueueDto(
    val page: Int = 1,
    val pageSize: Int = 0,
    val totalRecords: Int = 0,
    val records: List<LidarrQueueRecordDto> = emptyList(),
)

/** Paged album list, as returned by `wanted/missing` and `album`. */
@Serializable
data class LidarrAlbumPageDto(
    val page: Int = 1,
    val pageSize: Int = 0,
    val totalRecords: Int = 0,
    val records: List<LidarrAlbumDto> = emptyList(),
)

@Serializable
data class LidarrHistoryRecordDto(
    val id: Int? = null,
    val albumId: Int? = null,
    val artistId: Int? = null,
    val sourceTitle: String? = null,
    val date: String? = null,
    val eventType: String? = null,
    val album: LidarrAlbumDto? = null,
    val artist: LidarrArtistRefDto? = null,
)

@Serializable
data class LidarrHistoryPageDto(
    val page: Int = 1,
    val pageSize: Int = 0,
    val totalRecords: Int = 0,
    val records: List<LidarrHistoryRecordDto> = emptyList(),
)

@Serializable
data class LidarrCommandRequest(
    val name: String,
    val albumIds: List<Int>? = null,
    val artistIds: List<Int>? = null,
)

@Serializable
data class LidarrCommandDto(
    val id: Int? = null,
    val name: String? = null,
    val status: String? = null,
)

@Serializable
data class LidarrProfileDto(
    val id: Int = 0,
    val name: String? = null,
)

@Serializable
data class LidarrRootFolderDto(
    val id: Int = 0,
    val path: String? = null,
    val freeSpace: Long? = null,
)

/** Minimal payload: Lidarr fills the rest from its own metadata lookup. */
@Serializable
data class LidarrAddArtistRequest(
    val foreignArtistId: String,
    val artistName: String,
    val qualityProfileId: Int,
    val metadataProfileId: Int,
    val rootFolderPath: String,
    val monitored: Boolean = true,
    val addOptions: LidarrAddOptions = LidarrAddOptions(),
)

@Serializable
data class LidarrAddOptions(
    /** "none": add the artist without grabbing its whole discography. */
    val monitor: String = "none",
    val searchForMissingAlbums: Boolean = false,
)

@Serializable
data class LidarrMonitoredRequest(
    val albumIds: List<Int>,
    val monitored: Boolean = true,
)
