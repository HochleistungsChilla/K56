package app.resona.player.di

import android.content.Context
import app.resona.player.core.net.NetworkMonitor
import app.resona.player.data.MediaRepository
import app.resona.player.data.RepositoryRouter
import app.resona.player.data.SessionStore
import app.resona.player.data.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Manual dependency container. Deliberately framework-free (no Hilt/Koin) so the
 * project stays buildable with a plain JDK + Android SDK toolchain.
 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    val sessionStore = SessionStore(appContext)
    val settingsStore = SettingsStore(appContext)
    val networkMonitor = NetworkMonitor(appContext)
    val repository: MediaRepository = RepositoryRouter(appContext, sessionStore, settingsStore, networkMonitor)

    /** Outlives services; used for best-effort playback reporting. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
}
