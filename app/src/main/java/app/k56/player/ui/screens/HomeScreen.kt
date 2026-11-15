package app.k56.player.ui.screens

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import app.k56.player.data.MediaRepository
import app.k56.player.ui.appViewModel
import app.k56.player.ui.components.ArtistCard
import app.k56.player.ui.components.EmptyState
import app.k56.player.ui.components.ErrorState
import app.k56.player.ui.components.HorizontalAlbumRow
import app.k56.player.ui.components.LoadingState
import app.k56.player.ui.components.SectionHeader
import app.k56.player.ui.userMessage
import kotlinx.coroutines.launch

class HomeViewModel(
    private val repository: MediaRepository,
    private val appContext: Context,
) : ViewModel() {

    var recentAlbums by mutableStateOf<List<Album>>(emptyList())
        private set
    var artists by mutableStateOf<List<Artist>>(emptyList())
        private set
    var loading by mutableStateOf(true)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    val serverName: String =
        repository.sessionFlow.value?.serverName ?: appContext.getString(R.string.settings_server)
    val serverVersion: String = repository.sessionFlow.value?.serverVersion.orEmpty()
    val backendLabel: String =
        repository.sessionFlow.value?.backend?.label ?: appContext.getString(R.string.settings_server)

    init {
        load()
    }

    fun load() {
        loading = true
        error = null
        viewModelScope.launch {
            try {
                recentAlbums = repository.recentAlbums(limit = 24)
                artists = repository.artists(repository.defaultLibraryId(), limit = 20)
            } catch (e: Exception) {
                error = e.userMessage(appContext)
            } finally {
                loading = false
            }
        }
    }
}

@Composable
fun HomeScreen(
    repository: MediaRepository,
    onOpenAlbum: (String) -> Unit,
    onOpenArtist: (String) -> Unit,
) {
    val context = LocalContext.current
    val vm = appViewModel { HomeViewModel(repository, context.applicationContext) }

    when {
        vm.loading -> LoadingState()

        vm.error != null -> ErrorState(vm.error!!, vm::load)

        else -> LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 20.dp),
        ) {
            item {
                Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 4.dp)) {
                    Text(
                        stringResource(R.string.home_greeting),
                        style = MaterialTheme.typography.headlineMedium,
                    )
                    Text(
                        "${vm.serverName} · ${vm.backendLabel} ${vm.serverVersion}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (vm.recentAlbums.isNotEmpty()) {
                item { SectionHeader(stringResource(R.string.home_recently_added)) }
                item { HorizontalAlbumRow(vm.recentAlbums, onOpenAlbum) }
            }

            if (vm.artists.isNotEmpty()) {
                item { SectionHeader(stringResource(R.string.library_tab_artists)) }
                item {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(vm.artists, key = { it.id }) { artist ->
                            ArtistCard(
                                artist = artist,
                                onClick = { onOpenArtist(artist.id) },
                                modifier = Modifier.width(118.dp),
                            )
                        }
                    }
                }
            }

            if (vm.recentAlbums.isEmpty() && vm.artists.isEmpty()) {
                item {
                    EmptyState(
                        title = stringResource(R.string.home_empty_title),
                        subtitle = stringResource(R.string.home_empty_body),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}
