package com.minova.cinema.ui.browse

import com.minova.cinema.domain.CinemaCatalog
import com.minova.cinema.domain.MediaContent
import com.minova.cinema.domain.MediaKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeDiscoveryShelvesTest {
    private fun movie(
        id: String,
        genres: List<String>,
        watched: Boolean = false,
        lastViewedAt: Long? = null,
        addedAt: Long = 1,
    ) = MediaContent(
        ratingKey = id,
        title = id,
        secondaryTitle = null,
        summary = null,
        tagline = null,
        year = 2025,
        addedAtEpochSeconds = addedAt,
        durationMs = 90 * 60_000L,
        viewOffsetMs = if (watched) 90 * 60_000L else 0L,
        posterUrl = null,
        backdropUrl = null,
        contentRating = null,
        kind = MediaKind.Movie,
        genres = genres,
        isWatched = watched,
        audienceRating = 7.5,
        lastViewedAtEpochSeconds = lastViewedAt,
    )

    @Test
    fun personalizedShelvesAreCappedAndDoNotRepeatTheirLeadingCatalog() {
        val anchor = movie("Recent anchor", listOf("Drama", "Horror"), watched = true, lastViewedAt = 500)
        val candidates = (1..60).map { index ->
            movie("Candidate $index", if (index <= 30) listOf("Horror") else listOf("Drama"), addedAt = index.toLong())
        }
        val shelves = buildHomeDiscoveryShelves(
            CinemaCatalog("Test", listOf(anchor) + candidates, emptyList(), emptyList()),
        )
        val because = shelves.single { it.key == "because-you-watched" }.media
        val topPicks = shelves.single { it.key == "top-picks" }.media

        assertEquals(24, because.size)
        assertEquals(24, topPicks.size)
        assertTrue(because.map(MediaContent::ratingKey).toSet().intersect(topPicks.map(MediaContent::ratingKey).toSet()).isEmpty())
        assertFalse(because.any { it.ratingKey == anchor.ratingKey })
    }

    @Test
    fun becauseYouWatchedUsesTheMostRecentlyViewedTitleAndItsDistinctiveGenre() {
        val older = movie("Old sci-fi", listOf("Science Fiction"), watched = true, lastViewedAt = 100, addedAt = 9_999)
        val latest = movie("Latest horror", listOf("Drama", "Horror"), watched = true, lastViewedAt = 900)
        val commonDrama = (1..12).map { movie("Drama $it", listOf("Drama")) }
        val horror = listOf(movie("Horror one", listOf("Horror")), movie("Horror two", listOf("Horror")))
        val shelves = buildHomeDiscoveryShelves(
            CinemaCatalog("Test", listOf(older, latest) + commonDrama + horror, emptyList(), emptyList()),
        )
        val because = shelves.single { it.key == "because-you-watched" }

        assertEquals("Because You Watched Latest horror", because.title)
        assertEquals(horror.map(MediaContent::ratingKey).toSet(), because.media.map(MediaContent::ratingKey).toSet())
        assertNotEquals("Because You Watched Old sci-fi", because.title)
    }

    @Test
    fun favoriteGenreShelfIsSuppressedWhenItWouldDuplicateAnotherPersonalizedShelf() {
        val anchor = movie("Anchor", listOf("Mystery"), watched = true, lastViewedAt = 100)
        val choices = (1..5).map { movie("Mystery $it", listOf("Mystery")) }
        val shelves = buildHomeDiscoveryShelves(
            CinemaCatalog("Test", listOf(anchor) + choices, emptyList(), emptyList()),
        )

        assertFalse(shelves.any { it.key.startsWith("favorite-") })
    }
}
