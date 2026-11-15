package app.k56.player.data.plex

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PlexPin(
    val id: Long = 0,
    val code: String? = null,
    val authToken: String? = null,
    val expiresAt: String? = null,
)

@Serializable
data class PlexResource(
    val name: String? = null,
    val product: String? = null,
    @SerialName("productVersion") val productVersion: String? = null,
    val platform: String? = null,
    val clientIdentifier: String? = null,
    val provides: String? = null,
    val accessToken: String? = null,
    val connections: List<PlexConnection>? = null,
)

@Serializable
data class PlexConnection(
    val protocol: String? = null,
    val address: String? = null,
    val port: Int? = null,
    val uri: String? = null,
    val local: Boolean? = null,
    val relay: Boolean? = null,
    val IPv6: Boolean? = null,
)

@Serializable
data class PlexDirectoriesEnvelope(
    @SerialName("MediaContainer") val container: PlexDirectories? = null,
)

@Serializable
data class PlexDirectories(
    @SerialName("Directory") val directory: List<PlexSection>? = null,
    val size: Int? = null,
)

@Serializable
data class PlexSection(
    val key: String? = null,
    val type: String? = null,
    val title: String? = null,
)

@Serializable
data class PlexMetadataEnvelope(
    @SerialName("MediaContainer") val container: PlexMetadataContainer? = null,
)

@Serializable
data class PlexMetadataContainer(
    @SerialName("Metadata") val metadata: List<PlexItem>? = null,
    val size: Int? = null,
)

@Serializable
data class PlexHubsEnvelope(
    @SerialName("MediaContainer") val container: PlexHubs? = null,
)

@Serializable
data class PlexHubs(
    @SerialName("Hub") val hub: List<PlexHub>? = null,
)

@Serializable
data class PlexHub(
    @SerialName("Metadata") val metadata: List<PlexItem>? = null,
)

@Serializable
data class PlexItem(
    @SerialName("ratingKey") val ratingKey: String? = null,
    @SerialName("key") val key: String? = null,
    @SerialName("type") val type: String? = null,
    @SerialName("title") val title: String? = null,
    @SerialName("parentTitle") val parentTitle: String? = null,
    @SerialName("grandparentTitle") val grandparentTitle: String? = null,
    @SerialName("parentRatingKey") val parentRatingKey: String? = null,
    @SerialName("grandparentRatingKey") val grandparentRatingKey: String? = null,
    @SerialName("thumb") val thumb: String? = null,
    @SerialName("parentThumb") val parentThumb: String? = null,
    @SerialName("grandparentThumb") val grandparentThumb: String? = null,
    @SerialName("year") val year: Int? = null,
    @SerialName("leafCount") val leafCount: Int? = null,
    @SerialName("index") val index: Int? = null,
    @SerialName("parentIndex") val parentIndex: Int? = null,
    @SerialName("duration") val duration: Long? = null,
    @SerialName("Media") val media: List<PlexMedia>? = null,
)

@Serializable
data class PlexMedia(
    val id: Long? = null,
    val container: String? = null,
    val bitrate: Int? = null,
    @SerialName("Part") val part: List<PlexPart>? = null,
)

@Serializable
data class PlexPart(
    val id: Long? = null,
    val key: String? = null,
    val container: String? = null,
)
