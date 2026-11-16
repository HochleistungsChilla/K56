package app.k56.player.ui.screens

import android.content.Context
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import app.k56.player.core.model.ArtistDetails
import app.k56.player.core.model.Track
import app.k56.player.data.MediaRepository
import app.k56.player.data.library.LibraryDb
import app.k56.player.ui.appViewModel
import app.k56.player.ui.components.AlbumCard
import app.k56.player.ui.components.Artwork
import app.k56.player.ui.components.ErrorState
import app.k56.player.ui.components.HorizontalAlbumRow
import app.k56.player.ui.components.LoadingState
import app.k56.player.ui.components.ScreenHeader
import app.k56.player.ui.components.SectionHeader
import app.k56.player.ui.components.TrackRow
import app.k56.player.ui.userMessage
import kotlinx.coroutines.launch

class ArtistViewModel(
    private val repository: MediaRepository,
    private val artistId: String,
    private val appContext: Context,
) : ViewModel() {

    var details by mutableStateOf<ArtistDetails?>(null)
        private set
    var loading by mutableStateOf(true)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var offlineCopy by mutableStateOf(false)
        private set

    private val libraryDb = LibraryDb.get(appContext)

    init {
        load()
    }

    fun load() {
        loading = true
        error = null
        viewModelScope.launch {
            try {
                details = repository.artistDetails(artistId)
                offlineCopy = false
            } catch (e: Exception) {
                val artist = libraryDb.artistById(artistId)
                if (artist != null) {
                    details = ArtistDetails(
                        artist = artist,
                        albums = libraryDb.albumsByArtist(artistId),
                        topTracks = libraryDb.tracksByArtist(artist.name).take(10),
                        appearsOn = emptyList(),
                    )
                    offlineCopy = true
                    error = null
                } else {
                    error = e.userMessage(appContext)
                }
            } finally {
                loading = false
            }
        }
    }

    fun toggleFavorite() {
        val current = details ?: return
        val artist = current.artist
        val wanted = !artist.isFavorite
        details = current.copy(artist = artist.copy(isFavorite = wanted))
        viewModelScope.launch {
            try {
                val actual = repository.setFavorite(artist.id, wanted)
                val latest = details
                if (latest != null && latest.artist.id == artist.id && actual != wanted) {
                    details = latest.copy(artist = latest.artist.copy(isFavorite = actual))
                }
            } catch (e: Exception) {
                val latest = details
                if (latest != null && latest.artist.id == artist.id) {
                    details = latest.copy(artist = latest.artist.copy(isFavorite = !wanted))
                }
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
    onPlay: (List<Track>, Int) -> Unit,
    onPlayNext: (Track) -> Unit = {},
    onAddToQueue: (Track) -> Unit = {},
) {
    val context = LocalContext.current
    val vm = appViewModel { ArtistViewModel(repository, artistId, context.applicationContext) }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(title = stringResource(R.string.artist_title), onBack = onBack)

        if (vm.offlineCopy) {
            Text(
                text = stringResource(R.string.offline_copy),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }

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
                contentPadding = PaddingValues(bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    ArtistHeader(
                        details = details,
                        onPlay = { if (details.topTracks.isNotEmpty()) onPlay(details.topTracks, 0) },
                        onShuffle = {
                            if (details.topTracks.isNotEmpty()) onPlay(details.topTracks.shuffled(), 0)
                        },
                        isFavorite = details.artist.isFavorite,
                        onToggleFavorite = { vm.toggleFavorite() },
                    )
                }

                val overview = details.artist.overview
                if (!overview.isNullOrBlank()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            text = overview,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                    }
                }

                if (details.topTracks.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        SectionHeader(stringResource(R.string.artist_top_tracks))
                    }
                    details.topTracks.forEachIndexed { index, track ->
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            TrackRow(
                                track = track,
                                onClick = { onPlay(details.topTracks, index) },
                                index = index + 1,
                                onPlayNext = { onPlayNext(track) },
                                onAddToQueue = { onAddToQueue(track) },
                            )
                        }
                    }
                }

                if (details.appearsOn.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Spacer(Modifier.height(4.dp))
                        SectionHeader(stringResource(R.string.artist_appears_on))
                    }
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        HorizontalAlbumRow(
                            albums = details.appearsOn,
                            onOpenAlbum = onOpenAlbum,
                        )
                    }
                }

                if (details.albums.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        SectionHeader(stringResource(R.string.library_tab_albums))
                    }
                }
                items(details.albums, key = { it.id }) { album ->
                    AlbumCard(album = album, onClick = { onOpenAlbum(album.id) })
                }
            }
        }
    }
}

@Composable
private fun ArtistHeader(
    details: ArtistDetails,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
) {
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
            text = pluralStringResource(
                R.plurals.plural_albums,
                details.albums.size,
                details.albums.size,
            ),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        IconButton(onClick = onToggleFavorite) {
            Icon(
                imageVector = if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                contentDescription = null,
                tint = if (isFavorite) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (details.topTracks.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onPlay) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = stringResource(R.string.common_play))
                }
                IconButton(onClick = onShuffle) {
                    Icon(Icons.Rounded.Shuffle, contentDescription = stringResource(R.string.player_shuffle))
                }
            }
        }
    }
}
