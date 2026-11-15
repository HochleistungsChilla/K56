package app.k56.player.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.k56.player.core.model.SearchResults
import app.k56.player.core.model.Track
import app.k56.player.data.MediaRepository
import app.k56.player.ui.appViewModel
import app.k56.player.ui.components.Artwork
import app.k56.player.ui.components.ErrorState
import app.k56.player.ui.components.SectionHeader
import app.k56.player.ui.components.TrackRow
import app.k56.player.ui.userMessage
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SearchViewModel(private val repository: MediaRepository) : ViewModel() {

    var results by mutableStateOf(SearchResults())
        private set
    var searching by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    private var job: Job? = null

    fun search(query: String) {
        job?.cancel()
        val term = query.trim()
        if (term.length < 2) {
            results = SearchResults()
            searching = false
            error = null
            return
        }
        job = viewModelScope.launch {
            searching = true
            error = null
            try {
                results = repository.search(term)
            } catch (e: Exception) {
                error = e.userMessage()
            } finally {
                searching = false
            }
        }
    }
}

@Composable
fun SearchScreen(
    repository: MediaRepository,
    onOpenAlbum: (String) -> Unit,
    onOpenArtist: (String) -> Unit,
    onPlay: (List<Track>, Int) -> Unit,
) {
    val vm = appViewModel { SearchViewModel(repository) }
    var query by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(query) {
        delay(350)
        vm.search(query)
    }

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Titel, Alben, Interpreten") },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        )

        if (vm.searching) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }

        val error = vm.error
        if (error != null) {
            ErrorState(error, onRetry = { vm.search(query) }, modifier = Modifier.weight(1f))
            return@Column
        }

        val results = vm.results
        val empty = results.albums.isEmpty() && results.artists.isEmpty() && results.tracks.isEmpty()

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            if (results.albums.isNotEmpty()) {
                item { SectionHeader("Alben") }
                items(results.albums, key = { it.id }) { album ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenAlbum(album.id) }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Artwork(
                            url = album.imageUrl,
                            modifier = Modifier.size(56.dp),
                            shape = RoundedCornerShape(10.dp),
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                album.name,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                album.artist.orEmpty(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }

            if (results.artists.isNotEmpty()) {
                item { SectionHeader("Interpreten") }
                items(results.artists, key = { it.id }) { artist ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenArtist(artist.id) }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Artwork(
                            url = artist.imageUrl,
                            modifier = Modifier.size(56.dp),
                            shape = CircleShape,
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            artist.name,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            if (results.tracks.isNotEmpty()) {
                item { SectionHeader("Titel") }
                itemsIndexed(results.tracks, key = { _, track -> track.id }) { index, track ->
                    TrackRow(
                        track = track,
                        onClick = { onPlay(results.tracks, index) },
                        showArtwork = true,
                    )
                }
            }

            if (empty && query.trim().length >= 2 && !vm.searching) {
                item {
                    Text(
                        text = "Keine Treffer für „${query.trim()}“.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(24.dp),
                    )
                }
            }
        }
    }
}
