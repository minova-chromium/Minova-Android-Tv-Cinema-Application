package com.minova.cinema.ui.onboarding

import android.graphics.Bitmap
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.minova.cinema.R
import com.minova.cinema.presentation.PlexServerChoice
import com.minova.cinema.presentation.PlexSignInUiState
import com.minova.cinema.ui.platform.DeviceProfile
import com.minova.cinema.ui.platform.rememberDeviceProfile
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
    plexSignIn: PlexSignInUiState,
    onStartPlexSignIn: () -> Unit,
    onCancelPlexSignIn: () -> Unit,
    onSelectPlexServer: (String) -> Unit,
    onConnect: (String, String) -> Unit,
) {
    val handheld = rememberDeviceProfile() == DeviceProfile.Handheld
    val uriHandler = LocalUriHandler.current
    var server by rememberSaveable { mutableStateOf("") }
    var token by rememberSaveable { mutableStateOf("") }
    var showManualSetup by rememberSaveable { mutableStateOf(false) }
    var openedAuthorizationUrl by rememberSaveable { mutableStateOf<String?>(null) }
    val signInFocus = remember { FocusRequester() }

    LaunchedEffect(handheld) {
        if (!handheld) signInFocus.requestFocus()
    }
    LaunchedEffect(plexSignIn, handheld) {
        val waiting = plexSignIn as? PlexSignInUiState.Waiting ?: return@LaunchedEffect
        if (handheld && openedAuthorizationUrl != waiting.authorizationUrl) {
            openedAuthorizationUrl = waiting.authorizationUrl
            runCatching { uriHandler.openUri(waiting.authorizationUrl) }
        }
    }

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
                .widthIn(max = 720.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(
                    horizontal = if (handheld) 22.dp else 34.dp,
                    vertical = if (handheld) 24.dp else 34.dp,
                ),
        ) {
            BrandHeader()
            Text(
                text = "Connect your Plex library",
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White,
                modifier = Modifier.padding(top = if (handheld) 28.dp else 36.dp),
            )
            Text(
                text = "Authorize Minova in any phone browser. No Plex app, server address, " +
                    "password, or copied token is required.",
                style = MaterialTheme.typography.bodyLarge,
                color = MinovaMuted,
                modifier = Modifier.padding(top = 10.dp, bottom = 24.dp),
            )

            PlexSignInContent(
                state = plexSignIn,
                handheld = handheld,
                signInFocus = signInFocus,
                onStart = onStartPlexSignIn,
                onCancel = onCancelPlexSignIn,
                onOpenBrowser = { url -> runCatching { uriHandler.openUri(url) } },
                onSelectServer = onSelectPlexServer,
            )

            val displayedError = error ?: (plexSignIn as? PlexSignInUiState.Error)?.message
            if (displayedError != null) {
                Text(
                    text = displayedError,
                    color = MinovaCoral,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 16.dp),
                )
            }

            SecondaryButton(
                text = if (showManualSetup) "Hide advanced setup" else "Advanced manual setup",
                handheld = handheld,
                onClick = { showManualSetup = !showManualSetup },
                modifier = Modifier.padding(top = 18.dp).testTag("manual-setup-toggle"),
            )

            if (showManualSetup) {
                ManualSetup(
                    server = server,
                    token = token,
                    connecting = connecting,
                    handheld = handheld,
                    onServerChange = { server = it },
                    onTokenChange = { token = it },
                    onConnect = { onConnect(server, token) },
                )
            }
        }
    }
}

@Composable
private fun BrandHeader() {
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
}

@Composable
private fun PlexSignInContent(
    state: PlexSignInUiState,
    handheld: Boolean,
    signInFocus: FocusRequester,
    onStart: () -> Unit,
    onCancel: () -> Unit,
    onOpenBrowser: (String) -> Unit,
    onSelectServer: (String) -> Unit,
) {
    when (state) {
        PlexSignInUiState.Idle,
        is PlexSignInUiState.Error,
        -> PrimaryButton(
            text = "Sign in with Plex",
            handheld = handheld,
            enabled = true,
            onClick = onStart,
            modifier = Modifier.focusRequester(signInFocus).testTag("plex-sign-in-button"),
        )

        PlexSignInUiState.Starting -> StatusCard("Requesting a secure code from Plex…")
        PlexSignInUiState.Discovering -> StatusCard("Signed in. Finding your Plex servers…")
        is PlexSignInUiState.Connecting -> StatusCard("Connecting to ${state.serverName}…")
        is PlexSignInUiState.Waiting -> AuthorizationCard(
            state = state,
            handheld = handheld,
            onOpenBrowser = onOpenBrowser,
            onCancel = onCancel,
        )
        is PlexSignInUiState.SelectServer -> ServerPicker(
            servers = state.servers,
            handheld = handheld,
            onSelectServer = onSelectServer,
            onCancel = onCancel,
        )
    }
}

@Composable
private fun AuthorizationCard(
    state: PlexSignInUiState.Waiting,
    handheld: Boolean,
    onOpenBrowser: (String) -> Unit,
    onCancel: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MinovaSurface, RoundedCornerShape(18.dp))
            .border(1.dp, MinovaSurfaceRaised, RoundedCornerShape(18.dp))
            .padding(20.dp),
        horizontalArrangement = Arrangement.spacedBy(22.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!handheld) {
            QrCode(content = state.authorizationUrl, modifier = Modifier.size(196.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                if (handheld) "Finish in your browser" else "Scan with your phone",
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "Use any browser to approve Minova. The Plex app is not required.",
                color = MinovaMuted,
                modifier = Modifier.padding(top = 6.dp),
            )
            Text(
                "plex.tv/link   •   code ${state.code.uppercase()}",
                color = MinovaCyan,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 14.dp),
            )
            Text("Waiting for approval…", color = MinovaTeal, modifier = Modifier.padding(top = 10.dp))
            if (handheld) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    PrimaryButton(
                        text = "Open Plex website",
                        handheld = true,
                        enabled = true,
                        onClick = { onOpenBrowser(state.authorizationUrl) },
                    )
                    SecondaryButton("Cancel", true, onCancel)
                }
            } else {
                Row(
                    modifier = Modifier.padding(top = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    PrimaryButton(
                        text = "Open Plex website",
                        handheld = false,
                        enabled = true,
                        onClick = { onOpenBrowser(state.authorizationUrl) },
                    )
                    SecondaryButton("Cancel", false, onCancel)
                }
            }
        }
    }
}

@Composable
private fun ServerPicker(
    servers: List<PlexServerChoice>,
    handheld: Boolean,
    onSelectServer: (String) -> Unit,
    onCancel: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MinovaSurface, RoundedCornerShape(18.dp))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Choose a Plex server", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        Text("Minova will automatically prefer its fastest reachable local address.", color = MinovaMuted)
        servers.forEach { server ->
            PrimaryButton(
                text = "${server.name}  ·  ${if (server.owned) "Owned" else "Shared"}",
                handheld = handheld,
                enabled = true,
                onClick = { onSelectServer(server.id) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        SecondaryButton("Cancel", handheld, onCancel)
    }
}

@Composable
private fun StatusCard(message: String) {
    Text(
        message,
        color = MinovaTeal,
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier
            .fillMaxWidth()
            .background(MinovaSurface, RoundedCornerShape(16.dp))
            .padding(20.dp),
    )
}

@Composable
private fun ManualSetup(
    server: String,
    token: String,
    connecting: Boolean,
    handheld: Boolean,
    onServerChange: (String) -> Unit,
    onTokenChange: (String) -> Unit,
    onConnect: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 18.dp)
            .background(MinovaSurface.copy(alpha = 0.55f), RoundedCornerShape(18.dp))
            .padding(18.dp),
    ) {
        Text(
            "For reverse proxies, Tailscale, or custom server addresses.",
            color = MinovaMuted,
            modifier = Modifier.padding(bottom = 16.dp),
        )
        TvTextField(
            value = server,
            onValueChange = onServerChange,
            label = "Plex server",
            hint = "192.168.1.10:32400",
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
        )
        Spacer(Modifier.height(16.dp))
        TvTextField(
            value = token,
            onValueChange = onTokenChange,
            label = "X-Plex-Token",
            hint = "Paste your Plex token",
            modifier = Modifier.fillMaxWidth(),
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (!connecting) onConnect() }),
        )
        PrimaryButton(
            text = if (connecting) "Connecting…" else "Connect manually",
            handheld = handheld,
            enabled = !connecting,
            onClick = onConnect,
            modifier = Modifier.padding(top = 18.dp),
        )
    }
}

@Composable
private fun PrimaryButton(
    text: String,
    handheld: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (handheld) {
        androidx.compose.material3.Button(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier.fillMaxWidth().heightIn(min = 52.dp),
            shape = RoundedCornerShape(14.dp),
            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                containerColor = MinovaCyan,
                contentColor = Color(0xFF001419),
            ),
        ) { Text(text) }
    } else {
        Button(onClick = onClick, enabled = enabled, modifier = modifier) { Text(text) }
    }
}

@Composable
private fun SecondaryButton(
    text: String,
    handheld: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (handheld) {
        androidx.compose.material3.OutlinedButton(
            onClick = onClick,
            modifier = modifier.fillMaxWidth().heightIn(min = 48.dp),
            shape = RoundedCornerShape(14.dp),
        ) { Text(text, color = Color.White) }
    } else {
        androidx.tv.material3.OutlinedButton(onClick = onClick, modifier = modifier) { Text(text) }
    }
}

@Composable
private fun QrCode(content: String, modifier: Modifier = Modifier) {
    val bitmap = remember(content) { createQrBitmap(content) }
    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "Plex authorization QR code",
            modifier = modifier.background(Color.White, RoundedCornerShape(12.dp)).padding(10.dp),
        )
    }
}

private fun createQrBitmap(content: String, size: Int = 640): Bitmap? = runCatching {
    val matrix = MultiFormatWriter().encode(
        content,
        BarcodeFormat.QR_CODE,
        size,
        size,
        mapOf(EncodeHintType.MARGIN to 1),
    )
    Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).apply {
        for (y in 0 until size) {
            for (x in 0 until size) {
                setPixel(x, y, if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
            }
        }
    }
}.getOrNull()

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
