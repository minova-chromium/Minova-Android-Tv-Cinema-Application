package com.minova.cinema.presentation

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.minova.cinema.data.PlexRepository
import com.minova.cinema.data.PlexAccountRepository
import com.minova.cinema.data.PlexDiscoveredServer
import com.minova.cinema.data.PlexProfileRepository
import com.minova.cinema.data.PlaybackCapabilityAssistant
import com.minova.cinema.data.local.PlexPreferences
import com.minova.cinema.data.local.PlexDeviceIdentity
import com.minova.cinema.data.local.PlexCatalogCache
import com.minova.cinema.data.remote.PlexConfig
import com.minova.cinema.data.remote.PlexConnection
import com.minova.cinema.data.remote.PlexServiceFactory
import com.minova.cinema.domain.MediaContent
import com.minova.cinema.domain.CinemaCatalog
import com.minova.cinema.domain.CinemaPlaybackPlan
import com.minova.cinema.domain.MediaKind
import com.minova.cinema.domain.PlaybackDiagnostics
import com.minova.cinema.domain.selectSeriesPlaybackEpisode
import com.minova.cinema.data.remote.PlaybackQuality
import com.minova.cinema.offline.OfflineDownload
import com.minova.cinema.offline.OfflineDownloadState
import com.minova.cinema.offline.OfflineDownloadsStore
import com.minova.cinema.domain.PlaybackSource
import com.minova.cinema.tvhome.TvHomePublisher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.UnknownHostException
import java.net.ConnectException
import java.net.SocketTimeoutException
import javax.net.ssl.SSLException

class CinemaViewModel(
    private val preferences: PlexPreferences,
    private val catalogCache: PlexCatalogCache,
    private val tvHomePublisher: TvHomePublisher,
    private val offlineDownloadsStore: OfflineDownloadsStore,
    private val clientIdentifier: String,
    private val accountRepository: PlexAccountRepository,
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

    private val _offlineDownloads = MutableStateFlow<List<OfflineDownload>>(emptyList())
    val offlineDownloads: StateFlow<List<OfflineDownload>> = _offlineDownloads.asStateFlow()

    private val _plexSignIn = MutableStateFlow<PlexSignInUiState>(PlexSignInUiState.Idle)
    val plexSignIn: StateFlow<PlexSignInUiState> = _plexSignIn.asStateFlow()

    private var connection: PlexConnection? = null
    private var repository: PlexRepository? = null
    private var catalogJob: Job? = null
    private var detailJob: Job? = null
    private var watchlistJob: Job? = null
    private var offlineDownloadsJob: Job? = null
    private var plexSignInJob: Job? = null
    private var discoveredServers: Map<String, PlexDiscoveredServer> = emptyMap()

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
        cancelPlexSignIn()
        connectInternal(
            PlexConnection(normalized, token, clientIdentifier),
            persist = true,
            onboarding = true,
        )
    }

    fun startPlexSignIn() {
        plexSignInJob?.cancel()
        discoveredServers = emptyMap()
        plexSignInJob = viewModelScope.launch {
            _plexSignIn.value = PlexSignInUiState.Starting
            try {
                val challenge = accountRepository.createPin()
                _plexSignIn.value = PlexSignInUiState.Waiting(
                    code = challenge.code,
                    authorizationUrl = challenge.authorizationUrl,
                )
                val accountToken = accountRepository.awaitAuthorization(challenge)
                _plexSignIn.value = PlexSignInUiState.Discovering
                val servers = accountRepository.discoverServers(accountToken)
                if (servers.isEmpty()) {
                    throw IllegalStateException(
                        "Plex sign-in succeeded, but this account has no available Plex Media Server.",
                    )
                }
                discoveredServers = servers.associateBy(PlexDiscoveredServer::id)
                if (servers.size == 1) {
                    connectDiscoveredServer(servers.single())
                } else {
                    _plexSignIn.value = PlexSignInUiState.SelectServer(
                        servers.map { PlexServerChoice(it.id, it.name, it.owned) },
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _plexSignIn.value = PlexSignInUiState.Error(error.userMessage())
            }
        }
    }

    fun selectPlexServer(serverId: String) {
        val server = discoveredServers[serverId] ?: return
        plexSignInJob?.cancel()
        plexSignInJob = viewModelScope.launch {
            try {
                connectDiscoveredServer(server)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _plexSignIn.value = PlexSignInUiState.Error(error.userMessage())
            }
        }
    }

    fun cancelPlexSignIn() {
        plexSignInJob?.cancel()
        plexSignInJob = null
        discoveredServers = emptyMap()
        _plexSignIn.value = PlexSignInUiState.Idle
    }

    private suspend fun connectDiscoveredServer(server: PlexDiscoveredServer) {
        _plexSignIn.value = PlexSignInUiState.Connecting(server.name)
        val discoveredConnection = accountRepository.resolveConnection(server)
        _plexSignIn.value = PlexSignInUiState.Idle
        connectInternal(
            newConnection = discoveredConnection,
            persist = true,
            onboarding = true,
            ownerToken = server.accountToken,
        )
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
        cancelPlexSignIn()
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
                _uiState.value = CinemaUiState.Ready(catalog, currentConnection)
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    catalogCache.write(currentConnection, catalog)
                    tvHomePublisher.publish(catalog)
                }
                refreshProfiles()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                if (silent && previous != null) _uiState.value = previous.copy(refreshing = false)
                else _uiState.value = CinemaUiState.Error(error.userMessage())
            }
        }
    }

    fun refreshOfflineDownloads() {
        startOfflineDownloadMonitor()
    }

    fun downloadForOffline(content: MediaContent, onComplete: (String) -> Unit) {
        val currentConnection = connection
            ?: return onComplete("Connect to your Plex server before downloading.")
        val currentRepository = repository
            ?: return onComplete("Connect to your Plex server before downloading.")
        viewModelScope.launch {
            try {
                val playable = currentRepository.loadPlayable(content.ratingKey)
                    ?: error("Plex did not return a downloadable media item.")
                withContext(Dispatchers.IO) {
                    val existing = offlineDownloadsStore.list(currentConnection)
                        .firstOrNull { it.ratingKey == playable.ratingKey }
                    if (existing?.state == OfflineDownloadState.Failed) {
                        offlineDownloadsStore.remove(existing)
                    }
                    offlineDownloadsStore.stage(playable, currentConnection)
                }
                startOfflineDownloadMonitor()
                onComplete("Preparing download with Plex…")
            } catch (error: Exception) {
                onComplete(error.userMessage())
            }
        }
    }

    fun removeOfflineDownload(download: OfflineDownload) {
        val currentRepository = repository
        viewModelScope.launch {
            download.ticket?.let { ticket ->
                runCatching { currentRepository?.cancelOfflineDownload(ticket) }
            }
            withContext(Dispatchers.IO) { offlineDownloadsStore.remove(download) }
            refreshOfflineDownloadsSnapshot()
        }
    }

    fun retryOfflineDownload(download: OfflineDownload, onComplete: (String) -> Unit) {
        viewModelScope.launch {
            val request = download.asMediaRequest()
            download.ticket?.let { ticket ->
                runCatching { repository?.cancelOfflineDownload(ticket) }
            }
            withContext(Dispatchers.IO) { offlineDownloadsStore.remove(download) }
            refreshOfflineDownloadsSnapshot()
            downloadForOffline(request, onComplete)
        }
    }

    fun playOffline(download: OfflineDownload, onReady: (MediaContent?) -> Unit) {
        viewModelScope.launch {
            onReady(withContext(Dispatchers.IO) { offlineDownloadsStore.playableContent(download) })
        }
    }

    /** Load a collection only when opened, including server-defined smart membership. */
    suspend fun loadCollectionMembers(ratingKey: String): List<MediaContent> =
        checkNotNull(repository) { "Connect to your library first." }.loadCollectionMembers(ratingKey)

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
        if (offlineDownloadsStore.isOfflineContent(content)) {
            onReady(CinemaPlaybackPlan(mainFeature = content))
            return
        }
        val currentRepository = repository ?: return onReady(null)
        viewModelScope.launch {
            val playbackTarget = if (content.kind == MediaKind.Show) {
                val continueWatching = (_uiState.value as? CinemaUiState.Ready)
                    ?.catalog?.continueWatching.orEmpty()
                selectSeriesPlaybackEpisode(content, continueWatching, emptyList())
                    ?: runCatching {
                        selectSeriesPlaybackEpisode(
                            content,
                            continueWatching,
                            currentRepository.loadSeriesEpisodes(content.ratingKey),
                        )
                    }.getOrNull()
                    ?: return@launch onReady(null)
            } else {
                content
            }
            val playable = runCatching {
                currentRepository.loadPlayable(playbackTarget.ratingKey)
            }.getOrNull() ?: playbackTarget.takeIf(MediaContent::canPlay)
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
    fun rate(content: MediaContent, rating: Int, onComplete: (Boolean) -> Unit) {
        val repo = repository ?: return onComplete(false)
        viewModelScope.launch { onComplete(runCatching { repo.rate(content, rating) }.getOrDefault(false)) }
    }

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
        viewModelScope.launch {
            if (offlineDownloadsStore.isOfflineContent(content)) {
                val currentConnection = connection
                if (currentConnection != null) {
                    withContext(Dispatchers.IO) {
                        offlineDownloadsStore.list(currentConnection)
                            .firstOrNull { it.ratingKey == content.ratingKey }
                            ?.let { offlineDownloadsStore.updatePlaybackPosition(it, positionMs) }
                    }
                }
            }
            val currentRepository = repository ?: return@launch
            runCatching {
                currentRepository.reportTimeline(content, positionMs, durationMs, state)
            }
        }
    }

    private fun startOfflineDownloadMonitor() {
        offlineDownloadsJob?.cancel()
        offlineDownloadsJob = viewModelScope.launch {
            while (true) {
                val currentConnection = connection ?: break
                val currentRepository = repository ?: break
                var downloads = withContext(Dispatchers.IO) {
                    offlineDownloadsStore.list(currentConnection)
                }
                _offlineDownloads.value = downloads
                val pending = downloads.filter {
                    it.state == OfflineDownloadState.Preparing && it.systemDownloadId == null
                }
                pending.forEach { download ->
                    try {
                        var current = download
                        var ticket = current.ticket
                        if (ticket == null) {
                            ticket = currentRepository.createOfflineDownload(current.asMediaRequest())
                            current = withContext(Dispatchers.IO) {
                                offlineDownloadsStore.attachTicket(current, ticket)
                            }
                        }
                        val confirmedTicket = requireNotNull(ticket)
                        val queueItem = currentRepository.offlineDownloadStatus(confirmedTicket)
                        when (queueItem.status.lowercase()) {
                            "available", "done", "complete", "completed" -> withContext(Dispatchers.IO) {
                                offlineDownloadsStore.beginTransfer(
                                    current,
                                    currentConnection,
                                    currentRepository.offlineDownloadMediaUrl(confirmedTicket),
                                )
                            }
                            "error", "failed", "cancelled", "canceled" -> withContext(Dispatchers.IO) {
                                offlineDownloadsStore.fail(current, "Plex could not prepare this download.")
                            }
                        }
                    } catch (error: Exception) {
                        withContext(Dispatchers.IO) {
                            offlineDownloadsStore.fail(download, error.userMessage())
                        }
                    }
                }
                downloads = withContext(Dispatchers.IO) {
                    offlineDownloadsStore.list(currentConnection)
                }
                downloads.filter { it.state == OfflineDownloadState.Ready && it.ticket != null }
                    .forEach { ready ->
                        val ticket = ready.ticket ?: return@forEach
                        if (runCatching { currentRepository.cancelOfflineDownload(ticket) }.isSuccess) {
                            withContext(Dispatchers.IO) {
                                offlineDownloadsStore.clearTicket(ready)
                            }
                        }
                    }
                downloads = withContext(Dispatchers.IO) {
                    offlineDownloadsStore.list(currentConnection)
                }
                _offlineDownloads.value = downloads
                val stillWorking = downloads.any {
                    it.state == OfflineDownloadState.Preparing ||
                        it.state == OfflineDownloadState.Downloading ||
                        it.state == OfflineDownloadState.Paused
                }
                delay(if (stillWorking) 1_500L else 5_000L)
            }
        }
    }

    private suspend fun refreshOfflineDownloadsSnapshot() {
        val currentConnection = connection ?: return
        _offlineDownloads.value = withContext(Dispatchers.IO) {
            offlineDownloadsStore.list(currentConnection)
        }
    }

    fun handoffPlayback(
        content: MediaContent,
        positionMs: Long,
        durationMs: Long,
        onComplete: (errorMessage: String?) -> Unit,
    ) {
        val currentRepository = repository
            ?: return onComplete("Connect to your Plex library before continuing on another device.")
        viewModelScope.launch {
            runCatching {
                currentRepository.reportTimeline(
                    content = content,
                    positionMs = positionMs,
                    durationMs = durationMs,
                    state = "paused",
                )
            }.onSuccess {
                refresh(silent = true)
                onComplete(null)
            }.onFailure { error ->
                onComplete(error.userMessage())
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
        ownerToken: String? = null,
    ) {
        catalogJob?.cancel()
        watchlistJob?.cancel()
        catalogJob = viewModelScope.launch {
            val cached = if (onboarding) null else kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                catalogCache.read(newConnection)
            }
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
                startOfflineDownloadMonitor()
                val catalog = applyLocalLibrary(newRepository.loadCatalog())
                if (persist) preferences.saveConnection(
                    newConnection,
                    ownerToken = ownerToken ?: newConnection.token,
                )
                _uiState.value = CinemaUiState.Ready(catalog, newConnection)
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    catalogCache.write(newConnection, catalog)
                    tvHomePublisher.publish(catalog)
                }
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
        val causes = generateSequence(this as Throwable?) { it.cause }.toList()
        return when {
            message?.contains("401") == true -> "Plex rejected the token. Check it and try again."
            causes.any { it is UnknownHostException } ->
                "Could not find that server name. For a Tailscale address, connect this phone to Tailscale first."
            causes.any { it is SSLException } ->
                "The secure Plex connection failed. Check the HTTPS address and certificate."
            causes.any { it is SocketTimeoutException || it is ConnectException } ->
                "Could not reach that Plex server. Check the address, port, and phone network."
            message?.contains("Failed to connect") == true -> "Could not reach that Plex server on your network."
            else -> message ?: "Could not load the Plex library."
        }
    }

    class Factory(context: Context) : ViewModelProvider.Factory {
        private val appContext = context.applicationContext
        private val preferences = PlexPreferences(appContext)
        private val catalogCache = PlexCatalogCache(appContext)
        private val tvHomePublisher = TvHomePublisher(appContext)
        private val offlineDownloadsStore = OfflineDownloadsStore(appContext)
        private val clientIdentifier = PlexDeviceIdentity(appContext).get()
        private val accountRepository = PlexAccountRepository(
            clientIdentifier,
            PlexServiceFactory.createAccount(clientIdentifier),
        )

        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(CinemaViewModel::class.java))
            return CinemaViewModel(
                preferences,
                catalogCache,
                tvHomePublisher,
                offlineDownloadsStore,
                clientIdentifier,
                accountRepository,
            ) as T
        }
    }
}

private fun MediaContent.updateWatched(ratingKey: String, watched: Boolean): MediaContent =
    if (this.ratingKey == ratingKey) {
        copy(isWatched = watched, viewOffsetMs = if (watched) 0L else viewOffsetMs)
    } else this

private fun OfflineDownload.asMediaRequest(): MediaContent = MediaContent(
    ratingKey = ratingKey,
    title = title,
    secondaryTitle = secondaryTitle,
    summary = summary,
    tagline = null,
    year = year,
    durationMs = durationMs,
    viewOffsetMs = viewOffsetMs,
    posterUrl = null,
    backdropUrl = null,
    contentRating = contentRating,
    kind = kind,
    seasonNumber = seasonNumber,
    episodeNumber = episodeNumber,
    playback = PlaybackSource(
        partId = -1L,
        directUrl = "",
        metadataKey = metadataKey,
        audioStreams = emptyList(),
        subtitles = emptyList(),
        technicalInfo = technicalInfo,
    ),
)
