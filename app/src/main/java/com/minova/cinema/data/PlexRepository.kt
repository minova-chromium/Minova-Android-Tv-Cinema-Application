package com.minova.cinema.data

import com.minova.cinema.data.remote.Metadata
import com.minova.cinema.data.remote.MediaContainer
import com.minova.cinema.data.remote.PlexApiService
import com.minova.cinema.data.remote.PlexConnection
import com.minova.cinema.data.remote.PlexUrlFactory
import com.minova.cinema.data.remote.PlexWatchlistApiService
import com.minova.cinema.domain.CinemaCatalog
import com.minova.cinema.domain.AudioStream
import com.minova.cinema.domain.MediaContent
import com.minova.cinema.domain.MediaKind
import com.minova.cinema.domain.MediaCredit
import com.minova.cinema.domain.MediaMarker
import com.minova.cinema.domain.PlaybackSource
import com.minova.cinema.domain.PlexLibrary
import com.minova.cinema.domain.SubtitleStream
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import java.net.URLEncoder
import java.net.URI
import java.nio.charset.StandardCharsets
import java.util.Locale

class PlexRepository(
    private val connection: PlexConnection,
    private val api: PlexApiService,
    private val watchlistApi: PlexWatchlistApiService,
) {
    private val urls by lazy(LazyThreadSafetyMode.NONE) { PlexUrlFactory(connection) }

    suspend fun loadCatalog(): CinemaCatalog = coroutineScope {
        val sectionsResponse = api.getLibrarySections().mediaContainer
        val sections = sectionsResponse.directories.map {
            PlexLibrary(key = it.key, title = it.title, type = it.type)
        }
        val movieLibraries = sections.filter { it.type == "movie" }
        val showLibraries = sections.filter { it.type == "show" }

        val moviesDeferred = async {
            loadLibraries(movieLibraries)
        }
        val showsDeferred = async {
            loadLibraries(showLibraries)
        }
        val continueDeferred = async { loadContinueWatching() }

        val movies = moviesDeferred.await()
        val shows = showsDeferred.await()
        val localMedia = movies + shows
        val watchlist = runCatching {
            loadWatchlist(localMedia)
        }.getOrDefault(emptyList())

        CinemaCatalog(
            serverName = runCatching { URI(connection.baseUrl).host }
                .getOrNull()
                ?: "Plex",
            movies = movies,
            shows = shows,
            continueWatching = continueDeferred.await(),
            myList = watchlist,
        )
    }

    suspend fun loadChildren(ratingKey: String): List<MediaContent> {
        return api.getChildren(ratingKey).mediaContainer.metadata.map(::toContent)
    }

    suspend fun loadPlayable(ratingKey: String): MediaContent? {
        return api.getMetadata(ratingKey).mediaContainer.metadata.firstOrNull()?.let(::toContent)
    }

    suspend fun loadTrailers(ratingKey: String): List<MediaContent> {
        return api.getExtras(ratingKey).mediaContainer.metadata
            .filter { it.subtype.equals("trailer", ignoreCase = true) || it.extraType == 1 }
            .map(::toContent)
            .filter { it.canPlay }
    }

    /**
     * Finds trailers belonging to two different unwatched movies. Plex server
     * versions disagree on whether `/unwatched` is exposed, so the documented
     * section query is retained as a compatibility fallback.
     */
    suspend fun loadCinemaTrailers(
        mainFeatureRatingKey: String,
        count: Int = 2,
    ): List<MediaContent> {
        if (count <= 0) return emptyList()
        val movieSections = api.getLibrarySections().mediaContainer.directories
            .filter { it.type == "movie" }
        val candidates = movieSections.flatMap { section ->
            runCatching { api.getUnwatchedMovies(section.key).mediaContainer.metadata }
                .getOrElse {
                    api.getContainer(
                        "library/sections/${section.key}/all?unwatched=1&sort=titleSort:asc",
                    ).mediaContainer.metadata
                }
        }.asSequence()
            .filter { it.ratingKey != mainFeatureRatingKey }
            .filter { (it.viewCount ?: 0) == 0 }
            .distinctBy { it.ratingKey }
            .toList()
            .shuffled()
            .asSequence()
            .take(MAX_CINEMA_TRAILER_CANDIDATES)

        val trailers = mutableListOf<MediaContent>()
        for (candidate in candidates) {
            val response = runCatching {
                api.getMetadataWithExtras(candidate.ratingKey)
            }.getOrNull()
            val includedExtras = response?.mediaContainer?.metadata
                ?.firstOrNull()
                ?.extras
                ?.metadata
                .orEmpty()
            val metadata = includedExtras.ifEmpty {
                runCatching { api.getExtras(candidate.ratingKey).mediaContainer.metadata }
                    .getOrDefault(emptyList())
            }
            val trailer = metadata
                .firstOrNull { it.subtype.equals("trailer", true) || it.extraType == 1 }
                ?.let(::toContent)
                ?.takeIf(MediaContent::canPlay)
            if (trailer != null) trailers += trailer
            if (trailers.size == count) break
        }
        return trailers
    }

    suspend fun reportTimeline(
        content: MediaContent,
        positionMs: Long,
        durationMs: Long,
        state: String,
    ) {
        api.reportTimeline(
            ratingKey = content.ratingKey,
            key = content.playback?.metadataKey ?: "/library/metadata/${content.ratingKey}",
            state = state,
            timeMs = positionMs.coerceAtLeast(0L),
            durationMs = durationMs.coerceAtLeast(0L),
        )
    }

    suspend fun selectSubtitle(content: MediaContent, subtitleStreamId: Long?) {
        val partId = content.playback?.partId ?: return
        api.selectSubtitle(
            partId = partId,
            subtitleStreamId = subtitleStreamId ?: 0L,
        )
    }

    suspend fun selectAudio(content: MediaContent, audioStreamId: Long) {
        val partId = content.playback?.partId ?: return
        api.selectAudio(partId = partId, audioStreamId = audioStreamId)
    }

    suspend fun setWatched(content: MediaContent, watched: Boolean) {
        val response = if (watched) api.markWatched(content.ratingKey)
        else api.markUnwatched(content.ratingKey)
        check(response.isSuccessful) { "Plex could not update watched status (${response.code()})." }
    }

    suspend fun setWatchlisted(content: MediaContent, watchlisted: Boolean) {
        val providerRatingKey = content.identityGuids
            .asSequence()
            .plus(content.plexGuid.orEmpty())
            .firstOrNull { it.startsWith("plex://movie/") || it.startsWith("plex://show/") }
            ?.substringAfterLast('/')
            ?.takeIf { it.isNotBlank() }
            ?: error("This item has no Plex GUID and cannot be synced to Watchlist.")
        val response = if (watchlisted) watchlistApi.addToWatchlist(providerRatingKey)
        else watchlistApi.removeFromWatchlist(providerRatingKey)
        check(response.isSuccessful) { "Plex could not update Watchlist (${response.code()})." }
    }

    /** Returns the episode immediately following [content], including season boundaries. */
    suspend fun loadNextEpisode(content: MediaContent): MediaContent? {
        if (content.kind != MediaKind.Episode) return null
        val showRatingKey = content.grandparentRatingKey ?: return null
        val seasons = loadChildren(showRatingKey)
            .filter { it.kind == MediaKind.Season }
            .sortedBy { it.seasonNumber ?: Int.MAX_VALUE }
        val episodes = seasons.flatMap { season ->
            loadChildren(season.ratingKey)
                .filter { it.kind == MediaKind.Episode }
                .sortedBy { it.episodeNumber ?: Int.MAX_VALUE }
        }
        val currentIndex = episodes.indexOfFirst { it.ratingKey == content.ratingKey }
        return episodes.getOrNull(currentIndex + 1)
    }

    private suspend fun loadLibrary(library: PlexLibrary): List<MediaContent> {
        // includeGuids makes older Watchlist entries resolvable even when the
        // local Plex agent and Discover use different primary identifiers.
        val path = "library/sections/${library.key}/all?sort=titleSort:asc&includeGuids=1"
        return api.getContainer(path).mediaContainer.metadata.map(::toContent)
    }

    private suspend fun loadLibraries(libraries: List<PlexLibrary>): List<MediaContent> =
        coroutineScope {
            libraries.map { library -> async { loadLibrary(library) } }
                .awaitAll()
                .flatten()
                .distinctBy { it.ratingKey }
                .sortedBy { it.title.lowercase() }
        }

    private suspend fun loadContinueWatching(): List<MediaContent> {
        val metadata = try {
            val container = api.getContinueWatching().mediaContainer
            container.metadata.ifEmpty { container.hubs.flatMap { it.metadata } }
        } catch (_: Exception) {
            api.getOnDeck().mediaContainer.metadata
        }
        return metadata.map(::toContent)
    }

    /**
     * Plex Watchlist is account-wide and hosted by Discover. A Discover GUID
     * is not guaranteed to equal the GUID returned in a local library list,
     * so ask the PMS to resolve those GUIDs just like Plex's official clients.
     */
    suspend fun loadWatchlist(localMedia: List<MediaContent>): List<MediaContent> {
        val watchlistMetadata = loadAllWatchlistMetadata()
        if (watchlistMetadata.isEmpty() || localMedia.isEmpty()) return emptyList()

        val discoverGuids = watchlistMetadata
            .mapNotNull { it.guid ?: it.primaryGuid }
            .mapNotNull(::normalizeGuid)
            .distinct()

        val resolvedRatingKeys = discoverGuids
            .chunked(WATCHLIST_GUID_BATCH_SIZE)
            .flatMap { batch ->
                runCatching {
                    api.resolveMetadataGuids(batch.joinToString(",") { it.encodePathSegment() })
                        .mediaContainer
                        .metadata
                }.getOrDefault(emptyList())
            }
            .map { it.ratingKey }
            .filter { it.isNotBlank() }
            .distinct()

        // Older PMS/agent combinations may not support GUID resolution. The
        // expanded identity set still lets modern Plex, IMDb, TMDB, and TVDB
        // identifiers match without falling back to ambiguous title matching.
        val discoverIdentities = watchlistMetadata
            .flatMapTo(mutableSetOf(), Metadata::watchlistIdentityKeys)

        val localByRatingKey = localMedia.associateBy { it.ratingKey }
        val resolvedItems = resolvedRatingKeys.mapNotNull(localByRatingKey::get)
        val resolvedKeySet = resolvedItems.mapTo(mutableSetOf()) { it.ratingKey }
        val fallbackItems = localMedia.filter { item ->
            item.ratingKey !in resolvedKeySet && (
                item.identityGuids.any { it in discoverIdentities } ||
                    normalizeGuid(item.plexGuid)?.let { it in discoverIdentities } == true
                )
        }
        val matchedKeys = (resolvedItems + fallbackItems)
            .mapTo(mutableSetOf(), MediaContent::ratingKey)

        // Some long-lived Watchlist rows predate Plex's current GUID model.
        // Use title/year only when it identifies exactly one local item, so a
        // legacy entry is recovered without guessing between remakes.
        val localByTitleYear = localMedia
            .filter { it.year != null && it.kind in setOf(MediaKind.Movie, MediaKind.Show) }
            .groupBy { Triple(it.kind, it.title.watchlistTitleKey(), it.year) }
            .filterValues { it.size == 1 }
            .mapValues { (_, values) -> values.single() }
        val legacyItems = watchlistMetadata.mapNotNull { metadata ->
            val kind = when (metadata.type) {
                "movie" -> MediaKind.Movie
                "show" -> MediaKind.Show
                else -> null
            } ?: return@mapNotNull null
            val year = metadata.year ?: return@mapNotNull null
            localByTitleYear[Triple(kind, metadata.title.watchlistTitleKey(), year)]
        }.filter { it.ratingKey !in matchedKeys }

        return (resolvedItems + fallbackItems + legacyItems).distinctBy(MediaContent::ratingKey)
    }

    private suspend fun loadAllWatchlistMetadata(): List<Metadata> {
        val results = mutableListOf<Metadata>()
        var start = 0

        repeat(MAX_WATCHLIST_PAGES) {
            val container = loadWatchlistPage(start)
            val page = container.metadata.ifEmpty {
                container.hubs.flatMap { it.metadata }
            }
            if (page.isEmpty()) return results

            results += page
            val nextStart = start + page.size
            val totalSize = container.totalSize
            if (totalSize != null && nextStart >= totalSize) return results
            if (totalSize == null && page.size < WATCHLIST_PAGE_SIZE) return results
            if (nextStart <= start) return results
            start = nextStart
        }
        return results
    }

    private suspend fun loadWatchlistPage(start: Int): MediaContainer {
        var lastFailure: Throwable? = null
        repeat(WATCHLIST_PAGE_ATTEMPTS) { attempt ->
            try {
                return watchlistApi.getWatchlist(
                    start = start,
                    size = WATCHLIST_PAGE_SIZE,
                ).mediaContainer
            } catch (failure: Throwable) {
                if (failure is CancellationException) throw failure
                lastFailure = failure
                if (attempt + 1 < WATCHLIST_PAGE_ATTEMPTS) {
                    delay(WATCHLIST_RETRY_DELAY_MS * (attempt + 1))
                }
            }
        }
        throw requireNotNull(lastFailure)
    }

    private fun toContent(metadata: Metadata): MediaContent {
        val kind = when (metadata.type) {
            "show" -> MediaKind.Show
            "season" -> MediaKind.Season
            "episode" -> MediaKind.Episode
            "clip" -> MediaKind.Extra
            else -> MediaKind.Movie
        }
        val part = metadata.media.asSequence()
            .flatMap { it.parts.asSequence() }
            .firstOrNull { it.key.isNotBlank() }
        val subtitleStreams = part?.streams
            .orEmpty()
            .filter { it.streamType == 3 && it.id != null }
            .map { stream ->
                SubtitleStream(
                    id = stream.id!!,
                    label = stream.displayTitle
                        ?: stream.title
                        ?: stream.language
                        ?: stream.codec?.uppercase()
                        ?: "Subtitle",
                    language = stream.languageCode ?: stream.language,
                    key = stream.key?.let(urls::authenticated),
                    codec = stream.codec,
                    selected = stream.selected == true,
                    forced = stream.forced == true,
                )
            }
        val audioStreams = part?.streams
            .orEmpty()
            .filter { it.streamType == 2 && it.id != null }
            .map { stream ->
                AudioStream(
                    id = stream.id!!,
                    label = stream.displayTitle
                        ?: stream.title
                        ?: stream.language
                        ?: "Audio ${stream.id}",
                    language = stream.languageCode ?: stream.language,
                    codec = stream.codec,
                    channels = stream.channels,
                    selected = stream.selected == true,
                )
            }
        val playback = part?.takeIf { it.id != null }?.let {
            PlaybackSource(
                partId = it.id!!,
                directUrl = urls.authenticated(it.key),
                metadataKey = metadata.key ?: "/library/metadata/${metadata.ratingKey}",
                audioStreams = audioStreams,
                subtitles = subtitleStreams,
            )
        }
        val secondaryTitle = when (kind) {
            MediaKind.Episode -> buildString {
                append(metadata.grandparentTitle ?: metadata.parentTitle.orEmpty())
                if (metadata.parentIndex != null && metadata.index != null) {
                    append("  •  S${metadata.parentIndex} E${metadata.index}")
                }
            }.ifBlank { null }
            MediaKind.Season -> metadata.parentTitle
            else -> null
        }
        val posterPath = when (kind) {
            MediaKind.Episode -> metadata.grandparentThumb ?: metadata.parentThumb ?: metadata.thumb
            else -> metadata.thumb ?: metadata.parentThumb ?: metadata.grandparentThumb
        }
        val backdropPath = metadata.art ?: metadata.grandparentArt ?: metadata.parentArt
        val credits = buildList {
            metadata.roles.forEach { person ->
                add(
                    MediaCredit(
                        name = person.tag,
                        role = person.role?.takeIf { it.isNotBlank() } ?: "Cast",
                        imageUrl = person.thumb?.let(urls::authenticated),
                    ),
                )
            }
            metadata.directors.forEach { person ->
                add(MediaCredit(person.tag, "Director", person.thumb?.let(urls::authenticated)))
            }
            metadata.writers.forEach { person ->
                add(MediaCredit(person.tag, "Writer", person.thumb?.let(urls::authenticated)))
            }
            metadata.producers.forEach { person ->
                add(MediaCredit(person.tag, "Producer", person.thumb?.let(urls::authenticated)))
            }
        }.filter { it.name.isNotBlank() }.distinctBy { it.name to it.role }

        return MediaContent(
            ratingKey = metadata.ratingKey,
            plexGuid = metadata.guid,
            identityGuids = metadata.identityKeys(),
            title = metadata.title,
            secondaryTitle = secondaryTitle,
            summary = metadata.summary,
            tagline = metadata.tagline,
            year = metadata.year,
            durationMs = metadata.duration ?: part?.duration,
            viewOffsetMs = metadata.viewOffset ?: 0L,
            posterUrl = posterPath?.let(urls::authenticated),
            backdropUrl = backdropPath?.let(urls::authenticated),
            contentRating = metadata.contentRating,
            kind = kind,
            genres = metadata.genres.map { it.tag }.filter { it.isNotBlank() }.distinct(),
            seasonNumber = if (kind == MediaKind.Season) metadata.index else metadata.parentIndex,
            episodeNumber = if (kind == MediaKind.Episode) metadata.index else null,
            childCount = metadata.childCount ?: metadata.leafCount,
            parentRatingKey = metadata.parentRatingKey,
            grandparentRatingKey = metadata.grandparentRatingKey,
            isWatched = when (kind) {
                MediaKind.Show, MediaKind.Season -> {
                    metadata.leafCount != null && metadata.leafCount > 0 &&
                        metadata.viewedLeafCount == metadata.leafCount
                }
                else -> (metadata.viewCount ?: 0) > 0
            },
            credits = credits,
            markers = metadata.markers
                .filter { marker ->
                    marker.type.isNotBlank() && marker.endTimeOffset > marker.startTimeOffset
                }
                .map { marker ->
                    MediaMarker(
                        type = marker.type,
                        startTimeOffsetMs = marker.startTimeOffset.coerceAtLeast(0L),
                        endTimeOffsetMs = marker.endTimeOffset.coerceAtLeast(0L),
                        isFinal = marker.final == true,
                    )
                }
                .sortedBy(MediaMarker::startTimeOffsetMs),
            playback = playback,
        )
    }
}

// Plex Discover has rejected or silently capped larger page sizes for some
// accounts. Ten is the stable size used by affected official Plex clients.
private const val WATCHLIST_PAGE_SIZE = 10
private const val MAX_WATCHLIST_PAGES = 500
private const val WATCHLIST_PAGE_ATTEMPTS = 3
private const val WATCHLIST_RETRY_DELAY_MS = 250L
private const val WATCHLIST_GUID_BATCH_SIZE = 10
private const val MAX_CINEMA_TRAILER_CANDIDATES = 16

private fun Metadata.identityKeys(): Set<String> = buildSet {
    listOfNotNull(guid, primaryGuid).mapNotNullTo(this, ::normalizeGuid)
    guids.mapNotNullTo(this) { normalizeGuid(it.id) }
}

private fun Metadata.watchlistIdentityKeys(): Set<String> = buildSet {
    addAll(identityKeys())
    // Discover commonly uses the Plex GUID suffix as its provider ratingKey.
    // Adding this alias also covers responses where `guid` is omitted.
    if ((type == "movie" || type == "show") && ratingKey.isNotBlank()) {
        normalizeGuid("plex://$type/$ratingKey")?.let(::add)
    }
}

private fun normalizeGuid(value: String?): String? = value
    ?.trim()
    ?.trimEnd('/')
    ?.takeIf { it.isNotBlank() }
    ?.lowercase(Locale.ROOT)

private fun String.encodePathSegment(): String =
    URLEncoder.encode(this, StandardCharsets.UTF_8.name()).replace("+", "%20")

private fun String.watchlistTitleKey(): String = trim()
    .lowercase(Locale.ROOT)
    .replace(Regex("\\s+"), " ")
