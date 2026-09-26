package com.minova.cinema.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.minova.cinema.ui.theme.MinovaCyan
import com.minova.cinema.ui.theme.MinovaNightDeep
import com.minova.cinema.ui.platform.DeviceProfile
import com.minova.cinema.ui.platform.rememberDeviceProfile

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
    val handheld = rememberDeviceProfile() == DeviceProfile.Handheld
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MinovaNightDeep)
            .padding(if (handheld) 24.dp else 64.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "Plex is unavailable",
            style = if (handheld) MaterialTheme.typography.headlineLarge else MaterialTheme.typography.displayMedium,
        )
        Text(
            message,
            color = MinovaMuted,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(top = 14.dp, bottom = 30.dp),
        )
        if (handheld) {
            Column(
                Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                androidx.compose.material3.Button(
                    onClick = onRetry,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = MinovaCyan,
                        contentColor = Color(0xFF001419),
                    ),
                ) { Text("Try again") }
                androidx.compose.material3.OutlinedButton(
                    onClick = onChangeServer,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                ) { Text("Change server") }
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Button(onClick = onRetry) { Text("Try again") }
                OutlinedButton(onClick = onChangeServer) { Text("Change server") }
            }
        }
    }
}
