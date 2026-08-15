package com.minova.cinema.update

import android.app.Application
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

sealed interface UpdateUiState {
    data object Idle : UpdateUiState
    data object Checking : UpdateUiState
    data class Available(val update: AppUpdate) : UpdateUiState
    data class Downloading(
        val versionName: String,
        val progressPercent: Int?,
        val paused: Boolean = false,
        val visible: Boolean = true,
    ) : UpdateUiState
    data class ReadyToInstall(val versionName: String, val downloadId: Long) : UpdateUiState
}

class UpdateViewModel(application: Application) : AndroidViewModel(application) {
    private val manager = UpdateManager(application.applicationContext)
    private val mutableState = MutableStateFlow<UpdateUiState>(UpdateUiState.Idle)
    val state: StateFlow<UpdateUiState> = mutableState.asStateFlow()
    private var checkedThisSession = false
    private var downloadMonitor: Job? = null

    fun checkForUpdate(force: Boolean = false) {
        if (checkedThisSession && !force) return
        if (mutableState.value is UpdateUiState.Checking) return
        checkedThisSession = true

        // Recover a download that survived process recreation instead of
        // silently suppressing all future update checks.
        val preferences = UpdatePreferences(getApplication())
        if (preferences.downloadId != UpdatePreferences.NO_DOWNLOAD) {
            monitorDownload(preferences.downloadId, preferences.versionName)
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
            .onSuccess { downloadId ->
                monitorDownload(downloadId, available.update.versionName)
                Toast.makeText(
                    getApplication(),
                    "Downloading Minova Cinema ${available.update.versionName}...",
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

    fun installationHandoffStarted() {
        if (mutableState.value is UpdateUiState.ReadyToInstall) {
            mutableState.value = UpdateUiState.Idle
        }
    }

    fun hideDownloadProgress() {
        val downloading = mutableState.value as? UpdateUiState.Downloading ?: return
        mutableState.value = downloading.copy(visible = false)
    }

    private fun monitorDownload(downloadId: Long, storedVersionName: String) {
        downloadMonitor?.cancel()
        val versionName = storedVersionName.ifBlank { "update" }
        mutableState.value = UpdateUiState.Downloading(versionName, progressPercent = null)
        downloadMonitor = viewModelScope.launch {
            while (isActive) {
                val snapshot = manager.queryDownload(downloadId)
                when (snapshot.state) {
                    DownloadState.Successful -> {
                        val preferences = UpdatePreferences(getApplication())
                        if (preferences.downloadedApk?.isFile == true) {
                            preferences.markInstallationPending()
                            mutableState.value = UpdateUiState.ReadyToInstall(
                                versionName = versionName,
                                downloadId = downloadId,
                            )
                        } else {
                            failDownload("The downloaded update file could not be found.")
                        }
                        return@launch
                    }

                    DownloadState.Failed, DownloadState.Missing -> {
                        failDownload(
                            if (snapshot.reason != null) {
                                "The update download failed (code ${snapshot.reason})."
                            } else {
                                "The update download could not be found."
                            },
                        )
                        return@launch
                    }

                    DownloadState.Pending, DownloadState.Running, DownloadState.Paused -> {
                        val visible = (mutableState.value as? UpdateUiState.Downloading)?.visible
                            ?: true
                        mutableState.value = UpdateUiState.Downloading(
                            versionName = versionName,
                            progressPercent = snapshot.progressPercent,
                            paused = snapshot.state == DownloadState.Paused,
                            visible = visible,
                        )
                    }
                }
                delay(DOWNLOAD_POLL_INTERVAL_MS)
            }
        }
    }

    private fun failDownload(message: String) {
        UpdatePreferences(getApplication()).clear()
        mutableState.value = UpdateUiState.Idle
        Toast.makeText(getApplication(), message, Toast.LENGTH_LONG).show()
    }

    private companion object {
        const val DOWNLOAD_POLL_INTERVAL_MS = 500L
    }
}
