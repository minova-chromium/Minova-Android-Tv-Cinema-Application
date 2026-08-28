package com.minova.cinema.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Button
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.minova.cinema.tapo.TapoLight
import com.minova.cinema.tapo.TapoLightsUiState
import com.minova.cinema.ui.theme.MinovaCyan
import com.minova.cinema.ui.theme.MinovaMuted
import com.minova.cinema.ui.theme.MinovaNightDeep

@Composable
fun TapoCinemaLightsSection(
    state: TapoLightsUiState,
    onSaveCredentials: (String, String) -> Unit,
    onClearCredentials: () -> Unit,
    onDiscover: () -> Unit,
    onAssignmentChanged: (String, Boolean) -> Unit,
) {
    var settingsVisible by remember { mutableStateOf(false) }
    var loginVisible by remember { mutableStateOf(false) }
    val assignedCount = state.lights.count(TapoLight::isAssigned)

    Text("TP-LINK TAPO · LOCAL CINEMA LIGHTS", color = Color.White)
    Text(
        "A local-network fallback for TVs where Google Home does not expose its permissions service.",
        style = MaterialTheme.typography.bodyMedium,
        color = MinovaMuted,
        modifier = Modifier.padding(top = 8.dp),
    )
    Text(
        if (state.hasCredentials) {
            "Connected · $assignedCount selected"
        } else {
            "Not connected"
        },
        style = MaterialTheme.typography.titleMedium,
        color = if (state.hasCredentials) MinovaCyan else MinovaMuted,
        modifier = Modifier.padding(top = 12.dp),
    )
    Row(
        modifier = Modifier.padding(top = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Button(
            onClick = {
                if (state.hasCredentials) settingsVisible = true else loginVisible = true
            },
        ) {
            Text(if (state.hasCredentials) "Configure Tapo lights" else "Connect Tapo")
        }
        if (state.hasCredentials) {
            OutlinedButton(onClick = { loginVisible = true }) { Text("Change Tapo login") }
        }
    }
    state.message?.let { message ->
        Text(message, color = MinovaMuted, modifier = Modifier.padding(top = 10.dp))
    }

    if (loginVisible) {
        TapoLoginDialog(
            onDismiss = { loginVisible = false },
            onSave = { email, password ->
                onSaveCredentials(email, password)
                loginVisible = false
                settingsVisible = true
            },
        )
    }
    if (settingsVisible) {
        CinemaLightsSettingsScreen(
            state = state,
            onDismiss = { settingsVisible = false },
            onDiscover = onDiscover,
            onChangeLogin = {
                settingsVisible = false
                loginVisible = true
            },
            onClearCredentials = {
                onClearCredentials()
                settingsVisible = false
            },
            onAssignmentChanged = onAssignmentChanged,
        )
    }
}

/** Full-screen TV dialog; Foundation LazyColumn supplies predictable D-pad scrolling. */
@Composable
fun CinemaLightsSettingsScreen(
    state: TapoLightsUiState,
    onDismiss: () -> Unit,
    onDiscover: () -> Unit,
    onChangeLogin: () -> Unit,
    onClearCredentials: () -> Unit,
    onAssignmentChanged: (String, Boolean) -> Unit,
) {
    val firstFocus = remember { FocusRequester() }
    val listState = rememberLazyListState()
    LaunchedEffect(Unit) { firstFocus.requestFocus() }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MinovaNightDeep)
                .padding(horizontal = 52.dp, vertical = 32.dp),
        ) {
            Text("Tapo Cinema Room", style = MaterialTheme.typography.headlineLarge, color = Color.White)
            Text(
                "Select only the lights Minova may dim during Cinema Mode.",
                style = MaterialTheme.typography.bodyLarge,
                color = MinovaMuted,
                modifier = Modifier.padding(top = 6.dp),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Button(
                    onClick = onDiscover,
                    enabled = !state.discovering,
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(firstFocus),
                ) {
                    Text(if (state.discovering) "Scanning…" else "Scan for lights")
                }
                OutlinedButton(onClick = onChangeLogin, modifier = Modifier.weight(1f)) {
                    Text("Change login")
                }
                OutlinedButton(onClick = onClearCredentials, modifier = Modifier.weight(1f)) {
                    Text("Disconnect")
                }
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                    Text("Done")
                }
            }
            state.message?.let {
                Text(it, color = MinovaMuted, modifier = Modifier.padding(top = 12.dp))
            }
            Text(
                when (state.lights.size) {
                    0 -> "No compatible lights found yet"
                    1 -> "1 compatible light found"
                    else -> "${state.lights.size} compatible lights found · Use D-pad Up/Down to browse all lights"
                },
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 16.dp, bottom = 10.dp),
            )
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                itemsIndexed(
                    items = state.lights,
                    key = { _, light -> light.ipAddress },
                ) { _, light ->
                    TapoLightRow(
                        light = light,
                        onClick = {
                            onAssignmentChanged(light.ipAddress, !light.isAssigned)
                        },
                    )
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

@Composable
private fun TapoLightRow(light: TapoLight, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = androidx.tv.material3.ClickableSurfaceDefaults.shape(
            shape = RoundedCornerShape(14.dp),
            focusedShape = RoundedCornerShape(14.dp),
            pressedShape = RoundedCornerShape(14.dp),
        ),
        colors = androidx.tv.material3.ClickableSurfaceDefaults.colors(
            containerColor = Color(0xFF151B22),
            focusedContainerColor = Color(0xFF22333A),
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 15.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .border(
                        width = 2.dp,
                        color = if (light.isAssigned) MinovaCyan else MinovaMuted,
                        shape = RoundedCornerShape(7.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (light.isAssigned) {
                    Icon(Icons.Rounded.Check, contentDescription = null, tint = MinovaCyan)
                }
            }
            Column(Modifier.padding(start = 18.dp).weight(1f)) {
                Text(light.nickname, style = MaterialTheme.typography.titleMedium, color = Color.White)
                Text(
                    "${light.model} · ${light.ipAddress}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MinovaMuted,
                )
            }
            Text(
                if (light.isOn) "${light.brightness}%" else "Off",
                color = MinovaCyan,
            )
        }
    }
}

@Composable
private fun TapoLoginDialog(
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit,
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val emailFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { emailFocus.requestFocus() }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.72f)
                .background(MinovaNightDeep, RoundedCornerShape(22.dp))
                .padding(34.dp),
        ) {
            Text("Connect TP-Link Tapo", style = MaterialTheme.typography.headlineLarge, color = Color.White)
            Text(
                "Use the same case-sensitive email and password as the Tapo app. They are encrypted on this TV.",
                color = MinovaMuted,
                modifier = Modifier.padding(top = 8.dp),
            )
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { androidx.compose.material3.Text("Tapo email") },
                singleLine = true,
                colors = tapoTextFieldColors(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 22.dp)
                    .focusRequester(emailFocus),
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { androidx.compose.material3.Text("Tapo password") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                colors = tapoTextFieldColors(),
                modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
            )
            Row(
                modifier = Modifier.padding(top = 22.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Button(
                    onClick = { onSave(email, password) },
                    enabled = email.isNotBlank() && password.isNotBlank(),
                ) { Text("Save and scan") }
                OutlinedButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    }
}

@Composable
private fun tapoTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Color.White,
    unfocusedTextColor = Color.White,
    focusedBorderColor = MinovaCyan,
    unfocusedBorderColor = MinovaMuted,
    focusedLabelColor = MinovaCyan,
    unfocusedLabelColor = MinovaMuted,
    cursorColor = MinovaCyan,
)
