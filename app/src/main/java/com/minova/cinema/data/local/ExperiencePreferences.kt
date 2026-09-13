package com.minova.cinema.data.local

import android.content.Context
import androidx.core.content.edit
import com.google.gson.Gson

data class ExperienceSettings(
    val audioLanguage: String = "",
    val subtitleLanguage: String = "",
    val subtitlesOff: Boolean = false,
    val subtitleScale: Float = 1f,
    val subtitleBackground: Boolean = true,
    val textScale: Float = 1f,
    val strongFocus: Boolean = false,
    val highContrast: Boolean = false,
    val reducedMotion: Boolean = false,
    val themeMusic: Boolean = false,
    val themeVolume: Float = 0.08f,
    val dimLevel: Int = 0,
    val restoreLevel: Int = -1,
    val endScreen: Boolean = true,
)

data class TitleTrackPreference(val audioId: Long? = null, val subtitleId: Long? = null, val subtitlesOff: Boolean = false)

data class CustomCinemaPreset(
    val enabled: Boolean = false,
    val trailers: Boolean = true,
    val bumper: Boolean = true,
    val lights: Boolean = true,
    val dim: Int = 0,
    val restore: Int = -1,
)

/** Per-profile non-secret preferences. Clearing artwork never touches this file. */
class ExperiencePreferences(context: Context, profileKey: String) {
    private val prefs = context.getSharedPreferences("minova_experience", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val key = java.security.MessageDigest.getInstance("SHA-256")
        .digest(profileKey.toByteArray()).joinToString("") { "%02x".format(it) }
    fun read(): ExperienceSettings = runCatching {
        gson.fromJson(prefs.getString(key, null), ExperienceSettings::class.java)
    }.getOrNull() ?: ExperienceSettings()
    fun save(value: ExperienceSettings) { prefs.edit { putString(key, gson.toJson(value)) } }
    fun tracks(id: String): TitleTrackPreference? = runCatching {
        gson.fromJson(prefs.getString("$key:tracks:$id", null), TitleTrackPreference::class.java)
    }.getOrNull()
    fun saveTracks(id: String, value: TitleTrackPreference) { prefs.edit { putString("$key:tracks:$id", gson.toJson(value)) } }
    fun resetTracks(id: String) { prefs.edit { remove("$key:tracks:$id") } }
    fun customPreset(): CustomCinemaPreset? = runCatching {
        gson.fromJson(prefs.getString("$key:cinema-preset", null), CustomCinemaPreset::class.java)
    }.getOrNull()
    fun saveCustomPreset(value: CustomCinemaPreset) {
        prefs.edit { putString("$key:cinema-preset", gson.toJson(value)) }
    }
}
