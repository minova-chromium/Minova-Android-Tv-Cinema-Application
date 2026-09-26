package com.minova.cinema.ui.platform

import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/** The presentation target, independent of the current window size. */
enum class DeviceProfile {
    Television,
    Handheld,
}

fun Context.deviceProfile(): DeviceProfile {
    val uiModeManager = getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
    val isTelevision = uiModeManager?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION ||
        packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
    return if (isTelevision) DeviceProfile.Television else DeviceProfile.Handheld
}

@Composable
fun rememberDeviceProfile(): DeviceProfile {
    val context = LocalContext.current
    return remember(context) { context.deviceProfile() }
}
