package com.minova.cinema.home

import android.content.Context
import android.util.Log
import androidx.activity.ComponentActivity
import com.minova.cinema.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class TheaterLight(
    val id: String,
    val name: String,
    val roomName: String?,
    val supportsDimming: Boolean,
    val isAssigned: Boolean,
)

data class LightingUiState(
    val sdkAvailable: Boolean = false,
    val permissionGranted: Boolean = false,
    val loading: Boolean = false,
    val lights: List<TheaterLight> = emptyList(),
    val message: String? = null,
)

interface CinemaLightingController {
    val state: StateFlow<LightingUiState>

    fun registerPermissionCaller(activity: ComponentActivity)
    fun requestPermissions()
    fun refreshLights()
    fun setAssigned(lightId: String, assigned: Boolean)
    fun onCinemaPlaybackChanged(playing: Boolean)
    fun release()
}

/**
 * Loads the real Google Home implementation only in builds made with the
 * developer-only SDK. Keeping this boundary reflective lets every contributor
 * build the Plex client without access to Google's gated binary.
 */
object CinemaLightingProvider {
    fun create(context: Context): CinemaLightingController {
        if (BuildConfig.GOOGLE_HOME_SDK_ENABLED && android.os.Build.VERSION.SDK_INT >= 29) {
            val implementation = runCatching {
                Class.forName("com.minova.cinema.home.GoogleHomeCinemaLightingController")
                    .getConstructor(Context::class.java)
                    .newInstance(context.applicationContext) as CinemaLightingController
            }.onFailure { error ->
                Log.e("MinovaCinemaHome", "Unable to initialize Google Home", error)
            }
            implementation.getOrNull()?.let { return it }
            return UnavailableCinemaLightingController(
                "Google Home could not start. Update Google Play services and restart Minova Cinema.",
            )
        }
        return UnavailableCinemaLightingController(
            if (android.os.Build.VERSION.SDK_INT < 29) {
                "Google Home requires Android TV 10 or newer."
            } else {
                "Google Home is not included in this build."
            },
        )
    }
}

private class UnavailableCinemaLightingController(message: String) : CinemaLightingController {
    override val state: StateFlow<LightingUiState> = MutableStateFlow(
        LightingUiState(
            sdkAvailable = false,
            message = message,
        ),
    )

    override fun registerPermissionCaller(activity: ComponentActivity) = Unit
    override fun requestPermissions() = Unit
    override fun refreshLights() = Unit
    override fun setAssigned(lightId: String, assigned: Boolean) = Unit
    override fun onCinemaPlaybackChanged(playing: Boolean) = Unit
    override fun release() = Unit
}
