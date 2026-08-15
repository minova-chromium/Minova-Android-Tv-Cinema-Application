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

@Composable
fun SettingsScreen(
    serverUrl: String,
    autoplayNextEpisode: Boolean,
    inactivityCheckEnabled: Boolean,
    onRefresh: () -> Unit,
    onChangeServer: () -> Unit,
    onAutoplayNextEpisodeChanged: (Boolean) -> Unit,
    onInactivityCheckChanged: (Boolean) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MinovaNightDeep)
            .padding(horizontal = 64.dp, vertical = 48.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = painterResource(R.drawable.ic_launcher),
                contentDescription = "Minova Prism M",
                modifier = Modifier.size(48.dp),
            )
            Text("Settings", style = MaterialTheme.typography.displayMedium, modifier = Modifier.padding(start = 15.dp))
        }
        Text(
            "Plex connection and library",
            style = MaterialTheme.typography.bodyLarge,
            color = MinovaMuted,
            modifier = Modifier.padding(top = 8.dp),
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
                description = "Ask after three hours without remote activity",
                checked = inactivityCheckEnabled,
                onClick = { onInactivityCheckChanged(!inactivityCheckEnabled) },
                modifier = Modifier.weight(1f),
            )
        }
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
                Text(title, style = MaterialTheme.typography.titleMedium)
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
