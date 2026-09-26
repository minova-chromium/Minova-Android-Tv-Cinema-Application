package com.minova.cinema.data.local

import android.content.Context
import com.minova.cinema.data.remote.PlexConfig

/** Receiver-specific network address; the Plex token continues to come from the active profile. */
class CastPreferences(context: Context) {
    private val preferences = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    fun readServerUrl(): String? = preferences.getString(KEY_SERVER_URL, null)
        ?.takeIf(String::isNotBlank)

    /** Blank restores the normal connected Plex address. Returns the normalized saved value. */
    fun saveServerUrl(input: String): String? {
        val normalized = input.trim().takeIf(String::isNotBlank)
            ?.let(PlexConfig::normalizeServerAddress)
        preferences.edit().apply {
            if (normalized == null) remove(KEY_SERVER_URL) else putString(KEY_SERVER_URL, normalized)
        }.apply()
        return normalized
    }

    private companion object {
        const val FILE_NAME = "minova_cast"
        const val KEY_SERVER_URL = "server_url"
    }
}
