package com.minova.cinema.ui.browse

import android.os.SystemClock
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
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
    val started = remember(url) { SystemClock.elapsedRealtime() }
    var status by remember(url) { mutableStateOf<String?>(if (url == null) "No artwork" else "Loading…") }
    Box(modifier.background(Color(0xFF15202A)), contentAlignment = Alignment.Center) {
    if (status != null) Text(status.orEmpty(), color = Color(0xFFC7D1DB), fontSize = 11.sp)
    AsyncImage(
        model = request, contentDescription = description, modifier = Modifier.matchParentSize(), contentScale = contentScale,
        onSuccess = { status = null; ArtworkMetrics.record(started, it.result.dataSource.name, true) },
        onError = { status = if (url == null) "No artwork" else "Unavailable"; if (url != null) ArtworkMetrics.record(started, "ERROR", false) },
    )
    }
}

/** Retain the decoded previous backdrop until its replacement succeeds. */
@Composable
internal fun StableBackdrop(url: String?, modifier: Modifier) {
    val context = LocalContext.current
    val reducedMotion = com.minova.cinema.ui.experience.LocalExperienceSettings.current.reducedMotion
    var displayed by remember { mutableStateOf<SuccessResult?>(null) }
    LaunchedEffect(url) {
        if (url == null) { displayed = null; return@LaunchedEffect }
        val start = SystemClock.elapsedRealtime()
        val result = context.imageLoader.execute(artworkRequest(context, url, true))
        ArtworkMetrics.record(start, (result as? SuccessResult)?.dataSource?.name ?: "ERROR", result is SuccessResult)
        if (result is SuccessResult) displayed = result
    }
    Crossfade(targetState = displayed, animationSpec = tween(if (reducedMotion) 0 else 420), label = "ready_backdrop", modifier = modifier) { result ->
        if (result != null) Image(
            painter = result.image.asPainter(context), contentDescription = null,
            contentScale = ContentScale.Crop, modifier = modifier,
        )
    }
}
