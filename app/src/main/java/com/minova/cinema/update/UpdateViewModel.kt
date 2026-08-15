package com.minova.cinema.update

import android.app.Application
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface UpdateUiState {
    data object Idle : UpdateUiState
    data object Checking : UpdateUiState
    data class Available(val update: AppUpdate) : UpdateUiState
    data class Downloading(val update: AppUpdate) : UpdateUiState
}

class UpdateViewModel(application: Application) : AndroidViewModel(application) {
    private val manager = UpdateManager(application.applicationContext)
    private val mutableState = MutableStateFlow<UpdateUiState>(UpdateUiState.Idle)
    val state: StateFlow<UpdateUiState> = mutableState.asStateFlow()
    private var checkedThisSession = false

    fun checkForUpdate(force: Boolean = false) {
        if (checkedThisSession && !force) return
        if (mutableState.value is UpdateUiState.Checking) return
        checkedThisSession = true
        // A manifest receiver owns an in-progress download even if Android
        // recreated this process. Do not enqueue or offer the same APK twice.
        if (UpdatePreferences(getApplication()).downloadId != UpdatePreferences.NO_DOWNLOAD) {
            mutableState.value = UpdateUiState.Idle
            return
        }
        mutableState.value = UpdateUiState.Checking
        viewModelScope.launch {
            // An automatic check must never block access to the user's library.
            mutableState.value = runCatching { manager.findAvailableUpdate() }
                .fold(
                    onSuccess = { update ->
                        update?.let(UpdateUiState::Available) ?: UpdateUiState.Idle
                    },
                    onFailure = { UpdateUiState.Idle },
                )
        }
    }

    fun downloadUpdate() {
        val available = mutableState.value as? UpdateUiState.Available ?: return
        runCatching { manager.enqueueDownload(available.update) }
            .onSuccess {
                mutableState.value = UpdateUiState.Downloading(available.update)
                Toast.makeText(
                    getApplication(),
                    "Downloading Minova Cinema ${available.update.versionName}…",
                    Toast.LENGTH_LONG,
                ).show()
            }
            .onFailure { error ->
                mutableState.value = available
                Toast.makeText(
                    getApplication(),
                    error.message ?: "The update could not be downloaded.",
                    Toast.LENGTH_LONG,
                ).show()
            }
    }

    fun dismissUpdate() {
        if (mutableState.value is UpdateUiState.Available) {
            mutableState.value = UpdateUiState.Idle
        }
    }
}
