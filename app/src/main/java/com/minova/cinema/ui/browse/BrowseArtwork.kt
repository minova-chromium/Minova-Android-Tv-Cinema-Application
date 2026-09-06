package com.minova.cinema.ui.browse

import android.os.SystemClock
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil3.compose.AsyncImage
import coil3.compose.asPainter
import coil3.imageLoader
import coil3.request.SuccessResult
import com.minova.cinema.data.local.artworkRequest

/** Aggregate timings only: never store URLs, titles, or credentials. */
object ArtworkMetrics {
    data class Sample(val elapsedMs: Long, val source: String, val success: Boolean)
    private val samples = ArrayDeque<Sample>()
    @Synchronized fun record(start: Long, source: String, success: Boolean) {
        if (samples.size == 500) samples.removeFirst()
        samples.addLast(Sample(SystemClock.elapsedRealtime() - start, source, success))
    }
    @Synchronized fun snapshot(): List<Sample> = samples.toList()
    @Synchronized fun reset() = samples.clear()
}

@Composable
internal fun BrowseArtwork(url: String?, description: String?, modifier: Modifier, contentScale: ContentScale = ContentScale.Crop) {
    val context = LocalContext.current
    val request = remember(url, context) { artworkRequest(context, url, false) }
    var started by remember(url) { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    AsyncImage(
        model = request, contentDescription = description, modifier = modifier, contentScale = contentScale,
        onLoading = { started = SystemClock.elapsedRealtime() },
        onSuccess = { ArtworkMetrics.record(started, it.result.dataSource.name, true) },
        onError = { if (url != null) ArtworkMetrics.record(started, "ERROR", false) },
    )
}

/** Retain the decoded previous backdrop until its replacement succeeds. */
@Composable
internal fun StableBackdrop(url: String?, modifier: Modifier) {
    val context = LocalContext.current
    var displayed by remember { mutableStateOf<SuccessResult?>(null) }
    LaunchedEffect(url) {
        if (url == null) { displayed = null; return@LaunchedEffect }
        val start = SystemClock.elapsedRealtime()
        val result = context.imageLoader.execute(artworkRequest(context, url, true))
        ArtworkMetrics.record(start, (result as? SuccessResult)?.dataSource?.name ?: "ERROR", result is SuccessResult)
        if (result is SuccessResult) displayed = result
    }
    Crossfade(targetState = displayed, animationSpec = tween(420), label = "ready_backdrop", modifier = modifier) { result ->
        if (result != null) Image(
            painter = result.image.asPainter(context), contentDescription = null,
            contentScale = ContentScale.Crop, modifier = modifier,
        )
    }
}
