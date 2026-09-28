package com.minova.cinema.data.local

import android.content.Context
import androidx.core.content.edit
import java.util.UUID

/** Stable, non-secret Plex device identity unique to this Minova installation. */
class PlexDeviceIdentity(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES,
        Context.MODE_PRIVATE,
    )

    fun get(): String {
        preferences.getString(KEY_IDENTIFIER, null)
            ?.takeIf(String::isNotBlank)
            ?.let { return it }
        val generated = "minova-${UUID.randomUUID()}"
        preferences.edit { putString(KEY_IDENTIFIER, generated) }
        return generated
    }

    private companion object {
        const val PREFERENCES = "minova_plex_device"
        const val KEY_IDENTIFIER = "client_identifier"
    }
}
