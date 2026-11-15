package app.k56.player.ui.screens

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.k56.player.R
import app.k56.player.core.model.AlbumDetails
import app.k56.player.core.model.Track
import app.k56.player.data.MediaRepository
import app.k56.player.ui.appViewModel
import app.k56.player.ui.components.Artwork
import app.k56.player.ui.components.ErrorState
import app.k56.player.ui.components.LoadingState
import app.k56.player.ui.components.ScreenHeader
import app.k56.player.ui.components.TrackRow
import app.k56.player.ui.userMessage
import kotlinx.coroutines.launch

class AlbumViewModel(
    private val repository: MediaRepository,
    private val albumId: String,
    private val appContext: Context,
) : ViewModel() {

    var details by mutableStateOf<AlbumDetails?>(null)
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
                details = repository.albumDetails(albumId)
            } catch (e: Exception) {
                error = e.userMessage(appContext)
            } finally {
                loading = false
            }
        }
    }
}

@Composable
fun AlbumScreen(
    repository: MediaRepository,
    albumId: String,
    onBack: () -> Unit,
    onOpenArtist: (String) -> Unit,
    onPlay: (List<Track>, Int) -> Unit,
) {
    val context = LocalContext.current
    val vm = appViewModel { AlbumViewModel(repository, albumId, context.applicationContext) }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(title = stringResource(R.string.album_title), onBack = onBack)

        val details = vm.details
        val error = vm.error
        when {
            vm.loading -> LoadingState(Modifier.weight(1f))

            error != null -> ErrorState(error, vm::load, Modifier.weight(1f))

            details != null -> LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                item {
                    AlbumHeader(
                        details = details,
                        onOpenArtist = onOpenArtist,
                        onPlay = {
                            if (details.tracks.isNotEmpty()) onPlay(details.tracks, 0)
                        },
                    )
                }
                itemsIndexed(details.tracks, key = { _, track -> track.id }) { index, track ->
                    TrackRow(
                        track = track,
                        onClick = { onPlay(details.tracks, index) },
                        index = index + 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun AlbumHeader(
    details: AlbumDetails,
    onOpenArtist: (String) -> Unit,
    onPlay: () -> Unit,
) {
    val album = details.album
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Artwork(
            url = album.imageUrl,
            modifier = Modifier.size(220.dp),
            shape = RoundedCornerShape(20.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = album.name,
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        val artistId = album.artistId
        if (!album.artist.isNullOrBlank()) {
            Text(
                text = album.artist,
                style = MaterialTheme.typography.bodyMedium,
                color = if (artistId != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = if (artistId != null) {
                    Modifier.clickable { onOpenArtist(artistId) }
                } else {
                    Modifier
                },
            )
        }
        val trackCount = pluralStringResource(
            R.plurals.plural_tracks,
            details.tracks.size,
            details.tracks.size,
        )
        Text(
            text = listOfNotNull(album.year?.toString(), trackCount).joinToString(" · "),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(14.dp))
        Button(onClick = onPlay, enabled = details.tracks.isNotEmpty()) {
            Icon(Icons.Rounded.PlayArrow, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.common_play))
        }
        Spacer(Modifier.height(8.dp))
    }
}
