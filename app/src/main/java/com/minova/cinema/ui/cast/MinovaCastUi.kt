package com.minova.cinema.ui.cast

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.mediarouter.app.MediaRouteButton
import android.view.ContextThemeWrapper
import androidx.tv.material3.Text
import com.google.android.gms.cast.framework.CastButtonFactory
import com.minova.cinema.R
import com.minova.cinema.cast.CastPlaybackStatus
import com.minova.cinema.cast.MinovaCastState
import com.minova.cinema.ui.theme.MinovaCyan
import com.minova.cinema.ui.theme.MinovaMuted
import com.minova.cinema.ui.theme.MinovaSurfaceRaised
import com.minova.cinema.ui.theme.MinovaWhite

/** Native route chooser, embedded in Compose so discovery and connection stay SDK-managed. */
@Composable
fun MinovaCastButton(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    AndroidView(
        factory = { viewContext ->
            val routeContext = ContextThemeWrapper(
                viewContext,
                R.style.Theme_MinovaCinema_CastRoute,
            )
            MediaRouteButton(routeContext).apply {
                CastButtonFactory.setUpMediaRouteButton(context.applicationContext, this)
                contentDescription = "Cast to TV"
            }
        },
        modifier = modifier.size(48.dp).padding(10.dp),
    )
}

/** Compact remote that remains available while the user browses on the phone. */
@Composable
fun MinovaCastMiniController(
    state: MinovaCastState,
    onOpen: () -> Unit,
    onTogglePlayback: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!state.hasMedia) return
    val progress = if (state.durationMs > 0L) {
        (state.positionMs.toFloat() / state.durationMs.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MinovaSurfaceRaised.copy(alpha = 0.98f))
            .clickable(role = Role.Button, onClickLabel = "Open TV controls", onClick = onOpen),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    state.content?.title.orEmpty(),
                    color = MinovaWhite,
                    fontSize = 14.sp,
                    maxLines = 1,
                )
                Text(
                    "Playing on ${state.deviceName ?: "TV"}",
                    color = MinovaMuted,
                    fontSize = 11.sp,
                    maxLines = 1,
                )
            }
            CastMiniAction(
                description = if (state.status == CastPlaybackStatus.Playing) "Pause" else "Play",
                onClick = onTogglePlayback,
            ) {
                Icon(
                    if (state.status == CastPlaybackStatus.Playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = null,
                    tint = MinovaWhite,
                )
            }
            Spacer(Modifier.width(2.dp))
            CastMiniAction(description = "Stop casting", onClick = onStop) {
                Icon(Icons.Rounded.Close, contentDescription = null, tint = MinovaWhite)
            }
        }
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth().height(3.dp),
            color = MinovaCyan,
            trackColor = Color.Transparent,
        )
    }
}

@Composable
private fun CastMiniAction(
    description: String,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .semantics { contentDescription = description }
            .clickable(role = Role.Button, onClickLabel = description, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}
