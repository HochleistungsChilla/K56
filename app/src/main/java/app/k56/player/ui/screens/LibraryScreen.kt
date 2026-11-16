package app.k56.player.ui.screens

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.k56.player.R
import app.k56.player.core.model.Album
import app.k56.player.core.model.Artist
import app.k56.player.core.model.Track
import app.k56.player.data.MediaRepository
import app.k56.player.ui.appViewModel
import app.k56.player.ui.components.AlbumCard
import app.k56.player.ui.components.ArtistCard
import app.k56.player.ui.components.ErrorState
import app.k56.player.ui.components.EmptyState
import app.k56.player.ui.components.LoadingState
import app.k56.player.ui.components.TrackRow
import app.k56.player.ui.userMessage
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

class LibraryViewModel(
    private val repository: MediaRepository,
    private val appContext: Context,
) : ViewModel() {

    var albums by mutableStateOf<List<Album>>(emptyList())
        private set
    var artists by mutableStateOf<List<Artist>>(emptyList())
        private set
    var songs by mutableStateOf<List<Track>>(emptyList())
        private set
    var loading by mutableStateOf(true)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    init {
        load()
    }

    /** Toggle a track's favourite flag on the server and update the local list. */
    fun toggleTrackFavorite(track: Track) {
        val wanted = !track.isFavorite
        songs = songs.map { if (it.id == track.id) it.copy(isFavorite = wanted) else it }
        viewModelScope.launch {
            try {
                val actual = repository.setFavorite(track.id, wanted)
                if (actual != wanted) {
                    songs = songs.map { if (it.id == track.id) it.copy(isFavorite = actual) else it }
                }
            } catch (e: Exception) {
                // roll back the optimistic update
                songs = songs.map { if (it.id == track.id) it.copy(isFavorite = !wanted) else it }
                error = e.userMessage(appContext)
            }
        }
    }

    fun load() {
        loading = true
        error = null
        viewModelScope.launch {
            try {
                coroutineScope {
                    val libraryId = repository.defaultLibraryId()
                    val albumsJob = async { repository.albums(libraryId, limit = 300) }
                    val artistsJob = async { repository.artists(libraryId, limit = 300) }
                    val songsJob = async { repository.songs(libraryId, limit = 600) }
                    albums = albumsJob.await()
                    artists = artistsJob.await()
                    songs = songsJob.await()
                }
            } catch (e: Exception) {
                error = e.userMessage(appContext)
            } finally {
                loading = false
            }
        }
    }
}

@Composable
fun LibraryScreen(
    repository: MediaRepository,
    onOpenAlbum: (String) -> Unit,
    onOpenArtist: (String) -> Unit,
    onPlay: (List<Track>, Int) -> Unit,
) {
    val context = LocalContext.current
    val vm = appViewModel { LibraryViewModel(repository, context.applicationContext) }
    var tab by rememberSaveable { mutableIntStateOf(0) }

    Column(Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = tab) {
            listOf(
                stringResource(R.string.library_tab_albums),
                stringResource(R.string.library_tab_artists),
                stringResource(R.string.library_tab_tracks),
                stringResource(R.string.library_tab_favorites),
            ).forEachIndexed { index, label ->
                Tab(
                    selected = tab == index,
                    onClick = { tab = index },
                    text = { Text(label) },
                )
            }
        }

        val error = vm.error
        when {
            vm.loading -> LoadingState(Modifier.weight(1f))

            error != null -> ErrorState(error, vm::load, Modifier.weight(1f))

            tab == 0 -> LazyVerticalGrid(
                columns = GridCells.Adaptive(148.dp),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                items(vm.albums, key = { it.id }) { album ->
                    AlbumCard(album = album, onClick = { onOpenAlbum(album.id) })
                }
            }

            tab == 1 -> LazyVerticalGrid(
                columns = GridCells.Adaptive(126.dp),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                items(vm.artists, key = { it.id }) { artist ->
                    ArtistCard(artist = artist, onClick = { onOpenArtist(artist.id) })
                }
            }

            tab == 2 -> LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(bottom = 20.dp),
            ) {
                itemsIndexed(vm.songs, key = { _, track -> track.id }) { index, track ->
                    TrackRow(
                        track = track,
                        onClick = { onPlay(vm.songs, index) },
                        index = index + 1,
                        isFavorite = track.isFavorite,
                        onToggleFavorite = { vm.toggleTrackFavorite(track) },
                    )
                }
            }

            else -> {
                val favorites = vm.songs.filter { it.isFavorite }
                if (favorites.isEmpty()) {
                    EmptyState(
                        title = stringResource(R.string.library_tab_favorites),
                        subtitle = stringResource(R.string.library_empty_favorites),
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentPadding = PaddingValues(bottom = 20.dp),
                    ) {
                        itemsIndexed(favorites, key = { _, track -> track.id }) { index, track ->
                            TrackRow(
                                track = track,
                                onClick = { onPlay(favorites, index) },
                                index = index + 1,
                                isFavorite = true,
                                onToggleFavorite = { vm.toggleTrackFavorite(track) },
                            )
                        }
                    }
                }
            }
        }
    }
}
