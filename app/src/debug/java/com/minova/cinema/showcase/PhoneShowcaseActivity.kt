package com.minova.cinema.showcase

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.minova.cinema.domain.CinemaCatalog
import com.minova.cinema.domain.MediaCollection
import com.minova.cinema.domain.MediaContent
import com.minova.cinema.domain.MediaCredit
import com.minova.cinema.domain.MediaKind
import com.minova.cinema.data.local.ExperienceSettings
import com.minova.cinema.data.local.PlaybackSettings
import com.minova.cinema.home.LightingUiState
import com.minova.cinema.presentation.NetworkAssistantUiState
import com.minova.cinema.presentation.PlexProfilesUiState
import com.minova.cinema.presentation.ShowDetailUiState
import com.minova.cinema.tapo.TapoLightsUiState
import com.minova.cinema.ui.browse.BrowseScreen
import com.minova.cinema.ui.detail.DetailScreen
import com.minova.cinema.ui.experience.ExperienceSettingsDialog
import com.minova.cinema.ui.settings.SettingsScreen
import com.minova.cinema.ui.theme.MinovaCinemaTheme

private enum class PhoneShowcasePage { Browse, Detail, Settings }

/** Debug-only phone smoke surface using the production adaptive composables. */
class PhoneShowcaseActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MinovaCinemaTheme {
                var selected by remember { mutableStateOf<MediaContent?>(null) }
                var page by remember { mutableStateOf(PhoneShowcasePage.Browse) }
                var experienceOpen by remember {
                    mutableStateOf(intent.getBooleanExtra("showAppPreferences", false))
                }
                var experience by remember { mutableStateOf(ExperienceSettings()) }
                val context = LocalContext.current
                var catalog by remember { mutableStateOf(phoneShowcaseCatalog()) }
                var autoplayNextEpisode by remember { mutableStateOf(true) }
                var inactivityCheckEnabled by remember { mutableStateOf(true) }
                var inactivityTimeoutMs by remember { mutableStateOf(3 * 60 * 60 * 1_000L) }
                var screensaverTimeoutMs by remember { mutableStateOf(5 * 60 * 1_000L) }
                var cinemaModeEnabled by remember { mutableStateOf(true) }
                var cinemaTrailersEnabled by remember { mutableStateOf(true) }
                var cinemaBumperConfigured by remember { mutableStateOf(false) }

                fun notify(message: String) {
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                }

                fun updateContent(content: MediaContent) {
                    catalog = catalog.replaceContent(content)
                    if (selected?.ratingKey == content.ratingKey) selected = content
                }

                fun toggleMyList(content: MediaContent) {
                    val saved = catalog.myList.any { it.ratingKey == content.ratingKey }
                    catalog = catalog.copy(
                        myList = if (saved) {
                            catalog.myList.filterNot { it.ratingKey == content.ratingKey }
                        } else {
                            catalog.myList + content
                        },
                    )
                    notify(if (saved) "Removed from Watchlist" else "Added to Watchlist")
                }
                BackHandler(enabled = page != PhoneShowcasePage.Browse) {
                    selected = null
                    page = PhoneShowcasePage.Browse
                }
                val content = selected
                when (page) {
                    PhoneShowcasePage.Browse -> {
                    BrowseScreen(
                        catalog = catalog,
                        onOpen = { selected = it; page = PhoneShowcasePage.Detail },
                        onPlay = { notify("Play selected: ${it.title}") },
                        onToggleMyList = ::toggleMyList,
                        onSettings = { page = PhoneShowcasePage.Settings },
                        onDownloads = { notify("Downloads selected") },
                        onWatchlistRefresh = { notify("Watchlist refreshed") },
                        onDiscover = { notify("Discover selected") },
                        onPlayFromBeginning = { notify("Starting ${it.title} from the beginning") },
                        onSetWatched = { item, watched ->
                            updateContent(item.copy(isWatched = watched))
                            notify(if (watched) "Marked watched" else "Marked unwatched")
                        },
                        onRefresh = { notify("Library refreshed") },
                        onResetPlaybackPreferences = { notify("Playback preferences reset") },
                        loadCollectionMembers = { catalog.movies.take(4) },
                    )
                    }
                    PhoneShowcasePage.Detail -> if (content != null) {
                    DetailScreen(
                        content = content,
                        showDetail = ShowDetailUiState.Idle,
                        trailers = emptyList(),
                        isWatched = content.isWatched,
                        isInMyList = catalog.myList.any { it.ratingKey == content.ratingKey },
                        isInContinueWatching = catalog.continueWatching.any { it.ratingKey == content.ratingKey },
                        onPlay = { notify("Play selected: ${content.title}") },
                        onPlayTrailer = { notify("Trailer selected") },
                        onWatchedChanged = { watched ->
                            updateContent(content.copy(isWatched = watched))
                            notify(if (watched) "Marked watched" else "Marked unwatched")
                        },
                        onToggleMyList = { toggleMyList(content) },
                        onRemoveFromContinueWatching = {
                            catalog = catalog.copy(
                                continueWatching = catalog.continueWatching.filterNot {
                                    it.ratingKey == content.ratingKey
                                },
                            )
                            notify("Removed from Continue Watching")
                        },
                        onOpenEpisode = { notify("Episode selected: ${it.title}") },
                        onSeasonSelected = { notify("Season selected: ${it.title}") },
                        onBack = { selected = null; page = PhoneShowcasePage.Browse },
                    )
                    }
                    PhoneShowcasePage.Settings -> SettingsScreen(
                        serverUrl = "http://192.168.1.10:32400",
                        autoplayNextEpisode = autoplayNextEpisode,
                        inactivityCheckEnabled = inactivityCheckEnabled,
                        inactivityTimeoutMs = inactivityTimeoutMs,
                        screensaverTimeoutMs = screensaverTimeoutMs,
                        cinemaModeEnabled = cinemaModeEnabled,
                        cinemaTrailersEnabled = cinemaTrailersEnabled,
                        cinemaBumperConfigured = cinemaBumperConfigured,
                        lightingState = LightingUiState(message = "Google Home is ready when enabled in a release build."),
                        tapoLightsState = TapoLightsUiState(),
                        profilesState = PlexProfilesUiState.Ready(emptyList()),
                        networkAssistantState = NetworkAssistantUiState.Idle,
                        onRefresh = { notify("Library refreshed") },
                        onChangeServer = { notify("Change server selected") },
                        onAutoplayNextEpisodeChanged = { autoplayNextEpisode = it },
                        onInactivityCheckChanged = { inactivityCheckEnabled = it },
                        onInactivityTimeoutChanged = { inactivityTimeoutMs = it },
                        onScreensaverTimeoutChanged = { screensaverTimeoutMs = it },
                        onCinemaModeChanged = { cinemaModeEnabled = it },
                        onCinemaTrailersChanged = { cinemaTrailersEnabled = it },
                        onChooseCinemaBumper = {
                            cinemaBumperConfigured = true
                            notify("Bumper selected")
                        },
                        onClearCinemaBumper = { cinemaBumperConfigured = false },
                        onRequestHomePermission = { notify("Home permission selected") },
                        onRefreshLights = { notify("Lights refreshed") },
                        onLightAssignmentChanged = { _, _ -> notify("Light assignment changed") },
                        onSaveTapoCredentials = { _, _ -> notify("Tapo credentials saved") },
                        onClearTapoCredentials = { notify("Tapo credentials cleared") },
                        onDiscoverTapoLights = { notify("Discovering Tapo lights") },
                        onTapoLightAssignmentChanged = { _, _ -> notify("Tapo light assignment changed") },
                        onRefreshProfiles = { notify("Profiles refreshed") },
                        onSwitchProfile = { profile, _ -> notify("Profile selected: ${profile.title}") },
                        onRunNetworkTest = { notify("Network test started") },
                        onRequestTvHomeChannels = { notify("TV Home channels selected") },
                        onCustomizeHome = { notify("Customize Home selected") },
                        onCastServerUrlChanged = {
                            notify("TV playback address saved")
                            true
                        },
                        onExperienceSettings = { experienceOpen = true },
                        onBack = { page = PhoneShowcasePage.Browse },
                    )
                }
                if (experienceOpen) {
                    ExperienceSettingsDialog(
                        settings = experience,
                        onChange = { experience = it },
                        onPreset = {},
                        onRestoreLights = {},
                        onClose = { experienceOpen = false },
                        playback = PlaybackSettings(),
                        showCinemaControls = false,
                    )
                }
            }
        }
    }
}

private fun CinemaCatalog.replaceContent(updated: MediaContent): CinemaCatalog {
    fun List<MediaContent>.replace(): List<MediaContent> = map {
        if (it.ratingKey == updated.ratingKey) updated else it
    }
    return copy(
        movies = movies.replace(),
        shows = shows.replace(),
        continueWatching = continueWatching.replace(),
        myList = myList.replace(),
    )
}

private fun phoneShowcaseCatalog(): CinemaCatalog {
    val titles = listOf(
        "Beyond the Horizon",
        "Neon Divide",
        "The Last Broadcast",
        "Lumen",
        "Silent Tide",
        "Cinder Line",
        "Fractured Orbit",
        "New Dawn",
    ).mapIndexed { index, title ->
        MediaContent(
            ratingKey = "phone-$index",
            title = title,
            secondaryTitle = if (index % 3 == 0) "Minova Original" else null,
            summary = "A cinematic Plex library story presented through Minova Cinema's adaptive phone experience.",
            tagline = if (index == 0) "The edge of everything is only the beginning." else null,
            year = 2026 - index % 3,
            durationMs = (102L + index * 4L) * 60_000L,
            viewOffsetMs = if (index < 2) (18L + index * 11L) * 60_000L else 0L,
            posterUrl = null,
            backdropUrl = null,
            contentRating = "PG-13",
            kind = if (index == 5) MediaKind.Show else MediaKind.Movie,
            genres = listOf(if (index % 2 == 0) "Science Fiction" else "Drama", "Adventure"),
            isWatched = index == 3,
            credits = listOf(
                MediaCredit("Avery Stone", "Director", null),
                MediaCredit("Mara Chen", "Lead", null),
            ),
            audienceRating = 8.2 - index * 0.2,
            collections = if (index < 4) listOf("Minova Premieres") else emptyList(),
        )
    }
    return CinemaCatalog(
        serverName = "Phone preview",
        movies = titles.filter { it.kind == MediaKind.Movie },
        shows = titles.filter { it.kind == MediaKind.Show },
        continueWatching = titles.take(2),
        myList = titles.takeLast(3),
        collections = listOf(MediaCollection("collection-1", "Minova Premieres", null, "Movies", 4)),
    )
}
