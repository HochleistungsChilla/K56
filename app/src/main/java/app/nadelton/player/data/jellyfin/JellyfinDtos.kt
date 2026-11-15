package app.nadelton.player.data.jellyfin

import kotlinx.serialization.Serializable

@Serializable
data class PublicSystemInfoDto(
    val ServerName: String? = null,
    val Version: String? = null,
    val Id: String? = null,
    val OperatingSystem: String? = null,
)

@Serializable
data class LoginRequest(
    val Username: String,
    val Pw: String,
)

@Serializable
data class UserDto(
    val Id: String = "",
    val Name: String? = null,
)

@Serializable
data class AuthResultDto(
    val User: UserDto = UserDto(),
    val AccessToken: String = "",
    val ServerId: String? = null,
)

@Serializable
data class NameIdPairDto(
    val Name: String? = null,
    val Id: String? = null,
)

@Serializable
data class UserItemDataDto(
    val IsFavorite: Boolean? = null,
    val PlayCount: Int? = null,
    val PlaybackPositionTicks: Long? = null,
    val PlayedPercentage: Double? = null,
    val Played: Boolean? = null,
)

@Serializable
data class MediaSourceDto(
    val Id: String? = null,
    val Container: String? = null,
    val Bitrate: Int? = null,
    val RunTimeTicks: Long? = null,
)

@Serializable
data class BaseItemDto(
    val Id: String = "",
    val Name: String? = null,
    val SortName: String? = null,
    val Type: String? = null,
    val CollectionType: String? = null,
    val Album: String? = null,
    val AlbumId: String? = null,
    val AlbumArtist: String? = null,
    val AlbumPrimaryImageTag: String? = null,
    val AlbumArtists: List<NameIdPairDto>? = null,
    val Artists: List<String>? = null,
    val RunTimeTicks: Long? = null,
    val IndexNumber: Int? = null,
    val ParentIndexNumber: Int? = null,
    val ProductionYear: Int? = null,
    val ChildCount: Int? = null,
    val ImageTags: Map<String, String>? = null,
    val Container: String? = null,
    val Bitrate: Int? = null,
    val MediaSources: List<MediaSourceDto>? = null,
    val UserData: UserItemDataDto? = null,
    val Overview: String? = null,
    val Genres: List<String>? = null,
)

@Serializable
data class QueryResultDto(
    val Items: List<BaseItemDto>? = null,
    val TotalRecordCount: Int = 0,
    val StartIndex: Int = 0,
)

@Serializable
data class PlaybackReportDto(
    val ItemId: String? = null,
    val MediaSourceId: String? = null,
    val PlaySessionId: String? = null,
    val PositionTicks: Long? = null,
    val CanSeek: Boolean? = null,
    val IsPaused: Boolean? = null,
    val IsMuted: Boolean? = null,
    val VolumeLevel: Int? = null,
    val PlayMethod: String? = null,
    val RepeatMode: String? = null,
)

@Serializable
data class QuickConnectResultDto(
    val Authenticated: Boolean? = null,
    val Secret: String? = null,
    val Code: String? = null,
    val DeviceId: String? = null,
    val DeviceName: String? = null,
    val AppName: String? = null,
    val AppVersion: String? = null,
    val DateAdded: String? = null,
)

@Serializable
data class QuickConnectAuthDto(
    val Secret: String,
)
