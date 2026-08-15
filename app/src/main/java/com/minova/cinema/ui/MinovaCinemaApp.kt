package com.minova.cinema.ui

import android.app.Activity
import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.minova.cinema.domain.MediaContent
import com.minova.cinema.domain.MediaKind
import com.minova.cinema.domain.CinemaCatalog
import com.minova.cinema.data.local.PlaybackPreferences
import com.minova.cinema.presentation.CinemaUiState
import com.minova.cinema.presentation.CinemaViewModel
import com.minova.cinema.presentation.ShowDetailUiState
import com.minova.cinema.presentation.MovieDetailUiState
import com.minova.cinema.update.UpdateUiState
import com.minova.cinema.update.UpdateViewModel
import com.minova.cinema.update.UpdateInstaller
import com.minova.cinema.ui.browse.BrowseScreen
import com.minova.cinema.ui.ambient.AmbientInactivityTracker
import com.minova.cinema.ui.common.ConnectionErrorScreen
import com.minova.cinema.ui.common.LoadingScreen
import com.minova.cinema.ui.detail.DetailScreen
import com.minova.cinema.ui.intro.AnimatedIntroScreen
import com.minova.cinema.ui.onboarding.OnboardingScreen
import com.minova.cinema.ui.player.PlayerScreen
import com.minova.cinema.ui.settings.SettingsScreen
import com.minova.cinema.ui.update.UpdateAvailableDialog
import com.minova.cinema.ui.update.UpdateDownloadDialog

private sealed interface CinemaRoute {
    data object Browse : CinemaRoute
    data class Detail(val content: MediaContent) : CinemaRoute
    data class Player(val content: MediaContent) : CinemaRoute
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
            MainScreen(viewModel, ambientInactivityTracker)

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
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val showDetail by viewModel.showDetail.collectAsStateWithLifecycle()
    val movieDetail by viewModel.movieDetail.collectAsStateWithLifecycle()
    val routes = rememberRoutes()
    val playbackPreferences = remember(context.applicationContext) {
        PlaybackPreferences(context.applicationContext)
    }
    var playbackSettings by remember { mutableStateOf(playbackPreferences.read()) }
    var lastPlaybackInteractionAtMs by remember {
        mutableLongStateOf(SystemClock.elapsedRealtime())
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

            fun play(content: MediaContent) {
                viewModel.resolvePlayable(content) { playable ->
                    if (playable != null) {
                        lastPlaybackInteractionAtMs = SystemClock.elapsedRealtime()
                        routes.add(CinemaRoute.Player(playable))
                    }
                }
            }

            fun playNext(content: MediaContent) {
                viewModel.resolvePlayable(content) { playable ->
                    if (playable != null) {
                        routes[routes.lastIndex] = CinemaRoute.Player(playable)
                    }
                }
            }

            AnimatedContent(
                targetState = currentRoute,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "cinema_navigation",
            ) { route ->
                when (route) {
                    CinemaRoute.Browse -> BrowseScreen(
                        catalog = state.catalog,
                        onOpen = ::open,
                        onSettings = { routes.add(CinemaRoute.Settings) },
                    )
                    is CinemaRoute.Detail -> {
                        val detailedMovie = (movieDetail as? MovieDetailUiState.Ready)
                            ?.takeIf { it.movie.ratingKey == route.content.ratingKey }
                        DetailScreen(
                            content = detailedMovie?.movie ?: route.content,
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
                            onPlay = ::play,
                            onPlayTrailer = ::play,
                            onWatchedChanged = { watched ->
                                viewModel.setWatched(route.content, watched)
                            },
                            onToggleMyList = { viewModel.toggleMyList(route.content) },
                            onRemoveFromContinueWatching = {
                                viewModel.removeFromContinueWatching(route.content)
                            },
                            // Episode cards are playback actions. Resolve the
                            // full Plex metadata and enter the player directly
                            // instead of opening a second detail screen.
                            onOpenEpisode = ::play,
                            onSeasonSelected = viewModel::selectSeason,
                        )
                    }
                    is CinemaRoute.Player -> PlayerScreen(
                        content = route.content,
                        connection = state.connection,
                        autoplayNextEpisode = playbackSettings.autoplayNextEpisode,
                        inactivityCheckEnabled = playbackSettings.inactivityCheckEnabled,
                        lastInteractionAtMs = lastPlaybackInteractionAtMs,
                        onUserInteraction = {
                            lastPlaybackInteractionAtMs = SystemClock.elapsedRealtime()
                        },
                        onPlaybackActivityChanged = ambientInactivityTracker::updatePlaybackActivity,
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
                                route.content,
                                position,
                                duration,
                                playbackState,
                            )
                        },
                        onSubtitleStreamSelected = { subtitleId, onComplete ->
                            viewModel.selectSubtitle(route.content, subtitleId, onComplete)
                        },
                        onAudioStreamSelected = { audioId, onComplete ->
                            viewModel.selectAudio(route.content, audioId, onComplete)
                        },
                        onPlaybackEnded = { onReady ->
                            if (route.content.kind == MediaKind.Extra) {
                                // A trailer behaves like Plex's preview player:
                                // completion returns to the movie rather than
                                // leaving an empty fullscreen player behind.
                                if (routes.lastOrNull() is CinemaRoute.Player) {
                                    routes.removeAt(routes.lastIndex)
                                }
                                onReady(null)
                            } else {
                                viewModel.finishPlaybackAndLoadNext(route.content, onReady)
                            }
                        },
                        onPlayNext = ::playNext,
                    )
                    CinemaRoute.Settings -> SettingsScreen(
                        serverUrl = state.connection.baseUrl,
                        autoplayNextEpisode = playbackSettings.autoplayNextEpisode,
                        inactivityCheckEnabled = playbackSettings.inactivityCheckEnabled,
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
                    )
                }
            }
        }
    }
}

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
