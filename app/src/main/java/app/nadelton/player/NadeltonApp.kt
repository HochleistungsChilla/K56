package app.nadelton.player

import android.app.Application
import app.nadelton.player.di.AppContainer

class NadeltonApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
