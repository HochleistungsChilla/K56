package app.resona.player.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.resona.player.core.model.ArtistDetails
import app.resona.player.data.MediaRepository
import app.resona.player.ui.appViewModel
import app.resona.player.ui.components.AlbumCard
import app.resona.player.ui.components.Artwork
import app.resona.player.ui.components.ErrorState
import app.resona.player.ui.components.LoadingState
import app.resona.player.ui.components.ScreenHeader
import app.resona.player.ui.userMessage
import kotlinx.coroutines.launch

class ArtistViewModel(
    private val repository: MediaRepository,
    private val artistId: String,
) : ViewModel() {

    var details by mutableStateOf<ArtistDetails?>(null)
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
                details = repository.artistDetails(artistId)
            } catch (e: Exception) {
                error = e.userMessage()
            } finally {
                loading = false
            }
        }
    }
}

@Composable
fun ArtistScreen(
    repository: MediaRepository,
    artistId: String,
    onBack: () -> Unit,
    onOpenAlbum: (String) -> Unit,
) {
    val vm = appViewModel { ArtistViewModel(repository, artistId) }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(title = "Interpret", onBack = onBack)

        val details = vm.details
        val error = vm.error
        when {
            vm.loading -> LoadingState(Modifier.weight(1f))

            error != null -> ErrorState(error, vm::load, Modifier.weight(1f))

            details != null -> LazyVerticalGrid(
                columns = GridCells.Adaptive(148.dp),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    ArtistHeader(details)
                }
                items(details.albums, key = { it.id }) { album ->
                    AlbumCard(album = album, onClick = { onOpenAlbum(album.id) })
                }
            }
        }
    }
}

@Composable
private fun ArtistHeader(details: ArtistDetails) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Artwork(
            url = details.artist.imageUrl,
            modifier = Modifier.size(170.dp),
            shape = CircleShape,
        )
        Spacer(Modifier.height(14.dp))
        Text(
            text = details.artist.name,
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = "${details.albums.size} Alben",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
