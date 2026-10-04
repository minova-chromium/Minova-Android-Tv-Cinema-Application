package com.minova.cinema.data

import com.minova.cinema.data.remote.Metadata
import com.minova.cinema.data.remote.MediaContainer
import com.minova.cinema.data.remote.PlexApiService
import com.minova.cinema.data.remote.PlexConnection
import com.minova.cinema.data.remote.PlexUrlFactory
import com.minova.cinema.data.remote.PersonMetadataService
import com.minova.cinema.data.remote.PersonMetadataLookup
import com.minova.cinema.data.remote.PlexWatchlistApiService
import com.minova.cinema.data.remote.PlexDownloadQueueItem
import com.minova.cinema.data.remote.PlexDownloadResponse
import com.minova.cinema.data.remote.PersonTag
import com.minova.cinema.domain.CinemaCatalog
import com.minova.cinema.domain.MediaCollection
import com.minova.cinema.domain.AudioStream
import com.minova.cinema.domain.MediaContent
import com.minova.cinema.domain.MediaKind
import com.minova.cinema.domain.MediaCredit
import com.minova.cinema.domain.PersonProfile
import com.minova.cinema.domain.MediaMarker
import com.minova.cinema.domain.MediaChapter
import com.minova.cinema.domain.MediaTechnicalInfo
import com.minova.cinema.domain.PlaybackDiagnostics
import com.minova.cinema.domain.PlexPlaybackMode
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
import retrofit2.Response

data class PlexDownloadTicket(val queueId: Long, val itemId: Long)

class PlexDownloadUnavailableException(message: String) : IllegalStateException(message)

class PlexRepository(
    private val connection: PlexConnection,
    private val api: PlexApiService,
    private val watchlistApi: PlexWatchlistApiService,
    private val personMetadataService: PersonMetadataLookup = PersonMetadataService(),
) {
    private val urls by lazy(LazyThreadSafetyMode.NONE) { PlexUrlFactory(connection) }
    private val playableReads = RequestCoalescer<String, MediaContent?>(
        ttlMs = DETAIL_CACHE_TTL_MS,
        maxEntries = DETAIL_CACHE_ENTRIES,
    )
    private val childReads = RequestCoalescer<String, List<MediaContent>>(
        ttlMs = CHILD_CACHE_TTL_MS,
        maxEntries = CHILD_CACHE_ENTRIES,
    )
    private val trailerReads = RequestCoalescer<String, List<MediaContent>>(
        ttlMs = DETAIL_CACHE_TTL_MS,
        maxEntries = DETAIL_CACHE_ENTRIES,
    )

    suspend fun createOfflineDownload(content: MediaContent): PlexDownloadTicket {
        val metadataKey = content.playback?.metadataKey
            ?: "/library/metadata/${content.ratingKey}"
        val queueResponse = api.createDownloadQueue()
        queueResponse.requireDownloadAccess()
        val queueId = queueResponse.body()?.mediaContainer?.queues?.firstOrNull()?.id
            ?.takeIf { it > 0L }
            ?: error("Plex did not create a download queue.")
        val addResponse = api.addToDownloadQueue(queueId, metadataKey)
        addResponse.requireDownloadAccess()
        val itemId = addResponse.body()?.mediaContainer?.addedItems?.firstOrNull()?.id
            ?.takeIf { it > 0L }
            ?: error("Plex did not add this title to the download queue.")
        return PlexDownloadTicket(queueId, itemId)
    }

    suspend fun offlineDownloadStatus(ticket: PlexDownloadTicket): PlexDownloadQueueItem {
        val response = api.getDownloadQueueItem(ticket.queueId, ticket.itemId)
        response.requireDownloadAccess()
        return response.body()?.mediaContainer?.items?.firstOrNull()
            ?: error("Plex did not return the queued download.")
    }

    suspend fun cancelOfflineDownload(ticket: PlexDownloadTicket) {
        api.deleteDownloadQueueItem(ticket.queueId, ticket.itemId)
    }

    fun offlineDownloadMediaUrl(ticket: PlexDownloadTicket): String =
        connection.baseUrl.trimEnd('/') +
            "/downloadQueue/${ticket.queueId}/item/${ticket.itemId}/media"

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
        val collectionsDeferred = async {
            (movieLibraries + showLibraries).map { library -> async {
                try {
                    loadPagedMetadata("library/sections/${library.key}/collections").map { metadata ->
                        MediaCollection(
                            ratingKey = metadata.ratingKey,
                            title = metadata.title,
                            posterUrl = metadata.thumb?.takeIf(String::isNotBlank)?.let(urls::authenticated),
                            libraryTitle = library.title,
                            childCount = metadata.childCount,
                        )
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    emptyList()
                }
            } }.awaitAll().flatten().distinctBy { it.ratingKey }.sortedBy { it.title.lowercase() }
        }

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
            collections = collectionsDeferred.await(),
        )
    }

    suspend fun loadChildren(ratingKey: String): List<MediaContent> {
        return childReads.get(ratingKey) {
            api.getChildren(ratingKey).mediaContainer.metadata.map(::toContent)
        }
    }

    suspend fun loadSeriesEpisodes(showRatingKey: String): List<MediaContent> {
        val seasons = loadChildren(showRatingKey)
            .filter { it.kind == MediaKind.Season }
            .sortedBy { season ->
                season.seasonNumber?.takeIf { it > 0 } ?: Int.MAX_VALUE
            }
        return seasons.flatMap { season ->
            loadChildren(season.ratingKey)
                .filter { it.kind == MediaKind.Episode }
                .sortedBy { it.episodeNumber ?: Int.MAX_VALUE }
        }
    }

    suspend fun loadCollectionMembers(ratingKey: String): List<MediaContent> =
        loadPagedMetadata("library/collections/${ratingKey.encodePathSegment()}/children")
            .map(::toContent)
            .sortedWith(compareBy<MediaContent> { it.releaseDate ?: "${it.year ?: 9999}-12-31" }.thenBy { it.title })

    private suspend fun loadPagedMetadata(path: String): List<Metadata> {
        val results = mutableListOf<Metadata>()
        val seen = mutableSetOf<String>()
        var start = 0
        repeat(MAX_LIBRARY_PAGES) {
            val container = api.getContainer(path, start = start, size = LIBRARY_PAGE_SIZE).mediaContainer
            val page = container.metadata
            val additions = page.filter { it.ratingKey.isNotBlank() && seen.add(it.ratingKey) }
            if (additions.isEmpty()) return results
            results += additions
            start += page.size
            if (container.totalSize?.let { start >= it } ?: (page.size < LIBRARY_PAGE_SIZE)) return results
        }
        return results
    }

    suspend fun loadPlayable(ratingKey: String): MediaContent? {
        return playableReads.get(ratingKey) {
            api.getMetadata(ratingKey).mediaContainer.metadata.firstOrNull()?.let(::toContent)
        }
    }

    /** Reads Plex's live session decision, which is authoritative for Direct Play/Stream/Transcode. */
    suspend fun loadPlaybackDiagnostics(
        content: MediaContent,
        sessionId: String?,
        requestedQualityLabel: String,
    ): PlaybackDiagnostics {
        val sessions = api.getSessions().mediaContainer.metadata
        val active = sessions.firstOrNull { metadata ->
            (!sessionId.isNullOrBlank() && metadata.session?.id == sessionId) ||
                metadata.ratingKey == content.ratingKey
        }
        val media = active?.media?.firstOrNull()
        val transcode = active?.transcodeSession
        val videoDecision = transcode?.videoDecision ?: media?.videoDecision
        val audioDecision = transcode?.audioDecision ?: media?.audioDecision
        val decisions = listOfNotNull(videoDecision, audioDecision, media?.containerDecision)
            .map(String::lowercase)
        val mode = when {
            decisions.any { it == "transcode" } -> PlexPlaybackMode.Transcode
            transcode != null || media?.protocol.equals("hls", true) ||
                decisions.any { it == "copy" } -> PlexPlaybackMode.DirectStream
            active != null -> PlexPlaybackMode.DirectPlay
            requestedQualityLabel.equals("Original", true) -> PlexPlaybackMode.DirectPlay
            else -> PlexPlaybackMode.Transcode
        }
        val reason = when (mode) {
            PlexPlaybackMode.Transcode -> buildList {
                if (!requestedQualityLabel.equals("Original", true)) {
                    add("Quality was limited to $requestedQualityLabel")
                }
                if (videoDecision.equals("transcode", true)) add("Plex is converting the video")
                if (audioDecision.equals("transcode", true)) add("Plex is converting the audio")
                if (transcode?.hardwareEncoding?.isNotBlank() == true) add("hardware acceleration active")
            }.joinToString(" · ").ifBlank { "Plex selected a compatible stream for this device" }
            PlexPlaybackMode.DirectStream -> "Original audio/video are being repackaged into a compatible container"
            PlexPlaybackMode.DirectPlay -> "Playing the original file without conversion"
            PlexPlaybackMode.Unknown -> null
        }
        return PlaybackDiagnostics(
            mode = mode,
            reason = reason,
            videoDecision = videoDecision,
            audioDecision = audioDecision,
            source = content.playback?.technicalInfo,
        )
    }

    suspend fun loadTrailers(ratingKey: String): List<MediaContent> {
        return trailerReads.get(ratingKey) {
            api.getExtras(ratingKey).mediaContainer.metadata
                .filter { it.subtype.equals("trailer", ignoreCase = true) || it.extraType == 1 }
                .map(::toContent)
                .filter { it.canPlay }
        }
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
        val response = api.reportTimeline(
            ratingKey = content.ratingKey,
            key = content.playback?.metadataKey ?: "/library/metadata/${content.ratingKey}",
            state = state,
            timeMs = positionMs.coerceAtLeast(0L),
            durationMs = durationMs.coerceAtLeast(0L),
        )
        check(response.isSuccessful) {
            "Plex could not save playback progress (${response.code()})."
        }
    }

    suspend fun selectSubtitle(content: MediaContent, subtitleStreamId: Long?) {
        val partId = content.playback?.partId ?: return
        api.selectSubtitle(
            partId = partId,
            subtitleStreamId = subtitleStreamId ?: 0L,
        )
        playableReads.invalidate(content.ratingKey)
    }

    suspend fun selectAudio(content: MediaContent, audioStreamId: Long) {
        val partId = content.playback?.partId ?: return
        api.selectAudio(partId = partId, audioStreamId = audioStreamId)
        playableReads.invalidate(content.ratingKey)
    }

    suspend fun rate(content: MediaContent, rating: Int): Boolean = api.rate(content.ratingKey, rating.coerceIn(1, 10)).isSuccessful

    suspend fun setWatched(content: MediaContent, watched: Boolean) {
        val response = if (watched) api.markWatched(content.ratingKey)
        else api.markUnwatched(content.ratingKey)
        check(response.isSuccessful) { "Plex could not update watched status (${response.code()})." }
        playableReads.invalidate(content.ratingKey)
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

    suspend fun loadPersonProfile(credit: MediaCredit): PersonProfile {
        val personId = credit.personId?.takeIf(String::isNotBlank)
            ?: error("Plex did not provide a profile identity for ${credit.name}.")
        val person = runCatching { api.getPerson(personId).mediaContainer.directories.firstOrNull() }.getOrNull()
        val media = api.getPersonMedia(personId).mediaContainer.metadata
            .map(::toContent)
            .filter { it.kind == MediaKind.Movie || it.kind == MediaKind.Show }
            .distinctBy(MediaContent::ratingKey)
        val background = personMetadataService.lookup(credit.name)
        return PersonProfile(
            personId = personId,
            name = person?.tag?.takeIf(String::isNotBlank) ?: person?.title?.takeIf(String::isNotBlank) ?: credit.name,
            role = credit.role,
            imageUrl = (person?.thumb ?: credit.imageUrl)?.let(urls::authenticated),
            biography = background.biography,
            biographySource = background.sourceLabel,
            biographySourceUrl = background.sourceUrl,
            imdbUrl = background.imdbUrl,
            media = media,
        )
    }

    private suspend fun loadLibrary(library: PlexLibrary): List<MediaContent> {
        // includeGuids makes older Watchlist entries resolvable even when the
        // local Plex agent and Discover use different primary identifiers.
        val path = "library/sections/${library.key}/all?sort=titleSort:asc&includeGuids=1"
        val results = mutableListOf<MediaContent>()
        val seenRatingKeys = mutableSetOf<String>()
        var start = 0
        repeat(MAX_LIBRARY_PAGES) {
            val container = api.getContainer(path, start = start, size = LIBRARY_PAGE_SIZE)
                .mediaContainer
            val page = container.metadata
            if (page.isEmpty()) return results
            val mapped = page.map(::toContent).filter { seenRatingKeys.add(it.ratingKey) }
            if (mapped.isEmpty()) return results
            results += mapped
            val nextStart = start + page.size
            val total = container.totalSize
            if (total != null && nextStart >= total) return results
            if (total == null && page.size < LIBRARY_PAGE_SIZE) return results
            if (nextStart <= start) return results
            start = nextStart
        }
        return results
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
            val media = metadata.media.firstOrNull { candidate ->
                candidate.parts.any { candidatePart -> candidatePart.id == it.id }
            } ?: metadata.media.firstOrNull()
            PlaybackSource(
                partId = it.id!!,
                directUrl = urls.authenticated(it.key),
                metadataKey = metadata.key ?: "/library/metadata/${metadata.ratingKey}",
                audioStreams = audioStreams,
                subtitles = subtitleStreams,
                technicalInfo = MediaTechnicalInfo(
                    bitrateKbps = media?.bitrate,
                    width = media?.width,
                    height = media?.height,
                    videoResolution = media?.videoResolution,
                    videoCodec = media?.videoCodec,
                    audioCodec = media?.audioCodec,
                    container = media?.container ?: it.container,
                ),
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
                        personId = person.profileId(),
                    ),
                )
            }
            metadata.directors.forEach { person ->
                add(MediaCredit(person.tag, "Director", person.thumb?.let(urls::authenticated), person.profileId()))
            }
            metadata.writers.forEach { person ->
                add(MediaCredit(person.tag, "Writer", person.thumb?.let(urls::authenticated), person.profileId()))
            }
            metadata.producers.forEach { person ->
                add(MediaCredit(person.tag, "Producer", person.thumb?.let(urls::authenticated), person.profileId()))
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
            addedAtEpochSeconds = metadata.addedAt,
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
            viewedLeafCount = metadata.viewedLeafCount ?: 0,
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
            chapters = metadata.chapters
                .filter { it.endTimeOffset > it.startTimeOffset }
                .mapIndexed { index, chapter ->
                    MediaChapter(
                        title = chapter.title?.takeIf(String::isNotBlank) ?: "Chapter ${index + 1}",
                        startTimeOffsetMs = chapter.startTimeOffset.coerceAtLeast(0L),
                        endTimeOffsetMs = chapter.endTimeOffset.coerceAtLeast(0L),
                    )
                }
                .sortedBy(MediaChapter::startTimeOffsetMs),
            audienceRating = metadata.audienceRating ?: metadata.rating,
            collections = metadata.collections.map { it.tag }.filter(String::isNotBlank).distinct(),
            audioLanguages = metadata.media.flatMap { it.parts }.flatMap { it.streams }
                .filter { it.streamType == 2 }.mapNotNull { it.languageCode ?: it.language }.distinct(),
            resolution = metadata.media.firstOrNull()?.videoResolution,
            releaseDate = metadata.originallyAvailableAt,
            lastViewedAtEpochSeconds = metadata.lastViewedAt,
            userRating = metadata.userRating,
            themeUrl = metadata.theme?.takeIf { it.startsWith("/") && !it.startsWith("//") }?.let(urls::authenticated),
            playback = playback,
        )
    }
}

private fun PersonTag.profileId(): String? = tagKey?.takeIf(String::isNotBlank)
    ?: id?.takeIf(String::isNotBlank)
    ?: filter?.substringAfter('=', missingDelimiterValue = "")?.substringBefore('&')?.takeIf(String::isNotBlank)

// Plex Discover has rejected or silently capped larger page sizes for some
// accounts. Ten is the stable size used by affected official Plex clients.
private const val WATCHLIST_PAGE_SIZE = 10
private const val MAX_WATCHLIST_PAGES = 500
private const val WATCHLIST_PAGE_ATTEMPTS = 3
private const val WATCHLIST_RETRY_DELAY_MS = 250L
private const val WATCHLIST_GUID_BATCH_SIZE = 10
private const val MAX_CINEMA_TRAILER_CANDIDATES = 16
private const val LIBRARY_PAGE_SIZE = 200
private const val MAX_LIBRARY_PAGES = 2_000
private const val DETAIL_CACHE_TTL_MS = 30_000L
private const val CHILD_CACHE_TTL_MS = 60_000L
private const val DETAIL_CACHE_ENTRIES = 64
private const val CHILD_CACHE_ENTRIES = 48

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

private fun Response<PlexDownloadResponse>.requireDownloadAccess() {
    if (isSuccessful) return
    throw when (code()) {
        403 -> PlexDownloadUnavailableException(
            "Downloads require Plex Pass and the server owner’s Allow Downloads permission.",
        )
        404 -> PlexDownloadUnavailableException(
            "This Plex server is too old for secure Downloads. Update Plex Media Server and try again.",
        )
        else -> PlexDownloadUnavailableException(
            "Plex could not prepare this download (${code()}).",
        )
    }
}
