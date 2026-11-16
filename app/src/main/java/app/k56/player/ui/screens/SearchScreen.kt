package app.k56.player.ui.screens

import android.content.Context
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.k56.player.R
import app.k56.player.core.model.SearchResults
import app.k56.player.core.model.Track
import app.k56.player.data.MediaRepository
import app.k56.player.data.library.LibraryDb
import app.k56.player.ui.appViewModel
import app.k56.player.ui.components.Artwork
import app.k56.player.ui.components.ErrorState
import app.k56.player.ui.components.SectionHeader
import app.k56.player.ui.components.TrackRow
import app.k56.player.ui.userMessage
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray

class SearchViewModel(
    private val repository: MediaRepository,
    private val appContext: Context,
) : ViewModel() {

    var results by mutableStateOf(SearchResults())
        private set
    var searching by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var offlineCopy by mutableStateOf(false)
        private set
    var history by mutableStateOf(loadHistory())
        private set

    private var job: Job? = null
    private val libraryDb = LibraryDb.get(appContext)
    private val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

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
                offlineCopy = false
                remember(term)
            } catch (e: Exception) {
                val tracks = libraryDb.searchTracks(term)
                val albums = libraryDb.searchAlbums(term)
                val artists = libraryDb.searchArtists(term)
                if (tracks.isNotEmpty() || albums.isNotEmpty() || artists.isNotEmpty()) {
                    results = SearchResults(albums = albums, artists = artists, tracks = tracks)
                    offlineCopy = true
                    error = null
                    remember(term)
                } else {
                    error = e.userMessage(appContext)
                }
            } finally {
                searching = false
            }
        }
    }

    fun remember(term: String) {
        val clean = term.trim()
        if (clean.length < 2) return
        val updated = (listOf(clean) + history.filter { !it.equals(clean, ignoreCase = true) })
            .take(MAX_HISTORY)
        history = updated
        prefs.edit().putString(KEY_HISTORY, JSONArray(updated).toString()).apply()
    }

    fun clearHistory() {
        history = emptyList()
        prefs.edit().remove(KEY_HISTORY).apply()
    }

    private fun loadHistory(): List<String> {
        val raw = prefs.getString(KEY_HISTORY, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length())
                .map { array.optString(it) }
                .filter { it.isNotBlank() }
        }.getOrDefault(emptyList())
    }

    private companion object {
        const val PREFS = "k56_search_history"
        const val KEY_HISTORY = "history"
        const val MAX_HISTORY = 10
    }
}

@Composable
fun SearchScreen(
    repository: MediaRepository,
    onOpenAlbum: (String) -> Unit,
    onOpenArtist: (String) -> Unit,
    onPlay: (List<Track>, Int) -> Unit,
    onPlayNext: (Track) -> Unit = {},
    onAddToQueue: (Track) -> Unit = {},
) {
    val context = LocalContext.current
    val vm = appViewModel { SearchViewModel(repository, context.applicationContext) }
    var query by rememberSaveable { mutableStateOf("") }
    var filterAlbums by rememberSaveable { mutableStateOf(true) }
    var filterArtists by rememberSaveable { mutableStateOf(true) }
    var filterTracks by rememberSaveable { mutableStateOf(true) }
    val focusManager = LocalFocusManager.current

    LaunchedEffect(query) {
        delay(200)
        vm.search(query)
    }

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text(stringResource(R.string.search_hint)) },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(
                onSearch = {
                    focusManager.clearFocus()
                    vm.search(query)
                },
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        )

        if (query.isBlank()) {
            HistorySection(
                history = vm.history,
                onPick = { query = it },
                onClear = vm::clearHistory,
                modifier = Modifier.weight(1f),
            )
            return@Column
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = filterAlbums,
                onClick = { filterAlbums = !filterAlbums },
                label = { Text(stringResource(R.string.library_tab_albums)) },
            )
            FilterChip(
                selected = filterArtists,
                onClick = { filterArtists = !filterArtists },
                label = { Text(stringResource(R.string.library_tab_artists)) },
            )
            FilterChip(
                selected = filterTracks,
                onClick = { filterTracks = !filterTracks },
                label = { Text(stringResource(R.string.library_tab_tracks)) },
            )
        }

        if (vm.searching) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }

        if (vm.offlineCopy) {
            Text(
                text = stringResource(R.string.offline_copy),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }

        val error = vm.error
        if (error != null) {
            ErrorState(error, onRetry = { vm.search(query) }, modifier = Modifier.weight(1f))
            return@Column
        }

        val results = vm.results
        val showAlbums = filterAlbums && results.albums.isNotEmpty()
        val showArtists = filterArtists && results.artists.isNotEmpty()
        val showTracks = filterTracks && results.tracks.isNotEmpty()
        val empty = !showAlbums && !showArtists && !showTracks

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            if (showAlbums) {
                item { SectionHeader(stringResource(R.string.library_tab_albums)) }
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

            if (showArtists) {
                item { SectionHeader(stringResource(R.string.library_tab_artists)) }
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

            if (showTracks) {
                item { SectionHeader(stringResource(R.string.library_tab_tracks)) }
                itemsIndexed(results.tracks, key = { _, track -> track.id }) { index, track ->
                    TrackRow(
                        track = track,
                        onClick = { onPlay(results.tracks, index) },
                        showArtwork = true,
                        onPlayNext = { onPlayNext(track) },
                        onAddToQueue = { onAddToQueue(track) },
                    )
                }
            }

            if (empty && query.trim().length >= 2 && !vm.searching) {
                item {
                    Text(
                        text = stringResource(R.string.search_no_results, query.trim()),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(24.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun HistorySection(
    history: List<String>,
    onPick: (String) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (history.isEmpty()) {
        Column(
            modifier = modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.search_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    Column(modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.search_history),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onClear) {
                Text(
                    text = stringResource(R.string.search_clear_history),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
            items(history, key = { it }) { term ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPick(term) }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Rounded.History,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = term,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
