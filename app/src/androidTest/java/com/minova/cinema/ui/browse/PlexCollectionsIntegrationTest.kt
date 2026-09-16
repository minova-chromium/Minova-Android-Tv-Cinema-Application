package com.minova.cinema.ui.browse

import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import com.minova.cinema.data.PlexRepository
import com.minova.cinema.data.local.PlexCatalogCache
import com.minova.cinema.data.remote.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.lang.reflect.Proxy

class PlexCollectionsIntegrationTest {
    @Test fun collectionPostersRetainIdentityAndCacheWithoutCredentials() = runBlocking {
        val connection = PlexConnection("http://127.0.0.1:32400/", "collection-fixture-token")
        val api = fake<PlexApiService> { method, args ->
            when (method) {
                "getLibrarySections" -> PlexLibraryResponse(MediaContainer(directories = listOf(
                    Directory("1", "Movies", "movie"), Directory("2", "Series", "show"))))
                "getContainer" -> {
                    val path = args[0] as String
                    val section = if (path.contains("sections/1/")) "1" else "2"
                    if (path.endsWith("/collections")) response(listOf(
                        Metadata(ratingKey = "collection-$section", title = "Shared name", type = "collection",
                            thumb = "/library/metadata/collection-$section/thumb/123", childCount = 4),
                        Metadata(ratingKey = "missing-$section", title = "No poster", type = "collection"),
                    )) else response(listOf(Metadata(ratingKey = "movie-$section", title = "Member", type = "movie",
                        thumb = "/library/metadata/movie-$section/thumb/999", collections = listOf(Tag("Shared name")))))
                }
                else -> PlexLibraryResponse()
            }
        }
        val catalog = PlexRepository(connection, api, emptyWatchlist()).loadCatalog()
        val matching = catalog.collections.filter { it.title == "Shared name" }
        assertEquals(2, matching.size)
        assertEquals(setOf("Movies", "Series"), matching.map { it.libraryTitle }.toSet())
        matching.forEach {
            assertEquals("/library/metadata/${it.ratingKey}/thumb/123", Uri.parse(it.posterUrl).path)
            assertEquals(connection.token, Uri.parse(it.posterUrl).getQueryParameter("X-Plex-Token"))
            assertEquals(4, it.childCount)
        }
        assertTrue(catalog.collections.filter { it.title == "No poster" }.all { it.posterUrl == null })
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val cache = PlexCatalogCache(context)
        try {
            cache.write(connection, catalog)
            val restored = requireNotNull(cache.read(connection))
            assertEquals(catalog.collections, restored.collections)
            val cacheFiles = java.io.File(context.cacheDir, "plex_catalogs").listFiles().orEmpty()
            assertFalse(cacheFiles.any { it.readText().contains(connection.token) })
        } finally { cache.clear(connection) }
    }

    @Test fun collectionListsAndMembersArePaginatedByServerIdentity() = runBlocking {
        val requests = mutableListOf<Pair<String, Int>>()
        val api = fake<PlexApiService> { method, args ->
            when (method) {
                "getLibrarySections" -> PlexLibraryResponse(MediaContainer(directories = listOf(Directory("1", "Movies", "movie"))))
                "getContainer" -> {
                    val path = args[0] as String
                    val start = args[1] as? Int ?: 0
                    val size = args[2] as? Int ?: 200
                    requests += path to start
                    val total = when {
                        path.endsWith("/collections") -> 205
                        path.endsWith("/children") -> 450
                        else -> 0
                    }
                    val metadata = (start until minOf(start + size, total)).map { index ->
                        Metadata(ratingKey = "$index", title = "Item $index", type = if (total == 205) "collection" else "movie",
                            year = 2500 - index)
                    }
                    PlexLibraryResponse(MediaContainer(totalSize = total, metadata = metadata))
                }
                else -> PlexLibraryResponse()
            }
        }
        val repo = PlexRepository(PlexConnection("http://127.0.0.1:32400/", "fixture"), api, emptyWatchlist())
        assertEquals(205, repo.loadCatalog().collections.size)
        assertEquals(listOf(0, 200), requests.filter { it.first.endsWith("/collections") }.map { it.second })
        val members = repo.loadCollectionMembers("88")
        assertEquals(450, members.size)
        assertEquals("449", members.first().ratingKey)
        assertEquals(listOf(0, 200, 400), requests.filter { it.first == "library/collections/88/children" }.map { it.second })
    }

    private fun response(metadata: List<Metadata>) = PlexLibraryResponse(MediaContainer(totalSize = metadata.size, metadata = metadata))
    private fun emptyWatchlist() = fake<PlexWatchlistApiService> { _, _ -> PlexLibraryResponse() }
    private inline fun <reified T> fake(crossinline respond: (String, Array<out Any?>) -> Any): T =
        Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, method, args ->
            respond(method.name, args.orEmpty())
        } as T
}
