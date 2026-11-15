package app.nadelton.player.ui.screens

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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.nadelton.player.core.model.Album
import app.nadelton.player.core.model.Artist
import app.nadelton.player.core.model.Track
import app.nadelton.player.data.MediaRepository
import app.nadelton.player.ui.appViewModel
import app.nadelton.player.ui.components.AlbumCard
import app.nadelton.player.ui.components.ArtistCard
import app.nadelton.player.ui.components.ErrorState
import app.nadelton.player.ui.components.LoadingState
import app.nadelton.player.ui.components.TrackRow
import app.nadelton.player.ui.userMessage
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

class LibraryViewModel(private val repository: MediaRepository) : ViewModel() {

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
                error = e.userMessage()
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
    val vm = appViewModel { LibraryViewModel(repository) }
    var tab by rememberSaveable { mutableIntStateOf(0) }

    Column(Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = tab) {
            listOf("Alben", "Interpreten", "Titel").forEachIndexed { index, label ->
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

            else -> LazyColumn(
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
                    )
                }
            }
        }
    }
}
