package com.minova.cinema.data.local

import android.content.Context
import androidx.core.content.edit
import com.minova.cinema.data.remote.PlexConnection

class PlexPreferences(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        "minova_cinema_connection",
        Context.MODE_PRIVATE,
    )

    fun readConnection(): PlexConnection? {
        val baseUrl = preferences.getString(KEY_BASE_URL, null)?.takeIf { it.isNotBlank() }
            ?: return null
        val token = preferences.getString(KEY_TOKEN, null)?.takeIf { it.isNotBlank() }
            ?: return null
        return PlexConnection(baseUrl = baseUrl, token = token)
    }

    fun saveConnection(connection: PlexConnection) {
        preferences.edit {
            putString(KEY_BASE_URL, connection.baseUrl)
            putString(KEY_TOKEN, connection.token)
            if (!preferences.contains(KEY_OWNER_TOKEN)) {
                putString(KEY_OWNER_TOKEN, connection.token)
            }
        }
    }

    fun readOwnerConnection(): PlexConnection? {
        val active = readConnection() ?: return null
        val ownerToken = preferences.getString(KEY_OWNER_TOKEN, null)
            ?.takeIf(String::isNotBlank)
            ?: active.token
        return active.copy(token = ownerToken)
    }

    fun saveProfileConnection(connection: PlexConnection, profileUuid: String) {
        preferences.edit {
            putString(KEY_BASE_URL, connection.baseUrl)
            putString(KEY_TOKEN, connection.token)
            putString(KEY_ACTIVE_PROFILE_UUID, profileUuid)
        }
    }

    fun readActiveProfileUuid(): String? =
        preferences.getString(KEY_ACTIVE_PROFILE_UUID, null)?.takeIf(String::isNotBlank)

    fun clearConnection() {
        preferences.edit {
            remove(KEY_BASE_URL)
            remove(KEY_TOKEN)
            remove(KEY_OWNER_TOKEN)
            remove(KEY_ACTIVE_PROFILE_UUID)
        }
    }

    fun readDismissedContinueWatchingIds(): Set<String> =
        preferences.getStringSet(KEY_DISMISSED_CONTINUE, emptySet()).orEmpty().toSet()

    fun dismissContinueWatching(ratingKey: String) {
        val updated = readDismissedContinueWatchingIds().toMutableSet().apply { add(ratingKey) }
        preferences.edit { putStringSet(KEY_DISMISSED_CONTINUE, updated) }
    }

    private companion object {
        const val KEY_BASE_URL = "base_url"
        const val KEY_TOKEN = "token"
        const val KEY_DISMISSED_CONTINUE = "dismissed_continue_watching"
        const val KEY_OWNER_TOKEN = "owner_token"
        const val KEY_ACTIVE_PROFILE_UUID = "active_profile_uuid"
    }
}
