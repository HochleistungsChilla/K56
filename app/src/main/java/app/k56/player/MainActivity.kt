package app.k56.player

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import app.k56.player.ui.K56Root
import app.k56.player.ui.theme.K56Theme
import app.k56.player.ui.theme.ThemeController

class MainActivity : ComponentActivity() {

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* playback works regardless */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CrashLog.install(this)
        // Crashes from the previous run land in the debug log, so they can be shared.
        CrashLog.drain(this)?.lineSequence()
            ?.filter { it.isNotBlank() }
            ?.take(200)
            ?.forEach { app.k56.player.data.library.SyncLog.error("Absturz: $it") }
        enableEdgeToEdge()

        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        val container = (application as K56App).container
        ThemeController.load(this)
        setContent {
            K56Theme(mode = ThemeController.mode) {
                K56Root(container)
            }
        }
    }
}
