package com.minova.cinema.ui.player

import android.view.KeyEvent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.minova.cinema.R
import com.minova.cinema.ui.theme.MinovaCyan
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

const val MINOVA_TRAILER_PRE_ROLL_DURATION_MS = 4_000L

/**
 * A renderer-independent Minova card shown before Plex trailer items.
 *
 * [showRecordingDisclaimer] is deliberately supplied by the Activity instead
 * of reading build state here. Production playback always passes false; only
 * the explicit debug capture intent is allowed to display the extra line.
 */
@Composable
fun MinovaTrailerPreRoll(
    showRecordingDisclaimer: Boolean,
    modifier: Modifier = Modifier,
    durationMs: Long = MINOVA_TRAILER_PRE_ROLL_DURATION_MS,
    onFinished: () -> Unit,
) {
    val latestOnFinished by rememberUpdatedState(onFinished)
    val focusRequester = remember { FocusRequester() }
    val logoAlpha = remember { Animatable(0f) }
    val logoScale = remember { Animatable(0.82f) }
    val taglineAlpha = remember { Animatable(0f) }
    val disclaimerAlpha = remember { Animatable(0f) }
    val glowAlpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        coroutineScope {
            launch {
                glowAlpha.animateTo(0.72f, tween(1_250, easing = FastOutSlowInEasing))
            }
            launch {
                logoAlpha.animateTo(1f, tween(900, easing = FastOutSlowInEasing))
            }
            launch {
                logoScale.animateTo(1f, tween(1_100, easing = FastOutSlowInEasing))
            }
            launch {
                delay(420)
                taglineAlpha.animateTo(1f, tween(780, easing = FastOutSlowInEasing))
            }
            if (showRecordingDisclaimer) {
                launch {
                    delay(900)
                    disclaimerAlpha.animateTo(1f, tween(620, easing = FastOutSlowInEasing))
                }
            }
        }

        val introTimelineMs = if (showRecordingDisclaimer) 1_520L else 1_250L
        delay((durationMs - introTimelineMs - 550L).coerceAtLeast(150L))
        coroutineScope {
            launch { glowAlpha.animateTo(0f, tween(550)) }
            launch { logoAlpha.animateTo(0f, tween(550)) }
            launch { taglineAlpha.animateTo(0f, tween(500)) }
            launch { disclaimerAlpha.animateTo(0f, tween(450)) }
            launch { logoScale.animateTo(1.035f, tween(550)) }
        }
        latestOnFinished()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                // No D-pad command may activate the player underneath while
                // the theatrical card owns the screen.
                event.type == KeyEventType.KeyDown ||
                    event.nativeKeyEvent.action == KeyEvent.ACTION_UP
            }
            .testTag("minova-trailer-preroll"),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(620.dp)
                .graphicsLayer { alpha = glowAlpha.value }
                .background(
                    Brush.radialGradient(
                        0f to MinovaCyan.copy(alpha = 0.2f),
                        0.42f to MinovaCyan.copy(alpha = 0.055f),
                        1f to Color.Transparent,
                    ),
                ),
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            AsyncImage(
                model = R.raw.minova_symbol_color,
                contentDescription = "Minova",
                modifier = Modifier
                    .size(190.dp)
                    .graphicsLayer {
                        alpha = logoAlpha.value
                        scaleX = logoScale.value
                        scaleY = logoScale.value
                    },
            )
            Spacer(Modifier.height(30.dp))
            Box(
                Modifier
                    .width(96.dp)
                    .height(2.dp)
                    .graphicsLayer { alpha = taglineAlpha.value }
                    .background(MinovaCyan),
            )
            Spacer(Modifier.height(25.dp))
            androidx.tv.material3.Text(
                text = "BROUGHT TO YOU BY MINOVA",
                color = Color.White,
                fontSize = 26.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 4.2.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.graphicsLayer { alpha = taglineAlpha.value },
            )
            if (showRecordingDisclaimer) {
                Spacer(Modifier.height(17.dp))
                androidx.tv.material3.Text(
                    text = "Can be disabled in settings",
                    color = Color.White.copy(alpha = 0.72f * disclaimerAlpha.value),
                    fontSize = 17.sp,
                    letterSpacing = 0.6.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.testTag("promo-recording-disclaimer"),
                )
            }
        }
    }
}
