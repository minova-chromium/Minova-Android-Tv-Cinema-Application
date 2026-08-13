package com.minova.cinema.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import androidx.tv.material3.MaterialTheme
import com.minova.cinema.ui.theme.MinovaMuted
import com.minova.cinema.ui.theme.MinovaNightDeep

@Composable
fun LoadingScreen(message: String = "Loading your Plex library…") {
    Box(
        modifier = Modifier.fillMaxSize().background(MinovaNightDeep),
        contentAlignment = Alignment.Center,
    ) {
        Text(message, style = MaterialTheme.typography.headlineMedium, color = MinovaMuted)
    }
}

@Composable
fun ConnectionErrorScreen(
    message: String,
    onRetry: () -> Unit,
    onChangeServer: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MinovaNightDeep)
            .padding(64.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Plex is unavailable", style = MaterialTheme.typography.displayMedium)
        Text(
            message,
            color = MinovaMuted,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(top = 14.dp, bottom = 30.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Button(onClick = onRetry) { Text("Try again") }
            OutlinedButton(onClick = onChangeServer) { Text("Change server") }
        }
    }
}
