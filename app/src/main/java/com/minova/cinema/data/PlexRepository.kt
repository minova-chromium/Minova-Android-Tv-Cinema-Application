package com.minova.cinema.data

import androidx.core.net.toUri
import com.minova.cinema.data.remote.Metadata
import com.minova.cinema.data.remote.PlexApiService
import com.minova.cinema.data.remote.PlexConnection
import com.minova.cinema.data.remote.PlexUrlFactory
import com.minova.cinema.data.remote.PlexWatchlistApiService
import com.minova.cinema.domain.CinemaCatalog
import com.minova.cinema.domain.AudioStream
import com.minova.cinema.domain.MediaContent
import com.minova.cinema.domain.MediaKind
import com.minova.cinema.domain.MediaCredit
import com.minova.cinema.domain.PlaybackSource
import com.minova.cinema.domain.PlexLibrary
import com.minova.cinema.domain.SubtitleStream
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

class PlexRepository(
    private val connection: PlexConnection,
    private val api: PlexApiService,
    private val watchlistApi: PlexWatchlistApiService,
) {
    private val urls = PlexUrlFactory(connection)

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
        val watchlistGuids = runCatching {
            watchlistApi.getWatchlist().mediaContainer.metadata
                .mapNotNull { it.guid }
                .toSet()
        }.getOrDefault(emptySet())

        CinemaCatalog(
            serverName = connection.baseUrl.toUri().host ?: "Plex",
            movies = movies,
            shows = shows,
            continueWatching = continueDeferred.await(),
            myList = (movies + shows).filter { it.plexGuid in watchlistGuids },
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
        val providerRatingKey = content.plexGuid
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
        val path = "library/sections/${library.key}/all?sort=titleSort:asc"
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
            playback = playback,
        )
    }
}
