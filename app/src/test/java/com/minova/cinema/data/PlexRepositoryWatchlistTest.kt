package com.minova.cinema.data

import com.minova.cinema.data.remote.Directory
import com.minova.cinema.data.remote.GuidTag
import com.minova.cinema.data.remote.MediaContainer
import com.minova.cinema.data.remote.Metadata
import com.minova.cinema.data.remote.PlexApiService
import com.minova.cinema.data.remote.PlexConnection
import com.minova.cinema.data.remote.PlexLibraryResponse
import com.minova.cinema.data.remote.PlexWatchlistApiService
import com.minova.cinema.data.remote.TranscodeSession
import com.minova.cinema.data.remote.Session
import com.minova.cinema.domain.MediaContent
import com.minova.cinema.domain.MediaKind
import com.minova.cinema.domain.PlexPlaybackMode
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

class PlexRepositoryWatchlistTest {
    @Test
    fun `live Plex session reports transcode components and reason`() = runBlocking {
        val api = FakePlexApi(emptyList(), emptyMap()).apply {
            sessionsResponse = PlexLibraryResponse(
                MediaContainer(
                    metadata = listOf(
                        Metadata(
                            ratingKey = "42",
                            session = Session(id = "session-42"),
                            transcodeSession = TranscodeSession(
                                videoDecision = "transcode",
                                audioDecision = "copy",
                                protocol = "hls",
                            ),
                        ),
                    ),
                ),
            )
        }
        val repository = PlexRepository(
            PlexConnection("http://127.0.0.1:32400/", "token"),
            api,
            FakeWatchlistApi(emptyMap(), 0),
        )
        val content = MediaContent(
            ratingKey = "42",
            title = "Test",
            secondaryTitle = null,
            summary = null,
            tagline = null,
            year = 2026,
            durationMs = 1_000L,
            viewOffsetMs = 0L,
            posterUrl = null,
            backdropUrl = null,
            contentRating = null,
            kind = MediaKind.Movie,
        )

        val result = repository.loadPlaybackDiagnostics(content, "session-42", "1080p")

        assertEquals(PlexPlaybackMode.Transcode, result.mode)
        assertTrue(result.reason.orEmpty().contains("converting the video"))
        assertEquals("copy", result.audioDecision)
    }

    @Test
    fun `large libraries are fetched in stable 200 item pages`() = runBlocking {
        val media = (1..450).map { index ->
            Metadata(ratingKey = index.toString(), title = "Movie $index", type = "movie")
        }
        val api = FakePlexApi(media, emptyMap())
        val repository = PlexRepository(
            connection = PlexConnection("http://127.0.0.1:32400/", "token"),
            api = api,
            watchlistApi = FakeWatchlistApi(emptyMap(), 0),
        )

        val catalog = repository.loadCatalog()

        assertEquals(450, catalog.movies.size)
        assertEquals(listOf(0, 200, 400), api.containerStarts)
    }

    @Test
    fun watchlistIsPaginatedAndResolvedThroughTheLocalServer() = runBlocking {
        val first = movie(
            ratingKey = "101",
            title = "First",
            guid = "plex://movie/server-first",
        )
        val second = movie(
            ratingKey = "102",
            title = "Second",
            guid = "plex://movie/server-second",
        )
        val fallback = movie(
            ratingKey = "103",
            title = "Fallback",
            guid = "plex://movie/discover-fallback",
            externalGuid = "imdb://tt0000103",
        )
        val legacy = movie(
            ratingKey = "104",
            title = "Legacy Film",
            guid = "plex://movie/server-legacy",
            year = 1999,
        )
        val localApi = FakePlexApi(
            libraryItems = listOf(first, second, fallback, legacy),
            resolvedItems = mapOf(
                "plex://movie/discover-first" to first,
                "plex://movie/discover-second" to second,
            ),
        )
        val watchlistApi = FakeWatchlistApi(
            pages = mapOf(
                0 to listOf(
                    movie("discover-first", "First", "plex://movie/discover-first"),
                    movie(
                        "discover-second",
                        "Second",
                        guid = null,
                        primaryGuid = "plex://movie/discover-second",
                    ),
                ),
                2 to listOf(
                    movie(
                        "discover-fallback",
                        "Fallback",
                        guid = null,
                        externalGuid = "imdb://tt0000103",
                    ),
                    movie(
                        "discover-legacy",
                        "Legacy Film",
                        guid = null,
                        year = 1999,
                    ),
                ),
            ),
            totalSize = 4,
        )
        val repository = PlexRepository(
            connection = PlexConnection("http://127.0.0.1:32400/", "test-token"),
            api = localApi,
            watchlistApi = watchlistApi,
        )

        val catalog = repository.loadCatalog()

        assertEquals(listOf("101", "102", "103", "104"), catalog.myList.map { it.ratingKey })
        assertEquals(listOf(0, 2), watchlistApi.requestedStarts)
        assertEquals(listOf(10, 10), watchlistApi.requestedSizes)
        assertEquals(1, localApi.resolveRequests.size)
        assertTrue(localApi.resolveRequests.single().contains("%3A%2F%2F"))
    }

    @Test
    fun watchlistContinuesPastAFullPageWhenDiscoverOmitsTotalSize() = runBlocking {
        val localItems = (1..12).map { index ->
            movie("local-$index", "Movie $index", "plex://movie/item-$index")
        }
        val watchlistItems = (1..12).map { index ->
            movie("item-$index", "Movie $index", "plex://movie/item-$index")
        }
        val localApi = FakePlexApi(
            libraryItems = localItems,
            resolvedItems = watchlistItems.associate { discover ->
                requireNotNull(discover.guid) to requireNotNull(
                    localItems.firstOrNull { it.title == discover.title },
                )
            },
        )
        val watchlistApi = FakeWatchlistApi(
            pages = mapOf(0 to watchlistItems.take(10), 10 to watchlistItems.drop(10)),
            totalSize = null,
        )
        val repository = PlexRepository(
            connection = PlexConnection("http://127.0.0.1:32400/", "test-token"),
            api = localApi,
            watchlistApi = watchlistApi,
        )

        val catalog = repository.loadCatalog()

        assertEquals(12, catalog.myList.size)
        assertEquals(listOf(0, 10), watchlistApi.requestedStarts)
        assertEquals(listOf(10, 10), watchlistApi.requestedSizes)
    }

    private fun movie(
        ratingKey: String,
        title: String,
        guid: String?,
        primaryGuid: String? = null,
        externalGuid: String? = null,
        year: Int? = null,
    ): Metadata = Metadata(
        ratingKey = ratingKey,
        guid = guid,
        primaryGuid = primaryGuid,
        title = title,
        type = "movie",
        year = year,
        guids = externalGuid?.let { listOf(GuidTag(it)) }.orEmpty(),
    )
}

private class FakeWatchlistApi(
    private val pages: Map<Int, List<Metadata>>,
    private val totalSize: Int?,
) : PlexWatchlistApiService {
    val requestedStarts = mutableListOf<Int>()
    val requestedSizes = mutableListOf<Int>()

    override suspend fun getWatchlist(
        includeCollections: Int,
        includeExternalMedia: Int,
        start: Int,
        size: Int,
    ): PlexLibraryResponse {
        requestedStarts += start
        requestedSizes += size
        val metadata = pages[start].orEmpty()
        return PlexLibraryResponse(
            MediaContainer(
                size = metadata.size,
                offset = start,
                totalSize = totalSize,
                metadata = metadata,
            ),
        )
    }

    override suspend fun addToWatchlist(providerRatingKey: String): Response<Unit> =
        Response.success(Unit)

    override suspend fun removeFromWatchlist(providerRatingKey: String): Response<Unit> =
        Response.success(Unit)
}

private class FakePlexApi(
    private val libraryItems: List<Metadata>,
    private val resolvedItems: Map<String, Metadata>,
) : PlexApiService {
    val resolveRequests = mutableListOf<String>()
    val containerStarts = mutableListOf<Int>()
    var sessionsResponse: PlexLibraryResponse = PlexLibraryResponse()

    override suspend fun getLibrarySections(): PlexLibraryResponse = PlexLibraryResponse(
        MediaContainer(
            directories = listOf(Directory(key = "1", title = "Movies", type = "movie")),
        ),
    )

    override suspend fun getContainer(path: String, start: Int?, size: Int?): PlexLibraryResponse {
        val pageStart = start ?: 0
        val pageSize = size ?: libraryItems.size
        containerStarts += pageStart
        val page = libraryItems.drop(pageStart).take(pageSize)
        return PlexLibraryResponse(
            MediaContainer(
                size = page.size,
                offset = pageStart,
                totalSize = libraryItems.size,
                metadata = page,
            ),
        )
    }

    override suspend fun resolveMetadataGuids(
        guids: String,
        skipRefresh: Int,
    ): PlexLibraryResponse {
        resolveRequests += guids
        val resolved = guids.split(',')
            .map { URLDecoder.decode(it, StandardCharsets.UTF_8.name()) }
            .mapNotNull(resolvedItems::get)
        return PlexLibraryResponse(MediaContainer(size = resolved.size, metadata = resolved))
    }

    override suspend fun getContinueWatching(): PlexLibraryResponse = PlexLibraryResponse()

    override suspend fun getOnDeck(): PlexLibraryResponse = PlexLibraryResponse()

    override suspend fun getMetadata(ratingKey: String, includeMarkers: Int, includeChapters: Int) = unused()
    override suspend fun getMetadataWithExtras(
        ratingKey: String,
        includeExtras: Int,
        includeMarkers: Int,
        includeChapters: Int,
    ) = unused()
    override suspend fun getSessions() = sessionsResponse
    override suspend fun getUnwatchedMovies(sectionId: String) = unused()
    override suspend fun getChildren(ratingKey: String) = unused()
    override suspend fun getExtras(ratingKey: String) = unused()
    override suspend fun markWatched(ratingKey: String, identifier: String) = Response.success(Unit)
    override suspend fun markUnwatched(ratingKey: String, identifier: String) = Response.success(Unit)
    override suspend fun reportTimeline(
        ratingKey: String,
        key: String,
        state: String,
        timeMs: Long,
        durationMs: Long,
    ) = Response.success(Unit)

    override suspend fun selectSubtitle(
        partId: Long,
        subtitleStreamId: Long,
        allParts: Int,
    ) = Response.success(Unit)

    override suspend fun selectAudio(
        partId: Long,
        audioStreamId: Long,
        allParts: Int,
    ) = Response.success(Unit)

    private fun unused(): PlexLibraryResponse = error("Not used by this test")
}
