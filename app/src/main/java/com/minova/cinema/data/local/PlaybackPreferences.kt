package com.minova.cinema.data.local

import android.content.Context
import androidx.core.content.edit

data class PlaybackSettings(
    val autoplayNextEpisode: Boolean = true,
    val inactivityCheckEnabled: Boolean = true,
    val inactivityTimeoutMs: Long = 3L * 60L * 60L * 1_000L,
    val screensaverTimeoutMs: Long = 5L * 60L * 1_000L,
    val cinemaModeEnabled: Boolean = false,
    val cinemaTrailersEnabled: Boolean = true,
    val cinemaBumperUri: String? = null,
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
        inactivityTimeoutMs = preferences.getLong(KEY_INACTIVITY_TIMEOUT, DEFAULT_INACTIVITY_TIMEOUT_MS)
            .coerceIn(MIN_INACTIVITY_TIMEOUT_MS, MAX_INACTIVITY_TIMEOUT_MS),
        screensaverTimeoutMs = preferences.getLong(KEY_SCREENSAVER_TIMEOUT, DEFAULT_SCREENSAVER_TIMEOUT_MS)
            .coerceIn(MIN_SCREENSAVER_TIMEOUT_MS, MAX_SCREENSAVER_TIMEOUT_MS),
        cinemaModeEnabled = preferences.getBoolean(KEY_CINEMA_MODE, false),
        cinemaTrailersEnabled = preferences.getBoolean(KEY_CINEMA_TRAILERS, true),
        cinemaBumperUri = preferences.getString(KEY_CINEMA_BUMPER_URI, null),
    )

    fun setAutoplayNextEpisode(enabled: Boolean): PlaybackSettings {
        preferences.edit { putBoolean(KEY_AUTOPLAY_NEXT, enabled) }
        return read()
    }

    fun setInactivityCheckEnabled(enabled: Boolean): PlaybackSettings {
        preferences.edit { putBoolean(KEY_INACTIVITY_CHECK, enabled) }
        return read()
    }

    fun setInactivityTimeoutMs(timeoutMs: Long): PlaybackSettings {
        preferences.edit {
            putLong(
                KEY_INACTIVITY_TIMEOUT,
                timeoutMs.coerceIn(MIN_INACTIVITY_TIMEOUT_MS, MAX_INACTIVITY_TIMEOUT_MS),
            )
        }
        return read()
    }

    fun setScreensaverTimeoutMs(timeoutMs: Long): PlaybackSettings {
        preferences.edit {
            putLong(
                KEY_SCREENSAVER_TIMEOUT,
                timeoutMs.coerceIn(MIN_SCREENSAVER_TIMEOUT_MS, MAX_SCREENSAVER_TIMEOUT_MS),
            )
        }
        return read()
    }

    fun setCinemaModeEnabled(enabled: Boolean): PlaybackSettings {
        preferences.edit { putBoolean(KEY_CINEMA_MODE, enabled) }
        return read()
    }

    fun setCinemaTrailersEnabled(enabled: Boolean): PlaybackSettings {
        preferences.edit { putBoolean(KEY_CINEMA_TRAILERS, enabled) }
        return read()
    }

    fun setCinemaBumperUri(uri: String?): PlaybackSettings {
        preferences.edit {
            if (uri.isNullOrBlank()) remove(KEY_CINEMA_BUMPER_URI)
            else putString(KEY_CINEMA_BUMPER_URI, uri)
        }
        return read()
    }

    private companion object {
        const val PREFERENCES_NAME = "minova_cinema_playback"
        const val KEY_AUTOPLAY_NEXT = "autoplay_next_episode"
        const val KEY_INACTIVITY_CHECK = "inactivity_check_enabled"
        const val KEY_INACTIVITY_TIMEOUT = "inactivity_timeout_ms"
        const val KEY_SCREENSAVER_TIMEOUT = "screensaver_timeout_ms"
        const val KEY_CINEMA_MODE = "cinema_mode_enabled"
        const val KEY_CINEMA_TRAILERS = "cinema_trailers_enabled"
        const val KEY_CINEMA_BUMPER_URI = "cinema_bumper_uri"
        const val DEFAULT_INACTIVITY_TIMEOUT_MS = 3L * 60L * 60L * 1_000L
        const val MIN_INACTIVITY_TIMEOUT_MS = 30L * 60L * 1_000L
        const val MAX_INACTIVITY_TIMEOUT_MS = 6L * 60L * 60L * 1_000L
        const val DEFAULT_SCREENSAVER_TIMEOUT_MS = 5L * 60L * 1_000L
        const val MIN_SCREENSAVER_TIMEOUT_MS = 1L * 60L * 1_000L
        const val MAX_SCREENSAVER_TIMEOUT_MS = 30L * 60L * 1_000L
    }
}
