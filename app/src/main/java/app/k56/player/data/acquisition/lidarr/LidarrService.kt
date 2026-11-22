package app.k56.player.data.acquisition.lidarr

import app.k56.player.data.acquisition.AcquisitionConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.IOException

/** One album the user can ask Lidarr to fetch. */
data class AlbumCandidate(
    val foreignAlbumId: String,
    val title: String,
    val artistName: String,
    val artistForeignId: String?,
    val year: Int?,
    val trackCount: Int?,
    val coverUrl: String?,
    /** Lidarr already knows the artist (so it is not added twice). */
    val artistInLidarr: Boolean,
)

/** What a successful connection test found. */
data class LidarrConnection(
    val version: String?,
    val qualityProfiles: List<LidarrProfileDto>,
    val metadataProfiles: List<LidarrProfileDto>,
    val rootFolders: List<LidarrRootFolderDto>,
)

data class RequestOutcome(
    val albumTitle: String,
    val artistAdded: Boolean,
    val commandId: Int?,
)

class LidarrException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * The acquisition flow, kept out of the UI:
 *
 * 1. find or add the artist (monitor = none, so no discography grab)
 * 2. find the album under that artist and monitor it
 * 3. trigger `AlbumSearch`
 *
 * Nothing is matched silently: the caller shows [AlbumCandidate]s and passes
 * the one the user picked.
 */
class LidarrService(
    private val config: AcquisitionConfig,
) {
    private val api: LidarrApi = LidarrApiFactory.create(
        LidarrApiFactory.normalizeUrl(config.baseUrl),
        config.apiKey,
    )

    suspend fun test(): LidarrConnection = call {
        val status = api.status()
        LidarrConnection(
            version = status.version,
            qualityProfiles = api.qualityProfiles(),
            metadataProfiles = api.metadataProfiles(),
            rootFolders = api.rootFolders(),
        )
    }

    suspend fun lookupAlbums(term: String): List<AlbumCandidate> = call {
        if (term.isBlank()) return@call emptyList()
        val knownArtists = runCatching { api.artists() }.getOrDefault(emptyList())
        val knownIds = knownArtists.mapNotNull { it.foreignArtistId }.toSet()
        api.lookupAlbum(term.trim())
            .filter { it.foreignAlbumId != null && it.title != null }
            .mapNotNull { album ->
                val foreignAlbumId = album.foreignAlbumId ?: return@mapNotNull null
                val artistId = album.artist?.foreignArtistId
                AlbumCandidate(
                    foreignAlbumId = foreignAlbumId,
                    title = album.title.orEmpty(),
                    artistName = album.artist?.artistName.orEmpty(),
                    artistForeignId = artistId,
                    year = album.releaseDate?.take(4)?.toIntOrNull(),
                    trackCount = album.statistics?.totalTrackCount,
                    coverUrl = album.images.firstOrNull { it.coverType == "cover" }
                        ?.remoteUrl ?: album.images.firstOrNull()?.remoteUrl,
                    artistInLidarr = artistId != null && artistId in knownIds,
                )
            }
    }

    suspend fun request(candidate: AlbumCandidate): RequestOutcome = call {
        val artistForeignId = candidate.artistForeignId
            ?: throw LidarrException("Lidarr did not return a MusicBrainz artist id")

        val existingArtist = api.artists().firstOrNull { it.foreignArtistId == artistForeignId }
        val artist = existingArtist ?: api.addArtist(
            LidarrAddArtistRequest(
                foreignArtistId = artistForeignId,
                artistName = candidate.artistName,
                qualityProfileId = config.qualityProfileId,
                metadataProfileId = config.metadataProfileId,
                rootFolderPath = config.rootFolderPath,
            ),
        )
        val artistId = artist.id
            ?: throw LidarrException("Lidarr accepted the artist but returned no id")

        val album = api.albumsByArtist(artistId)
            .firstOrNull { it.foreignAlbumId == candidate.foreignAlbumId }
            ?: throw LidarrException(
                "The album is not in Lidarr yet. Refresh the artist in Lidarr and try again.",
            )
        val albumId = album.id ?: throw LidarrException("Lidarr returned an album without an id")

        api.monitorAlbums(LidarrMonitoredRequest(albumIds = listOf(albumId), monitored = true))
        val command = api.command(
            LidarrCommandRequest(name = "AlbumSearch", albumIds = listOf(albumId)),
        )

        RequestOutcome(
            albumTitle = album.title ?: candidate.title,
            artistAdded = existingArtist == null,
            commandId = command.id,
        )
    }

    suspend fun queue(): LidarrQueueDto = call { api.queue() }

    suspend fun wanted(): LidarrAlbumPageDto = call { api.wantedMissing() }

    private suspend fun <T> call(block: suspend () -> T): T = withContext(Dispatchers.IO) {
        try {
            block()
        } catch (e: HttpException) {
            throw LidarrException(httpMessage(e), e)
        } catch (e: IOException) {
            throw LidarrException("Lidarr is not reachable: ${e.message}", e)
        }
    }

    private fun httpMessage(e: HttpException): String = when (e.code()) {
        401, 403 -> "Lidarr rejected the API key"
        404 -> "Not found: is this really a Lidarr URL?"
        else -> "Lidarr error ${e.code()}"
    }
}
