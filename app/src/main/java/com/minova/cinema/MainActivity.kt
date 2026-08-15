package com.minova.cinema

import android.os.Bundle
import android.view.KeyEvent
import android.view.Window
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.minova.cinema.presentation.CinemaViewModel
import com.minova.cinema.update.UpdateInstaller
import com.minova.cinema.update.UpdateViewModel
import com.minova.cinema.ui.MinovaCinemaApp
import com.minova.cinema.ui.ambient.AmbientInactivityTracker
import com.minova.cinema.ui.ambient.AmbientScreensaverHost
import com.minova.cinema.ui.theme.MinovaCinemaTheme

class MainActivity : ComponentActivity() {
    private val viewModel: CinemaViewModel by viewModels { CinemaViewModel.Factory(this) }
    private val updateViewModel: UpdateViewModel by viewModels()
    private val ambientInactivityTracker = AmbientInactivityTracker()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        setContent {
            MinovaCinemaTheme {
                AmbientScreensaverHost(ambientInactivityTracker) {
                    MinovaCinemaApp(viewModel, updateViewModel, ambientInactivityTracker)
                }
            }
        }

        // Window.Callback is the supported interception point above Compose,
        // AndroidView, and PlayerView. It lets ambient mode see every TV key
        // before the currently focused child can act on it.
        window.callback = AmbientWindowCallback(window.callback, ambientInactivityTracker)
    }

    override fun onResume() {
        super.onResume()
        // If Android sent the user to "Install unknown apps", returning to
        // Minova Cinema continues the pending installation automatically.
        UpdateInstaller.resumePendingInstall(this)
    }

    private class AmbientWindowCallback(
        private val delegate: Window.Callback,
        private val tracker: AmbientInactivityTracker,
    ) : Window.Callback by delegate {
        override fun dispatchKeyEvent(event: KeyEvent): Boolean {
            // The full dismissing key gesture is consumed so it cannot activate
            // the focused card or player control underneath the screensaver.
            if (tracker.onKeyEvent(event)) return true
            return delegate.dispatchKeyEvent(event)
        }
    }
}
