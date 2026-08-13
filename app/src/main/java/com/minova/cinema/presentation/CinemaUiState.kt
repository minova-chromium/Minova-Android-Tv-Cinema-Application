package com.minova.cinema.presentation

import com.minova.cinema.data.remote.PlexConnection
import com.minova.cinema.domain.CinemaCatalog
import com.minova.cinema.domain.MediaContent

sealed interface CinemaUiState {
    data class Onboarding(
        val connecting: Boolean = false,
        val error: String? = null,
    ) : CinemaUiState

    data object Loading : CinemaUiState

    data class Ready(
        val catalog: CinemaCatalog,
        val connection: PlexConnection,
        val refreshing: Boolean = false,
    ) : CinemaUiState

    data class Error(val message: String) : CinemaUiState
}

sealed interface ShowDetailUiState {
    data object Idle : ShowDetailUiState
    data class Loading(val show: MediaContent) : ShowDetailUiState
    data class Ready(
        val show: MediaContent,
        val seasons: List<MediaContent>,
        val selectedSeason: MediaContent?,
        val episodes: List<MediaContent>,
        val loadingEpisodes: Boolean = false,
    ) : ShowDetailUiState
    data class Error(val show: MediaContent, val message: String) : ShowDetailUiState
}

sealed interface MovieDetailUiState {
    data object Idle : MovieDetailUiState
    data class Loading(val movie: MediaContent) : MovieDetailUiState
    data class Ready(
        val movie: MediaContent,
        val trailers: List<MediaContent>,
    ) : MovieDetailUiState
    data class Error(val movie: MediaContent, val message: String) : MovieDetailUiState
}
