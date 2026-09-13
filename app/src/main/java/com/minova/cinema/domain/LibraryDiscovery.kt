package com.minova.cinema.domain

enum class WatchFilter(val label: String) { All("Any watch status"), Unwatched("Unwatched"), InProgress("In progress"), Watched("Watched") }
enum class LibrarySort(val label: String) { Title("Title A–Z"), Added("Recently added"), Released("Release date"), Rating("Highest rated") }
data class LibraryFilter(
    val genre: String? = null,
    val watch: WatchFilter = WatchFilter.All,
    val year: Int? = null,
    val resolution: String? = null,
    val language: String? = null,
    val sort: LibrarySort = LibrarySort.Title,
)

fun filterLibrary(items: List<MediaContent>, filter: LibraryFilter): List<MediaContent> {
    val result = items.filter { item ->
        (filter.genre == null || item.genres.any { it.equals(filter.genre, true) }) &&
            (filter.year == null || item.year == filter.year) &&
            (filter.resolution == null || item.resolution == filter.resolution) &&
            (filter.language == null || item.audioLanguages.any { it.equals(filter.language, true) }) &&
            when (filter.watch) {
                WatchFilter.All -> true
                WatchFilter.Unwatched -> !item.isWatched && item.viewOffsetMs == 0L
                WatchFilter.InProgress -> item.viewOffsetMs > 0L && item.progress < 1f
                WatchFilter.Watched -> item.isWatched
            }
    }
    val titleOrder = compareBy<MediaContent> { it.title.lowercase(java.util.Locale.ROOT) }.thenBy { it.ratingKey }
    return when (filter.sort) {
        LibrarySort.Title -> result.sortedWith(titleOrder)
        LibrarySort.Added -> result.sortedWith(compareByDescending<MediaContent> { it.addedAtEpochSeconds ?: 0L }.then(titleOrder))
        LibrarySort.Released -> result.sortedWith(compareByDescending<MediaContent> { it.releaseDate ?: "${it.year ?: 0}-00-00" }.then(titleOrder))
        LibrarySort.Rating -> result.sortedWith(compareByDescending<MediaContent> { it.audienceRating ?: -1.0 }.then(titleOrder))
    }
}

fun movieNightChoices(movies: List<MediaContent>, minutes: Int, genres: Set<String>, seed: Int): List<MediaContent> =
    movies.filter { it.kind == MediaKind.Movie && !it.isWatched && it.viewOffsetMs == 0L &&
        it.durationMs != null && it.durationMs > 0 && it.durationMs <= minutes.toLong() * 60_000 &&
        (genres.isEmpty() || it.genres.any { genre -> genre in genres }) }
        .distinctBy { it.ratingKey }.shuffled(kotlin.random.Random(seed)).take(5)

fun collectionMembers(items: List<MediaContent>, name: String): List<MediaContent> = items
    .filter { name in it.collections }.distinctBy { it.ratingKey }
    .sortedWith(compareBy<MediaContent> { it.releaseDate ?: "${it.year ?: 9999}-12-31" }.thenBy { it.title })

fun relatedTitles(movie: MediaContent, movies: List<MediaContent>): List<MediaContent> = movies
    .filter { it.ratingKey != movie.ratingKey && !it.isWatched && it.genres.any { genre -> genre in movie.genres } }
    .sortedWith(compareByDescending<MediaContent> { it.genres.count { genre -> genre in movie.genres } }
        .thenByDescending { it.audienceRating ?: 0.0 }).take(6)
