package app.k56.player.ui.screens

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
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
import app.k56.player.core.model.Album
import app.k56.player.core.model.AlbumDetails
import app.k56.player.core.model.Track
import app.k56.player.core.net.NetworkTransport
import app.k56.player.data.MediaRepository
import app.k56.player.data.SettingsStore
import app.k56.player.data.library.LibraryDb
import app.k56.player.data.offline.OfflineStore
import app.k56.player.ui.appViewModel
import app.k56.player.ui.enqueueDownloads
import app.k56.player.ui.components.Artwork
import app.k56.player.ui.components.ErrorState
import app.k56.player.ui.components.HorizontalAlbumRow
import app.k56.player.ui.components.LoadingState
import app.k56.player.ui.components.ScreenHeader
import app.k56.player.ui.components.SectionHeader
import app.k56.player.ui.components.TrackRow
import app.k56.player.ui.userMessage
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

class AlbumViewModel(
    private val repository: MediaRepository,
    private val albumId: String,
    private val appContext: Context,
) : ViewModel() {

    var details by mutableStateOf<AlbumDetails?>(null)
        private set
    var moreFromArtist by mutableStateOf<List<Album>>(emptyList())
        private set
    var loading by mutableStateOf(true)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var offlineCopy by mutableStateOf(false)
        private set

    private val libraryDb = LibraryDb.get(appContext)
    private val favoriteArtistIds = libraryDb.favoriteArtistIds()

    val albumIsFavorite: Boolean
        get() = details?.album?.let { album ->
            album.isFavorite || (album.artistId != null && album.artistId in favoriteArtistIds)
        } == true

    init {
        load()
    }

    fun load() {
        loading = true
        error = null
        viewModelScope.launch {
            try {
                val loaded = coroutineScope {
                    val detailsDeferred = async { repository.albumDetails(albumId) }
                    val artistId = runCatching { detailsDeferred.await() }
                        .getOrNull()?.album?.artistId
                    val moreDeferred = artistId?.let { async { repository.albumsByArtist(it) } }
                    detailsDeferred.await() to (moreDeferred?.await() ?: emptyList())
                }
                details = loaded.first
                moreFromArtist = loaded.second
                    .filter { it.id != loaded.first.album.id }
                    .take(12)
                offlineCopy = false
            } catch (e: Exception) {
                val album = libraryDb.albumById(albumId)
                if (album != null) {
                    details = AlbumDetails(album = album, tracks = libraryDb.tracksByAlbum(albumId))
                    moreFromArtist = album.artistId
                        ?.let { libraryDb.albumsByArtist(it) }
                        ?.filter { it.id != albumId }
                        ?.take(12)
                        .orEmpty()
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
        val album = current.album
        val wanted = !album.isFavorite
        details = current.copy(album = album.copy(isFavorite = wanted))
        viewModelScope.launch {
            try {
                val actual = repository.setFavorite(album.id, wanted)
                val latest = details
                if (latest != null && latest.album.id == album.id && actual != wanted) {
                    details = latest.copy(album = latest.album.copy(isFavorite = actual))
                }
            } catch (e: Exception) {
                val latest = details
                if (latest != null && latest.album.id == album.id) {
                    details = latest.copy(album = latest.album.copy(isFavorite = !wanted))
                }
            }
        }
    }
}

@Composable
fun AlbumScreen(
    repository: MediaRepository,
    offlineStore: OfflineStore,
    settingsStore: SettingsStore,
    albumId: String,
    onBack: () -> Unit,
    onOpenArtist: (String) -> Unit,
    onOpenAlbum: (String) -> Unit,
    onPlay: (List<Track>, Int) -> Unit,
    onPlayNext: (Track) -> Unit = {},
    onAddToQueue: (Track) -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val vm = appViewModel { AlbumViewModel(repository, albumId, context.applicationContext) }
    val offline by offlineStore.tracks.collectAsState()
    val downloadedIds = offline.map { it.id }.toSet()
    val downloads by offlineStore.progress.collectAsState()
    val net by repository.networkStatus.collectAsState()
    val onWifi = net.transport == NetworkTransport.WIFI || net.transport == NetworkTransport.ETHERNET
    val wifiOnlyDownloads = settingsStore.wifiOnlyDownloads.value

    fun downloadTracks(tracks: List<Track>) {
        scope.launch {
            enqueueDownloads(tracks, repository, offlineStore)
        }
    }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(title = stringResource(R.string.album_title), onBack = onBack)

        if (vm.offlineCopy) {
            Text(
                text = stringResource(R.string.offline_copy),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
        }

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
                        onShuffle = {
                            if (details.tracks.isNotEmpty()) onPlay(details.tracks.shuffled(), 0)
                        },
                        isFavorite = vm.albumIsFavorite,
                        onToggleFavorite = { vm.toggleFavorite() },
                        onDownloadAll = { downloadTracks(details.tracks) },
                    )
                }
                val overview = details.album.overview
                val genres = details.album.genres
                if (!overview.isNullOrBlank() || genres.isNotEmpty()) {
                    item {
                        AboutSection(
                            overview = overview?.takeIf { it.isNotBlank() },
                            genres = genres,
                        )
                    }
                }
                itemsIndexed(details.tracks, key = { _, track -> track.id }) { index, track ->
                    TrackRow(
                        track = track,
                        onClick = { onPlay(details.tracks, index) },
                        index = index + 1,
                        isDownloaded = track.id in downloadedIds,
                        downloadProgress = downloads[track.id],
                        onDownload = if (track.id in downloadedIds) null else { { downloadTracks(listOf(track)) } },
                        onPlayNext = { onPlayNext(track) },
                        onAddToQueue = { onAddToQueue(track) },
                    )
                }
                if (vm.moreFromArtist.isNotEmpty()) {
                    item {
                        Spacer(Modifier.height(8.dp))
                        SectionHeader(stringResource(R.string.album_more_from_artist))
                        HorizontalAlbumRow(
                            albums = vm.moreFromArtist,
                            onOpenAlbum = onOpenAlbum,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AboutSection(
    overview: String?,
    genres: List<String>,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp),
    ) {
        SectionHeader(stringResource(R.string.album_about))
        if (!overview.isNullOrBlank()) {
            Text(
                text = overview,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (genres.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            androidx.compose.foundation.lazy.LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(genres.size, key = { genres[it] }) { index ->
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                    ) {
                        Text(
                            text = genres[index],
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        )
                    }
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
    onShuffle: () -> Unit,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onDownloadAll: () -> Unit,
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
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = onPlay, enabled = details.tracks.isNotEmpty()) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.common_play))
            }
            Spacer(Modifier.width(8.dp))
            IconButton(onClick = onShuffle, enabled = details.tracks.isNotEmpty()) {
                Icon(
                    Icons.Rounded.Shuffle,
                    contentDescription = stringResource(R.string.player_shuffle),
                )
            }
            IconButton(onClick = onToggleFavorite) {
                Icon(
                    imageVector = if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    contentDescription = null,
                    tint = if (isFavorite) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        OutlinedButton(onClick = onDownloadAll, enabled = details.tracks.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Rounded.Download, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.album_download))
        }
        Spacer(Modifier.height(8.dp))
    }
}
