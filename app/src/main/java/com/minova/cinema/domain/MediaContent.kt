package com.minova.cinema.domain

enum class MediaKind {
    Movie,
    Show,
    Season,
    Episode,
    Extra,
}

data class PlexLibrary(
    val key: String,
    val title: String,
    val type: String,
)

data class SubtitleStream(
    val id: Long,
    val label: String,
    val language: String?,
    val key: String?,
    val codec: String?,
    val selected: Boolean,
    val forced: Boolean,
)

data class AudioStream(
    val id: Long,
    val label: String,
    val language: String?,
    val codec: String?,
    val channels: Int?,
    val selected: Boolean,
)

data class MediaCredit(
    val name: String,
    val role: String,
    val imageUrl: String?,
)

data class MediaMarker(
    val type: String,
    val startTimeOffsetMs: Long,
    val endTimeOffsetMs: Long,
    val isFinal: Boolean,
)

data class MediaChapter(
    val title: String,
    val startTimeOffsetMs: Long,
    val endTimeOffsetMs: Long,
)

data class MediaTechnicalInfo(
    val bitrateKbps: Int? = null,
    val width: Int? = null,
    val height: Int? = null,
    val videoResolution: String? = null,
    val videoCodec: String? = null,
    val audioCodec: String? = null,
    val container: String? = null,
)

enum class PlexPlaybackMode(val label: String) {
    DirectPlay("Direct Play"),
    DirectStream("Direct Stream"),
    Transcode("Transcoding"),
    Unknown("Analyzing"),
}

data class PlaybackDiagnostics(
    val mode: PlexPlaybackMode = PlexPlaybackMode.Unknown,
    val reason: String? = null,
    val videoDecision: String? = null,
    val audioDecision: String? = null,
    val source: MediaTechnicalInfo? = null,
)

data class PlaybackSource(
    val partId: Long,
    val directUrl: String,
    val metadataKey: String,
    val audioStreams: List<AudioStream>,
    val subtitles: List<SubtitleStream>,
    val technicalInfo: MediaTechnicalInfo = MediaTechnicalInfo(),
)

data class MediaContent(
    val ratingKey: String,
    val plexGuid: String? = null,
    val identityGuids: Set<String> = emptySet(),
    val title: String,
    val secondaryTitle: String?,
    val summary: String?,
    val tagline: String?,
    val year: Int?,
    val addedAtEpochSeconds: Long? = null,
    val durationMs: Long?,
    val viewOffsetMs: Long,
    val posterUrl: String?,
    val backdropUrl: String?,
    val contentRating: String?,
    val kind: MediaKind,
    val genres: List<String> = emptyList(),
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
    val childCount: Int? = null,
    val parentRatingKey: String? = null,
    val grandparentRatingKey: String? = null,
    val isWatched: Boolean = false,
    val credits: List<MediaCredit> = emptyList(),
    val markers: List<MediaMarker> = emptyList(),
    val chapters: List<MediaChapter> = emptyList(),
    val audienceRating: Double? = null,
    val playback: PlaybackSource? = null,
) {
    val progress: Float
        get() = if (durationMs == null || durationMs <= 0L) 0f
        else (viewOffsetMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)

    val canPlay: Boolean get() = playback != null

    val timeLeftMs: Long?
        get() = durationMs
            ?.takeIf { it > 0L }
            ?.let { (it - viewOffsetMs).coerceAtLeast(0L) }

    val timeLeftLabel: String?
        get() = timeLeftMs?.takeIf { it > 0L }?.let(::formatTimeLeft)

    val remainingMs: Long?
        get() = timeLeftMs?.takeIf { viewOffsetMs > 0L }

    val remainingTimeLabel: String?
        get() = remainingMs?.takeIf { it > 0L }?.let(::formatTimeLeft)

    val metadataLine: String
        get() = buildList {
            year?.let { add(it.toString()) }
            contentRating?.let(::add)
            durationMs?.takeIf {
                kind == MediaKind.Movie || kind == MediaKind.Episode || kind == MediaKind.Extra
            }?.let {
                add("${it / 60_000} min")
            }
            if (kind == MediaKind.Show) childCount?.let { add("$it seasons") }
        }.joinToString("  •  ")
}

private fun formatTimeLeft(remainingMs: Long): String {
    val totalMinutes = (remainingMs + 59_999L) / 60_000L
    val hours = totalMinutes / 60L
    val minutes = totalMinutes % 60L
    return when {
        hours > 0L && minutes > 0L -> "${hours}h ${minutes}m left"
        hours > 0L -> "${hours}h left"
        else -> "${minutes.coerceAtLeast(1L)} min left"
    }
}

data class CinemaCatalog(
    val serverName: String,
    val movies: List<MediaContent>,
    val shows: List<MediaContent>,
    val continueWatching: List<MediaContent>,
    val myList: List<MediaContent> = emptyList(),
)

/** A single Media3 session: trailers, optional local bumper, then the feature. */
data class CinemaPlaybackPlan(
    val mainFeature: MediaContent,
    val trailers: List<MediaContent> = emptyList(),
    val bumperUri: String? = null,
    val cinemaModeActive: Boolean = false,
)
