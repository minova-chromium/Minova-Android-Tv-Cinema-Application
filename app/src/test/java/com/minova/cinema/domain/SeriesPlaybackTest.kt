package com.minova.cinema.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SeriesPlaybackTest {
    private val show = media("show", MediaKind.Show)
    private val first = episode("episode-1", number = 1)
    private val second = episode("episode-2", number = 2)

    @Test
    fun freshSeriesStartsAtFirstUnwatchedEpisode() {
        assertFalse(hasSeriesPlaybackProgress(show, emptyList()))
        assertEquals(first, selectSeriesPlaybackEpisode(show, emptyList(), listOf(first, second)))
    }

    @Test
    fun partiallyWatchedEpisodeResumesAtItsSavedPosition() {
        val inProgress = second.copy(viewOffsetMs = 12_000L)
        assertTrue(hasSeriesPlaybackProgress(show, listOf(inProgress)))
        assertEquals(inProgress, selectSeriesPlaybackEpisode(show, listOf(inProgress), listOf(first, second)))
    }

    @Test
    fun completedEpisodeContinuesWithNextUnwatchedEpisode() {
        val progressedShow = show.copy(viewedLeafCount = 1)
        val watchedFirst = first.copy(isWatched = true)
        assertTrue(hasSeriesPlaybackProgress(progressedShow, emptyList()))
        assertEquals(second, selectSeriesPlaybackEpisode(progressedShow, emptyList(), listOf(watchedFirst, second)))
    }

    @Test
    fun fullyWatchedSeriesFallsBackToFirstEpisodeForReplay() {
        val watchedFirst = first.copy(isWatched = true)
        val watchedSecond = second.copy(isWatched = true)
        assertEquals(watchedFirst, selectSeriesPlaybackEpisode(
            show.copy(viewedLeafCount = 2, isWatched = true),
            emptyList(),
            listOf(watchedFirst, watchedSecond),
        ))
    }

    private fun episode(key: String, number: Int) = media(key, MediaKind.Episode).copy(
        episodeNumber = number,
        grandparentRatingKey = show.ratingKey,
    )

    private fun media(key: String, kind: MediaKind) = MediaContent(
        ratingKey = key,
        title = key,
        secondaryTitle = null,
        summary = null,
        tagline = null,
        year = 2026,
        durationMs = 30_000L,
        viewOffsetMs = 0L,
        posterUrl = null,
        backdropUrl = null,
        contentRating = null,
        kind = kind,
    )
}
