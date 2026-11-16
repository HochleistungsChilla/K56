package app.k56.player.data.library

import app.k56.player.core.model.Album
import app.k56.player.core.model.Artist
import app.k56.player.core.model.LibraryCounts
import app.k56.player.core.model.Track
import app.k56.player.data.MediaRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
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
    /** Own scope: a sync started from the UI must outlive that screen. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow(SyncState())
    val state: StateFlow<SyncState> = _state.asStateFlow()

    /** Fire-and-forget sync that survives navigation and backgrounding. */
    fun start(force: Boolean = false) {
        scope.launch { sync(force) }
    }

    /** True when there is no index yet or the last one is older than [maxAgeMs]. */
    fun isStale(maxAgeMs: Long = DEFAULT_MAX_AGE_MS): Boolean =
        db.isEmpty() || System.currentTimeMillis() - db.lastSync() > maxAgeMs

    /** True when a full sync is warranted: empty index or very old. */
    fun needsFullSync(maxAgeMs: Long = FULL_SYNC_MAX_AGE_MS): Boolean =
        db.isEmpty() || System.currentTimeMillis() - db.lastSync() > maxAgeMs

    /**
     * Picks up changed tracks and albums since the last full sync. Deletions are
     * not tracked here — they are caught by the periodic full sync.
     */
    suspend fun syncIncremental(): Result<LibraryCounts> = withContext(Dispatchers.IO) {
        runCatching {
            if (_state.value.running) return@runCatching db.counts()
            val last = db.lastSync()
            if (last <= 0) return@runCatching sync(force = true).getOrThrow()

            _state.value = SyncState(running = true, phase = "incremental")
            val delta = repository.changedSince(last)
            _state.value = _state.value.copy(phase = "writing", loaded = delta.tracks.size)
            db.applyDelta(delta.tracks, delta.albums)
            _state.value = SyncState(
                running = false,
                phase = "done",
                loaded = delta.tracks.size,
                finishedAt = System.currentTimeMillis(),
            )
            db.counts()
        }.onFailure { error ->
            if (error is kotlinx.coroutines.CancellationException) throw error
            _state.value = _state.value.copy(
                running = false,
                phase = "failed",
                error = error.message,
            )
        }
    }

    suspend fun sync(force: Boolean = false): Result<LibraryCounts> = withContext(Dispatchers.IO) {
        runCatching {
        if (_state.value.running && !force) return@runCatching db.counts()

        _state.value = SyncState(running = true, phase = "start")
        SyncLog.info("Sync gestartet${if (force) " (erzwungen)" else ""}")
        val libraryId = repository.defaultLibraryId()
        val totals = runCatching { repository.libraryCounts(libraryId) }
            .getOrDefault(LibraryCounts())

        db.beginStaging()
        try {
            val trackIds = HashSet<String>()
            var trackCount = 0

            // Favorites first: usable long before the rest of the library is in.
            _state.value = _state.value.copy(phase = "favorites")
            val favorites = attempt("Favoriten", pageSize = 0) { repository.favoriteTracks(-1) }
            val freshFavorites = favorites.filter { trackIds.add(it.id) }
            db.appendTracks(freshFavorites)
            db.clearLive()
            db.flushStaging()
            trackCount = freshFavorites.size
            _state.value = _state.value.copy(
                phase = "favorites",
                loaded = trackCount,
                total = totals.songs,
            )

            // Albums, published in chunks so the library fills while it syncs.
            var albumStart = 0
            var albumCount = 0
            var albumPages = 0
            val albumIds = HashSet<String>()
            while (true) {
                val page = attempt("Alben ab $albumStart") {
                    repository.albums(libraryId, limit = PAGE, startIndex = albumStart)
                }
                if (page.isEmpty()) break
                val fresh = page.filter { albumIds.add(it.id) }
                if (fresh.isEmpty()) break
                db.appendAlbums(fresh)
                albumCount += fresh.size
                albumStart += page.size
                albumPages++
                if (albumPages % FLUSH_EVERY == 0) db.flushStaging()
                _state.value = _state.value.copy(
                    phase = "albums",
                    loaded = albumCount,
                    total = totals.albums,
                )
                if (totals.albums > 0 && albumCount >= totals.albums) break
            }
            db.flushStaging()

            // Tracks: same, page by page, published every few pages.
            var trackStart = 0
            var trackPages = 0
            while (true) {
                val page = attempt("Titel ab $trackStart") {
                    repository.songsPage(libraryId, startIndex = trackStart, limit = PAGE)
                }
                if (page.isEmpty()) break
                val fresh = page.filter { trackIds.add(it.id) }
                if (fresh.isEmpty()) break
                db.appendTracks(fresh)
                trackCount += fresh.size
                trackStart += page.size
                trackPages++
                if (trackPages % FLUSH_EVERY == 0) db.flushStaging()
                _state.value = _state.value.copy(
                    phase = "tracks",
                    loaded = trackCount,
                    total = totals.songs,
                )
                if (totals.songs > 0 && trackCount >= totals.songs) break
            }
            db.flushStaging()

            _state.value = _state.value.copy(phase = "artists")
            val artists = attempt("Interpreten", pageSize = 0) {
                repository.artists(libraryId, limit = -1)
            }
            db.appendArtists(artists)

            _state.value = _state.value.copy(phase = "genres")
            db.appendGenres(attempt("Genres", pageSize = 0) { repository.genres(libraryId) })
            db.flushStaging()

            _state.value = _state.value.copy(phase = "writing")
            db.finishSync(totals)

            _state.value = SyncState(
                running = false,
                phase = "done",
                loaded = trackCount,
                total = totals.songs,
                finishedAt = System.currentTimeMillis(),
            )
            SyncLog.info("Sync fertig: $trackCount Titel, $albumCount Alben")
            db.counts()
        } catch (t: Throwable) {
            SyncLog.error("Sync abgebrochen: ${t.javaClass.simpleName}: ${t.message}")
            db.abortStaging()
            throw t
        }
    }.onFailure { error ->
        _state.value = _state.value.copy(
            running = false,
            phase = "failed",
            error = error.message,
        )
    }
    }

    /**
     * Runs one page request, retrying transient failures (timeouts, 5xx) with
     * backoff, and writes what happened into [SyncLog].
     */
    private suspend fun <T> attempt(
        label: String,
        pageSize: Int = 500,
        block: suspend () -> List<T>,
    ): List<T> {
        var last: Throwable? = null
        for (round in 1..3) {
            val started = System.currentTimeMillis()
            try {
                val result = block()
                val took = System.currentTimeMillis() - started
                SyncLog.info("$label: ${result.size} Einträge in $took ms")
                return result
            } catch (cancel: kotlinx.coroutines.CancellationException) {
                throw cancel
            } catch (error: Throwable) {
                last = error
                SyncLog.warn(
                    "$label fehlgeschlagen (Versuch $round/3): " +
                        "${error.javaClass.simpleName}: ${error.message}",
                )
                if (round < 3) delay(600L * round)
            }
        }
        throw last ?: IllegalStateException("$label: unbekannter Fehler")
    }

    companion object {
        private const val PAGE = 500
        private const val FLUSH_EVERY = 10
        const val DEFAULT_MAX_AGE_MS = 24L * 60L * 60L * 1000L
        const val FULL_SYNC_MAX_AGE_MS = 7L * 24L * 60L * 60L * 1000L
    }
}
