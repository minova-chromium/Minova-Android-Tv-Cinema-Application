package com.minova.cinema.presentation

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.minova.cinema.data.PlexRepository
import com.minova.cinema.data.PlexProfileRepository
import com.minova.cinema.data.PlaybackCapabilityAssistant
import com.minova.cinema.data.local.PlexPreferences
import com.minova.cinema.data.local.PlexCatalogCache
import com.minova.cinema.data.local.PlexArtworkPrefetcher
import com.minova.cinema.data.remote.PlexConfig
import com.minova.cinema.data.remote.PlexConnection
import com.minova.cinema.data.remote.PlexServiceFactory
import com.minova.cinema.domain.MediaContent
import com.minova.cinema.domain.CinemaCatalog
import com.minova.cinema.domain.CinemaPlaybackPlan
import com.minova.cinema.domain.MediaKind
import com.minova.cinema.domain.PlaybackDiagnostics
import com.minova.cinema.data.remote.PlaybackQuality
import com.minova.cinema.tvhome.TvHomePublisher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class CinemaViewModel(
    private val preferences: PlexPreferences,
    private val catalogCache: PlexCatalogCache,
    private val artworkPrefetcher: PlexArtworkPrefetcher,
    private val tvHomePublisher: TvHomePublisher,
) : ViewModel() {
    private val _uiState = MutableStateFlow<CinemaUiState>(CinemaUiState.Loading)
    val uiState: StateFlow<CinemaUiState> = _uiState.asStateFlow()

    private val _showDetail = MutableStateFlow<ShowDetailUiState>(ShowDetailUiState.Idle)
    val showDetail: StateFlow<ShowDetailUiState> = _showDetail.asStateFlow()

    private val _movieDetail = MutableStateFlow<MovieDetailUiState>(MovieDetailUiState.Idle)
    val movieDetail: StateFlow<MovieDetailUiState> = _movieDetail.asStateFlow()

    private val _profiles = MutableStateFlow<PlexProfilesUiState>(PlexProfilesUiState.Loading)
    val profiles: StateFlow<PlexProfilesUiState> = _profiles.asStateFlow()

    private val _networkAssistant = MutableStateFlow<NetworkAssistantUiState>(NetworkAssistantUiState.Idle)
    val networkAssistant: StateFlow<NetworkAssistantUiState> = _networkAssistant.asStateFlow()

    private var connection: PlexConnection? = null
    private var repository: PlexRepository? = null
    private var catalogJob: Job? = null
    private var detailJob: Job? = null
    private var watchlistJob: Job? = null

    init {
        val saved = preferences.readConnection()
        if (saved == null) {
            _uiState.value = CinemaUiState.Onboarding()
        } else {
            connectInternal(saved, persist = false, onboarding = false)
        }
    }

    fun connect(serverInput: String, tokenInput: String) {
        val normalized = try {
            PlexConfig.normalizeServerAddress(serverInput)
        } catch (error: IllegalArgumentException) {
            _uiState.value = CinemaUiState.Onboarding(error = error.message)
            return
        }
        val token = tokenInput.trim()
        if (token.isBlank()) {
            _uiState.value = CinemaUiState.Onboarding(error = "Enter your Plex token.")
            return
        }
        connectInternal(PlexConnection(normalized, token), persist = true, onboarding = true)
    }

    fun retry() {
        val saved = preferences.readConnection()
        if (saved == null) _uiState.value = CinemaUiState.Onboarding()
        else connectInternal(saved, persist = false, onboarding = false)
    }

    fun changeServer() {
        catalogJob?.cancel()
        detailJob?.cancel()
        watchlistJob?.cancel()
        preferences.clearConnection()
        connection = null
        repository = null
        _showDetail.value = ShowDetailUiState.Idle
        _movieDetail.value = MovieDetailUiState.Idle
        _uiState.value = CinemaUiState.Onboarding()
    }

    fun refreshProfiles() {
        val owner = preferences.readOwnerConnection() ?: return
        viewModelScope.launch {
            val previous = (_profiles.value as? PlexProfilesUiState.Ready)?.profiles.orEmpty()
            runCatching {
                PlexProfileRepository(owner, PlexServiceFactory.createHome(owner))
                    .loadProfiles(preferences.readActiveProfileUuid())
            }.onSuccess { _profiles.value = PlexProfilesUiState.Ready(it) }
                .onFailure { _profiles.value = PlexProfilesUiState.Error(it.userMessage(), previous) }
        }
    }

    fun switchProfile(profile: com.minova.cinema.domain.PlexHomeProfile, pin: String?) {
        val owner = preferences.readOwnerConnection() ?: return
        val currentProfiles = when (val current = _profiles.value) {
            is PlexProfilesUiState.Ready -> current.profiles
            is PlexProfilesUiState.Error -> current.profiles
            is PlexProfilesUiState.Switching -> current.profiles
            PlexProfilesUiState.Loading -> emptyList()
        }
        _profiles.value = PlexProfilesUiState.Switching(currentProfiles, profile.uuid)
        viewModelScope.launch {
            runCatching {
                PlexProfileRepository(owner, PlexServiceFactory.createHome(owner)).switch(profile, pin)
            }.onSuccess { switched ->
                preferences.saveProfileConnection(switched, profile.uuid)
                _profiles.value = PlexProfilesUiState.Ready(
                    currentProfiles.map { it.copy(isActive = it.uuid == profile.uuid) },
                )
                connectInternal(switched, persist = false, onboarding = false)
            }.onFailure {
                val message = if (profile.isProtected) {
                    "Plex rejected that PIN. Try again."
                } else it.userMessage()
                _profiles.value = PlexProfilesUiState.Error(message, currentProfiles)
            }
        }
    }

    fun runNetworkAndCodecTest() {
        val ready = _uiState.value as? CinemaUiState.Ready ?: return
        val currentRepository = repository ?: return
        val sample = (ready.catalog.movies + ready.catalog.continueWatching)
            .firstOrNull { it.kind == MediaKind.Movie || it.kind == MediaKind.Episode }
            ?: return run {
                _networkAssistant.value = NetworkAssistantUiState.Error("No playable media is available for a server speed test.")
            }
        _networkAssistant.value = NetworkAssistantUiState.Testing
        viewModelScope.launch {
            runCatching {
                val playable = currentRepository.loadPlayable(sample.ratingKey)
                    ?: error("Plex did not return a playable test item.")
                val url = playable.playback?.directUrl
                    ?: error("The selected Plex item has no direct media URL.")
                PlaybackCapabilityAssistant().analyze(ready.connection, url)
            }.onSuccess { _networkAssistant.value = NetworkAssistantUiState.Ready(it) }
                .onFailure { _networkAssistant.value = NetworkAssistantUiState.Error(it.userMessage()) }
        }
    }

    fun requestTvHomeChannels() {
        tvHomePublisher.requestChannelsBrowsable()
    }

    fun refresh(silent: Boolean = false) {
        val currentConnection = connection ?: return
        val currentRepository = repository ?: return
        catalogJob?.cancel()
        watchlistJob?.cancel()
        catalogJob = viewModelScope.launch {
            val previous = _uiState.value as? CinemaUiState.Ready
            if (silent && previous != null) _uiState.value = previous.copy(refreshing = true)
            else _uiState.value = CinemaUiState.Loading
            try {
                val catalog = applyLocalLibrary(currentRepository.loadCatalog())
                catalogCache.write(currentConnection, catalog)
                artworkPrefetcher.prefetch(catalog)
                tvHomePublisher.publish(catalog)
                _uiState.value = CinemaUiState.Ready(catalog, currentConnection)
                refreshProfiles()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                if (silent && previous != null) _uiState.value = previous.copy(refreshing = false)
                else _uiState.value = CinemaUiState.Error(error.userMessage())
            }
        }
    }

    /** Re-syncs account-wide Plex Watchlist data when its tab is opened. */
    fun refreshWatchlist() {
        val currentRepository = repository ?: return
        val ready = _uiState.value as? CinemaUiState.Ready ?: return
        watchlistJob?.cancel()
        watchlistJob = viewModelScope.launch {
            val localMedia = ready.catalog.movies + ready.catalog.shows
            runCatching { currentRepository.loadWatchlist(localMedia) }
                .onSuccess { watchlist ->
                    val latest = _uiState.value as? CinemaUiState.Ready ?: return@onSuccess
                    _uiState.value = latest.copy(
                        catalog = latest.catalog.copy(myList = watchlist),
                    )
                }
        }
    }

    fun loadShow(show: MediaContent) {
        val currentRepository = repository ?: return
        detailJob?.cancel()
        detailJob = viewModelScope.launch {
            _movieDetail.value = MovieDetailUiState.Idle
            _showDetail.value = ShowDetailUiState.Loading(show)
            try {
                val detailedShow = currentRepository.loadPlayable(show.ratingKey) ?: show
                val seasons = currentRepository.loadChildren(show.ratingKey)
                _showDetail.value = ShowDetailUiState.Ready(
                    show = detailedShow,
                    seasons = seasons,
                    selectedSeason = null,
                    episodes = emptyList(),
                )
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                _showDetail.value = ShowDetailUiState.Error(show, error.userMessage())
            }
        }
    }

    fun loadMovie(movie: MediaContent) {
        val currentRepository = repository ?: return
        detailJob?.cancel()
        detailJob = viewModelScope.launch {
            _showDetail.value = ShowDetailUiState.Idle
            _movieDetail.value = MovieDetailUiState.Loading(movie)
            try {
                val detailed = currentRepository.loadPlayable(movie.ratingKey) ?: movie
                val trailers = runCatching {
                    currentRepository.loadTrailers(movie.ratingKey)
                }.getOrDefault(emptyList())
                _movieDetail.value = MovieDetailUiState.Ready(detailed, trailers)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                _movieDetail.value = MovieDetailUiState.Error(movie, error.userMessage())
            }
        }
    }

    fun selectSeason(season: MediaContent) {
        val currentRepository = repository ?: return
        val current = _showDetail.value as? ShowDetailUiState.Ready ?: return
        detailJob?.cancel()
        detailJob = viewModelScope.launch {
            _showDetail.value = current.copy(
                selectedSeason = season,
                episodes = emptyList(),
                loadingEpisodes = true,
            )
            try {
                val episodes = currentRepository.loadChildren(season.ratingKey)
                _showDetail.value = current.copy(
                    selectedSeason = season,
                    episodes = episodes,
                    loadingEpisodes = false,
                )
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                _showDetail.value = ShowDetailUiState.Error(current.show, error.userMessage())
            }
        }
    }

    fun resolvePlayable(content: MediaContent, onReady: (MediaContent?) -> Unit) {
        val currentRepository = repository ?: return onReady(null)
        viewModelScope.launch {
            val detailed = runCatching { currentRepository.loadPlayable(content.ratingKey) }.getOrNull()
            onReady(detailed ?: content.takeIf { it.canPlay })
        }
    }

    /** Resolves the feature and builds its optional theatrical pre-roll off the UI thread. */
    fun resolvePlaybackPlan(
        content: MediaContent,
        cinemaModeEnabled: Boolean,
        cinemaTrailersEnabled: Boolean,
        bumperUri: String?,
        onReady: (CinemaPlaybackPlan?) -> Unit,
    ) {
        val currentRepository = repository ?: return onReady(null)
        viewModelScope.launch {
            val playable = runCatching {
                currentRepository.loadPlayable(content.ratingKey)
            }.getOrNull() ?: content.takeIf(MediaContent::canPlay)
            if (playable == null) return@launch onReady(null)

            val shouldUseCinemaMode = cinemaModeEnabled && playable.kind == MediaKind.Movie
            val trailers = if (shouldUseCinemaMode && cinemaTrailersEnabled) {
                runCatching {
                    currentRepository.loadCinemaTrailers(playable.ratingKey)
                }.getOrDefault(emptyList())
            } else {
                emptyList()
            }
            onReady(
                CinemaPlaybackPlan(
                    mainFeature = playable,
                    trailers = trailers,
                    bumperUri = bumperUri?.takeIf { shouldUseCinemaMode && it.isNotBlank() },
                    cinemaModeActive = shouldUseCinemaMode,
                ),
            )
        }
    }

    fun toggleMyList(content: MediaContent) {
        val ready = _uiState.value as? CinemaUiState.Ready ?: return
        val included = ready.catalog.myList.any { it.ratingKey == content.ratingKey }
        val currentRepository = repository ?: return
        viewModelScope.launch {
            runCatching { currentRepository.setWatchlisted(content, watchlisted = !included) }
                .onSuccess {
                    val updated = if (included) {
                        ready.catalog.myList.filterNot { it.ratingKey == content.ratingKey }
                    } else {
                        ready.catalog.myList + content
                    }
                    _uiState.value = ready.copy(
                        catalog = ready.catalog.copy(myList = updated.distinctBy { it.ratingKey }),
                    )
                }
        }
    }

    fun finishPlaybackAndLoadNext(content: MediaContent, onReady: (MediaContent?) -> Unit) {
        val currentRepository = repository ?: return onReady(null)
        viewModelScope.launch {
            if (content.kind != com.minova.cinema.domain.MediaKind.Extra) {
                runCatching { currentRepository.setWatched(content, watched = true) }
            }
            val next = runCatching { currentRepository.loadNextEpisode(content) }.getOrNull()
            onReady(next)
        }
    }

    /**
     * Updates Plex first, then mirrors the confirmed watched state through all
     * in-memory catalog/detail copies so every visible badge changes together.
     */
    fun setWatched(content: MediaContent, watched: Boolean) {
        val currentRepository = repository ?: return
        viewModelScope.launch {
            runCatching { currentRepository.setWatched(content, watched) }
                .onSuccess {
                    val ready = _uiState.value as? CinemaUiState.Ready
                    if (ready != null) {
                        val update: (MediaContent) -> MediaContent = { item ->
                            if (item.ratingKey == content.ratingKey) {
                                item.copy(
                                    isWatched = watched,
                                    viewOffsetMs = if (watched) 0L else item.viewOffsetMs,
                                )
                            } else item
                        }
                        _uiState.value = ready.copy(
                            catalog = ready.catalog.copy(
                                movies = ready.catalog.movies.map(update),
                                shows = ready.catalog.shows.map(update),
                                continueWatching = if (watched) {
                                    ready.catalog.continueWatching.filterNot {
                                        it.ratingKey == content.ratingKey
                                    }
                                } else {
                                    ready.catalog.continueWatching.map(update)
                                },
                                myList = ready.catalog.myList.map(update),
                            ),
                        )
                    }
                    _showDetail.value = when (val detail = _showDetail.value) {
                        is ShowDetailUiState.Ready -> detail.copy(
                            show = detail.show.updateWatched(content.ratingKey, watched),
                            seasons = detail.seasons.map {
                                it.updateWatched(content.ratingKey, watched)
                            },
                            selectedSeason = detail.selectedSeason?.updateWatched(
                                content.ratingKey,
                                watched,
                            ),
                            episodes = detail.episodes.map {
                                it.updateWatched(content.ratingKey, watched)
                            },
                        )
                        is ShowDetailUiState.Loading -> detail.copy(
                            show = detail.show.updateWatched(content.ratingKey, watched),
                        )
                        is ShowDetailUiState.Error -> detail.copy(
                            show = detail.show.updateWatched(content.ratingKey, watched),
                        )
                        ShowDetailUiState.Idle -> ShowDetailUiState.Idle
                    }
                }
        }
    }

    fun removeFromContinueWatching(content: MediaContent) {
        val ready = _uiState.value as? CinemaUiState.Ready ?: return
        preferences.dismissContinueWatching(content.ratingKey)
        _uiState.value = ready.copy(
            catalog = ready.catalog.copy(
                continueWatching = ready.catalog.continueWatching.filterNot {
                    it.ratingKey == content.ratingKey
                },
            ),
        )
    }

    fun reportPlayback(
        content: MediaContent,
        positionMs: Long,
        durationMs: Long,
        state: String,
    ) {
        val currentRepository = repository ?: return
        viewModelScope.launch {
            runCatching {
                currentRepository.reportTimeline(content, positionMs, durationMs, state)
            }
        }
    }

    fun selectSubtitle(
        content: MediaContent,
        subtitleStreamId: Long?,
        onComplete: () -> Unit,
    ) {
        val currentRepository = repository ?: return onComplete()
        viewModelScope.launch {
            runCatching { currentRepository.selectSubtitle(content, subtitleStreamId) }
            onComplete()
        }
    }

    fun selectAudio(
        content: MediaContent,
        audioStreamId: Long,
        onComplete: () -> Unit,
    ) {
        val currentRepository = repository ?: return onComplete()
        viewModelScope.launch {
            runCatching { currentRepository.selectAudio(content, audioStreamId) }
            onComplete()
        }
    }

    fun loadPlaybackDiagnostics(
        content: MediaContent,
        sessionId: String?,
        quality: PlaybackQuality,
        onReady: (PlaybackDiagnostics) -> Unit,
    ) {
        val currentRepository = repository ?: return
        viewModelScope.launch {
            runCatching {
                currentRepository.loadPlaybackDiagnostics(content, sessionId, quality.label)
            }.onSuccess(onReady)
        }
    }

    private fun connectInternal(
        newConnection: PlexConnection,
        persist: Boolean,
        onboarding: Boolean,
    ) {
        catalogJob?.cancel()
        watchlistJob?.cancel()
        val cached = if (onboarding) null else catalogCache.read(newConnection)
        catalogJob = viewModelScope.launch {
            _uiState.value = when {
                onboarding -> CinemaUiState.Onboarding(connecting = true)
                cached != null -> CinemaUiState.Ready(cached, newConnection, refreshing = true)
                else -> CinemaUiState.Loading
            }
            try {
                val newRepository = PlexRepository(
                    connection = newConnection,
                    api = PlexServiceFactory.create(newConnection),
                    watchlistApi = PlexServiceFactory.createWatchlist(newConnection),
                )
                // Cached browsing can resolve details immediately while the
                // paged background refresh is still in progress.
                connection = newConnection
                repository = newRepository
                val catalog = applyLocalLibrary(newRepository.loadCatalog())
                if (persist) preferences.saveConnection(newConnection)
                catalogCache.write(newConnection, catalog)
                artworkPrefetcher.prefetch(catalog)
                tvHomePublisher.publish(catalog)
                _uiState.value = CinemaUiState.Ready(catalog, newConnection)
                refreshProfiles()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                _uiState.value = if (cached != null) {
                    CinemaUiState.Ready(cached, newConnection, refreshing = false)
                } else if (onboarding) {
                    CinemaUiState.Onboarding(error = error.userMessage())
                } else {
                    CinemaUiState.Error(error.userMessage())
                }
            }
        }
    }

    private fun applyLocalLibrary(catalog: CinemaCatalog): CinemaCatalog {
        val dismissed = preferences.readDismissedContinueWatchingIds()
        return catalog.copy(
            continueWatching = catalog.continueWatching.filterNot { it.ratingKey in dismissed },
        )
    }

    private fun Throwable.userMessage(): String {
        return when {
            message?.contains("401") == true -> "Plex rejected the token. Check it and try again."
            message?.contains("Failed to connect") == true -> "Could not reach that Plex server on your network."
            else -> message ?: "Could not load the Plex library."
        }
    }

    class Factory(context: Context) : ViewModelProvider.Factory {
        private val appContext = context.applicationContext
        private val preferences = PlexPreferences(appContext)
        private val catalogCache = PlexCatalogCache(appContext)
        private val artworkPrefetcher = PlexArtworkPrefetcher(appContext)
        private val tvHomePublisher = TvHomePublisher(appContext)

        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(CinemaViewModel::class.java))
            return CinemaViewModel(
                preferences,
                catalogCache,
                artworkPrefetcher,
                tvHomePublisher,
            ) as T
        }
    }
}

private fun MediaContent.updateWatched(ratingKey: String, watched: Boolean): MediaContent =
    if (this.ratingKey == ratingKey) {
        copy(isWatched = watched, viewOffsetMs = if (watched) 0L else viewOffsetMs)
    } else this
