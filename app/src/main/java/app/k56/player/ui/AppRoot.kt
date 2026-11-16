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
import androidx.compose.runtime.getValue
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
import app.k56.player.core.model.PlaybackSource
import app.k56.player.core.model.Track
import app.k56.player.R
import app.k56.player.data.MediaRepository
import app.k56.player.di.AppContainer
import app.k56.player.playback.PlaybackConnection
import app.k56.player.playback.PlaybackQueue
import app.k56.player.ui.components.MiniPlayer
import app.k56.player.ui.screens.AlbumScreen
import app.k56.player.ui.screens.ArtistScreen
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
    val scope = rememberCoroutineScope()

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
                    navController.navigate("player")
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
                    onOpenAlbum = { navController.navigate("album/$it") },
                    onOpenArtist = { navController.navigate("artist/$it") },
                )
            }
            composable("library") {
                LibraryScreen(
                    repository = repository,
                    offlineStore = container.offlineStore,
                    onOpenAlbum = { navController.navigate("album/$it") },
                    onOpenArtist = { navController.navigate("artist/$it") },
                    onPlay = playTracks,
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
                SettingsScreen(repository, container.offlineStore)
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
