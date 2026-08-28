package com.minova.cinema.presentation

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.minova.cinema.tapo.TapoAuthManager
import com.minova.cinema.tapo.TapoCinemaPreferences
import com.minova.cinema.tapo.TapoDiscoveryManager
import com.minova.cinema.tapo.TapoLightsRepository
import com.minova.cinema.tapo.TapoLightsUiState
import kotlinx.coroutines.flow.StateFlow

class TapoLightsViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = TapoLightsRepository(
        authManager = TapoAuthManager(application),
        discoveryManager = TapoDiscoveryManager(application),
        preferences = TapoCinemaPreferences(application),
        scope = viewModelScope,
    )

    val state: StateFlow<TapoLightsUiState> = repository.state

    fun saveCredentials(email: String, password: String) = repository.saveCredentials(email, password)
    fun clearCredentials() = repository.clearCredentials()
    fun discover() = repository.discover()
    fun setAssigned(ipAddress: String, assigned: Boolean) =
        repository.setAssigned(ipAddress, assigned)

    fun onPlaybackChanged(playing: Boolean) = repository.onPlaybackChanged(playing)

    class Factory(private val application: Application) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(TapoLightsViewModel::class.java))
            return TapoLightsViewModel(application) as T
        }
    }
}

