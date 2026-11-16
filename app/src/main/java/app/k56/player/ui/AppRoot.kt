package app.k56.player.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import app.k56.player.core.model.PlaybackSource
import app.k56.player.core.model.Track
import app.k56.player.data.ScreenOrientation
import app.k56.player.R
import app.k56.player.data.MediaRepository
import app.k56.player.di.AppContainer
import app.k56.player.playback.PlaybackConnection
import app.k56.player.playback.PlaybackQueue
import app.k56.player.ui.components.MiniPlayer
import app.k56.player.ui.screens.AlbumScreen
import app.k56.player.ui.screens.ArtistScreen
import app.k56.player.ui.screens.CacheScreen
import app.k56.player.ui.screens.DspScreen
import app.k56.player.ui.screens.HomeScreen
import app.k56.player.ui.screens.LibraryScreen
import app.k56.player.ui.screens.LoginScreen
import app.k56.player.ui.screens.PlayerScreen
import app.k56.player.ui.screens.SearchScreen
import app.k56.player.ui.screens.SettingsScreen
import kotlinx.coroutines.launch

@Composable
fun K56Root(container: AppContainer) {
    val session by container.sessionStore.session.collectAsStateWithLifecycle()

    if (session == null) {
        LoginScreen(container.repository)
    } else {
        MainShell(container)
    }
}

@Composable
private fun MainShell(container: AppContainer) {
    val context = LocalContext.current
    val connection = remember { PlaybackConnection(context) }
    val controller by connection.controller.collectAsStateWithLifecycle()

    DisposableEffect(Unit) {
        onDispose { connection.release() }
    }

    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val route = backStackEntry?.destination?.route
    val showBars = route != "player"
    val repository: MediaRepository = container.repository
    val keepScreenOn by container.settingsStore.keepScreenOn.collectAsStateWithLifecycle()
    val showTrackNumbers by container.settingsStore.showTrackNumbers.collectAsStateWithLifecycle()
    val showTrackSubtitle by container.settingsStore.showTrackSubtitle.collectAsStateWithLifecycle()
    val showItemCount by container.settingsStore.showItemCount.collectAsStateWithLifecycle()
    val libraryColumns by container.settingsStore.libraryColumns.collectAsStateWithLifecycle()
    val wifiOnlyDownloads by container.settingsStore.wifiOnlyDownloads.collectAsStateWithLifecycle()
    val ignoreArticles by container.settingsStore.ignoreArticles.collectAsStateWithLifecycle()
    val showNowPlayingFavorite by container.settingsStore.showNowPlayingFavorite.collectAsStateWithLifecycle()
    val fitCover by container.settingsStore.fitCover.collectAsStateWithLifecycle()
    val compactControls by container.settingsStore.compactControls.collectAsStateWithLifecycle()
    val autoCacheFavorites by container.settingsStore.autoCacheFavorites.collectAsStateWithLifecycle()
    val screenOrientation by container.settingsStore.screenOrientation.collectAsStateWithLifecycle()
    val gridSpacing by container.settingsStore.gridSpacing.collectAsStateWithLifecycle()
    val activity = context as? android.app.Activity
    val scope = rememberCoroutineScope()

    // Apply the locked screen orientation.
    DisposableEffect(screenOrientation) {
        val previous = activity?.requestedOrientation
        activity?.requestedOrientation = when (screenOrientation) {
            ScreenOrientation.PORTRAIT -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            ScreenOrientation.LANDSCAPE -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            ScreenOrientation.AUTO -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
        onDispose {
            activity?.requestedOrientation = previous ?: android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    // The UI-side queue of Tracks, kept in sync with the controller so it can be
    // persisted and restored as "Save Now Playing state".
    val queueTracks = remember { mutableStateListOf<Track>() }
    val persistQueue: () -> Unit = {
        container.nowPlayingStore.save(queueTracks.toList(), controller?.currentMediaItemIndex ?: 0)
    }

    // Keep the persisted active index fresh as playback moves through the queue.
    DisposableEffect(controller) {
        val active = controller
        if (active != null) {
            val listener = object : Player.Listener {
                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    persistQueue()
                }
            }
            active.addListener(listener)
            onDispose { active.removeListener(listener) }
        } else {
            onDispose { }
        }
    }

    // Keep favorited tracks on disk whenever the option is enabled.
    LaunchedEffect(autoCacheFavorites) {
        if (autoCacheFavorites) container.favoriteCache.sync()
    }

    val playTracks: (List<Track>, Int) -> Unit = { tracks, index ->
        val active = controller
        if (active != null && tracks.isNotEmpty()) {
            scope.launch {
                val items = tracks.mapNotNull { track ->
                    val offline = container.offlineStore.find(track.id)
                    if (offline != null) {
                        // Already on disk: no server round-trip, works without a network.
                        return@mapNotNull PlaybackQueue.mediaItem(
                            track,
                            PlaybackSource(url = offline.uri(context).toString(), mimeType = null, transcoded = false),
                        )
                    }
                    val source = try {
                        repository.playbackSource(track)
                    } catch (_: Exception) {
                        null
                    } ?: return@mapNotNull null
                    PlaybackQueue.mediaItem(track, source)
                }
                if (items.isNotEmpty()) {
                    active.setMediaItems(items, index.coerceIn(0, items.lastIndex), 0L)
                    active.prepare()
                    active.play()
                    queueTracks.clear()
                    queueTracks.addAll(tracks)
                    persistQueue()
                    navController.navigate("player")
                }
            }
        }
    }

    suspend fun resolveItem(track: Track): MediaItem? {
        val offline = container.offlineStore.find(track.id)
        if (offline != null) {
            return PlaybackQueue.mediaItem(
                track,
                PlaybackSource(url = offline.uri(context).toString(), mimeType = null, transcoded = false),
            )
        }
        val source = runCatching { repository.playbackSource(track) }.getOrNull() ?: return null
        return PlaybackQueue.mediaItem(track, source)
    }

    // Restore the saved queue once the controller is ready (skip tracks the server no longer has).
    LaunchedEffect(controller) {
        val active = controller ?: return@LaunchedEffect
        val saved = container.nowPlayingStore.read() ?: return@LaunchedEffect
        val resolved = saved.tracks.mapNotNull { track -> resolveItem(track)?.let { track to it } }
        if (resolved.isEmpty()) {
            container.nowPlayingStore.clear()
            return@LaunchedEffect
        }
        queueTracks.clear()
        queueTracks.addAll(resolved.map { it.first })
        active.setMediaItems(
            resolved.map { it.second },
            saved.index.coerceIn(0, resolved.lastIndex),
            0L,
        )
        active.prepare()
    }

    val playNext: (Track) -> Unit = { track ->
        val active = controller
        if (active != null) {
            scope.launch {
                resolveItem(track)?.let { item ->
                    val index = (active.currentMediaItemIndex + 1)
                        .coerceAtMost(active.mediaItemCount.coerceAtLeast(0))
                    active.addMediaItem(index, item)
                    queueTracks.add(index.coerceAtMost(queueTracks.size), track)
                    persistQueue()
                }
            }
        }
    }

    val addToQueue: (Track) -> Unit = { track ->
        val active = controller
        if (active != null) {
            scope.launch {
                resolveItem(track)?.let {
                    active.addMediaItem(it)
                    queueTracks.add(track)
                    persistQueue()
                }
            }
        }
    }

    val toggleFavorite: (String, Boolean) -> Unit = { id, current ->
        val active = controller
        if (active != null && id.isNotBlank()) {
            scope.launch {
                val newState = runCatching { repository.setFavorite(id, !current) }
                    .getOrDefault(current)
                val index = active.currentMediaItemIndex
                if (index >= 0) {
                    val item = active.getMediaItemAt(index)
                    if (item.mediaId == id) {
                        active.replaceMediaItem(index, PlaybackQueue.withFavorite(item, newState))
                    }
                }
            }
        }
    }

    Scaffold(
        bottomBar = {
            if (showBars) {
                Column {
                    MiniPlayer(controller) { navController.navigate("player") }
                    NavigationBar {
                        val destinations = listOf(
                            BottomDestination("home", stringResource(R.string.nav_home), Icons.Rounded.Home),
                            BottomDestination(
                                "library",
                                stringResource(R.string.nav_library),
                                Icons.Rounded.LibraryMusic,
                            ),
                            BottomDestination("search", stringResource(R.string.nav_search), Icons.Rounded.Search),
                            BottomDestination(
                                "settings",
                                stringResource(R.string.nav_settings),
                                Icons.Rounded.Settings,
                            ),
                        )
                        destinations.forEach { destination ->
                            NavigationBarItem(
                                selected = route == destination.route,
                                onClick = {
                                    navController.navigate(destination.route) {
                                        popUpTo(navController.graph.startDestinationId) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = { Icon(destination.icon, contentDescription = null) },
                                label = { Text(destination.label) },
                            )
                        }
                    }
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "home",
            modifier = Modifier.padding(innerPadding),
        ) {
            composable("home") {
                HomeScreen(
                    repository = repository,
                    settingsStore = container.settingsStore,
                    onOpenAlbum = { navController.navigate("album/$it") },
                    onOpenArtist = { navController.navigate("artist/$it") },
                    onPlay = playTracks,
                )
            }
            composable("library") {
                LibraryScreen(
                    repository = repository,
                    offlineStore = container.offlineStore,
                    settingsStore = container.settingsStore,
                    onOpenAlbum = { navController.navigate("album/$it") },
                    onOpenArtist = { navController.navigate("artist/$it") },
                    onPlay = playTracks,
                    onPlayNext = playNext,
                    onAddToQueue = addToQueue,
                    showTrackNumbers = showTrackNumbers,
                    columns = libraryColumns,
                    wifiOnlyDownloads = wifiOnlyDownloads,
                    ignoreArticles = ignoreArticles,
                    showSubtitle = showTrackSubtitle,
                    showItemCount = showItemCount,
                    gridSpacing = gridSpacing,
                )
            }
            composable("cache") {
                CacheScreen(
                    offlineStore = container.offlineStore,
                    mediaCache = container.mediaCache,
                    settingsStore = container.settingsStore,
                    onBack = { navController.popBackStack() },
                )
            }
            composable("dsp") {
                DspScreen(
                    dsp = container.dspState,
                    irLibrary = container.irLibrary,
                    ddcStore = container.ddcStore,
                    onBack = { navController.popBackStack() },
                )
            }
            composable("search") {
                SearchScreen(
                    repository = repository,
                    onOpenAlbum = { navController.navigate("album/$it") },
                    onOpenArtist = { navController.navigate("artist/$it") },
                    onPlay = playTracks,
                )
            }
            composable("settings") {
                SettingsScreen(
                    repository = repository,
                    offlineStore = container.offlineStore,
                    settingsStore = container.settingsStore,
                    onOpenDsp = { navController.navigate("dsp") },
                    onOpenCache = { navController.navigate("cache") },
                )
            }
            composable(
                route = "album/{albumId}",
                arguments = listOf(navArgument("albumId") { type = NavType.StringType }),
            ) { entry ->
                AlbumScreen(
                    repository = repository,
                    albumId = entry.arguments?.getString("albumId").orEmpty(),
                    onBack = { navController.popBackStack() },
                    onOpenArtist = { navController.navigate("artist/$it") },
                    onPlay = playTracks,
                )
            }
            composable(
                route = "artist/{artistId}",
                arguments = listOf(navArgument("artistId") { type = NavType.StringType }),
            ) { entry ->
                ArtistScreen(
                    repository = repository,
                    artistId = entry.arguments?.getString("artistId").orEmpty(),
                    onBack = { navController.popBackStack() },
                    onOpenAlbum = { navController.navigate("album/$it") },
                )
            }
            composable("player") {
                PlayerScreen(
                    controller = controller,
                    onBack = { navController.popBackStack() },
                    onOpenDsp = { navController.navigate("dsp") },
                    keepScreenOn = keepScreenOn,
                    showFavorite = showNowPlayingFavorite,
                    fitCover = fitCover,
                    compactControls = compactControls,
                    onToggleFavorite = toggleFavorite,
                )
            }
        }
    }
}

private data class BottomDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
)
