package com.minova.cinema.data.local

import android.content.Context
import coil3.imageLoader
import coil3.request.ImageRequest
import com.minova.cinema.domain.CinemaCatalog

/** Warms the exact Plex artwork used by the first TV rows to prevent focus flashes. */
class PlexArtworkPrefetcher(context: Context) {
    private val appContext = context.applicationContext
    private val imageLoader = appContext.imageLoader

    fun prefetch(catalog: CinemaCatalog) {
        val media = buildList {
            addAll(catalog.continueWatching.take(20))
            addAll(catalog.myList.take(20))
            addAll(catalog.movies.sortedByDescending { it.addedAtEpochSeconds }.take(40))
            addAll(catalog.shows.sortedByDescending { it.addedAtEpochSeconds }.take(40))
        }.distinctBy { it.ratingKey }

        media.flatMap { listOfNotNull(it.backdropUrl, it.posterUrl) }
            .distinct()
            .forEach { url ->
                imageLoader.enqueue(
                    ImageRequest.Builder(appContext)
                        .data(url)
                        .build(),
                )
            }
    }
}
