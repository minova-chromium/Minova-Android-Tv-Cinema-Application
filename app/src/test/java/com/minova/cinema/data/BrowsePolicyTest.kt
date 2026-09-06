package com.minova.cinema.data

import com.minova.cinema.data.local.*
import com.minova.cinema.domain.*
import org.junit.Assert.*
import org.junit.Test

class BrowsePolicyTest {
    @Test fun largeLibraryOnlyPrefetchesTwoFollowingTitles() {
        val library = (0 until 5_000).map { movie(it) }
        assertEquals(listOf("3501", "3502"), artworkNeighbours(library, "3500").map { it.ratingKey })
        assertTrue(artworkNeighbours(library, "missing").isEmpty())
        assertTrue(artworkNeighbours(library, "4999").isEmpty())
    }
    @Test fun orderingPreservesNewShelvesAndHonorsHiddenAndRemovedShelves() {
        val layout = HomeLayoutPreferences(shelfOrder = listOf("b", "gone", "a", "b"), hiddenShelves = setOf("a"))
        assertEquals(listOf("b", "c"), orderedShelfKeys(listOf("a", "b", "c"), layout))
        assertTrue(orderedShelfKeys(listOf("a"), layout).isEmpty())
    }
    private fun movie(index: Int) = MediaContent(
        ratingKey = "$index", title = "Movie $index", secondaryTitle = null, summary = null,
        tagline = null, year = 2026, durationMs = 1_000, viewOffsetMs = 0,
        posterUrl = null, backdropUrl = null, contentRating = null, kind = MediaKind.Movie,
    )
}
