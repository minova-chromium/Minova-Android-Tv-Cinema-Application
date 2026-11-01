package com.minova.cinema.data.local

import android.content.Context
import androidx.core.content.edit
import java.io.File

const val OFFLINE_ARTWORK_MAX_BYTES = 384L * 1024L * 1024L
const val TRANSIENT_ARTWORK_MAX_BYTES = 96L * 1024L * 1024L

/** App-wide because Coil uses one image loader across Plex profiles. */
class ArtworkCachePreferences(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(
        "minova_artwork_cache",
        Context.MODE_PRIVATE,
    )

    fun isEnabled(): Boolean = prefs.getBoolean("offline_artwork_enabled", true)

    fun setEnabled(enabled: Boolean) {
        prefs.edit { putBoolean("offline_artwork_enabled", enabled) }
    }
}

fun offlineArtworkDirectory(context: Context): File =
    context.applicationContext.filesDir.resolve("offline_artwork")

fun offlineArtworkSize(context: Context): Long = offlineArtworkDirectory(context)
    .walkTopDown()
    .filter(File::isFile)
    .sumOf(File::length)
