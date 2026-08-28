package com.minova.cinema.tapo

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.tapoCinemaDataStore by preferencesDataStore(name = "tapo_cinema_lights")

/** Persists only non-secret Cinema Room assignments. */
class TapoCinemaPreferences(private val context: Context) {
    val assignedIps: Flow<Set<String>> = context.tapoCinemaDataStore.data.map { preferences ->
        preferences[KEY_ASSIGNED_IPS].orEmpty()
    }

    suspend fun setAssigned(ipAddress: String, assigned: Boolean) {
        context.tapoCinemaDataStore.edit { preferences ->
            val updated = preferences[KEY_ASSIGNED_IPS].orEmpty().toMutableSet()
            if (assigned) updated += ipAddress else updated -= ipAddress
            preferences[KEY_ASSIGNED_IPS] = updated
        }
    }

    private companion object {
        val KEY_ASSIGNED_IPS = stringSetPreferencesKey("assigned_ip_addresses")
    }
}

