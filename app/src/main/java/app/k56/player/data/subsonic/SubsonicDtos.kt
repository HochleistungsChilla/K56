package app.k56.player.data.subsonic

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SubsonicEnvelope(
    @SerialName("subsonic-response") val response: SubsonicResponse,
)

@Serializable
data class SubsonicResponse(
    val status: String = "ok",
    val version: String? = null,
    val error: SubsonicError? = null,
    val musicFolders: MusicFoldersDto? = null,
    val albumList2: AlbumListDto? = null,
    val artists: ArtistsDto? = null,
    val artist: ArtistDto? = null,
    val album: AlbumDto? = null,
    val searchResult3: SearchResultDto? = null,
    val randomSongs: SongsDto? = null,
)

@Serializable
data class SubsonicError(
    val code: Int = -1,
    val message: String? = null,
)

@Serializable
data class MusicFoldersDto(
    val musicFolder: List<MusicFolderDto>? = null,
)

@Serializable
data class MusicFolderDto(
    val id: String = "",
    val name: String? = null,
)

@Serializable
data class AlbumListDto(
    val album: List<AlbumDto>? = null,
)

@Serializable
data class SongsDto(
    val song: List<SongDto>? = null,
)

@Serializable
data class AlbumDto(
    val id: String = "",
    val name: String? = null,
    val artist: String? = null,
    val artistId: String? = null,
    val coverArt: String? = null,
    val songCount: Int? = null,
    val year: Int? = null,
    val starred: String? = null,
    val song: List<SongDto>? = null,
)

@Serializable
data class ArtistsDto(
    val index: List<IndexDto>? = null,
)

@Serializable
data class IndexDto(
    val name: String? = null,
    val artist: List<ArtistDto>? = null,
)

@Serializable
data class ArtistDto(
    val id: String = "",
    val name: String? = null,
    val albumCount: Int? = null,
    val coverArt: String? = null,
    val starred: String? = null,
    val album: List<AlbumDto>? = null,
)

@Serializable
data class SongDto(
    val id: String = "",
    val title: String? = null,
    val album: String? = null,
    val albumId: String? = null,
    val artist: String? = null,
    val artistId: String? = null,
    val duration: Int? = null,
    val track: Int? = null,
    val discNumber: Int? = null,
    val suffix: String? = null,
    val bitRate: Int? = null,
    val coverArt: String? = null,
    val starred: String? = null,
    val playCount: Int? = null,
)

@Serializable
data class SearchResultDto(
    val artist: List<ArtistDto>? = null,
    val album: List<AlbumDto>? = null,
    val song: List<SongDto>? = null,
)
