package com.minova.cinema.ui.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.Button
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.minova.cinema.R
import com.minova.cinema.home.LightingUiState
import com.minova.cinema.tapo.TapoLightsUiState
import com.minova.cinema.domain.PlexHomeProfile
import com.minova.cinema.presentation.PlexProfilesUiState
import com.minova.cinema.presentation.NetworkAssistantUiState
import com.minova.cinema.ui.theme.MinovaCyan
import com.minova.cinema.ui.theme.MinovaMuted
import com.minova.cinema.ui.theme.MinovaNightDeep
import kotlin.math.abs

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
) {
    var pinProfile by remember { mutableStateOf<PlexHomeProfile?>(null) }
    var pin by remember { mutableStateOf("") }
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MinovaNightDeep),
        contentPadding = PaddingValues(
            start = 58.dp,
            top = 42.dp,
            end = 58.dp,
            bottom = 58.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(R.drawable.ic_launcher),
                    contentDescription = "Minova Prism M",
                    modifier = Modifier.size(46.dp),
                )
                Column(Modifier.padding(start = 15.dp)) {
                    Text(
                        "Settings",
                        style = MaterialTheme.typography.displayMedium,
                        color = Color.White,
                    )
                    Text(
                        "Playback, timers, server and Cinema Mode",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MinovaMuted,
                    )
                }
            }
        }

        item {
            SettingsCard(title = "Plex server", subtitle = "Connection and library") {
                Text(
                    serverUrl,
                    style = MaterialTheme.typography.headlineMedium,
                    color = MinovaCyan,
                    modifier = Modifier.padding(top = 18.dp),
                )
                Row(
                    modifier = Modifier.padding(top = 22.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Button(onClick = onRefresh) { Text("Refresh library") }
                    OutlinedButton(onClick = onChangeServer) { Text("Change server") }
                    OutlinedButton(onClick = onRequestTvHomeChannels) { Text("Add TV Home channels") }
                }
            }
        }

        item {
            val profiles = when (profilesState) {
                is PlexProfilesUiState.Ready -> profilesState.profiles
                is PlexProfilesUiState.Switching -> profilesState.profiles
                is PlexProfilesUiState.Error -> profilesState.profiles
                PlexProfilesUiState.Loading -> emptyList()
            }
            SettingsCard(title = "Plex Home", subtitle = "Profiles, managed users and PIN protection") {
                if (profiles.isNotEmpty()) {
                    LazyRow(
                        modifier = Modifier.fillMaxWidth().padding(top = 18.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(profiles, key = PlexHomeProfile::uuid) { profile ->
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
                            modifier = Modifier.width(260.dp),
                        )
                    }
                    }
                } else {
                    Text(
                        if (profilesState is PlexProfilesUiState.Loading) "Loading Plex Home…" else "No Plex Home profiles found.",
                        color = MinovaMuted,
                        modifier = Modifier.padding(top = 18.dp, bottom = 8.dp),
                    )
                }
                OutlinedButton(
                    onClick = onRefreshProfiles,
                    modifier = Modifier.padding(top = 12.dp),
                ) { Text("Refresh profiles") }
                when (profilesState) {
                    is PlexProfilesUiState.Error -> Text(
                        profilesState.message,
                        color = Color(0xFFFFB4AB),
                        modifier = Modifier.padding(top = 12.dp),
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

        item {
            SettingsCard(
                title = "Playback",
                subtitle = "Autoplay and viewing safeguards",
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 18.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    PlaybackToggleButton(
                        title = "Autoplay next episode",
                        description = "Start after the 10-second Next Up countdown",
                        checked = autoplayNextEpisode,
                        onClick = { onAutoplayNextEpisodeChanged(!autoplayNextEpisode) },
                        modifier = Modifier.weight(1f),
                    )
                    PlaybackToggleButton(
                        title = "Continue watching check",
                        description = "Ask before ending a long inactive session",
                        checked = inactivityCheckEnabled,
                        onClick = { onInactivityCheckChanged(!inactivityCheckEnabled) },
                        modifier = Modifier.weight(1f),
                    )
                }
                Text(
                    "TIMERS · USE LEFT / RIGHT TO ADJUST",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MinovaMuted,
                    modifier = Modifier.padding(top = 26.dp, bottom = 10.dp),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    TvPresetSlider(
                        title = "Ambient screensaver",
                        value = screensaverTimeoutMs,
                        presets = SCREENSAVER_PRESETS,
                        formattedValue = formatMinutes(screensaverTimeoutMs),
                        description = "Idle time before the bouncing Minova logo appears",
                        onValueChanged = onScreensaverTimeoutChanged,
                        modifier = Modifier.weight(1f),
                    )
                    TvPresetSlider(
                        title = "Playback sleep check",
                        value = inactivityTimeoutMs,
                        presets = SLEEP_PRESETS,
                        formattedValue = formatHours(inactivityTimeoutMs),
                        description = "Time before the 30-second Continue watching? prompt",
                        onValueChanged = onInactivityTimeoutChanged,
                        enabled = inactivityCheckEnabled,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        item {
            SettingsCard(
                title = "Network & codec assistant",
                subtitle = "Test this Plex connection and inspect the TV's decoders",
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
                        networkAssistantState.message,
                        color = Color(0xFFFFB4AB),
                        modifier = Modifier.padding(top = 16.dp),
                    )
                }
                Button(
                    onClick = onRunNetworkTest,
                    enabled = networkAssistantState !is NetworkAssistantUiState.Testing,
                    modifier = Modifier.padding(top = 16.dp),
                ) { Text(if (networkAssistantState is NetworkAssistantUiState.Testing) "Testing…" else "Run connection test") }
            }
        }

        item {
            SettingsCard(
                title = "Cinema Mode",
                subtitle = "Trailers, local bumper and theater lighting",
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 18.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    PlaybackToggleButton(
                        title = "Cinema Mode",
                        description = "Run the pre-show and assigned theater lights",
                        checked = cinemaModeEnabled,
                        onClick = { onCinemaModeChanged(!cinemaModeEnabled) },
                        modifier = Modifier.weight(1f),
                    )
                    PlaybackToggleButton(
                        title = "Play trailers",
                        description = "Two random unwatched Plex movie trailers",
                        checked = cinemaTrailersEnabled,
                        onClick = { onCinemaTrailersChanged(!cinemaTrailersEnabled) },
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
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
                        OutlinedButton(onClick = onClearCinemaBumper) {
                            Text("Clear bumper")
                        }
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }

        item {
            SettingsCard(
                title = "Cinema lights",
                subtitle = "Choose only the lights Minova may control",
            ) {
                Spacer(Modifier.height(18.dp))
                TheaterLightsSection(
                    lightingState = lightingState,
                    onRequestHomePermission = onRequestHomePermission,
                    onRefreshLights = onRefreshLights,
                    onLightAssignmentChanged = onLightAssignmentChanged,
                )
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 26.dp)
                        .height(1.dp)
                        .background(Color.White.copy(alpha = 0.12f)),
                )
                TapoCinemaLightsSection(
                    state = tapoLightsState,
                    onSaveCredentials = onSaveTapoCredentials,
                    onClearCredentials = onClearTapoCredentials,
                    onDiscover = onDiscoverTapoLights,
                    onAssignmentChanged = onTapoLightAssignmentChanged,
                )
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
}

@Composable
private fun PlexPinDialog(
    profile: PlexHomeProfile,
    pin: String,
    onPinChanged: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.55f)
                .background(Color(0xFF111821), RoundedCornerShape(22.dp))
                .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(22.dp))
                .padding(28.dp),
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
            Row(Modifier.padding(top = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onConfirm, enabled = pin.length == 4) { Text("Switch profile") }
                OutlinedButton(onClick = onDismiss) { Text("Cancel") }
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
            .background(Color(0xFF111821), RoundedCornerShape(20.dp))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(20.dp))
            .padding(horizontal = 28.dp, vertical = 24.dp),
    ) {
        Text(title, style = MaterialTheme.typography.headlineMedium, color = Color.White)
        Text(
            subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MinovaMuted,
            modifier = Modifier.padding(top = 3.dp),
        )
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
            Button(
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
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(onClick = onRequestHomePermission) { Text("Change home access") }
                OutlinedButton(onClick = onRefreshLights, enabled = !lightingState.loading) {
                    Text(if (lightingState.loading) "Refreshing…" else "Refresh lights")
                }
            }
            lightingState.message?.let {
                Text(it, color = MinovaMuted, modifier = Modifier.padding(top = 10.dp))
            }
            lightingState.lights.chunked(2).forEach { rowLights ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
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
    OutlinedButton(onClick = onClick, modifier = modifier.height(96.dp)) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = Color.White)
                Text(value, style = MaterialTheme.typography.titleMedium, color = MinovaCyan)
            }
            Text(
                description,
                style = MaterialTheme.typography.bodyMedium,
                color = MinovaMuted,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun TvPresetSlider(
    title: String,
    value: Long,
    presets: List<Long>,
    formattedValue: String,
    description: String,
    onValueChanged: (Long) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val currentIndex = presets.indices.minByOrNull { index -> abs(presets[index] - value) } ?: 0
    val fraction = if (presets.size <= 1) 0f else currentIndex.toFloat() / (presets.lastIndex.toFloat())

    fun adjustBy(delta: Int) {
        val target = (currentIndex + delta).coerceIn(0, presets.lastIndex)
        onValueChanged(presets[target])
    }

    Surface(
        onClick = {
            val next = if (currentIndex == presets.lastIndex) 0 else currentIndex + 1
            onValueChanged(presets[next])
        },
        enabled = enabled,
        modifier = modifier
            .height(132.dp)
            .onPreviewKeyEvent { event ->
                if (!enabled || event.type != KeyEventType.KeyDown) {
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
            containerColor = Color(0xFF151E29),
            focusedContainerColor = Color(0xFF20333C),
        ),
    ) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
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
                color = if (enabled) MinovaMuted else MinovaMuted.copy(alpha = 0.55f),
            )
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

@Composable
private fun PlaybackToggleButton(
    title: String,
    description: String,
    checked: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedButton(onClick = onClick, modifier = modifier.height(92.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
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
}
