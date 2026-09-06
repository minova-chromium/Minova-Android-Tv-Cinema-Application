package com.minova.cinema.data.local

import android.content.Context
import androidx.core.content.edit
import com.google.gson.Gson

data class HomeLayoutPreferences(
    val openingTab: String = "Home",
    val shelfOrder: List<String> = emptyList(),
    val hiddenShelves: Set<String> = emptySet(),
)

/** Per-server, per-Plex-profile presentation preferences. No credentials stored. */
class BrowsePreferences(context: Context, profileKey: String) {
    private val store = context.applicationContext.getSharedPreferences("minova_browse", Context.MODE_PRIVATE)
    private val key = java.security.MessageDigest.getInstance("SHA-256")
        .digest(profileKey.toByteArray()).joinToString("") { "%02x".format(it) }
    fun read(): HomeLayoutPreferences = runCatching {
        Gson().fromJson(store.getString(key, null), HomeLayoutPreferences::class.java)
    }.getOrNull() ?: HomeLayoutPreferences()
    fun save(value: HomeLayoutPreferences) { store.edit { putString(key, Gson().toJson(value)) } }
}

internal fun orderedShelfKeys(available: List<String>, preferences: HomeLayoutPreferences): List<String> =
    (preferences.shelfOrder + available).distinct().filter { it in available && it !in preferences.hiddenShelves }
