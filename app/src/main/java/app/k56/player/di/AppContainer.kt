package app.k56.player.di

import android.content.Context
import app.k56.player.core.net.NetworkMonitor
import app.k56.player.data.MediaRepository
import app.k56.player.data.RepositoryRouter
import app.k56.player.data.SessionStore
import app.k56.player.data.SettingsStore
import app.k56.player.data.offline.OfflineStore
import app.k56.player.dsp.DspState
import app.k56.player.dsp.IrLibrary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Manual dependency container. Deliberately framework-free (no Hilt/Koin) so the
 * project stays buildable with a plain JDK + Android SDK toolchain.
 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    /** Outlives services; used for best-effort playback reporting. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val sessionStore = SessionStore(appContext)
    val settingsStore = SettingsStore(appContext)
    val networkMonitor = NetworkMonitor(appContext)
    val repository: MediaRepository = RepositoryRouter(appContext, sessionStore, settingsStore, networkMonitor)
    val offlineStore = OfflineStore(appContext, settingsStore)

    val irLibrary = IrLibrary(appContext)
    val dspState = DspState(appContext).also { state ->
        // Restore the last kernel and pick up newly dropped .irs files.
        state.restoreIr(irLibrary.directory)
        appScope.launch { runCatching { irLibrary.scanAndImport() } }
    }

}
