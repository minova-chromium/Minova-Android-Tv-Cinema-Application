package com.minova.cinema.domain

fun hasSeriesPlaybackProgress(
    show: MediaContent,
    continueWatching: List<MediaContent>,
): Boolean = show.kind == MediaKind.Show && (
    show.viewedLeafCount > 0 || show.isWatched ||
        continueWatching.any { it.belongsTo(show) }
    )

fun selectSeriesPlaybackEpisode(
    show: MediaContent,
    continueWatching: List<MediaContent>,
    orderedEpisodes: List<MediaContent>,
): MediaContent? {
    if (show.kind != MediaKind.Show) return null
    val continueEpisodes = continueWatching.filter { it.belongsTo(show) }
    return continueEpisodes.firstOrNull { it.viewOffsetMs > 0L && !it.isWatched }
        ?: continueEpisodes.firstOrNull { !it.isWatched }
        ?: orderedEpisodes.firstOrNull { it.viewOffsetMs > 0L && !it.isWatched }
        ?: orderedEpisodes.firstOrNull { !it.isWatched }
        ?: orderedEpisodes.firstOrNull()
}

private fun MediaContent.belongsTo(show: MediaContent): Boolean =
    kind == MediaKind.Episode && grandparentRatingKey == show.ratingKey
