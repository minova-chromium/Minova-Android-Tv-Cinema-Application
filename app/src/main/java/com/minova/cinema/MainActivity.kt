package com.minova.cinema

import android.os.Bundle
import android.content.Intent
import android.view.KeyEvent
import android.view.Window
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.minova.cinema.presentation.CinemaViewModel
import com.minova.cinema.presentation.TapoLightsViewModel
import com.minova.cinema.update.UpdateInstaller
import com.minova.cinema.update.UpdateViewModel
import com.minova.cinema.ui.MinovaCinemaApp
import com.minova.cinema.ui.ambient.AmbientInactivityTracker
import com.minova.cinema.ui.ambient.AmbientScreensaverHost
import com.minova.cinema.ui.theme.MinovaCinemaTheme
import com.minova.cinema.home.CinemaLightingController
import com.minova.cinema.home.CinemaLightingProvider

class MainActivity : ComponentActivity() {
    private val viewModel: CinemaViewModel by viewModels { CinemaViewModel.Factory(this) }
    private val updateViewModel: UpdateViewModel by viewModels()
    private val tapoLightsViewModel: TapoLightsViewModel by viewModels {
        TapoLightsViewModel.Factory(application)
    }
    private val ambientInactivityTracker = AmbientInactivityTracker()
    private lateinit var cinemaLightingController: CinemaLightingController
    private var pendingDeepLinkRatingKey by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingDeepLinkRatingKey = intent.deepLinkRatingKey()
        cinemaLightingController = CinemaLightingProvider.create(applicationContext)
        cinemaLightingController.registerPermissionCaller(this)
        enableEdgeToEdge()
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        setContent {
            MinovaCinemaTheme {
                AmbientScreensaverHost(ambientInactivityTracker) {
                    MinovaCinemaApp(
                        viewModel,
                        updateViewModel,
                        ambientInactivityTracker,
                        cinemaLightingController,
                        tapoLightsViewModel,
                        deepLinkRatingKey = pendingDeepLinkRatingKey,
                        onDeepLinkConsumed = { pendingDeepLinkRatingKey = null },
                    )
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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingDeepLinkRatingKey = intent.deepLinkRatingKey()
    }

    override fun onDestroy() {
        cinemaLightingController.release()
        super.onDestroy()
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

private fun Intent?.deepLinkRatingKey(): String? = this?.data
    ?.takeIf { it.scheme == "minova" && it.host == "content" }
    ?.pathSegments
    ?.firstOrNull()
    ?.takeIf(String::isNotBlank)
