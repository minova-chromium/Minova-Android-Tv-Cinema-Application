package com.minova.cinema.data.local

import android.content.Context
import coil3.imageLoader
import coil3.request.ImageRequest
import com.minova.cinema.domain.MediaContent
import kotlinx.coroutines.delay

/** Cancellable, sequential prefetch of two neighbours, never a whole-library burst. */
class PlexArtworkPrefetcher(context: Context) {
    private val appContext = context.applicationContext
    private val imageLoader = appContext.imageLoader

    suspend fun prefetch(media: List<MediaContent>, focusedKey: String) {
        delay(800)
        for (item in artworkNeighbours(media, focusedKey)) {
            item.posterUrl?.let { imageLoader.execute(artworkRequest(appContext, it, false)) }
        }
    }
}

internal fun artworkNeighbours(media: List<MediaContent>, focusedKey: String): List<MediaContent> {
    val index = media.indexOfFirst { it.ratingKey == focusedKey }
    return if (index < 0) emptyList() else media.drop(index + 1).take(2)
}

/** Same bounded decode size in foreground and prefetch, sharing Coil's cache. */
fun artworkRequest(context: Context, url: String?, backdrop: Boolean): ImageRequest =
    ImageRequest.Builder(context).data(url)
        .size(if (backdrop) 1920 else 400, if (backdrop) 1080 else 600).build()
