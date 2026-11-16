package app.k56.player.data.library

import app.k56.player.core.model.Album
import app.k56.player.core.model.Artist
import app.k56.player.core.model.LibraryCounts
import app.k56.player.core.model.Track
import app.k56.player.data.MediaRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.asStateFlow

/** Progress of a running [LibrarySync]. */
data class SyncState(
    val running: Boolean = false,
    val phase: String = "",
    val loaded: Int = 0,
    val total: Int = 0,
    val error: String? = null,
    val finishedAt: Long = 0L,
)

/**
 * Copies the server library into [LibraryDb] so the app can browse and search
 * offline. Full sync only; the page size keeps memory flat on large libraries.
 */
class LibrarySync(
    private val repository: MediaRepository,
    private val db: LibraryDb,
) {
    private val _state = MutableStateFlow(SyncState())
    val state: StateFlow<SyncState> = _state.asStateFlow()

    /** True when there is no index yet or the last one is older than [maxAgeMs]. */
    fun isStale(maxAgeMs: Long = DEFAULT_MAX_AGE_MS): Boolean =
        db.isEmpty() || System.currentTimeMillis() - db.lastSync() > maxAgeMs

    suspend fun sync(force: Boolean = false): Result<LibraryCounts> = withContext(Dispatchers.IO) {
        runCatching {
        if (_state.value.running && !force) return@runCatching db.counts()

        _state.value = SyncState(running = true, phase = "start")
        val libraryId = repository.defaultLibraryId()
        val totals = runCatching { repository.libraryCounts(libraryId) }
            .getOrDefault(LibraryCounts())

        val albums = mutableListOf<Album>()
        var albumStart = 0
        while (albumStart < CAP) {
            val page = repository.albums(libraryId, limit = PAGE, startIndex = albumStart)
            if (page.isEmpty()) break
            albums += page
            albumStart += page.size
            _state.value = _state.value.copy(
                phase = "albums",
                loaded = albums.size,
                total = totals.albums,
            )
            if (page.size < PAGE) break
            if (totals.albums > 0 && albums.size >= totals.albums) break
        }

        val tracks = mutableListOf<Track>()
        var trackStart = 0
        while (trackStart < CAP) {
            val page = repository.songsPage(libraryId, startIndex = trackStart, limit = PAGE)
            if (page.isEmpty()) break
            tracks += page
            trackStart += page.size
            _state.value = _state.value.copy(
                phase = "tracks",
                loaded = tracks.size,
                total = totals.songs,
            )
            if (page.size < PAGE) break
            if (totals.songs > 0 && tracks.size >= totals.songs) break
        }

        _state.value = _state.value.copy(phase = "artists")
        val artists = runCatching {
            repository.artists(libraryId, limit = CAP)
        }.getOrDefault(emptyList<Artist>())

        _state.value = _state.value.copy(phase = "genres")
        val genres = runCatching { repository.genres(libraryId) }.getOrDefault(emptyList())

        _state.value = _state.value.copy(phase = "writing")
        db.replaceAll(
            tracks = tracks,
            albums = albums,
            artists = artists,
            genres = genres,
            serverCounts = totals,
        )

        _state.value = SyncState(
            running = false,
            phase = "done",
            loaded = tracks.size,
            total = totals.songs,
            finishedAt = System.currentTimeMillis(),
        )
        db.counts()
    }.onFailure { error ->
        _state.value = _state.value.copy(
            running = false,
            phase = "failed",
            error = error.message,
        )
    }
    }

    companion object {
        private const val PAGE = 500
        private const val CAP = 20_000
        const val DEFAULT_MAX_AGE_MS = 24L * 60L * 60L * 1000L
    }
}
