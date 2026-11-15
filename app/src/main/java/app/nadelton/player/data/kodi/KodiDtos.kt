package app.nadelton.player.data.kodi

import kotlinx.serialization.Serializable

@Serializable
data class KodiVersion(
    val major: Int = 0,
    val minor: Int = 0,
    val patch: Int = 0,
) {
    val label: String get() = "$major.$minor.$patch"
}

@Serializable
data class KodiAppProperties(
    val version: KodiVersion? = null,
    val name: String? = null,
)

@Serializable
data class KodiArtistsResult(
    val artists: List<KodiArtist>? = null,
)

@Serializable
data class KodiArtist(
    val artistid: Int = 0,
    val label: String? = null,
    val thumbnail: String? = null,
)

@Serializable
data class KodiAlbumsResult(
    val albums: List<KodiAlbum>? = null,
)

@Serializable
data class KodiAlbum(
    val albumid: Int = 0,
    val label: String? = null,
    val artist: List<String>? = null,
    val artistid: List<Int>? = null,
    val year: Int? = null,
    val thumbnail: String? = null,
)

@Serializable
data class KodiSongsResult(
    val songs: List<KodiSong>? = null,
)

@Serializable
data class KodiSong(
    val songid: Int = 0,
    val label: String? = null,
    val title: String? = null,
    val artist: List<String>? = null,
    val album: String? = null,
    val albumid: Int? = null,
    val duration: Int? = null,
    val track: Int? = null,
    val disc: Int? = null,
    val file: String? = null,
    val thumbnail: String? = null,
)

@Serializable
data class KodiAlbumDetailsResult(
    val albumdetails: KodiAlbum? = null,
)

@Serializable
data class KodiArtistDetailsResult(
    val artistdetails: KodiArtist? = null,
)

@Serializable
data class KodiDownloadDetails(
    val path: String? = null,
    val protocol: String? = null,
)

@Serializable
data class KodiPreparedDownload(
    val details: KodiDownloadDetails? = null,
)
