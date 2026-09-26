package com.minova.cinema

import android.os.Bundle
import android.os.Build
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.Window
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.FragmentActivity
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
import com.minova.cinema.ui.platform.DeviceProfile
import com.minova.cinema.ui.platform.deviceProfile

class MainActivity : FragmentActivity() {
    private val viewModel: CinemaViewModel by viewModels { CinemaViewModel.Factory(this) }
    private val updateViewModel: UpdateViewModel by viewModels()
    private val tapoLightsViewModel: TapoLightsViewModel by viewModels {
        TapoLightsViewModel.Factory(application)
    }
    private val ambientInactivityTracker = AmbientInactivityTracker()
    private lateinit var cinemaLightingController: CinemaLightingController
    private var pendingDeepLinkRatingKey by mutableStateOf<String?>(null)
    private var isTrailerRecordingMode by mutableStateOf(false)
    private var disablePlexTrailersForCapture by mutableStateOf(false)
    private val localNetworkPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            // The ViewModel may have attempted its saved Plex connection while
            // the Android permission sheet was visible. Retry immediately so
            // the user does not need to restart or press Refresh.
            viewModel.retry()
        } else {
            Toast.makeText(
                this,
                "Local network access is required to sync with Plex.",
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingDeepLinkRatingKey = intent.deepLinkRatingKey()
        updatePromoCaptureFlags(intent)
        cinemaLightingController = CinemaLightingProvider.create(applicationContext)
        cinemaLightingController.registerPermissionCaller(this)
        enableEdgeToEdge()
        requestLocalNetworkAccessIfNeeded()
        if (deviceProfile() == DeviceProfile.Television) {
            WindowInsetsControllerCompat(window, window.decorView).apply {
                hide(WindowInsetsCompat.Type.systemBars())
                systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            if (resources.configuration.smallestScreenWidthDp < TABLET_MIN_WIDTH_DP) {
                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            }
            WindowInsetsControllerCompat(window, window.decorView).apply {
                isAppearanceLightStatusBars = false
                isAppearanceLightNavigationBars = false
            }
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
                        isTrailerRecordingMode = isTrailerRecordingMode,
                        disablePlexTrailersForCapture = disablePlexTrailersForCapture,
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

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (ambientInactivityTracker.onTouchEvent(event)) return true
        return super.dispatchTouchEvent(event)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingDeepLinkRatingKey = intent.deepLinkRatingKey()
        updatePromoCaptureFlags(intent)
    }

    override fun onDestroy() {
        cinemaLightingController.release()
        super.onDestroy()
    }

    private fun updatePromoCaptureFlags(intent: Intent) {
        // Capture-only UI is never enabled in a release build, even if another
        // application sends a forged extra to the exported launcher Activity.
        isTrailerRecordingMode = BuildConfig.DEBUG && intent.getBooleanExtra(
            PromoCaptureContract.EXTRA_PROMO_RECORDING,
            false,
        )
        disablePlexTrailersForCapture = isTrailerRecordingMode && intent.getBooleanExtra(
            PromoCaptureContract.EXTRA_DISABLE_PLEX_TRAILERS,
            true,
        )
    }

    private fun requestLocalNetworkAccessIfNeeded() {
        if (
            Build.VERSION.SDK_INT >= ANDROID_17_API_LEVEL &&
            ContextCompat.checkSelfPermission(this, LOCAL_NETWORK_PERMISSION) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            localNetworkPermissionLauncher.launch(LOCAL_NETWORK_PERMISSION)
        }
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

    private companion object {
        const val TABLET_MIN_WIDTH_DP = 600
        const val ANDROID_17_API_LEVEL = 37
        const val LOCAL_NETWORK_PERMISSION = "android.permission.ACCESS_LOCAL_NETWORK"
    }
}

private fun Intent?.deepLinkRatingKey(): String? = this?.data
    ?.takeIf { it.scheme == "minova" && it.host == "content" }
    ?.pathSegments
    ?.firstOrNull()
    ?.takeIf(String::isNotBlank)
