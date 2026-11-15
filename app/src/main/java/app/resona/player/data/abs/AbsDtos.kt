package app.resona.player.data.abs

import kotlinx.serialization.Serializable

@Serializable
data class AbsLoginRequest(
    val username: String,
    val password: String,
)

@Serializable
data class AbsLoginResponse(
    val user: AbsUser? = null,
    val userDefaultLibraryId: String? = null,
)

@Serializable
data class AbsUser(
    val id: String? = null,
    val username: String? = null,
    val token: String? = null,
)

@Serializable
data class AbsStatus(
    val app: String? = null,
    val serverVersion: String? = null,
    val serverName: String? = null,
    val version: String? = null,
)

@Serializable
data class AbsLibrariesResponse(
    val libraries: List<AbsLibrary>? = null,
)

@Serializable
data class AbsLibrary(
    val id: String? = null,
    val name: String? = null,
    val mediaType: String? = null,
)

@Serializable
data class AbsItemsResponse(
    val results: List<AbsLibraryItem>? = null,
    val total: Int = 0,
)

@Serializable
data class AbsLibraryItem(
    val id: String? = null,
    val mediaType: String? = null,
    val media: AbsMedia? = null,
)

@Serializable
data class AbsItemResponse(
    val id: String? = null,
    val media: AbsMedia? = null,
)

@Serializable
data class AbsMedia(
    val metadata: AbsMetadata? = null,
    val coverPath: String? = null,
    val duration: Double? = null,
    val numTracks: Int? = null,
    val tracks: List<AbsTrack>? = null,
    val chapters: List<AbsChapter>? = null,
)

@Serializable
data class AbsMetadata(
    val title: String? = null,
    val authorName: String? = null,
    val narratorName: String? = null,
    val publishedYear: Int? = null,
)

@Serializable
data class AbsTrack(
    val index: Int? = null,
    val startOffset: Double? = null,
    val duration: Double? = null,
    val contentUrl: String? = null,
    val codec: String? = null,
    val bitRate: Int? = null,
    val title: String? = null,
)

@Serializable
data class AbsChapter(
    val start: Double? = null,
    val end: Double? = null,
    val title: String? = null,
)

@Serializable
data class AbsProgressRequest(
    val currentTime: Double,
    val duration: Double? = null,
    val progress: Double? = null,
    val isFinished: Boolean? = null,
)
