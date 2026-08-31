package com.minova.cinema.data.local

import android.content.Context
import androidx.core.net.toUri
import com.google.gson.Gson
import com.minova.cinema.data.remote.PlexConnection
import com.minova.cinema.data.remote.PlexUrlFactory
import com.minova.cinema.domain.CinemaCatalog
import com.minova.cinema.domain.MediaContent
import java.io.File

/**
 * Small private, per-server metadata cache. Playback URLs and Plex tokens are
 * deliberately never written: cached art paths are re-authenticated when read.
 */
class PlexCatalogCache(context: Context) {
    private val directory = File(context.applicationContext.cacheDir, "plex_catalogs")
    private val gson = Gson()

    fun read(connection: PlexConnection): CinemaCatalog? = runCatching {
        val file = cacheFile(connection)
        if (!file.isFile) return null
        val envelope = gson.fromJson(file.readText(), CacheEnvelope::class.java)
        if (System.currentTimeMillis() - envelope.savedAtMs > MAX_CACHE_AGE_MS) return null
        envelope.catalog.restoreAuthenticatedUrls(connection)
    }.getOrNull()

    fun write(connection: PlexConnection, catalog: CinemaCatalog) {
        runCatching {
            directory.mkdirs()
            val destination = cacheFile(connection)
            val temporary = File(destination.parentFile, "${destination.name}.tmp")
            temporary.writeText(
                gson.toJson(CacheEnvelope(System.currentTimeMillis(), catalog.sanitizedForCache())),
            )
            if (!temporary.renameTo(destination)) {
                temporary.copyTo(destination, overwrite = true)
                temporary.delete()
            }
        }
    }

    fun clear(connection: PlexConnection) {
        runCatching { cacheFile(connection).delete() }
    }

    private fun cacheFile(connection: PlexConnection): File {
        val key = connection.baseUrl.lowercase().hashCode().toUInt().toString(16)
        return File(directory, "$key.json")
    }

    private data class CacheEnvelope(
        val savedAtMs: Long = 0L,
        val catalog: CinemaCatalog = CinemaCatalog("Plex", emptyList(), emptyList(), emptyList()),
    )

    private companion object {
        const val MAX_CACHE_AGE_MS = 7L * 24L * 60L * 60L * 1_000L
    }
}

private fun CinemaCatalog.sanitizedForCache(): CinemaCatalog = copy(
    movies = movies.map(MediaContent::sanitizedForCache),
    shows = shows.map(MediaContent::sanitizedForCache),
    continueWatching = continueWatching.map(MediaContent::sanitizedForCache),
    myList = myList.map(MediaContent::sanitizedForCache),
)

private fun MediaContent.sanitizedForCache(): MediaContent = copy(
    posterUrl = posterUrl.withoutAuthentication(),
    backdropUrl = backdropUrl.withoutAuthentication(),
    credits = credits.map { it.copy(imageUrl = it.imageUrl.withoutAuthentication()) },
    playback = null,
)

private fun CinemaCatalog.restoreAuthenticatedUrls(connection: PlexConnection): CinemaCatalog {
    val urls = PlexUrlFactory(connection)
    fun restore(content: MediaContent): MediaContent = content.copy(
        posterUrl = content.posterUrl?.let(urls::authenticated),
        backdropUrl = content.backdropUrl?.let(urls::authenticated),
        credits = content.credits.map { credit ->
            credit.copy(imageUrl = credit.imageUrl?.let(urls::authenticated))
        },
    )
    return copy(
        movies = movies.map(::restore),
        shows = shows.map(::restore),
        continueWatching = continueWatching.map(::restore),
        myList = myList.map(::restore),
    )
}

private fun String?.withoutAuthentication(): String? = this?.let { value ->
    val uri = value.toUri()
    uri.buildUpon().clearQuery().fragment(null).build().toString()
}
