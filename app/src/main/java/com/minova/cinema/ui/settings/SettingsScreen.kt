package com.minova.cinema.ui.settings

import com.minova.cinema.ui.experience.accessibleFocus
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.NetworkCheck
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.minova.cinema.R
import com.minova.cinema.BuildConfig
import com.minova.cinema.home.LightingUiState
import com.minova.cinema.tapo.TapoLightsUiState
import com.minova.cinema.domain.PlexHomeProfile
import com.minova.cinema.presentation.PlexProfilesUiState
import com.minova.cinema.presentation.NetworkAssistantUiState
import com.minova.cinema.ui.theme.MinovaCyan
import com.minova.cinema.ui.theme.MinovaMuted
import com.minova.cinema.ui.theme.MinovaNightDeep
import kotlin.math.abs
import com.minova.cinema.ui.platform.DeviceProfile
import com.minova.cinema.ui.platform.rememberDeviceProfile

@Composable
fun SettingsScreen(
    serverUrl: String,
    autoplayNextEpisode: Boolean,
    inactivityCheckEnabled: Boolean,
    inactivityTimeoutMs: Long,
    screensaverTimeoutMs: Long,
    cinemaModeEnabled: Boolean,
    cinemaTrailersEnabled: Boolean,
    cinemaBumperConfigured: Boolean,
    lightingState: LightingUiState,
    tapoLightsState: TapoLightsUiState,
    profilesState: PlexProfilesUiState,
    networkAssistantState: NetworkAssistantUiState,
    onRefresh: () -> Unit,
    onChangeServer: () -> Unit,
    onAutoplayNextEpisodeChanged: (Boolean) -> Unit,
    onInactivityCheckChanged: (Boolean) -> Unit,
    onInactivityTimeoutChanged: (Long) -> Unit,
    onScreensaverTimeoutChanged: (Long) -> Unit,
    onCinemaModeChanged: (Boolean) -> Unit,
    onCinemaTrailersChanged: (Boolean) -> Unit,
    onChooseCinemaBumper: () -> Unit,
    onClearCinemaBumper: () -> Unit,
    onRequestHomePermission: () -> Unit,
    onRefreshLights: () -> Unit,
    onLightAssignmentChanged: (String, Boolean) -> Unit,
    onSaveTapoCredentials: (String, String) -> Unit,
    onClearTapoCredentials: () -> Unit,
    onDiscoverTapoLights: () -> Unit,
    onTapoLightAssignmentChanged: (String, Boolean) -> Unit,
    onRefreshProfiles: () -> Unit,
    onSwitchProfile: (PlexHomeProfile, String?) -> Unit,
    onRunNetworkTest: () -> Unit,
    onRequestTvHomeChannels: () -> Unit,
    onCustomizeHome: () -> Unit = {},
    onTestTapoLights: () -> Unit = {},
    onExperienceSettings: () -> Unit = {},
    onBack: () -> Unit = {},
) {
    val handheld = rememberDeviceProfile() == DeviceProfile.Handheld
    var pinProfile by remember { mutableStateOf<PlexHomeProfile?>(null) }
    var pin by remember { mutableStateOf("") }
    var timerDialog by remember { mutableStateOf<TimerSettingType?>(null) }
    var savedMessage by remember { mutableStateOf<String?>(null) }
    var activeSection by remember { mutableStateOf(SettingsSection.Plex) }
    var contentFocusActive by remember { mutableStateOf(false) }
    val sectionEntryFocus = remember { SettingsSection.entries.associateWith { FocusRequester() } }
    val sectionRailFocus = remember { SettingsSection.entries.associateWith { FocusRequester() } }

    fun entryModifier(section: SettingsSection): Modifier = Modifier
        .focusRequester(sectionEntryFocus.getValue(section))
        .focusProperties { left = sectionRailFocus.getValue(section) }
        .onFocusChanged { if (it.isFocused) contentFocusActive = true }

    BackHandler(enabled = !handheld && contentFocusActive) {
        sectionRailFocus.getValue(activeSection).requestFocus()
        contentFocusActive = false
    }

    LaunchedEffect(savedMessage) {
        if (savedMessage != null) {
            kotlinx.coroutines.delay(1_800)
            savedMessage = null
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF05090E),
                        MinovaNightDeep,
                        Color(0xFF071820),
                    ),
                ),
            ),
    ) {
        Column(
            Modifier.fillMaxSize().then(
                if (handheld) Modifier.statusBarsPadding().navigationBarsPadding() else Modifier,
            ),
        ) {
            if (handheld) {
                MobileSettingsNavigation(
                    activeSection = activeSection,
                    onBack = onBack,
                    onSectionSelected = { section ->
                        activeSection = section
                        contentFocusActive = false
                    },
                )
            }
            Row(Modifier.fillMaxWidth().weight(1f)) {
                if (!handheld) {
                    SettingsNavigationRail(
                        activeSection = activeSection,
                        railFocusRequesters = sectionRailFocus,
                        compact = false,
                        onSectionSelected = { section ->
                            activeSection = section
                            contentFocusActive = false
                        },
                        onEnterSection = { section ->
                            activeSection = section
                            contentFocusActive = true
                            sectionEntryFocus.getValue(section).requestFocus()
                        },
                    )
                }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            start = if (handheld) 16.dp else 34.dp,
                            top = if (handheld) 16.dp else 26.dp,
                            end = if (handheld) 16.dp else 48.dp,
                            bottom = 12.dp,
                        ),
                ) {
                    SettingsPageHeader(activeSection, compact = handheld)
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(
                        start = if (handheld) 12.dp else 34.dp,
                        top = 6.dp,
                        end = if (handheld) 12.dp else 48.dp,
                        bottom = 56.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                when (activeSection) {
                SettingsSection.Plex -> item {
            SettingsCard(title = "Plex server", subtitle = "Connection and library") {
                Text(
                    "CONNECTED SERVER",
                    style = MaterialTheme.typography.bodySmall,
                    color = MinovaMuted,
                    modifier = Modifier.padding(top = 16.dp),
                )
                Text(
                    serverUrl,
                    style = MaterialTheme.typography.titleLarge,
                    color = MinovaCyan,
                    modifier = Modifier.padding(top = 4.dp),
                )
                if (handheld) {
                    Column(
                        Modifier.fillMaxWidth().padding(top = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        SettingsPrimaryButton(
                            onClick = onRefresh,
                            modifier = entryModifier(SettingsSection.Plex).fillMaxWidth()
                                .testTag("settings-refresh-library"),
                        ) { Text("Refresh library") }
                        SettingsSecondaryButton(onClick = onChangeServer, modifier = Modifier.fillMaxWidth()) { Text("Change server") }
                    }
                } else {
                    Row(
                        modifier = Modifier.padding(top = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        SettingsPrimaryButton(
                            onClick = onRefresh,
                            modifier = entryModifier(SettingsSection.Plex).testTag("settings-refresh-library"),
                        ) { Text("Refresh library") }
                        SettingsSecondaryButton(onClick = onChangeServer) { Text("Change server") }
                        SettingsSecondaryButton(onClick = onRequestTvHomeChannels) { Text("Add TV Home channels") }
                    }
                }
            }
            }

                SettingsSection.Profiles -> item {
            val profiles = when (profilesState) {
                is PlexProfilesUiState.Ready -> profilesState.profiles
                is PlexProfilesUiState.Switching -> profilesState.profiles
                is PlexProfilesUiState.Error -> profilesState.profiles
                PlexProfilesUiState.Loading -> emptyList()
            }
            SettingsCard(title = "Plex Home", subtitle = "Profiles, managed users and PIN protection") {
                if (profiles.isNotEmpty()) {
                    if (handheld) {
                        Column(
                            Modifier.fillMaxWidth().padding(top = 14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            profiles.forEachIndexed { index, profile ->
                                PlaybackToggleButton(
                                    title = profile.title,
                                    description = buildString {
                                        append(if (profile.isManaged) "Managed user" else if (profile.isAdmin) "Home admin" else "Home member")
                                        if (profile.isProtected) append(" · PIN")
                                    },
                                    checked = profile.isActive,
                                    onClick = {
                                        if (profile.isProtected) {
                                            pin = ""
                                            pinProfile = profile
                                        } else onSwitchProfile(profile, null)
                                    },
                                    modifier = Modifier.fillMaxWidth().then(
                                        if (index == 0) entryModifier(SettingsSection.Profiles) else Modifier,
                                    ),
                                )
                            }
                        }
                    } else {
                        LazyRow(
                            modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            itemsIndexed(
                                items = profiles,
                                key = { _, profile -> profile.uuid },
                            ) { index, profile ->
                        PlaybackToggleButton(
                            title = profile.title,
                            description = buildString {
                                append(if (profile.isManaged) "Managed user" else if (profile.isAdmin) "Home admin" else "Home member")
                                if (profile.isProtected) append(" · PIN")
                            },
                            checked = profile.isActive,
                            onClick = {
                                if (profile.isProtected) {
                                    pin = ""
                                    pinProfile = profile
                                } else onSwitchProfile(profile, null)
                            },
                            modifier = Modifier
                                .width(260.dp)
                                .then(
                                    if (index == 0) entryModifier(SettingsSection.Profiles)
                                    else Modifier,
                                ),
                        )
                            }
                        }
                    }
                } else {
                    Text(
                        if (profilesState is PlexProfilesUiState.Loading) "Loading Plex Home…" else "No Plex Home profiles found.",
                        color = MinovaMuted,
                        modifier = Modifier.padding(top = 14.dp, bottom = 6.dp),
                    )
                }
                SettingsSecondaryButton(
                    onClick = onRefreshProfiles,
                    modifier = Modifier
                        .padding(top = 12.dp)
                        .then(
                            if (profiles.isEmpty()) entryModifier(SettingsSection.Profiles)
                            else Modifier,
                        )
                        .testTag("settings-refresh-profiles"),
                ) { Text("Refresh profiles") }
                when (profilesState) {
                    is PlexProfilesUiState.Error -> Text(
                        readableSettingsError(
                            profilesState.message,
                            fallback = "Plex Home profiles are temporarily unavailable. Your current profile remains active.",
                        ),
                        color = Color(0xFFFFB4AB),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier
                            .padding(top = 12.dp)
                            .background(Color(0xFFFFB4AB).copy(alpha = 0.08f), RoundedCornerShape(10.dp))
                            .padding(horizontal = 12.dp, vertical = 9.dp),
                    )
                    is PlexProfilesUiState.Switching -> Text(
                        "Switching profile…",
                        color = MinovaCyan,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                    else -> Unit
                }
            }
        }

                SettingsSection.Playback -> item {
            SettingsSecondaryButton(onClick = onCustomizeHome, modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                Text("Customize home screen · shelves & opening tab")
            }
            SettingsSecondaryButton(onClick = onExperienceSettings, modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                Text(
                    if (handheld) "App preferences · language, subtitles & accessibility"
                    else "Cinema preferences · languages, accessibility, lights & storage",
                )
            }
            SettingsCard(
                title = "Playback",
                subtitle = "Autoplay and viewing safeguards",
            ) {
                Box(Modifier.fillMaxWidth().padding(top = 14.dp)) {
                    ResponsiveSettingsPair(
                        handheld = handheld,
                        first = { itemModifier ->
                    PlaybackToggleButton(
                        title = "Autoplay next episode",
                        description = "Start after the 10-second Next Up countdown",
                        checked = autoplayNextEpisode,
                        onClick = {
                            onAutoplayNextEpisodeChanged(!autoplayNextEpisode)
                            savedMessage = "Autoplay preference saved"
                        },
                        modifier = entryModifier(SettingsSection.Playback).then(itemModifier),
                    )
                        },
                        second = { itemModifier ->
                    PlaybackToggleButton(
                        title = "Continue watching check",
                        description = "Ask before ending a long inactive session",
                        checked = inactivityCheckEnabled,
                        onClick = {
                            onInactivityCheckChanged(!inactivityCheckEnabled)
                            savedMessage = "Continue watching check saved"
                        },
                        modifier = itemModifier,
                    )
                        },
                    )
                }
                Text(
                    if (handheld) "TIMERS · TAP TO ADJUST" else "TIMERS · PRESS OK TO ADJUST",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MinovaMuted,
                    modifier = Modifier.padding(top = 20.dp, bottom = 9.dp),
                )
                ResponsiveSettingsPair(
                    handheld = handheld,
                    first = { itemModifier ->
                    TimerSettingButton(
                        title = "Ambient screensaver",
                        value = screensaverTimeoutMs,
                        presets = SCREENSAVER_PRESETS,
                        formattedValue = formatMinutes(screensaverTimeoutMs),
                        description = "Idle time before the bouncing Minova logo appears",
                        onClick = { timerDialog = TimerSettingType.AmbientScreensaver },
                        modifier = itemModifier,
                    )
                    },
                    second = { itemModifier ->
                    TimerSettingButton(
                        title = "Playback sleep check",
                        value = inactivityTimeoutMs,
                        presets = SLEEP_PRESETS,
                        formattedValue = formatHours(inactivityTimeoutMs),
                        description = "Time before the 30-second Continue watching? prompt",
                        onClick = { timerDialog = TimerSettingType.PlaybackSleepCheck },
                        enabled = inactivityCheckEnabled,
                        modifier = itemModifier,
                    )
                    },
                )
            }
        }

                SettingsSection.Network -> item {
            val artwork = com.minova.cinema.ui.browse.ArtworkMetrics.snapshot()
            val loaded = artwork.filter { it.success }
            val p95 = loaded.map { it.elapsedMs }.sorted().let { times ->
                times.getOrNull(((times.size - 1) * 0.95).toInt()) ?: 0L
            }
            Text("Artwork · ${loaded.size} loaded · ${loaded.count { it.source == "MEMORY_CACHE" }} memory hits · " +
                "${artwork.count { !it.success }} errors · p95 ${p95}ms", color = MinovaMuted,
                modifier = Modifier.padding(bottom = 12.dp))
            SettingsCard(
                title = "Network & codec assistant",
                subtitle = "Test this Plex connection and inspect this device's decoders",
            ) {
                when (networkAssistantState) {
                    NetworkAssistantUiState.Idle -> Text(
                        "Run a short local transfer test for an automatic quality recommendation.",
                        color = MinovaMuted,
                        modifier = Modifier.padding(top = 16.dp),
                    )
                    NetworkAssistantUiState.Testing -> Text(
                        "Testing the Plex connection…",
                        color = MinovaCyan,
                        modifier = Modifier.padding(top = 16.dp),
                    )
                    is NetworkAssistantUiState.Ready -> {
                        Text(
                            "${networkAssistantState.report.speedMbps} Mbps · ${networkAssistantState.report.recommendation}",
                            color = MinovaCyan,
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.padding(top = 16.dp),
                        )
                        Text(
                            "${networkAssistantState.report.tvSummary}\nDecoders: ${networkAssistantState.report.supportedVideoCodecs.joinToString()}",
                            color = MinovaMuted,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                    is NetworkAssistantUiState.Error -> Text(
                        readableSettingsError(
                            networkAssistantState.message,
                            fallback = "The connection test could not finish. Check the Plex server and try again.",
                        ),
                        color = Color(0xFFFFB4AB),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier
                            .padding(top = 16.dp)
                            .background(Color(0xFFFFB4AB).copy(alpha = 0.08f), RoundedCornerShape(10.dp))
                            .padding(horizontal = 12.dp, vertical = 9.dp),
                    )
                }
                SettingsPrimaryButton(
                    onClick = onRunNetworkTest,
                    enabled = networkAssistantState !is NetworkAssistantUiState.Testing,
                    modifier = entryModifier(SettingsSection.Network).padding(top = 16.dp),
                ) { Text(if (networkAssistantState is NetworkAssistantUiState.Testing) "Testing…" else "Run connection test") }
            }
        }

                SettingsSection.Cinema -> item {
            SettingsCard(
                title = "Cinema Mode",
                subtitle = "Trailers, local bumper and theater lighting",
            ) {
                Box(Modifier.fillMaxWidth().padding(top = 14.dp)) {
                    ResponsiveSettingsPair(
                        handheld = handheld,
                        first = { itemModifier ->
                    PlaybackToggleButton(
                        title = "Cinema Mode",
                        description = "Run the pre-show and assigned theater lights",
                        checked = cinemaModeEnabled,
                        onClick = {
                            onCinemaModeChanged(!cinemaModeEnabled)
                            savedMessage = "Cinema Mode saved"
                        },
                        modifier = entryModifier(SettingsSection.Cinema).then(itemModifier),
                    )
                        },
                        second = { itemModifier ->
                    PlaybackToggleButton(
                        title = "Play trailers",
                        description = "Minova presentation, then up to two unwatched Plex movie trailers",
                        checked = cinemaTrailersEnabled,
                        onClick = {
                            onCinemaTrailersChanged(!cinemaTrailersEnabled)
                            savedMessage = "Trailer preference saved"
                        },
                        modifier = itemModifier,
                    )
                        },
                    )
                }
                if (handheld) {
                    Column(
                        Modifier.fillMaxWidth().padding(top = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        ValueButton(
                            title = "Local Atmos bumper",
                            value = if (cinemaBumperConfigured) "Selected" else "Not selected",
                            description = "Choose the local video played before the feature",
                            onClick = onChooseCinemaBumper,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        if (cinemaBumperConfigured) {
                            SettingsSecondaryButton(
                                onClick = {
                                    onClearCinemaBumper()
                                    savedMessage = "Cinema bumper cleared"
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text("Clear bumper") }
                        }
                    }
                } else Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ValueButton(
                        title = "Local Atmos bumper",
                        value = if (cinemaBumperConfigured) "Selected" else "Not selected",
                        description = "Choose the local video played before the feature",
                        onClick = onChooseCinemaBumper,
                        modifier = Modifier.weight(1f),
                    )
                    if (cinemaBumperConfigured) {
                        SettingsSecondaryButton(onClick = {
                            onClearCinemaBumper()
                            savedMessage = "Cinema bumper cleared"
                        }) {
                            Text("Clear bumper")
                        }
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }

                SettingsSection.Lights -> item {
            SettingsCard(
                title = "Cinema lights",
                subtitle = "Choose only the lights Minova may control",
            ) {
                Spacer(Modifier.height(14.dp))
                TheaterLightsSection(
                    lightingState = lightingState,
                    onRequestHomePermission = onRequestHomePermission,
                    onRefreshLights = onRefreshLights,
                    onLightAssignmentChanged = { id, assigned ->
                        onLightAssignmentChanged(id, assigned)
                        savedMessage = "Cinema light selection saved"
                    },
                )
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 20.dp)
                        .height(1.dp)
                        .background(Color.White.copy(alpha = 0.12f)),
                )
                TapoCinemaLightsSection(
                    state = tapoLightsState,
                    onSaveCredentials = onSaveTapoCredentials,
                    onClearCredentials = onClearTapoCredentials,
                    onDiscover = onDiscoverTapoLights,
                    onTestLights = onTestTapoLights,
                    onAssignmentChanged = { ip, assigned ->
                        onTapoLightAssignmentChanged(ip, assigned)
                        savedMessage = "Tapo light selection saved"
                    },
                    entryModifier = entryModifier(SettingsSection.Lights),
                )
            }
                }
                }
            }
            }
        }
        }
        savedMessage?.let { message ->
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 48.dp, bottom = 26.dp)
                    .background(Color(0xFF17313A), RoundedCornerShape(10.dp))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            ) {
                Text("✓  $message", color = Color.White, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }

    pinProfile?.let { profile ->
        PlexPinDialog(
            profile = profile,
            pin = pin,
            onPinChanged = { pin = it.filter(Char::isDigit).take(4) },
            onConfirm = {
                onSwitchProfile(profile, pin)
                pinProfile = null
            },
            onDismiss = { pinProfile = null },
        )
    }

    timerDialog?.let { type ->
        when (type) {
            TimerSettingType.AmbientScreensaver -> TimerPresetDialog(
                title = "Ambient screensaver",
                description = "Choose how long Minova can sit idle before the OLED-friendly bouncing logo appears.",
                value = screensaverTimeoutMs,
                presets = SCREENSAVER_PRESETS,
                formattedValue = ::formatMinutes,
                onValueChanged = onScreensaverTimeoutChanged,
                onDismiss = {
                    timerDialog = null
                    savedMessage = "Screensaver timer saved"
                },
            )
            TimerSettingType.PlaybackSleepCheck -> TimerPresetDialog(
                title = "Playback sleep check",
                description = "Choose when Minova asks whether you are still watching during a long playback session.",
                value = inactivityTimeoutMs,
                presets = SLEEP_PRESETS,
                formattedValue = ::formatHours,
                onValueChanged = onInactivityTimeoutChanged,
                onDismiss = {
                    timerDialog = null
                    savedMessage = "Sleep check timer saved"
                },
            )
        }
    }
}

private enum class TimerSettingType { AmbientScreensaver, PlaybackSleepCheck }

@Composable
internal fun SettingsPrimaryButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    if (rememberDeviceProfile() == DeviceProfile.Handheld) {
        androidx.compose.material3.Button(
            onClick = onClick,
            modifier = modifier.heightIn(min = 52.dp),
            enabled = enabled,
            shape = RoundedCornerShape(12.dp),
            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                containerColor = Color(0xFF26313D),
                contentColor = Color.White,
                disabledContainerColor = Color(0xFF26313D).copy(alpha = 0.45f),
                disabledContentColor = Color.White.copy(alpha = 0.45f),
            ),
            content = content,
        )
        return
    }
    Button(
        onClick = onClick,
        modifier = modifier.accessibleFocus(),
        enabled = enabled,
        shape = ButtonDefaults.shape(
            shape = RoundedCornerShape(12.dp),
            focusedShape = RoundedCornerShape(12.dp),
            pressedShape = RoundedCornerShape(12.dp),
            disabledShape = RoundedCornerShape(12.dp),
            focusedDisabledShape = RoundedCornerShape(12.dp),
        ),
        colors = ButtonDefaults.colors(
            containerColor = Color(0xFF26313D),
            contentColor = Color.White,
            focusedContainerColor = MinovaCyan,
            focusedContentColor = Color(0xFF001419),
        ),
        content = content,
    )
}

@Composable
internal fun SettingsSecondaryButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    if (rememberDeviceProfile() == DeviceProfile.Handheld) {
        androidx.compose.material3.Button(
            onClick = onClick,
            modifier = modifier.heightIn(min = 52.dp),
            enabled = enabled,
            shape = RoundedCornerShape(12.dp),
            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                containerColor = Color(0xFF121C25),
                contentColor = Color.White,
                disabledContainerColor = Color(0xFF121C25).copy(alpha = 0.45f),
                disabledContentColor = Color.White.copy(alpha = 0.45f),
            ),
            content = content,
        )
        return
    }
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.accessibleFocus(),
        enabled = enabled,
        // The default outlined border keeps its capsule shape even when the
        // container is rectangular. Use the same clean surface in every state.
        border = ButtonDefaults.border(
            border = androidx.tv.material3.Border.None,
            focusedBorder = androidx.tv.material3.Border.None,
            pressedBorder = androidx.tv.material3.Border.None,
            disabledBorder = androidx.tv.material3.Border.None,
            focusedDisabledBorder = androidx.tv.material3.Border.None,
        ),
        shape = ButtonDefaults.shape(
            shape = RoundedCornerShape(12.dp),
            focusedShape = RoundedCornerShape(12.dp),
            pressedShape = RoundedCornerShape(12.dp),
            disabledShape = RoundedCornerShape(12.dp),
            focusedDisabledShape = RoundedCornerShape(12.dp),
        ),
        colors = ButtonDefaults.colors(
            containerColor = Color(0xFF121C25),
            contentColor = Color.White,
            focusedContainerColor = Color.White,
            focusedContentColor = Color(0xFF071017),
        ),
        content = content,
    )
}

@Composable
private fun ResponsiveSettingsPair(
    handheld: Boolean,
    first: @Composable (Modifier) -> Unit,
    second: @Composable (Modifier) -> Unit,
) {
    if (handheld) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            first(Modifier.fillMaxWidth())
            second(Modifier.fillMaxWidth())
        }
    } else {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            first(Modifier.weight(1f))
            second(Modifier.weight(1f))
        }
    }
}

private enum class SettingsSection(
    val number: String,
    val label: String,
    val description: String,
    val icon: ImageVector,
) {
    Plex("01", "Plex server", "Connection & library", Icons.Rounded.Storage),
    Profiles("02", "Profiles", "Plex Home users", Icons.Rounded.AccountCircle),
    Playback("03", "Playback", "Autoplay & timers", Icons.Rounded.PlayCircle),
    Network("04", "Network", "Quality & codecs", Icons.Rounded.NetworkCheck),
    Cinema("05", "Cinema Mode", "Pre-show settings", Icons.Rounded.Movie),
    Lights("06", "Cinema lights", "Google Home & Tapo", Icons.Rounded.Lightbulb),
}

@Composable
private fun MobileSettingsNavigation(
    activeSection: SettingsSection,
    onBack: () -> Unit,
    onSectionSelected: (SettingsSection) -> Unit,
) {
    val configuration = LocalConfiguration.current
    val landscape = configuration.screenWidthDp > configuration.screenHeightDp
    Column(
        Modifier.fillMaxWidth().background(Color(0xFF050A0F))
            .border(0.5.dp, Color.White.copy(alpha = 0.08f)),
    ) {
        Row(
            Modifier.fillMaxWidth().height(if (landscape) 50.dp else 60.dp).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(42.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.07f))
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = Color.White, modifier = Modifier.size(24.dp))
            }
            Image(
                painter = painterResource(R.drawable.ic_launcher),
                contentDescription = null,
                modifier = Modifier.padding(start = 12.dp).size(if (landscape) 28.dp else 33.dp),
            )
            Column(Modifier.padding(start = 9.dp).weight(1f)) {
                Text("Settings", color = Color.White, style = MaterialTheme.typography.titleLarge)
                Text("Minova Cinema · ${BuildConfig.VERSION_NAME}", color = MinovaCyan, style = MaterialTheme.typography.bodySmall)
            }
        }
        Column(
            Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, bottom = 9.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            listOf(
                SettingsSection.Plex,
                SettingsSection.Profiles,
                SettingsSection.Playback,
                SettingsSection.Network,
            ).chunked(if (landscape) 4 else 2).forEach { sections ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    sections.forEach { section ->
                        val selected = section == activeSection
                        Column(
                            Modifier.weight(1f).height(if (landscape) 40.dp else 44.dp).clip(RoundedCornerShape(12.dp))
                                .background(if (selected) MinovaCyan else Color.White.copy(alpha = 0.07f))
                                .clickable { onSectionSelected(section) }
                                .padding(horizontal = 5.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Icon(
                                section.icon,
                                null,
                                tint = if (selected) Color(0xFF001419) else MinovaMuted,
                                modifier = Modifier.size(17.dp),
                            )
                            Text(
                                section.label,
                                color = if (selected) Color(0xFF001419) else Color.White,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsNavigationRail(
    activeSection: SettingsSection,
    railFocusRequesters: Map<SettingsSection, FocusRequester>,
    compact: Boolean,
    onSectionSelected: (SettingsSection) -> Unit,
    onEnterSection: (SettingsSection) -> Unit,
) {
    Column(
        modifier = Modifier
            .width(if (compact) 76.dp else 286.dp)
            .fillMaxHeight()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF071017), Color(0xFF08131B), Color(0xFF050A0F)),
                ),
            )
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = 0.07f),
                shape = RoundedCornerShape(topEnd = 26.dp, bottomEnd = 26.dp),
            )
            .padding(
                start = if (compact) 10.dp else 22.dp,
                top = if (compact) 14.dp else 22.dp,
                end = if (compact) 10.dp else 18.dp,
                bottom = if (compact) 12.dp else 18.dp,
            ),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = painterResource(R.drawable.ic_launcher),
                contentDescription = "Minova Prism M",
                modifier = Modifier.size(34.dp),
            )
            if (!compact) Column(Modifier.padding(start = 12.dp)) {
                Text(
                    "MINOVA CINEMA",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                )
                Text(
                    "TV EXPERIENCE",
                    style = MaterialTheme.typography.bodySmall,
                    color = MinovaCyan,
                )
            }
        }

        if (!compact) Text(
            "SETTINGS",
            style = MaterialTheme.typography.bodySmall,
            color = MinovaMuted,
            modifier = Modifier.padding(top = 22.dp, start = 6.dp, bottom = 6.dp),
        )

        SettingsSection.entries.forEach { section ->
            val selected = activeSection == section
            Surface(
                onClick = { onSectionSelected(section) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .padding(vertical = 2.dp)
                    .focusRequester(railFocusRequesters.getValue(section))
                    .testTag("settings-section-${section.name}")
                    .onFocusChanged { state ->
                        if (state.isFocused) onSectionSelected(section)
                    }
                    .onPreviewKeyEvent { event ->
                        if (
                            event.type == KeyEventType.KeyDown &&
                            event.key == Key.DirectionRight
                        ) {
                            onEnterSection(section)
                            true
                        } else {
                            false
                        }
                    },
                shape = androidx.tv.material3.ClickableSurfaceDefaults.shape(
                    shape = RoundedCornerShape(13.dp),
                    focusedShape = RoundedCornerShape(13.dp),
                    pressedShape = RoundedCornerShape(13.dp),
                ),
                colors = androidx.tv.material3.ClickableSurfaceDefaults.colors(
                    containerColor = if (selected) Color(0xFF102A34) else Color.Transparent,
                    focusedContainerColor = Color(0xFF18404A),
                    pressedContainerColor = Color(0xFF20515C),
                ),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(27.dp)
                            .background(
                                if (selected) MinovaCyan.copy(alpha = 0.18f)
                                else Color.White.copy(alpha = 0.06f),
                                RoundedCornerShape(9.dp),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = section.icon,
                            contentDescription = null,
                            tint = if (selected) MinovaCyan else MinovaMuted,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    if (!compact) Column(Modifier.padding(start = 11.dp)) {
                        Text(
                            section.label,
                            style = MaterialTheme.typography.titleSmall,
                            color = Color.White,
                        )
                        Text(
                            section.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (selected) Color.White.copy(alpha = 0.72f) else MinovaMuted,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.weight(1f))
        if (!compact) Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White.copy(alpha = 0.04f), RoundedCornerShape(12.dp))
                .padding(horizontal = 14.dp, vertical = 11.dp),
        ) {
            Column {
                Text("MINOVA CINEMA", style = MaterialTheme.typography.bodySmall, color = MinovaMuted)
                Text(
                    "Version ${BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

@Composable
private fun SettingsPageHeader(section: SettingsSection, compact: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 2.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom,
    ) {
        Column(Modifier.weight(1f)) {
            if (!compact) Text(
                "CONTROL ROOM",
                style = MaterialTheme.typography.bodyMedium,
                color = MinovaCyan,
            )
            Text(
                section.label,
                style = if (compact) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.headlineLarge,
                color = Color.White,
                modifier = Modifier.padding(top = if (compact) 0.dp else 2.dp),
            )
            Text(
                section.description,
                style = MaterialTheme.typography.bodyLarge,
                color = MinovaMuted,
                modifier = Modifier.padding(top = 5.dp),
            )
        }
        if (!compact) Text(
            "RIGHT TO OPEN  ·  BACK TO RETURN",
            style = MaterialTheme.typography.bodySmall,
            color = MinovaMuted,
        )
    }
}

@Composable
private fun PlexPinDialog(
    profile: PlexHomeProfile,
    pin: String,
    onPinChanged: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val handheld = rememberDeviceProfile() == DeviceProfile.Handheld
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth(if (handheld) 0.92f else 0.55f)
                .background(Color(0xFF111821), RoundedCornerShape(22.dp))
                .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(22.dp))
                .padding(if (handheld) 20.dp else 28.dp),
        ) {
            Text("Unlock ${profile.title}", style = MaterialTheme.typography.headlineMedium, color = Color.White)
            Text("Enter the four-digit Plex Home PIN.", color = MinovaMuted, modifier = Modifier.padding(top = 6.dp))
            BasicTextField(
                value = pin,
                onValueChange = onPinChanged,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                textStyle = MaterialTheme.typography.headlineMedium.copy(color = Color.White),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 20.dp)
                    .background(Color(0xFF1B2633), RoundedCornerShape(12.dp))
                    .border(1.dp, MinovaCyan, RoundedCornerShape(12.dp))
                    .padding(16.dp),
            )
            if (handheld) Column(
                Modifier.fillMaxWidth().padding(top = 20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SettingsPrimaryButton(onClick = onConfirm, enabled = pin.length == 4, modifier = Modifier.fillMaxWidth()) { Text("Switch profile") }
                SettingsSecondaryButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
            } else Row(Modifier.padding(top = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SettingsPrimaryButton(onClick = onConfirm, enabled = pin.length == 4) { Text("Switch profile") }
                SettingsSecondaryButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    }
}

@Composable
private fun SettingsCard(
    title: String,
    subtitle: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.horizontalGradient(
                    listOf(Color(0xFF101922), Color(0xFF0D151D)),
                ),
                RoundedCornerShape(18.dp),
            )
            .padding(horizontal = if (rememberDeviceProfile() == DeviceProfile.Handheld) 15.dp else 22.dp, vertical = 20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .width(4.dp)
                    .height(30.dp)
                    .background(MinovaCyan, RoundedCornerShape(50)),
            )
            Column(Modifier.padding(start = 12.dp)) {
                Text(title, style = MaterialTheme.typography.titleLarge, color = Color.White)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MinovaMuted,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        content()
    }
}

@Composable
private fun TheaterLightsSection(
    lightingState: LightingUiState,
    onRequestHomePermission: () -> Unit,
    onRefreshLights: () -> Unit,
    onLightAssignmentChanged: (String, Boolean) -> Unit,
) {
    val handheld = rememberDeviceProfile() == DeviceProfile.Handheld
    val selectedCount = lightingState.lights.count { it.isAssigned }
    Text("GOOGLE HOME · CINEMA LIGHTS", style = MaterialTheme.typography.bodyMedium, color = MinovaMuted)
    Text(
        "Connect a Google Home, then check only the lights Cinema Mode may control.",
        style = MaterialTheme.typography.bodyMedium,
        color = MinovaMuted,
        modifier = Modifier.padding(top = 8.dp),
    )
    when {
        !lightingState.sdkAvailable -> Text(
            lightingState.message ?: "Google Home is not available in this build.",
            color = MinovaMuted,
            modifier = Modifier.padding(top = 16.dp),
        )
        !lightingState.permissionGranted -> {
            SettingsPrimaryButton(
                onClick = onRequestHomePermission,
                enabled = !lightingState.loading,
                modifier = Modifier.padding(top = 16.dp),
            ) {
                Text(if (lightingState.loading) "Connecting…" else "Connect Google Home")
            }
            lightingState.message?.let {
                Text(it, color = MinovaMuted, modifier = Modifier.padding(top = 10.dp))
            }
        }
        else -> {
            Text(
                "Connected · $selectedCount selected",
                style = MaterialTheme.typography.titleMedium,
                color = MinovaCyan,
                modifier = Modifier.padding(top = 14.dp),
            )
            if (handheld) {
                Column(
                    Modifier.fillMaxWidth().padding(top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    SettingsSecondaryButton(onClick = onRequestHomePermission, modifier = Modifier.fillMaxWidth()) { Text("Change home access") }
                    SettingsSecondaryButton(onClick = onRefreshLights, enabled = !lightingState.loading, modifier = Modifier.fillMaxWidth()) {
                        Text(if (lightingState.loading) "Refreshing…" else "Refresh lights")
                    }
                }
            } else Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SettingsSecondaryButton(onClick = onRequestHomePermission) { Text("Change home access") }
                SettingsSecondaryButton(onClick = onRefreshLights, enabled = !lightingState.loading) {
                    Text(if (lightingState.loading) "Refreshing…" else "Refresh lights")
                }
            }
            lightingState.message?.let {
                Text(it, color = MinovaMuted, modifier = Modifier.padding(top = 10.dp))
            }
            if (handheld) {
                Column(
                    Modifier.fillMaxWidth().padding(top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    lightingState.lights.forEach { light ->
                        PlaybackToggleButton(
                            title = light.name,
                            description = buildString {
                                append(light.roomName ?: "Google Home")
                                append(if (light.supportsDimming) " · Dimmable" else " · On/off")
                            },
                            checked = light.isAssigned,
                            onClick = { onLightAssignmentChanged(light.id, !light.isAssigned) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            } else lightingState.lights.chunked(2).forEach { rowLights ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    rowLights.forEach { light ->
                        PlaybackToggleButton(
                            title = light.name,
                            description = buildString {
                                append(light.roomName ?: "Google Home")
                                append(if (light.supportsDimming) " · Dimmable" else " · On/off")
                            },
                            checked = light.isAssigned,
                            onClick = { onLightAssignmentChanged(light.id, !light.isAssigned) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (rowLights.size == 1) Spacer(Modifier.weight(1f))
                }
            }
            Text(
                "Only selected lights fade during Cinema Mode. Other devices remain untouched.",
                style = MaterialTheme.typography.bodyMedium,
                color = MinovaMuted,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
}

@Composable
private fun ValueButton(
    title: String,
    value: String,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val content: @Composable () -> Unit = {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 17.dp, vertical = 12.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = Color.White)
            Text(
                value,
                style = MaterialTheme.typography.bodyMedium,
                color = MinovaCyan,
                modifier = Modifier.padding(top = 2.dp),
            )
            Text(
                description,
                style = MaterialTheme.typography.bodyMedium,
                color = MinovaMuted,
                modifier = Modifier.padding(top = 3.dp),
            )
        }
    }
    if (rememberDeviceProfile() == DeviceProfile.Handheld) {
        androidx.compose.material3.Surface(
            onClick = onClick,
            modifier = modifier.heightIn(min = 100.dp),
            shape = RoundedCornerShape(12.dp),
            color = Color(0xFF151E29),
            contentColor = Color.White,
            content = content,
        )
        return
    }
    Surface(
        onClick = onClick,
        modifier = modifier.heightIn(min = 100.dp),
        shape = androidx.tv.material3.ClickableSurfaceDefaults.shape(
            shape = RoundedCornerShape(12.dp),
            focusedShape = RoundedCornerShape(12.dp),
            pressedShape = RoundedCornerShape(12.dp),
        ),
        colors = androidx.tv.material3.ClickableSurfaceDefaults.colors(
            containerColor = Color(0xFF151E29),
            focusedContainerColor = Color(0xFF21404A),
            pressedContainerColor = Color(0xFF19313A),
        ),
        content = { content() },
    )
}

@Composable
private fun TimerSettingButton(
    title: String,
    value: Long,
    presets: List<Long>,
    formattedValue: String,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val currentIndex = presets.indices.minByOrNull { index -> abs(presets[index] - value) } ?: 0
    val fraction = if (presets.size <= 1) 0f else currentIndex.toFloat() / (presets.lastIndex.toFloat())

    val content: @Composable () -> Unit = {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 13.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = Color.White)
                Text(
                    formattedValue,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (enabled) MinovaCyan else MinovaMuted,
                )
            }
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(30.dp)
                    .padding(horizontal = 2.dp, vertical = 8.dp),
            ) {
                val trackHeight = 5.dp.toPx()
                val centerY = size.height / 2f
                val thumbX = size.width * fraction
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.16f),
                    topLeft = androidx.compose.ui.geometry.Offset(0f, centerY - trackHeight / 2f),
                    size = androidx.compose.ui.geometry.Size(size.width, trackHeight),
                    cornerRadius = CornerRadius(trackHeight / 2f),
                )
                drawRoundRect(
                    color = if (enabled) MinovaCyan else MinovaMuted,
                    topLeft = androidx.compose.ui.geometry.Offset(0f, centerY - trackHeight / 2f),
                    size = androidx.compose.ui.geometry.Size(thumbX, trackHeight),
                    cornerRadius = CornerRadius(trackHeight / 2f),
                )
                presets.indices.forEach { index ->
                    val tickX = if (presets.size <= 1) 0f else size.width * index / presets.lastIndex
                    drawCircle(Color(0xFF111821), radius = 3.dp.toPx(), center = androidx.compose.ui.geometry.Offset(tickX, centerY))
                }
                drawCircle(
                    color = if (enabled) MinovaCyan.copy(alpha = 0.22f) else Color.Transparent,
                    radius = 11.dp.toPx(),
                    center = androidx.compose.ui.geometry.Offset(thumbX, centerY),
                )
                drawCircle(
                    color = if (enabled) MinovaCyan else MinovaMuted,
                    radius = 6.dp.toPx(),
                    center = androidx.compose.ui.geometry.Offset(thumbX, centerY),
                )
            }
            Text(
                description,
                style = MaterialTheme.typography.bodyMedium,
                color = if (enabled) MinovaMuted else MinovaMuted.copy(alpha = 0.82f),
            )
        }
    }
    val surfaceModifier = modifier.heightIn(min = 128.dp).testTag("timer-setting-$title")
    if (rememberDeviceProfile() == DeviceProfile.Handheld) {
        androidx.compose.material3.Surface(
            onClick = onClick,
            enabled = enabled,
            modifier = surfaceModifier,
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFF151E29),
            contentColor = Color.White,
            content = content,
        )
        return
    }
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = surfaceModifier,
        shape = androidx.tv.material3.ClickableSurfaceDefaults.shape(
            shape = RoundedCornerShape(16.dp),
            focusedShape = RoundedCornerShape(16.dp),
            pressedShape = RoundedCornerShape(16.dp),
        ),
        colors = androidx.tv.material3.ClickableSurfaceDefaults.colors(
            containerColor = Color(0xFF151E29),
            focusedContainerColor = Color(0xFF20333C),
        ),
        content = { content() },
    )
}

@Composable
private fun TimerPresetDialog(
    title: String,
    description: String,
    value: Long,
    presets: List<Long>,
    formattedValue: (Long) -> String,
    onValueChanged: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val handheld = rememberDeviceProfile() == DeviceProfile.Handheld
    val adjustmentFocus = remember { FocusRequester() }
    val currentIndex = presets.indices.minByOrNull { index -> abs(presets[index] - value) } ?: 0
    val fraction = if (presets.size <= 1) 0f else currentIndex.toFloat() / presets.lastIndex.toFloat()

    fun adjustBy(delta: Int) {
        val target = (currentIndex + delta).coerceIn(0, presets.lastIndex)
        onValueChanged(presets[target])
    }

    LaunchedEffect(handheld) {
        if (!handheld) adjustmentFocus.requestFocus()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.76f)),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(if (handheld) 0.92f else 0.62f)
                    .background(
                        Brush.linearGradient(listOf(Color(0xFF14212B), Color(0xFF0C141C))),
                        RoundedCornerShape(24.dp),
                    )
                    .border(1.dp, MinovaCyan.copy(alpha = 0.32f), RoundedCornerShape(24.dp))
                    .padding(if (handheld) 20.dp else 28.dp),
            ) {
                Text("TIMER", style = MaterialTheme.typography.bodyMedium, color = MinovaCyan)
                Text(
                    title,
                    style = MaterialTheme.typography.headlineLarge,
                    color = Color.White,
                    modifier = Modifier.padding(top = 3.dp),
                )
                Text(
                    description,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MinovaMuted,
                    modifier = Modifier.padding(top = 6.dp),
                )

                if (handheld) {
                    Column(
                        Modifier.fillMaxWidth().padding(top = 18.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text("Choose a value", color = MinovaMuted, style = MaterialTheme.typography.bodyMedium)
                        presets.chunked(3).forEach { rowPresets ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                rowPresets.forEach { preset ->
                                    val selected = preset == presets[currentIndex]
                                    Box(
                                        Modifier.weight(1f).height(50.dp).clip(RoundedCornerShape(12.dp))
                                            .background(if (selected) MinovaCyan else Color(0xFF18232D))
                                            .clickable { onValueChanged(preset) },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            formattedValue(preset),
                                            color = if (selected) Color(0xFF001419) else Color.White,
                                            style = MaterialTheme.typography.titleSmall,
                                        )
                                    }
                                }
                                repeat(3 - rowPresets.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                    }
                } else Surface(
                    onClick = {
                        val next = if (currentIndex == presets.lastIndex) 0 else currentIndex + 1
                        onValueChanged(presets[next])
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(132.dp)
                        .padding(top = 20.dp)
                        .focusRequester(adjustmentFocus)
                        .testTag("timer-adjustment")
                        .onPreviewKeyEvent { event ->
                            if (event.type != KeyEventType.KeyDown) {
                                false
                            } else {
                                when (event.key) {
                                    Key.DirectionLeft -> {
                                        adjustBy(-1)
                                        true
                                    }
                                    Key.DirectionRight -> {
                                        adjustBy(1)
                                        true
                                    }
                                    else -> false
                                }
                            }
                        },
                    shape = androidx.tv.material3.ClickableSurfaceDefaults.shape(
                        shape = RoundedCornerShape(16.dp),
                        focusedShape = RoundedCornerShape(16.dp),
                        pressedShape = RoundedCornerShape(16.dp),
                    ),
                    colors = androidx.tv.material3.ClickableSurfaceDefaults.colors(
                        containerColor = Color(0xFF18232D),
                        focusedContainerColor = Color(0xFF1C3540),
                    ),
                ) {
                    Column(Modifier.padding(horizontal = 22.dp, vertical = 15.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("‹", style = MaterialTheme.typography.headlineMedium, color = MinovaMuted)
                            Text(
                                formattedValue(value),
                                style = MaterialTheme.typography.headlineMedium,
                                color = MinovaCyan,
                            )
                            Text("›", style = MaterialTheme.typography.headlineMedium, color = MinovaMuted)
                        }
                        Canvas(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(24.dp)
                                .padding(horizontal = 4.dp, vertical = 8.dp),
                        ) {
                            val trackHeight = 4.dp.toPx()
                            val centerY = size.height / 2f
                            val thumbX = size.width * fraction
                            drawRoundRect(
                                color = Color.White.copy(alpha = 0.14f),
                                topLeft = androidx.compose.ui.geometry.Offset(0f, centerY - trackHeight / 2f),
                                size = androidx.compose.ui.geometry.Size(size.width, trackHeight),
                                cornerRadius = CornerRadius(trackHeight / 2f),
                            )
                            drawRoundRect(
                                color = MinovaCyan,
                                topLeft = androidx.compose.ui.geometry.Offset(0f, centerY - trackHeight / 2f),
                                size = androidx.compose.ui.geometry.Size(thumbX, trackHeight),
                                cornerRadius = CornerRadius(trackHeight / 2f),
                            )
                            presets.indices.forEach { index ->
                                val x = if (presets.size <= 1) 0f else size.width * index / presets.lastIndex
                                drawCircle(
                                    color = if (index <= currentIndex) MinovaCyan else Color(0xFF53616C),
                                    radius = 3.dp.toPx(),
                                    center = androidx.compose.ui.geometry.Offset(x, centerY),
                                )
                            }
                        }
                        Text(
                            "Use D-pad Left / Right, then press Down",
                            style = MaterialTheme.typography.bodySmall,
                            color = MinovaMuted,
                        )
                    }
                }

                SettingsPrimaryButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 14.dp)
                        .height(56.dp)
                        .testTag("timer-done"),
                ) {
                    Text("Done")
                }
            }
        }
    }
}

private val SCREENSAVER_PRESETS = listOf(1L, 5L, 10L, 15L, 30L).map { it * 60_000L }
private val SLEEP_PRESETS = listOf(
    30L * 60_000L,
    60L * 60_000L,
    2L * 60L * 60_000L,
    3L * 60L * 60_000L,
    4L * 60L * 60_000L,
    6L * 60L * 60_000L,
)

private fun formatMinutes(value: Long): String = "${value / 60_000L} min"

private fun formatHours(value: Long): String = if (value < 60L * 60_000L) {
    "${value / 60_000L} min"
} else {
    "${value / (60L * 60_000L)} hr"
}

private fun readableSettingsError(message: String, fallback: String): String {
    val compact = message.replace(Regex("\\s+"), " ").trim()
    return when {
        compact.isBlank() -> fallback
        "BEGIN_ARRAY" in compact || "BEGIN_OBJECT" in compact -> fallback
        compact.length > 180 -> compact.take(177).trimEnd() + "…"
        else -> compact
    }
}

@Composable
private fun PlaybackToggleButton(
    title: String,
    description: String,
    checked: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val content: @Composable () -> Unit = {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 17.dp, vertical = 11.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .border(
                        width = 2.dp,
                        color = if (checked) MinovaCyan else MinovaMuted,
                        shape = RoundedCornerShape(6.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (checked) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = null,
                        tint = MinovaCyan,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = Color.White)
                Text(
                    description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MinovaMuted,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
        }
    }
    if (rememberDeviceProfile() == DeviceProfile.Handheld) {
        androidx.compose.material3.Surface(
            onClick = onClick,
            modifier = modifier.heightIn(min = 90.dp),
            shape = RoundedCornerShape(12.dp),
            color = Color(0xFF151E29),
            contentColor = Color.White,
            content = content,
        )
        return
    }
    Surface(
        onClick = onClick,
        modifier = modifier.heightIn(min = 90.dp),
        shape = androidx.tv.material3.ClickableSurfaceDefaults.shape(
            shape = RoundedCornerShape(12.dp),
            focusedShape = RoundedCornerShape(12.dp),
            pressedShape = RoundedCornerShape(12.dp),
        ),
        colors = androidx.tv.material3.ClickableSurfaceDefaults.colors(
            containerColor = Color(0xFF151E29),
            focusedContainerColor = Color(0xFF21404A),
            pressedContainerColor = Color(0xFF19313A),
        ),
        content = { content() },
    )
}
