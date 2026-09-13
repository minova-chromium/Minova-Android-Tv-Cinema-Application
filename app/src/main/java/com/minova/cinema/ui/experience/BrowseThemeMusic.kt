package com.minova.cinema.ui.experience

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.delay

@Composable
internal fun BrowseThemeMusic(url: String?, enabled: Boolean, volume: Float) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    var foreground by remember { mutableStateOf(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, _ -> foreground = owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    // No player or audio resources at all when the optional feature is disabled.
    if (enabled && foreground && url != null) {
        val player = remember { ExoPlayer.Builder(context).build() }
        DisposableEffect(player) { onDispose { player.release() } }
        LaunchedEffect(volume) { player.volume = volume.coerceIn(0f, 0.2f) }
        LaunchedEffect(url) {
            player.stop()
            delay(1_200) // Don't download/play every title crossed while scrolling.
            player.setMediaItem(MediaItem.fromUri(url))
            player.prepare()
            player.play()
        }
    }
}
