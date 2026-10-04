package com.minova.cinema.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.minova.cinema.data.local.ExperiencePreferences
import com.minova.cinema.ui.experience.*
import com.minova.cinema.ui.theme.MinovaCinemaTheme
import android.app.Activity
import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import com.minova.cinema.data.local.BrowsePreferences
import com.minova.cinema.data.local.CastPreferences
import com.minova.cinema.ui.browse.HomeCustomizationDialog
import com.minova.cinema.ui.browse.buildHomeDiscoveryShelves
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.minova.cinema.domain.MediaContent
import com.minova.cinema.domain.MediaCredit
import com.minova.cinema.domain.MediaKind
import com.minova.cinema.domain.CinemaCatalog
import com.minova.cinema.domain.CinemaPlaybackPlan
import com.minova.cinema.domain.hasSeriesPlaybackProgress
import com.minova.cinema.data.local.PlaybackPreferences
import com.minova.cinema.presentation.CinemaUiState
import com.minova.cinema.presentation.CinemaViewModel
import com.minova.cinema.presentation.ShowDetailUiState
import com.minova.cinema.presentation.MovieDetailUiState
import com.minova.cinema.presentation.PersonProfileUiState
import com.minova.cinema.presentation.TapoLightsViewModel
import com.minova.cinema.update.UpdateUiState
import com.minova.cinema.update.UpdateViewModel
import com.minova.cinema.update.UpdateInstaller
import com.minova.cinema.ui.browse.BrowseScreen
import com.minova.cinema.ui.ambient.AmbientInactivityTracker
import com.minova.cinema.ui.common.ConnectionErrorScreen
import com.minova.cinema.ui.common.LoadingScreen
import com.minova.cinema.ui.detail.DetailScreen
import com.minova.cinema.ui.detail.PersonProfileScreen
import com.minova.cinema.ui.intro.AnimatedIntroScreen
import com.minova.cinema.ui.onboarding.OnboardingScreen
import com.minova.cinema.ui.offline.OfflineDownloadsScreen
import com.minova.cinema.ui.player.PlayerScreen
import com.minova.cinema.ui.platform.DeviceProfile
import com.minova.cinema.ui.platform.rememberDeviceProfile
import com.minova.cinema.ui.settings.SettingsScreen
import com.minova.cinema.ui.update.UpdateAvailableDialog
import com.minova.cinema.ui.update.UpdateDownloadDialog
import com.minova.cinema.home.CinemaLightingController
import com.minova.cinema.data.remote.PlexConnection
import com.minova.cinema.cast.CastPlaybackStatus
import com.minova.cinema.cast.MinovaCastController
import com.minova.cinema.cast.MinovaCastState
import com.minova.cinema.ui.cast.MinovaCastMiniController
import kotlinx.coroutines.flow.MutableStateFlow

private sealed interface CinemaRoute {
    data object Browse : CinemaRoute
    data class Detail(val content: MediaContent) : CinemaRoute
    data class Person(val credit: MediaCredit) : CinemaRoute
    data class Player(val plan: CinemaPlaybackPlan, val sessionId: String = java.util.UUID.randomUUID().toString()) : CinemaRoute
    data class Finished(val content: MediaContent) : CinemaRoute
    data object Downloads : CinemaRoute
    data object Settings : CinemaRoute
}

private object TopLevelRoute {
    const val Intro = "intro"
    const val Main = "main"
}

@Composable
fun MinovaCinemaApp(
    viewModel: CinemaViewModel,
    updateViewModel: UpdateViewModel,
    ambientInactivityTracker: AmbientInactivityTracker,
    cinemaLightingController: CinemaLightingController,
    tapoLightsViewModel: TapoLightsViewModel,
    deepLinkRatingKey: String? = null,
    onDeepLinkConsumed: () -> Unit = {},
    isTrailerRecordingMode: Boolean = false,
    disablePlexTrailersForCapture: Boolean = false,
    isInPictureInPictureMode: Boolean = false,
) {
    val context = LocalContext.current
    val navController = rememberNavController()
    val updateState by updateViewModel.state.collectAsStateWithLifecycle()
    val readyToInstall = updateState as? UpdateUiState.ReadyToInstall

    // This runs while MainActivity is visibly in the foreground. Android TV
    // blocks the old receiver-based background launch on some devices.
    LaunchedEffect(readyToInstall?.downloadId) {
        if (readyToInstall != null && UpdateInstaller.installDownloadedApk(context)) {
            updateViewModel.installationHandoffStarted()
        }
    }

    // The intro is a real navigation destination, not an overlay. Removing it
    // inclusively ensures Back from the root Browse screen exits the activity.
    NavHost(
        navController = navController,
        startDestination = TopLevelRoute.Intro,
        enterTransition = { fadeIn(tween(550)) },
        exitTransition = { fadeOut(tween(550)) },
        popEnterTransition = { fadeIn(tween(550)) },
        popExitTransition = { fadeOut(tween(550)) },
    ) {
        composable(TopLevelRoute.Intro) {
            AnimatedIntroScreen(
                onFinished = {
                    navController.navigate(TopLevelRoute.Main) {
                        popUpTo(TopLevelRoute.Intro) { inclusive = true }
                        launchSingleTop = true
                    }
                },
            )
        }
        composable(TopLevelRoute.Main) {
            LaunchedEffect(Unit) {
                updateViewModel.checkForUpdate()
            }
            MainScreen(
                viewModel,
                ambientInactivityTracker,
                cinemaLightingController,
                tapoLightsViewModel,
                deepLinkRatingKey,
                onDeepLinkConsumed,
                isTrailerRecordingMode,
                disablePlexTrailersForCapture,
                isInPictureInPictureMode,
            )

            (updateState as? UpdateUiState.Available)?.let { available ->
                UpdateAvailableDialog(
                    update = available.update,
                    onUpdateNow = updateViewModel::downloadUpdate,
                    onLater = updateViewModel::dismissUpdate,
                )
            }
            (updateState as? UpdateUiState.Downloading)?.let { downloading ->
                if (downloading.visible) {
                    UpdateDownloadDialog(
                        versionName = downloading.versionName,
                        progressPercent = downloading.progressPercent,
                        paused = downloading.paused,
                        onHide = updateViewModel::hideDownloadProgress,
                    )
                }
            }
        }
    }
}

/** Existing Plex application destination hosted behind the launch intro. */
@Composable
private fun MainScreen(
    viewModel: CinemaViewModel,
    ambientInactivityTracker: AmbientInactivityTracker,
    cinemaLightingController: CinemaLightingController,
    tapoLightsViewModel: TapoLightsViewModel,
    deepLinkRatingKey: String?,
    onDeepLinkConsumed: () -> Unit,
    isTrailerRecordingMode: Boolean,
    disablePlexTrailersForCapture: Boolean,
    isInPictureInPictureMode: Boolean,
) {
    val context = LocalContext.current
    val handheld = rememberDeviceProfile() == DeviceProfile.Handheld
    val phone = handheld && LocalConfiguration.current.smallestScreenWidthDp < 600
    val castController = remember(context.applicationContext, handheld) {
        if (handheld) {
            runCatching { MinovaCastController(context.applicationContext) }.getOrNull()
        } else {
            null
        }
    }
    val castStateFlow = remember(castController) {
        castController?.state ?: MutableStateFlow(MinovaCastState())
    }
    val castState by castStateFlow.collectAsStateWithLifecycle()
    val castPreferences = remember(context.applicationContext) {
        CastPreferences(context.applicationContext)
    }
    var castServerUrl by remember { mutableStateOf(castPreferences.readServerUrl()) }
    DisposableEffect(castController) {
        onDispose { castController?.close() }
    }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val showDetail by viewModel.showDetail.collectAsStateWithLifecycle()
    val movieDetail by viewModel.movieDetail.collectAsStateWithLifecycle()
    val personProfile by viewModel.personProfile.collectAsStateWithLifecycle()
    val profilesState by viewModel.profiles.collectAsStateWithLifecycle()
    val networkAssistantState by viewModel.networkAssistant.collectAsStateWithLifecycle()
    val offlineDownloads by viewModel.offlineDownloads.collectAsStateWithLifecycle()
    val plexSignIn by viewModel.plexSignIn.collectAsStateWithLifecycle()
    val lightingState by cinemaLightingController.state.collectAsStateWithLifecycle()
    val tapoLightsState by tapoLightsViewModel.state.collectAsStateWithLifecycle()
    val routes = rememberRoutes()
    val lifecycleOwner = LocalLifecycleOwner.current
    val playbackPreferences = remember(context.applicationContext) {
        PlaybackPreferences(context.applicationContext)
    }
    var playbackSettings by remember { mutableStateOf(playbackPreferences.read()) }
    var lastPlaybackInteractionAtMs by remember {
        mutableLongStateOf(SystemClock.elapsedRealtime())
    }
    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refresh(silent = true)
                viewModel.refreshOfflineDownloads()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val bumperPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            playbackSettings = playbackPreferences.setCinemaBumperUri(uri.toString())
        }
    }

    LaunchedEffect(playbackSettings.screensaverTimeoutMs) {
        ambientInactivityTracker.updateTimeout(playbackSettings.screensaverTimeoutMs)
    }

    LaunchedEffect(uiState::class) {
        if (uiState is CinemaUiState.Onboarding || uiState is CinemaUiState.Error) {
            routes.clear()
            routes.add(CinemaRoute.Browse)
        }
    }

    when (val state = uiState) {
        is CinemaUiState.Onboarding -> OnboardingScreen(
            connecting = state.connecting,
            error = state.error,
            plexSignIn = plexSignIn,
            onStartPlexSignIn = viewModel::startPlexSignIn,
            onCancelPlexSignIn = viewModel::cancelPlexSignIn,
            onSelectPlexServer = viewModel::selectPlexServer,
            onConnect = viewModel::connect,
        )
        CinemaUiState.Loading -> LoadingScreen()
        is CinemaUiState.Error -> ConnectionErrorScreen(
            message = state.message,
            onRetry = viewModel::retry,
            onChangeServer = viewModel::changeServer,
        )
        is CinemaUiState.Ready -> {
            val currentRoute = routes.last()
            LaunchedEffect(
                castState.content?.ratingKey,
                castState.positionMs,
                castState.status,
            ) {
                castState.content?.let { castContent ->
                    val plexState = when (castState.status) {
                        CastPlaybackStatus.Playing -> "playing"
                        CastPlaybackStatus.Paused -> "paused"
                        CastPlaybackStatus.Buffering -> "buffering"
                        CastPlaybackStatus.Idle -> "stopped"
                    }
                    viewModel.reportPlayback(
                        castContent,
                        castState.positionMs,
                        castState.durationMs.takeIf { it > 0L }
                            ?: castContent.durationMs.orEmptyDuration(),
                        plexState,
                    )
                }
            }
            LaunchedEffect(castState.errorMessage) {
                castState.errorMessage?.let {
                    android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_LONG).show()
                    castController?.clearError()
                }
            }
            LaunchedEffect(phone, currentRoute is CinemaRoute.Player) {
                if (phone) {
                    (context as? Activity)?.requestedOrientation = if (currentRoute is CinemaRoute.Player) {
                        ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                    } else {
                        ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                    }
                }
            }
            val profileKey = state.connection.baseUrl + "\u0000" +
                (com.minova.cinema.data.local.PlexPreferences(context).readActiveProfileUuid() ?: "owner")
            val browsePreferences = remember(profileKey) { BrowsePreferences(context, profileKey) }
            var homePreferences by remember(profileKey) { mutableStateOf(browsePreferences.read()) }
            var customizeHome by remember { mutableStateOf(false) }
            val experiencePreferences = remember(profileKey) { ExperiencePreferences(context, profileKey) }
            var experience by remember(profileKey) { mutableStateOf(experiencePreferences.read()) }
            var customPreset by remember(profileKey) { mutableStateOf(experiencePreferences.customPreset()) }
            var experienceOpen by remember { mutableStateOf(false) }
            var discoveryOpen by remember { mutableStateOf(false) }
            var highlightedTheme by remember { mutableStateOf<String?>(null) }
            val density = LocalDensity.current
            LaunchedEffect(experience.dimLevel, experience.restoreLevel) {
                tapoLightsViewModel.setCinemaLevels(experience.dimLevel, experience.restoreLevel)
            }
            val browseStateHolder = rememberSaveableStateHolder()
            val browseStateKey = remember(profileKey) {
                java.security.MessageDigest.getInstance("SHA-256").digest(profileKey.toByteArray())
                    .joinToString("") { "%02x".format(it) }
            }
            if (customizeHome) HomeCustomizationDialog(
                shelves = buildHomeDiscoveryShelves(state.catalog).map { it.key to it.title },
                preferences = homePreferences,
                onSave = { homePreferences = it; browsePreferences.save(it) },
                onDismiss = { customizeHome = false },
            )

            LaunchedEffect(deepLinkRatingKey, state.catalog) {
                val ratingKey = deepLinkRatingKey ?: return@LaunchedEffect
                val content = (
                    state.catalog.movies + state.catalog.shows +
                        state.catalog.continueWatching + state.catalog.myList
                    ).firstOrNull { it.ratingKey == ratingKey }
                if (content != null) {
                    when (content.kind) {
                        MediaKind.Show -> viewModel.loadShow(content)
                        MediaKind.Movie -> viewModel.loadMovie(content)
                        else -> Unit
                    }
                    routes.clear()
                    routes.add(CinemaRoute.Browse)
                    routes.add(CinemaRoute.Detail(content))
                }
                onDeepLinkConsumed()
            }

            BackHandler(enabled = routes.size > 1) {
                val leavingPlayer = routes.lastOrNull() is CinemaRoute.Player
                routes.removeAt(routes.lastIndex)
                if (leavingPlayer) viewModel.refresh(silent = true)
            }

            fun open(content: MediaContent) {
                when (content.kind) {
                    MediaKind.Show -> viewModel.loadShow(content)
                    MediaKind.Movie -> viewModel.loadMovie(content)
                    else -> Unit
                }
                routes.add(CinemaRoute.Detail(content))
            }

            fun openPerson(credit: MediaCredit) {
                if (credit.personId.isNullOrBlank()) return
                viewModel.loadPerson(credit)
                routes.add(CinemaRoute.Person(credit))
            }

            fun play(content: MediaContent, fromBeginning: Boolean = false) {
                viewModel.resolvePlaybackPlan(
                    content = content,
                    cinemaModeEnabled = !handheld && playbackSettings.cinemaModeEnabled,
                    cinemaTrailersEnabled = !handheld && playbackSettings.cinemaTrailersEnabled &&
                        !disablePlexTrailersForCapture,
                    bumperUri = playbackSettings.cinemaBumperUri.takeIf {
                        !handheld && playbackSettings.cinemaBumperEnabled
                    },
                ) { plan ->
                    if (plan != null) {
                        lastPlaybackInteractionAtMs = SystemClock.elapsedRealtime()
                        val castStarted = handheld && castController?.cast(
                            content = plan.mainFeature,
                            connection = PlexConnection(
                                baseUrl = castServerUrl ?: state.connection.baseUrl,
                                token = state.connection.token,
                            ),
                            fromBeginning = fromBeginning,
                        ) == true
                        if (castStarted) {
                            android.widget.Toast.makeText(
                                context,
                                "Playing on ${castState.deviceName ?: "TV"}",
                                android.widget.Toast.LENGTH_SHORT,
                            ).show()
                        } else {
                            routes.add(
                                CinemaRoute.Player(
                                    if (fromBeginning) {
                                        plan.copy(mainFeature = plan.mainFeature.copy(viewOffsetMs = 0L))
                                    } else {
                                        plan
                                    },
                                ),
                            )
                        }
                    }
                }
            }

            fun playNext(content: MediaContent) {
                viewModel.resolvePlayable(content) { playable ->
                    if (playable != null) {
                        routes[routes.lastIndex] = CinemaRoute.Player(
                            CinemaPlaybackPlan(mainFeature = playable),
                        )
                    }
                }
            }

            CompositionLocalProvider(LocalExperienceSettings provides experience,
                LocalDensity provides Density(density.density, density.fontScale * experience.textScale.coerceIn(1f, 1.2f))) {
            BrowseThemeMusic(highlightedTheme,
                experience.themeMusic && currentRoute == CinemaRoute.Browse && !discoveryOpen && !experienceOpen && !customizeHome,
                experience.themeVolume)
            if (discoveryOpen) DiscoveryDialog(state.catalog, onOpen = { discoveryOpen = false; open(it) },
                onWatched = viewModel::setWatched, onClose = { discoveryOpen = false })
            if (experienceOpen) ExperienceSettingsDialog(experience,
                showCinemaControls = !handheld,
                onChange = { experience = it; experiencePreferences.save(it) },
                playback = playbackSettings,
                onCinemaChange = { changed ->
                    playbackSettings = playbackPreferences.applyCustomCinemaPreset(
                        com.minova.cinema.data.local.CustomCinemaPreset(changed.cinemaModeEnabled,
                            changed.cinemaTrailersEnabled, changed.cinemaBumperEnabled, changed.cinemaLightsEnabled))
                },
                onPreset = { name ->
                    playbackSettings = playbackPreferences.applyCinemaPreset(name)
                    experience = experience.copy(dimLevel = if (name == "quiet") 10 else 0, restoreLevel = -1)
                    experiencePreferences.save(experience)
                },
                onRestoreLights = tapoLightsViewModel::restoreLights,
                hasCustomPreset = customPreset != null,
                onSavePreset = {
                    customPreset = com.minova.cinema.data.local.CustomCinemaPreset(
                        playbackSettings.cinemaModeEnabled, playbackSettings.cinemaTrailersEnabled,
                        playbackSettings.cinemaBumperEnabled, playbackSettings.cinemaLightsEnabled,
                        experience.dimLevel, experience.restoreLevel,
                    ).also(experiencePreferences::saveCustomPreset)
                },
                onLoadPreset = {
                    customPreset?.let { preset ->
                        playbackSettings = playbackPreferences.applyCustomCinemaPreset(preset)
                        experience = experience.copy(dimLevel = preset.dim, restoreLevel = preset.restore)
                        experiencePreferences.save(experience)
                    }
                },
                onClose = { experienceOpen = false })
            MinovaCinemaTheme(highContrast = experience.highContrast) {
            Box(Modifier.fillMaxSize()) {
            AnimatedContent(
                targetState = currentRoute,
                transitionSpec = { fadeIn(tween(if (experience.reducedMotion) 0 else 220)) togetherWith fadeOut(tween(if (experience.reducedMotion) 0 else 180)) },
                label = "cinema_navigation",
            ) { route ->
                when (route) {
                    CinemaRoute.Browse -> browseStateHolder.SaveableStateProvider(browseStateKey) { BrowseScreen(
                        catalog = state.catalog,
                        onOpen = ::open,
                        onPlay = { play(it) },
                        onToggleMyList = viewModel::toggleMyList,
                        onSettings = { routes.add(CinemaRoute.Settings) },
                        onDownloads = { routes.add(CinemaRoute.Downloads) },
                        onWatchlistRefresh = viewModel::refreshWatchlist,
                        loadCollectionMembers = viewModel::loadCollectionMembers,
                        homePreferences = homePreferences,
                        onPlayFromBeginning = { play(it, true) },
                        onSetWatched = viewModel::setWatched,
                        onDiscover = { discoveryOpen = true },
                        onRefresh = { viewModel.refresh(silent = true) },
                        refreshing = state.refreshing,
                        onHighlighted = { highlightedTheme = it?.themeUrl },
                        onResetPlaybackPreferences = {
                            experiencePreferences.resetTracks(it.ratingKey)
                            android.widget.Toast.makeText(context, "Title preferences reset", android.widget.Toast.LENGTH_SHORT).show()
                        },
                    ) }
                    is CinemaRoute.Detail -> {
                        val detailedMovie = (movieDetail as? MovieDetailUiState.Ready)
                            ?.takeIf { it.movie.ratingKey == route.content.ratingKey }
                        val detailedShow = (showDetail as? ShowDetailUiState.Ready)
                            ?.takeIf { it.show.ratingKey == route.content.ratingKey }
                        DetailScreen(
                            content = detailedMovie?.movie ?: detailedShow?.show ?: route.content,
                            showDetail = showDetail,
                            trailers = detailedMovie?.trailers.orEmpty(),
                            isWatched = currentWatchedState(
                                route.content,
                                state.catalog,
                                showDetail,
                            ),
                            isInMyList = state.catalog.myList.any {
                                it.ratingKey == route.content.ratingKey
                            },
                            isInContinueWatching = state.catalog.continueWatching.any {
                                it.ratingKey == route.content.ratingKey
                            },
                            seriesHasProgress = hasSeriesPlaybackProgress(
                                detailedShow?.show ?: route.content,
                                state.catalog.continueWatching,
                            ),
                            onPlay = { play(it) },
                            onPlayTrailer = { play(it) },
                            onWatchedChanged = { watched ->
                                viewModel.setWatched(route.content, watched)
                            },
                            onToggleMyList = { viewModel.toggleMyList(route.content) },
                            onRemoveFromContinueWatching = {
                                viewModel.removeFromContinueWatching(route.content)
                            },
                            onOpenPerson = ::openPerson,
                            // Episode cards are playback actions. Resolve the
                            // full Plex metadata and enter the player directly
                            // instead of opening a second detail screen.
                            onOpenEpisode = { play(it) },
                            onSeasonSelected = viewModel::selectSeason,
                            offlineDownloads = offlineDownloads,
                            onDownload = { content ->
                                viewModel.downloadForOffline(content) { message ->
                                    android.widget.Toast.makeText(
                                        context,
                                        message,
                                        android.widget.Toast.LENGTH_LONG,
                                    ).show()
                                }
                            },
                            onPlayOffline = { download ->
                                viewModel.playOffline(download) { offlineContent ->
                                    if (offlineContent == null) {
                                        android.widget.Toast.makeText(
                                            context,
                                            "The downloaded file is no longer available.",
                                            android.widget.Toast.LENGTH_LONG,
                                        ).show()
                                    } else {
                                        routes.add(
                                            CinemaRoute.Player(
                                                CinemaPlaybackPlan(mainFeature = offlineContent),
                                            ),
                                        )
                                    }
                                }
                            },
                            onOpenDownloads = { routes.add(CinemaRoute.Downloads) },
                            onBack = {
                                if (routes.size > 1) routes.removeAt(routes.lastIndex)
                            },
                        )
                    }
                    is CinemaRoute.Person -> PersonProfileScreen(
                        state = when (val profileState = personProfile) {
                            is PersonProfileUiState.Ready -> profileState.takeIf {
                                it.profile.personId == route.credit.personId
                            } ?: PersonProfileUiState.Loading(route.credit)
                            is PersonProfileUiState.Error -> profileState.takeIf {
                                it.credit.personId == route.credit.personId
                            } ?: PersonProfileUiState.Loading(route.credit)
                            is PersonProfileUiState.Loading -> profileState
                            PersonProfileUiState.Idle -> PersonProfileUiState.Loading(route.credit)
                        },
                        onBack = {
                            if (routes.size > 1) routes.removeAt(routes.lastIndex)
                        },
                        onOpen = ::open,
                    )
                    is CinemaRoute.Player -> PlayerScreen(
                        content = route.plan.mainFeature,
                        preRollTrailers = route.plan.trailers,
                        bumperUri = route.plan.bumperUri,
                        cinemaModeActive = route.plan.cinemaModeActive && playbackSettings.cinemaLightsEnabled,
                        showMinovaTrailerPreRoll = (
                            route.plan.cinemaModeActive && route.plan.trailers.isNotEmpty()
                            ) || (
                            isTrailerRecordingMode &&
                                route.plan.mainFeature.kind == MediaKind.Movie
                            ),
                        isTrailerRecordingMode = isTrailerRecordingMode,
                        experienceSettings = experience,
                        initialTrackPreference = experiencePreferences.tracks(route.plan.mainFeature.ratingKey),
                        isInPictureInPictureMode = isInPictureInPictureMode,
                        onReturnToDetails = {
                            routes.removeAt(routes.lastIndex)
                            if (routes.lastOrNull() !is CinemaRoute.Detail) open(route.plan.mainFeature)
                        },
                        connection = state.connection,
                        autoplayNextEpisode = playbackSettings.autoplayNextEpisode,
                        inactivityCheckEnabled = playbackSettings.inactivityCheckEnabled,
                        inactivityTimeoutMs = playbackSettings.inactivityTimeoutMs,
                        lastInteractionAtMs = lastPlaybackInteractionAtMs,
                        onUserInteraction = {
                            lastPlaybackInteractionAtMs = SystemClock.elapsedRealtime()
                        },
                        onPlaybackActivityChanged = ambientInactivityTracker::updatePlaybackActivity,
                        onCinemaPlaybackChanged = { playing ->
                            cinemaLightingController.onCinemaPlaybackChanged(playing)
                            tapoLightsViewModel.onPlaybackChanged(playing, route.sessionId)
                        },
                        onAutoplayNextEpisodeChanged = { enabled ->
                            playbackSettings = playbackPreferences.setAutoplayNextEpisode(enabled)
                        },
                        onInactivityTimeout = {
                            if (routes.lastOrNull() is CinemaRoute.Player) {
                                routes.removeAt(routes.lastIndex)
                                viewModel.refresh(silent = true)
                            }
                            // Third-party TV apps cannot power off the device.
                            // Removing the keep-screen-on player and returning
                            // Home lets Android TV's own sleep policy take over.
                            (context as? Activity)?.moveTaskToBack(true)
                        },
                        onProgress = { position, duration, playbackState ->
                            viewModel.reportPlayback(
                                route.plan.mainFeature,
                                position,
                                duration,
                                playbackState,
                            )
                        },
                        onHandoffRequested = { position, duration, onComplete ->
                            viewModel.handoffPlayback(
                                content = route.plan.mainFeature,
                                positionMs = position,
                                durationMs = duration,
                                onComplete = onComplete,
                            )
                        },
                        onHandoffFinished = {
                            if (routes.lastOrNull() is CinemaRoute.Player) {
                                routes.removeAt(routes.lastIndex)
                            }
                            viewModel.refresh(silent = true)
                        },
                        onSubtitleStreamSelected = { subtitleId, onComplete ->
                            val key = route.plan.mainFeature.ratingKey
                            val prior = experiencePreferences.tracks(key) ?: com.minova.cinema.data.local.TitleTrackPreference()
                            experiencePreferences.saveTracks(key, prior.copy(subtitleId = subtitleId, subtitlesOff = subtitleId == null || subtitleId == 0L))
                            viewModel.selectSubtitle(route.plan.mainFeature, subtitleId, onComplete)
                        },
                        onAudioStreamSelected = { audioId, onComplete ->
                            val key = route.plan.mainFeature.ratingKey
                            val prior = experiencePreferences.tracks(key) ?: com.minova.cinema.data.local.TitleTrackPreference()
                            experiencePreferences.saveTracks(key, prior.copy(audioId = audioId))
                            viewModel.selectAudio(route.plan.mainFeature, audioId, onComplete)
                        },
                        initialAudioDelayMs = playbackSettings.audioDelayMs,
                        initialSubtitleDelayMs = playbackSettings.subtitleDelayMs,
                        onAudioDelayChanged = { delayMs ->
                            playbackSettings = playbackPreferences.setAudioDelayMs(delayMs)
                        },
                        onSubtitleDelayChanged = { delayMs ->
                            playbackSettings = playbackPreferences.setSubtitleDelayMs(delayMs)
                        },
                        onDiagnosticsRequested = viewModel::loadPlaybackDiagnostics,
                        onPlaybackEnded = { onReady ->
                            if (route.plan.mainFeature.kind == MediaKind.Movie && experience.endScreen) {
                                if (routes.lastOrNull() is CinemaRoute.Player) routes[routes.lastIndex] = CinemaRoute.Finished(route.plan.mainFeature)
                                viewModel.finishPlaybackAndLoadNext(route.plan.mainFeature) { viewModel.refresh(silent = true) }
                                onReady(null)
                            } else if (route.plan.mainFeature.kind == MediaKind.Extra) {
                                // A trailer behaves like Plex's preview player:
                                // completion returns to the movie rather than
                                // leaving an empty fullscreen player behind.
                                if (routes.lastOrNull() is CinemaRoute.Player) {
                                    routes.removeAt(routes.lastIndex)
                                }
                                onReady(null)
                            } else {
                                viewModel.finishPlaybackAndLoadNext(route.plan.mainFeature) { next ->
                                    if (next == null && routes.lastOrNull() is CinemaRoute.Player) {
                                        routes.removeAt(routes.lastIndex)
                                        viewModel.refresh(silent = true)
                                    }
                                    onReady(next)
                                }
                            }
                        },
                        onPlayNext = ::playNext,
                    )
                    is CinemaRoute.Finished -> MovieFinishedDialog(route.content, state.catalog,
                        onRate = { rating, onComplete -> viewModel.rate(route.content, rating, onComplete) },
                        onOpen = ::open,
                        onHome = { routes.clear(); routes.add(CinemaRoute.Browse) })
                    CinemaRoute.Downloads -> OfflineDownloadsScreen(
                        downloads = offlineDownloads,
                        onBack = {
                            if (routes.size > 1) routes.removeAt(routes.lastIndex)
                        },
                        onPlay = { download ->
                            viewModel.playOffline(download) { offlineContent ->
                                if (offlineContent == null) {
                                    android.widget.Toast.makeText(
                                        context,
                                        "The downloaded file is no longer available.",
                                        android.widget.Toast.LENGTH_LONG,
                                    ).show()
                                } else {
                                    routes.add(
                                        CinemaRoute.Player(
                                            CinemaPlaybackPlan(mainFeature = offlineContent),
                                        ),
                                    )
                                }
                            }
                        },
                        onRetry = { download ->
                            viewModel.retryOfflineDownload(download) { message ->
                                android.widget.Toast.makeText(
                                    context,
                                    message,
                                    android.widget.Toast.LENGTH_LONG,
                                ).show()
                            }
                        },
                        onRemove = viewModel::removeOfflineDownload,
                    )
                    CinemaRoute.Settings -> SettingsScreen(
                        serverUrl = state.connection.baseUrl,
                        autoplayNextEpisode = playbackSettings.autoplayNextEpisode,
                        inactivityCheckEnabled = playbackSettings.inactivityCheckEnabled,
                        inactivityTimeoutMs = playbackSettings.inactivityTimeoutMs,
                        screensaverTimeoutMs = playbackSettings.screensaverTimeoutMs,
                        cinemaModeEnabled = playbackSettings.cinemaModeEnabled,
                        cinemaTrailersEnabled = playbackSettings.cinemaTrailersEnabled,
                        cinemaBumperConfigured = !playbackSettings.cinemaBumperUri.isNullOrBlank(),
                        lightingState = lightingState,
                        tapoLightsState = tapoLightsState,
                        profilesState = profilesState,
                        networkAssistantState = networkAssistantState,
                        onRefresh = {
                            routes.clear()
                            routes.add(CinemaRoute.Browse)
                            viewModel.refresh(silent = true)
                        },
                        onChangeServer = viewModel::changeServer,
                        onAutoplayNextEpisodeChanged = { enabled ->
                            playbackSettings = playbackPreferences.setAutoplayNextEpisode(enabled)
                        },
                        onInactivityCheckChanged = { enabled ->
                            playbackSettings = playbackPreferences.setInactivityCheckEnabled(enabled)
                        },
                        onInactivityTimeoutChanged = { timeout ->
                            playbackSettings = playbackPreferences.setInactivityTimeoutMs(timeout)
                        },
                        onScreensaverTimeoutChanged = { timeout ->
                            playbackSettings = playbackPreferences.setScreensaverTimeoutMs(timeout)
                        },
                        onCinemaModeChanged = { enabled ->
                            playbackSettings = playbackPreferences.setCinemaModeEnabled(enabled)
                        },
                        onCinemaTrailersChanged = { enabled ->
                            playbackSettings = playbackPreferences.setCinemaTrailersEnabled(enabled)
                        },
                        onChooseCinemaBumper = {
                            bumperPicker.launch(arrayOf("video/*"))
                        },
                        onClearCinemaBumper = {
                            playbackSettings = playbackPreferences.setCinemaBumperUri(null)
                        },
                        onRequestHomePermission = cinemaLightingController::requestPermissions,
                        onRefreshLights = cinemaLightingController::refreshLights,
                        onLightAssignmentChanged = cinemaLightingController::setAssigned,
                        onSaveTapoCredentials = tapoLightsViewModel::saveCredentials,
                        onClearTapoCredentials = tapoLightsViewModel::clearCredentials,
                        onDiscoverTapoLights = tapoLightsViewModel::discover,
                        onTapoLightAssignmentChanged = tapoLightsViewModel::setAssigned,
                        onRefreshProfiles = viewModel::refreshProfiles,
                        onSwitchProfile = viewModel::switchProfile,
                        onRunNetworkTest = viewModel::runNetworkAndCodecTest,
                        onRequestTvHomeChannels = viewModel::requestTvHomeChannels,
                        onCustomizeHome = { customizeHome = true },
                        onTestTapoLights = tapoLightsViewModel::testLights,
                        onExperienceSettings = { experienceOpen = true },
                        castServerUrl = castServerUrl,
                        onCastServerUrlChanged = { address ->
                            runCatching { castPreferences.saveServerUrl(address) }
                                .onSuccess { castServerUrl = it }
                                .isSuccess
                        },
                        onBack = {
                            if (routes.size > 1) routes.removeAt(routes.lastIndex)
                        },
                    )
                }
            }
                if (handheld && currentRoute !is CinemaRoute.Player) {
                    MinovaCastMiniController(
                        state = castState,
                        onOpen = { castController?.openExpandedControls(context) },
                        onTogglePlayback = { castController?.togglePlayback() },
                        onStop = { castController?.stop() },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(
                                start = 12.dp,
                                end = 12.dp,
                                bottom = if (currentRoute == CinemaRoute.Browse) 78.dp else 14.dp,
                            ),
                    )
                }
            }
            }
            }
        }
    }
}

private fun Long?.orEmptyDuration(): Long = this?.coerceAtLeast(0L) ?: 0L

@Composable
private fun rememberRoutes() = androidx.compose.runtime.remember {
    mutableStateListOf<CinemaRoute>(CinemaRoute.Browse)
}

/** Resolves the freshest copy because navigation routes intentionally stay immutable. */
private fun currentWatchedState(
    content: MediaContent,
    catalog: CinemaCatalog,
    detail: ShowDetailUiState,
): Boolean {
    val catalogCopy = (
        catalog.movies + catalog.shows + catalog.continueWatching + catalog.myList
        ).firstOrNull { it.ratingKey == content.ratingKey }
    if (catalogCopy != null) return catalogCopy.isWatched

    val detailItems = when (detail) {
        is ShowDetailUiState.Ready -> listOfNotNull(
            detail.show,
            detail.selectedSeason,
            *detail.seasons.toTypedArray(),
            *detail.episodes.toTypedArray(),
        )
        is ShowDetailUiState.Loading -> listOf(detail.show)
        is ShowDetailUiState.Error -> listOf(detail.show)
        ShowDetailUiState.Idle -> emptyList()
    }
    return detailItems.firstOrNull { it.ratingKey == content.ratingKey }?.isWatched
        ?: content.isWatched
}
