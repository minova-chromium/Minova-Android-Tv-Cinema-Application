package com.minova.cinema.ui.onboarding

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.minova.cinema.R
import com.minova.cinema.ui.theme.MinovaCoral
import com.minova.cinema.ui.theme.MinovaCyan
import com.minova.cinema.ui.theme.MinovaMuted
import com.minova.cinema.ui.theme.MinovaNightDeep
import com.minova.cinema.ui.theme.MinovaSurface
import com.minova.cinema.ui.theme.MinovaSurfaceRaised
import com.minova.cinema.ui.theme.MinovaTeal

@Composable
fun OnboardingScreen(
    connecting: Boolean,
    error: String?,
    onConnect: (String, String) -> Unit,
) {
    var server by rememberSaveable { mutableStateOf("") }
    var token by rememberSaveable { mutableStateOf("") }
    val serverFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) { serverFocus.requestFocus() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(Color(0xFF162536), MinovaNightDeep),
                    radius = 950f,
                    center = androidx.compose.ui.geometry.Offset(250f, 120f),
                ),
            ),
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .width(670.dp)
                .padding(32.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(R.drawable.ic_launcher),
                    contentDescription = "Minova Prism M",
                    modifier = Modifier.size(62.dp),
                )
                Spacer(Modifier.width(16.dp))
                Image(
                    painter = painterResource(R.drawable.minova_cinema_wordmark),
                    contentDescription = "Minova Cinema",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.width(174.dp).height(66.dp),
                )
            }

            Text(
                text = "Connect your Plex library",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(top = 42.dp),
            )
            Text(
                text = "Enter the local address of your Plex Media Server and your Plex token. " +
                    "These stay on this TV.",
                style = MaterialTheme.typography.bodyLarge,
                color = MinovaMuted,
                modifier = Modifier.padding(top = 10.dp, bottom = 28.dp),
            )

            TvTextField(
                value = server,
                onValueChange = { server = it },
                label = "Plex server",
                hint = "192.168.1.10:32400",
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(serverFocus),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Next,
                ),
            )
            Spacer(Modifier.height(18.dp))
            TvTextField(
                value = token,
                onValueChange = { token = it },
                label = "X-Plex-Token",
                hint = "Paste your Plex token",
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(
                    onDone = { if (!connecting) onConnect(server, token) },
                ),
            )

            if (error != null) {
                Text(
                    text = error,
                    color = MinovaCoral,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 16.dp),
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 28.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "LAN connection  •  JSON API  •  Direct Play",
                    color = MinovaTeal,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Button(
                    onClick = { onConnect(server, token) },
                    enabled = !connecting,
                ) {
                    Text(if (connecting) "Connecting…" else "Connect")
                }
            }
        }
    }
}

@Composable
private fun TvTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    hint: String,
    modifier: Modifier = Modifier,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(8.dp)

    Column(modifier) {
        Text(
            text = label.uppercase(),
            color = if (focused) MinovaCyan else MinovaMuted,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(bottom = 7.dp),
        )
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp)
                .onFocusChanged { focused = it.isFocused }
                .background(MinovaSurface, shape)
                .border(
                    width = if (focused) 2.dp else 1.dp,
                    color = if (focused) MinovaCyan else MinovaSurfaceRaised,
                    shape = shape,
                )
                .padding(horizontal = 18.dp, vertical = 16.dp),
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color.White),
            singleLine = true,
            cursorBrush = SolidColor(MinovaCyan),
            visualTransformation = visualTransformation,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            decorationBox = { innerTextField ->
                if (value.isEmpty()) {
                    Text(hint, color = MinovaMuted, style = MaterialTheme.typography.bodyLarge)
                }
                innerTextField()
            },
        )
    }
}
