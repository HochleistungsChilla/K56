package app.resona.player

import android.app.Application
import app.resona.player.di.AppContainer

class ResonaApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
