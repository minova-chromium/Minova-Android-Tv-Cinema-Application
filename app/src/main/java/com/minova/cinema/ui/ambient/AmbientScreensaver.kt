package com.minova.cinema.ui.ambient

import android.os.SystemClock
import android.view.KeyEvent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.minova.cinema.R
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.random.Random

private const val AMBIENT_TIMEOUT_MS = 300_000L

/**
 * Activity-level state holder so D-pad activity is observed even when focus is
 * inside a Compose Dialog, AndroidView, PlayerView, or deeply nested TV card.
 */
@Stable
class AmbientInactivityTracker {
    var lastInteractionAtMs by mutableLongStateOf(SystemClock.elapsedRealtime())
        private set

    var playbackActive by mutableStateOf(false)
        private set

    var screensaverVisible by mutableStateOf(false)
        private set

    // Consuming only ACTION_DOWN is not enough: Compose TV buttons commonly
    // click on ACTION_UP. Keep consuming the matching gesture after dismissal.
    private val consumedUntilKeyUp = mutableSetOf<Int>()

    /** Returns true only when a key belongs to the gesture dismissing ambient mode. */
    fun onKeyEvent(event: KeyEvent): Boolean {
        if (!event.keyCode.isTvNavigationKey()) return false

        if (event.keyCode in consumedUntilKeyUp) {
            if (event.action == KeyEvent.ACTION_UP) consumedUntilKeyUp.remove(event.keyCode)
            return true
        }

        if (event.action != KeyEvent.ACTION_DOWN) return false
        val dismissingScreensaver = screensaverVisible
        resetTimer()
        if (dismissingScreensaver) consumedUntilKeyUp += event.keyCode
        return dismissingScreensaver
    }

    /** Active means Media3 is READY and isPlaying, not merely buffering or prepared. */
    fun updatePlaybackActivity(active: Boolean) {
        if (playbackActive == active) return
        playbackActive = active
        // Begin a fresh five-minute window when playback pauses/stops. This
        // prevents time spent watching from being counted as menu inactivity.
        resetTimer()
    }

    internal fun showIfStillIdle(expectedInteractionAtMs: Long) {
        if (!playbackActive && lastInteractionAtMs == expectedInteractionAtMs) {
            screensaverVisible = true
        }
    }

    private fun resetTimer() {
        lastInteractionAtMs = SystemClock.elapsedRealtime()
        screensaverVisible = false
    }
}

/** Places the app and OLED-safe ambient layer in one fullscreen composition. */
@Composable
fun AmbientScreensaverHost(
    tracker: AmbientInactivityTracker,
    content: @Composable () -> Unit,
) {
    val lastInteractionAtMs = tracker.lastInteractionAtMs
    val playbackActive = tracker.playbackActive

    LaunchedEffect(lastInteractionAtMs, playbackActive) {
        if (playbackActive) return@LaunchedEffect
        val elapsed = (SystemClock.elapsedRealtime() - lastInteractionAtMs).coerceAtLeast(0L)
        delay((AMBIENT_TIMEOUT_MS - elapsed).coerceAtLeast(0L))
        tracker.showIfStillIdle(lastInteractionAtMs)
    }

    Box(Modifier.fillMaxSize()) {
        content()
        if (tracker.screensaverVisible) {
            BouncingLogoScreensaver(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(100f),
            )
        }
    }
}

/**
 * Classic DVD-style motion using frame time, pixel boundaries, and perfect
 * reflection. The asset is the cleaned Android rendering of the supplied
 * Minova wordmark, so the original alpha silhouette remains intact.
 */
@Composable
fun BouncingLogoScreensaver(modifier: Modifier = Modifier) {
    val neonColors = listOf(
        Color(0xFF00E5FF), // Minova cyan
        Color(0xFFFF4DDF), // neon magenta
        Color(0xFFFFEA00), // cinema yellow
        Color(0xFF42FF8A), // neon green
    )
    var tintIndex by remember { mutableIntStateOf(0) }

    BoxWithConstraints(
        modifier = modifier.background(Color.Black),
        contentAlignment = Alignment.TopStart,
    ) {
        val density = LocalDensity.current
        val logoWidth = minOf(320.dp, maxWidth * 0.34f)
        val logoHeight = logoWidth * (158f / 854f)
        val screenWidthPx = with(density) { maxWidth.toPx() }
        val screenHeightPx = with(density) { maxHeight.toPx() }
        val logoWidthPx = with(density) { logoWidth.toPx() }
        val logoHeightPx = with(density) { logoHeight.toPx() }
        val maxX = (screenWidthPx - logoWidthPx).coerceAtLeast(0f)
        val maxY = (screenHeightPx - logoHeightPx).coerceAtLeast(0f)
        val horizontalSpeed = with(density) { 84.dp.toPx() }
        val verticalSpeed = with(density) { 61.dp.toPx() }

        var position by remember(maxX, maxY) {
            mutableStateOf(Offset(maxX * 0.19f, maxY * 0.31f))
        }
        var velocity by remember(horizontalSpeed, verticalSpeed) {
            mutableStateOf(Offset(horizontalSpeed, verticalSpeed))
        }

        LaunchedEffect(maxX, maxY, horizontalSpeed, verticalSpeed) {
            position = Offset(maxX * 0.19f, maxY * 0.31f)
            velocity = Offset(horizontalSpeed, verticalSpeed)
            var previousFrameNanos = withFrameNanos { it }

            while (true) {
                withFrameNanos { frameNanos ->
                    // Clamp long frames so returning from a system interruption
                    // cannot teleport the logo through an edge.
                    val deltaSeconds = ((frameNanos - previousFrameNanos) / 1_000_000_000f)
                        .coerceIn(0f, 0.05f)
                    previousFrameNanos = frameNanos

                    var nextX = position.x + velocity.x * deltaSeconds
                    var nextY = position.y + velocity.y * deltaSeconds
                    var velocityX = velocity.x
                    var velocityY = velocity.y
                    var collided = false

                    if (nextX <= 0f && velocityX < 0f) {
                        nextX = -nextX
                        velocityX = abs(velocityX)
                        collided = true
                    } else if (nextX >= maxX && velocityX > 0f) {
                        nextX = (2f * maxX - nextX).coerceAtLeast(0f)
                        velocityX = -abs(velocityX)
                        collided = true
                    }

                    if (nextY <= 0f && velocityY < 0f) {
                        nextY = -nextY
                        velocityY = abs(velocityY)
                        collided = true
                    } else if (nextY >= maxY && velocityY > 0f) {
                        nextY = (2f * maxY - nextY).coerceAtLeast(0f)
                        velocityY = -abs(velocityY)
                        collided = true
                    }

                    position = Offset(nextX.coerceIn(0f, maxX), nextY.coerceIn(0f, maxY))
                    velocity = Offset(velocityX, velocityY)
                    if (collided) {
                        // Advance by at least one, so every impact visibly
                        // changes color instead of randomly choosing the same.
                        tintIndex = (tintIndex + Random.nextInt(1, neonColors.size)) % neonColors.size
                    }
                }
            }
        }

        Image(
            painter = painterResource(R.drawable.minova_wordmark),
            contentDescription = "Minova ambient screensaver",
            colorFilter = ColorFilter.tint(neonColors[tintIndex]),
            modifier = Modifier
                .size(width = logoWidth, height = logoHeight)
                .graphicsLayer {
                    translationX = position.x
                    translationY = position.y
                },
        )
    }
}

private fun Int.isTvNavigationKey(): Boolean = this == KeyEvent.KEYCODE_DPAD_UP ||
    this == KeyEvent.KEYCODE_DPAD_DOWN ||
    this == KeyEvent.KEYCODE_DPAD_LEFT ||
    this == KeyEvent.KEYCODE_DPAD_RIGHT ||
    this == KeyEvent.KEYCODE_DPAD_CENTER ||
    this == KeyEvent.KEYCODE_ENTER ||
    this == KeyEvent.KEYCODE_NUMPAD_ENTER ||
    this == KeyEvent.KEYCODE_BUTTON_A
