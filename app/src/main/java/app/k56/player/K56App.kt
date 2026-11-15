package app.k56.player

import android.app.Application
import app.k56.player.di.AppContainer

class K56App : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
