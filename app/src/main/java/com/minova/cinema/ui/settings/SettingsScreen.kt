package com.minova.cinema.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import com.minova.cinema.R
import com.minova.cinema.ui.theme.MinovaCyan
import com.minova.cinema.ui.theme.MinovaMuted
import com.minova.cinema.ui.theme.MinovaNightDeep
import com.minova.cinema.home.LightingUiState
import com.minova.cinema.tapo.TapoLightsUiState

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
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MinovaNightDeep)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 64.dp, vertical = 48.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = painterResource(R.drawable.ic_launcher),
                contentDescription = "Minova Prism M",
                modifier = Modifier.size(48.dp),
            )
            Text(
                "Settings",
                style = MaterialTheme.typography.displayMedium,
                color = Color.White,
                modifier = Modifier.padding(start = 15.dp),
            )
        }
        Text(
            "Plex connection and library",
            style = MaterialTheme.typography.bodyLarge,
            color = MinovaMuted,
            modifier = Modifier.padding(top = 8.dp),
        )
        Spacer(Modifier.height(34.dp))
        TheaterLightsSection(
            lightingState = lightingState,
            onRequestHomePermission = onRequestHomePermission,
            onRefreshLights = onRefreshLights,
            onLightAssignmentChanged = onLightAssignmentChanged,
        )
        Spacer(Modifier.height(38.dp))
        TapoCinemaLightsSection(
            state = tapoLightsState,
            onSaveCredentials = onSaveTapoCredentials,
            onClearCredentials = onClearTapoCredentials,
            onDiscover = onDiscoverTapoLights,
            onAssignmentChanged = onTapoLightAssignmentChanged,
        )
        Spacer(Modifier.height(54.dp))
        Text("CONNECTED SERVER", style = MaterialTheme.typography.bodyMedium, color = MinovaMuted)
        Text(
            serverUrl,
            style = MaterialTheme.typography.headlineMedium,
            color = MinovaCyan,
            modifier = Modifier.padding(top = 8.dp),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 34.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(onClick = onRefresh) { Text("Refresh library") }
            OutlinedButton(onClick = onChangeServer) { Text("Change Plex server") }
        }
        Spacer(Modifier.height(44.dp))
        Text("PLAYBACK", style = MaterialTheme.typography.bodyMedium, color = MinovaMuted)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PlaybackToggleButton(
                title = "Autoplay next episode",
                description = "Start after a 10-second Next Up countdown",
                checked = autoplayNextEpisode,
                onClick = { onAutoplayNextEpisodeChanged(!autoplayNextEpisode) },
                modifier = Modifier.weight(1f),
            )
            PlaybackToggleButton(
                title = "Continue watching check",
                description = "Ask after ${formatHours(inactivityTimeoutMs)} without remote activity",
                checked = inactivityCheckEnabled,
                onClick = { onInactivityCheckChanged(!inactivityCheckEnabled) },
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(34.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            ValueButton(
                title = "Screensaver timer",
                value = formatMinutes(screensaverTimeoutMs),
                description = "Press to cycle: 1, 5, 10, 15 or 30 minutes",
                onClick = {
                    onScreensaverTimeoutChanged(nextPreset(screensaverTimeoutMs, SCREENSAVER_PRESETS))
                },
                modifier = Modifier.weight(1f),
            )
            ValueButton(
                title = "Continue-watching timer",
                value = formatHours(inactivityTimeoutMs),
                description = "Press to cycle: 30 minutes, 1, 2, 3, 4 or 6 hours",
                onClick = {
                    onInactivityTimeoutChanged(nextPreset(inactivityTimeoutMs, SLEEP_PRESETS))
                },
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(40.dp))
        Text("CINEMA MODE", style = MaterialTheme.typography.bodyMedium, color = MinovaMuted)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PlaybackToggleButton(
                title = "Cinema Mode",
                description = "Use your local bumper and assigned theater lights",
                checked = cinemaModeEnabled,
                onClick = { onCinemaModeChanged(!cinemaModeEnabled) },
                modifier = Modifier.weight(1f),
            )
            PlaybackToggleButton(
                title = "Play trailers",
                description = "Play two random trailers from unwatched Plex movies",
                checked = cinemaTrailersEnabled,
                onClick = { onCinemaTrailersChanged(!cinemaTrailersEnabled) },
                modifier = Modifier.weight(1f),
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ValueButton(
                title = "Local Atmos bumper",
                value = if (cinemaBumperConfigured) "Selected" else "Not selected",
                description = "Choose a local video file; 4K/Atmos is passed through when supported",
                onClick = onChooseCinemaBumper,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.weight(1f))
        }
        if (cinemaBumperConfigured) {
            OutlinedButton(
                onClick = onClearCinemaBumper,
                modifier = Modifier.padding(top = 14.dp),
            ) {
                Text("Clear local bumper")
            }
        }
        Spacer(Modifier.height(48.dp))
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
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    rowLights.forEach { light ->
                        PlaybackToggleButton(
                            title = light.name,
                            description = buildString {
                                append(light.roomName ?: "Google Home")
                                append(if (light.supportsDimming) " · Dimmable" else " · On/off")
                            },
                            checked = light.isAssigned,
                            onClick = {
                                onLightAssignmentChanged(light.id, !light.isAssigned)
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (rowLights.size == 1) Spacer(Modifier.weight(1f))
                }
            }
            Text(
                "Only selected lights fade during Cinema Mode. Other Google Home devices are untouched.",
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
    OutlinedButton(onClick = onClick, modifier = modifier.height(92.dp)) {
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

private val SCREENSAVER_PRESETS = listOf(1L, 5L, 10L, 15L, 30L).map { it * 60_000L }
private val SLEEP_PRESETS = listOf(30L * 60_000L, 60L * 60_000L, 2L * 60L * 60_000L,
    3L * 60L * 60_000L, 4L * 60L * 60_000L, 6L * 60L * 60_000L)

private fun nextPreset(current: Long, presets: List<Long>): Long =
    presets.firstOrNull { it > current } ?: presets.first()

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
    OutlinedButton(onClick = onClick, modifier = modifier.height(88.dp)) {
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
                        shape = RoundedCornerShape(5.dp),
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
