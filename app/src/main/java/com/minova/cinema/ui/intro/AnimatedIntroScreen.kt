package com.minova.cinema.ui.intro

import android.media.AudioAttributes
import android.media.SoundPool
import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.minova.cinema.R
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val INTRO_DURATION_MS = 3_500L
private const val LOGO_ANIMATION_MS = 2_000

/**
 * Full-screen Minova Cinema launch sequence using the official wordmarks.
 *
 * The optional chime is loaded before the timeline begins so its first sample
 * and the first visible logo frame are synchronized. If res/raw/intro_chime.*
 * is absent, the animation starts immediately and silently.
 */
@Composable
fun AnimatedIntroScreen(onFinished: () -> Unit) {
    val context = LocalContext.current
    val latestOnFinished by rememberUpdatedState(onFinished)
    val focusRequester = remember { FocusRequester() }

    val logoAlpha = remember { Animatable(0f) }
    val logoScale = remember { Animatable(0.8f) }
    val titleAlpha = remember { Animatable(0f) }
    val subtitleAlpha = remember { Animatable(0f) }

    var audioPrepared by remember { mutableStateOf(false) }
    var completed by remember { mutableStateOf(false) }

    // This guard prevents the timer, BackHandler and D-pad from navigating more
    // than once when two events happen during the same frame.
    fun finishIntro() {
        if (!completed) {
            completed = true
            latestOnFinished()
        }
    }

    // SoundPool is appropriate for a short, low-latency UI cue. getIdentifier
    // keeps the sound optional: adding intro_chime.ogg/wav to res/raw enables it
    // without making a missing binary asset break the build.
    DisposableEffect(context) {
        val chimeResource = context.resources.getIdentifier(
            "intro_chime",
            "raw",
            context.packageName,
        )
        val soundPool = if (chimeResource != 0) {
            SoundPool.Builder()
                .setMaxStreams(1)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                .build()
        } else {
            null
        }

        var disposed = false
        if (soundPool == null) {
            audioPrepared = true
        } else {
            soundPool.setOnLoadCompleteListener { pool, soundId, status ->
                if (!disposed) {
                    if (status == 0) {
                        pool.play(soundId, 1f, 1f, 1, 0, 1f)
                    }
                    // Start even if a malformed optional audio file fails.
                    audioPrepared = true
                }
            }
            soundPool.load(context, chimeResource, 1)
        }

        onDispose {
            disposed = true
            soundPool?.release()
        }
    }

    // Request focus so the intro itself receives remote keys before any other
    // focusable destination is composed.
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    // A single structured timeline keeps the total duration deterministic.
    LaunchedEffect(audioPrepared) {
        if (!audioPrepared) return@LaunchedEffect

        coroutineScope {
            launch {
                logoAlpha.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(LOGO_ANIMATION_MS, easing = FastOutSlowInEasing),
                )
            }
            launch {
                logoScale.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(LOGO_ANIMATION_MS, easing = FastOutSlowInEasing),
                )
            }
            launch {
                delay(500)
                titleAlpha.animateTo(1f, tween(1_100, easing = FastOutSlowInEasing))
            }
            launch {
                delay(1_050)
                subtitleAlpha.animateTo(1f, tween(900, easing = FastOutSlowInEasing))
            }
        }

        // The longest animation ends at 2 seconds; hold the completed lockup
        // until the requested 3.5-second total has elapsed.
        delay(INTRO_DURATION_MS - LOGO_ANIMATION_MS)
        finishIntro()
    }

    // Back must skip, not exit, while the intro is the current destination.
    BackHandler(onBack = ::finishIntro)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.nativeKeyEvent.keyCode) {
                    KeyEvent.KEYCODE_DPAD_CENTER,
                    KeyEvent.KEYCODE_ENTER,
                    KeyEvent.KEYCODE_NUMPAD_ENTER,
                    KeyEvent.KEYCODE_BACK,
                    -> {
                        if (event.nativeKeyEvent.repeatCount == 0) finishIntro()
                        true
                    }
                    else -> false
                }
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        AsyncImage(
            model = R.raw.minova_symbol_color,
            contentDescription = "Minova Cinema",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .size(172.dp)
                .graphicsLayer {
                    alpha = logoAlpha.value
                    scaleX = logoScale.value
                    scaleY = logoScale.value
                },
        )

        Spacer(Modifier.height(28.dp))
        Image(
            painter = painterResource(R.drawable.minova_wordmark),
            contentDescription = "Minova",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .width(360.dp)
                .height(64.dp)
                .graphicsLayer { alpha = titleAlpha.value },
        )
        Spacer(Modifier.height(10.dp))
        Image(
            painter = painterResource(R.drawable.cinema_wordmark),
            contentDescription = "Cinema",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .width(360.dp)
                .height(68.dp)
                .graphicsLayer { alpha = subtitleAlpha.value },
        )
    }
}
