package com.minova.cinema.domain

import org.junit.Assert.*
import org.junit.Test

class LibraryDiscoveryTest {
    private fun movie(id: String, title: String = id) = MediaContent(
        ratingKey = id, title = title, secondaryTitle = null, summary = null, tagline = null,
        year = 2025, durationMs = 90 * 60_000L, viewOffsetMs = 0, posterUrl = null,
        backdropUrl = null, contentRating = null, kind = MediaKind.Movie,
        genres = listOf("Action"), audioLanguages = listOf("en"), resolution = "1080",
    )

    @Test fun combinesEveryFilterInsteadOfReplacingPreviousChoices() {
        val expected = movie("match")
        val items = listOf(expected, expected.copy(ratingKey = "watched", isWatched = true),
            expected.copy(ratingKey = "progress", viewOffsetMs = 10_000),
            expected.copy(ratingKey = "year", year = 2020), expected.copy(ratingKey = "resolution", resolution = null),
            expected.copy(ratingKey = "language", audioLanguages = emptyList()),
            expected.copy(ratingKey = "genre", genres = listOf("Drama")))
        assertEquals(listOf(expected), filterLibrary(items, LibraryFilter("action", WatchFilter.Unwatched, 2025, "1080", "EN")))
        assertEquals(items.size, filterLibrary(items, LibraryFilter()).size)
    }

    @Test fun separatesWatchStatesAndExcludesCompletedProgress() {
        val items = listOf(movie("new"), movie("started").copy(viewOffsetMs = 500),
            movie("complete").copy(viewOffsetMs = 90 * 60_000, isWatched = true))
        assertEquals(listOf("started"), filterLibrary(items, LibraryFilter(watch = WatchFilter.InProgress)).map { it.ratingKey })
        assertEquals(listOf("complete"), filterLibrary(items, LibraryFilter(watch = WatchFilter.Watched)).map { it.ratingKey })
    }

    @Test fun sortingIsStableWithMissingAndTiedMetadata() {
        val items = listOf(movie("b", "Beta"), movie("z", "alpha").copy(audienceRating = 9.0, addedAtEpochSeconds = 300),
            movie("a", "alpha").copy(audienceRating = 9.0, addedAtEpochSeconds = 300))
        assertEquals(listOf("a", "z", "b"), filterLibrary(items, LibraryFilter(sort = LibrarySort.Rating)).map { it.ratingKey })
        assertEquals(listOf("a", "z", "b"), filterLibrary(items, LibraryFilter(sort = LibrarySort.Added)).map { it.ratingKey })
    }

    @Test fun movieNightRespectsTimeGenresAndUnwatchedWithoutDuplicates() {
        val eligible = movie("eligible")
        val movies = listOf(eligible, eligible, movie("long").copy(durationMs = 200 * 60_000),
            movie("unknown").copy(durationMs = null), movie("zero").copy(durationMs = 0),
            movie("seen").copy(isWatched = true), movie("started").copy(viewOffsetMs = 10),
            movie("show").copy(kind = MediaKind.Show), movie("genre").copy(genres = listOf("Drama")))
        assertEquals(listOf(eligible), movieNightChoices(movies, 90, setOf("Action"), 1))
        assertTrue(movieNightChoices(movies, 60, emptySet(), 1).isEmpty())
    }

    @Test fun pickerIsReproducibleAndLimitedToFive() {
        val movies = (1..30).map { movie("$it") }
        assertEquals(movieNightChoices(movies, 120, emptySet(), 4), movieNightChoices(movies, 120, emptySet(), 4))
        assertEquals(5, movieNightChoices(movies, 120, emptySet(), 4).size)
        assertNotEquals(movieNightChoices(movies, 120, emptySet(), 4), movieNightChoices(movies, 120, emptySet(), 5))
    }

    @Test fun collectionsUsePlexMembershipAndReleaseOrderNotTitleGuessing() {
        val older = movie("older").copy(collections = listOf("Saga"), releaseDate = "2001-01-01")
        val later = movie("later").copy(collections = listOf("Saga"), releaseDate = "2005-01-01")
        val unknown = movie("unknown").copy(collections = listOf("Saga"), year = null)
        assertEquals(listOf(older, later, unknown), collectionMembers(listOf(later, movie("fake", "Saga Part 3"), unknown, older, older), "Saga"))
    }

    @Test fun recommendationsNeverContainFinishedMovieOrWatchedTitles() {
        val source = movie("source")
        val good = movie("next")
        assertEquals(listOf(good), relatedTitles(source, listOf(source, good, movie("seen").copy(isWatched = true), movie("different").copy(genres = listOf("Comedy")))))
    }
}
