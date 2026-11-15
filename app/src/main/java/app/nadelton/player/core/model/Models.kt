package app.nadelton.player.core.model

/**
 * All source families the original product supports. Only some have adapters
 * today; [isImplemented] drives the login picker so unsupported sources are
 * visible but clearly disabled instead of pretending to work.
 */
enum class BackendKind(val label: String, val hint: String) {
    JELLYFIN("Jellyfin", "Media-Server"),
    EMBY("Emby", "Media-Server"),
    SUBSONIC("Subsonic", "Subsonic-API"),
    NAVIDROME("Navidrome", "Subsonic-API"),
    OPENSUBSONIC("OpenSubsonic", "Subsonic-API"),
    PLEX("Plex", "Media-Server"),
    AUDIOBOOKSHELF("Audiobookshelf", "Hörbücher"),
    KODI("Kodi", "Media-Center"),
    WEBDAV("WebDAV", "Dateien"),
    SMB("SMB", "Dateien"),
    LOCAL("Lokale Dateien", "Dateien"),
    GOOGLE_DRIVE("Google Drive", "Cloud"),
    ONEDRIVE("OneDrive", "Cloud"),
    DROPBOX("Dropbox", "Cloud"),
    BOX("Box", "Cloud"),
    PCLOUD("pCloud", "Cloud"),
}

val BackendKind.isJellyfinFamily: Boolean
    get() = this == BackendKind.JELLYFIN || this == BackendKind.EMBY

val BackendKind.isSubsonicFamily: Boolean
    get() = this == BackendKind.SUBSONIC ||
        this == BackendKind.NAVIDROME ||
        this == BackendKind.OPENSUBSONIC

val BackendKind.isImplemented: Boolean
    get() = isJellyfinFamily || isSubsonicFamily || this == BackendKind.PLEX ||
        this == BackendKind.AUDIOBOOKSHELF || this == BackendKind.LOCAL ||
        this == BackendKind.KODI || this == BackendKind.WEBDAV

/**
 * A persisted session. [serverUrl] is the address used at login; [internalUrl]
 * and [externalUrl] drive LAN/WAN routing. Tokens stay on-device.
 */
data class Session(
    val backend: BackendKind,
    val serverUrl: String,
    val internalUrl: String?,
    val externalUrl: String?,
    val preferExternal: Boolean,
    val serverName: String,
    val serverVersion: String,
    val serverId: String,
    val userId: String,
    val userName: String,
    val token: String,
    val deviceId: String,
    val extra: Map<String, String> = emptyMap(),
)

data class LibraryView(
    val id: String,
    val name: String,
    val collectionType: String?,
)

data class Album(
    val id: String,
    val name: String,
    val artist: String?,
    val artistId: String? = null,
    val year: Int?,
    val imageUrl: String?,
    val trackCount: Int?,
    val isFavorite: Boolean = false,
)

data class Artist(
    val id: String,
    val name: String,
    val imageUrl: String?,
)

data class Track(
    val id: String,
    val title: String,
    val albumId: String?,
    val albumName: String?,
    val artistName: String?,
    val durationMs: Long,
    val indexNumber: Int?,
    val discNumber: Int?,
    val container: String?,
    val bitrate: Int?,
    val imageUrl: String?,
    val streamKey: String? = null,
    val isFavorite: Boolean = false,
    val played: Boolean = false,
)

data class AlbumDetails(
    val album: Album,
    val tracks: List<Track>,
)

data class ArtistDetails(
    val artist: Artist,
    val albums: List<Album>,
)

data class SearchResults(
    val albums: List<Album> = emptyList(),
    val artists: List<Artist> = emptyList(),
    val tracks: List<Track> = emptyList(),
)

/** What the player should hand to ExoPlayer for one track. */
data class PlaybackSource(
    val url: String,
    val mimeType: String?,
    val transcoded: Boolean,
)

data class ServerProbe(
    val baseUrl: String,
    val serverName: String,
    val version: String,
    val serverId: String,
    val supported: Boolean,
)

data class DiscoveredServer(
    val name: String,
    val url: String,
)
