package com.minova.cinema.data.local

import android.content.Context
import androidx.core.content.edit

data class PlaybackSettings(
    val autoplayNextEpisode: Boolean = true,
    val inactivityCheckEnabled: Boolean = true,
)

/** Persistent living-room playback preferences, independent of Plex login data. */
class PlaybackPreferences(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    fun read(): PlaybackSettings = PlaybackSettings(
        autoplayNextEpisode = preferences.getBoolean(KEY_AUTOPLAY_NEXT, true),
        inactivityCheckEnabled = preferences.getBoolean(KEY_INACTIVITY_CHECK, true),
    )

    fun setAutoplayNextEpisode(enabled: Boolean): PlaybackSettings {
        preferences.edit { putBoolean(KEY_AUTOPLAY_NEXT, enabled) }
        return read()
    }

    fun setInactivityCheckEnabled(enabled: Boolean): PlaybackSettings {
        preferences.edit { putBoolean(KEY_INACTIVITY_CHECK, enabled) }
        return read()
    }

    private companion object {
        const val PREFERENCES_NAME = "minova_cinema_playback"
        const val KEY_AUTOPLAY_NEXT = "autoplay_next_episode"
        const val KEY_INACTIVITY_CHECK = "inactivity_check_enabled"
    }
}
