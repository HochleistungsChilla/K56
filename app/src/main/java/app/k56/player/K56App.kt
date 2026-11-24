package app.k56.player

import android.app.Application
import app.k56.player.core.net.DiagnosticsStore
import app.k56.player.di.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

class K56App : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        applyVerboseDiagnostics()
    }

    /** Verbose mode is a debug-build affordance; the release build ignores the switch. */
    private fun applyVerboseDiagnostics() {
        container.settingsStore.verboseDiagnostics
            .onEach { enabled -> DiagnosticsStore.setVerbose(BuildConfig.DEBUG && enabled) }
            .launchIn(CoroutineScope(SupervisorJob() + Dispatchers.Default))
    }
}
