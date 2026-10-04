package com.minova.cinema.data.remote

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

data class PersonBackground(
    val biography: String?,
    val sourceLabel: String?,
    val sourceUrl: String?,
    val imdbUrl: String,
)

fun interface PersonMetadataLookup {
    suspend fun lookup(name: String): PersonBackground
}

class PersonMetadataService(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build(),
) : PersonMetadataLookup {
    override suspend fun lookup(name: String): PersonBackground = withContext(Dispatchers.IO) {
        val fallbackImdb = imdbSearchUrl(name)
        runCatching {
            val searchUrl = "https://en.wikipedia.org/w/api.php".toHttpUrl().newBuilder()
                .addQueryParameter("action", "query")
                .addQueryParameter("format", "json")
                .addQueryParameter("formatversion", "2")
                .addQueryParameter("generator", "search")
                .addQueryParameter("gsrsearch", "intitle:$name")
                .addQueryParameter("gsrnamespace", "0")
                .addQueryParameter("gsrlimit", "5")
                .addQueryParameter("prop", "extracts|pageprops")
                .addQueryParameter("exintro", "1")
                .addQueryParameter("explaintext", "1")
                .addQueryParameter("exchars", "1600")
                .addQueryParameter("redirects", "1")
                .build()
            val search = requestJson(searchUrl.toString())
            val pages = search.getAsJsonObject("query")?.getAsJsonArray("pages")
                ?.mapNotNull { it.takeIf { value -> value.isJsonObject }?.asJsonObject }
                .orEmpty()
            val normalized = name.trim().lowercase()
            val page = pages.firstOrNull { it.string("title")?.trim()?.lowercase() == normalized }
                ?: pages.firstOrNull { it.string("title")?.trim()?.lowercase()?.startsWith("$normalized (") == true }
                ?: pages.firstOrNull()
            val title = page?.string("title")
            val entityId = page?.getAsJsonObject("pageprops")?.string("wikibase_item")
            val imdbId = entityId?.takeIf { it.matches(Regex("Q\\d+", RegexOption.IGNORE_CASE)) }?.let { id ->
                val entity = requestJson("https://www.wikidata.org/wiki/Special:EntityData/$id.json")
                entity.getAsJsonObject("entities")?.getAsJsonObject(id)
                    ?.getAsJsonObject("claims")?.getAsJsonArray("P345")
                    ?.mapNotNull { claim ->
                        claim.asJsonObject.getAsJsonObject("mainsnak")
                            ?.getAsJsonObject("datavalue")?.string("value")
                    }
                    ?.firstOrNull { it.matches(Regex("nm\\d+", RegexOption.IGNORE_CASE)) }
            }
            PersonBackground(
                biography = page?.string("extract"),
                sourceLabel = title?.let { "Wikipedia" },
                sourceUrl = title?.let { "https://en.wikipedia.org/wiki/${it.replace(' ', '_').urlEncodePath()}" },
                imdbUrl = imdbId?.let { "https://www.imdb.com/name/$it/" } ?: fallbackImdb,
            )
        }.getOrElse {
            PersonBackground(null, null, null, fallbackImdb)
        }
    }

    private fun requestJson(url: String): JsonObject {
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("User-Agent", "Minova Cinema/1.0 (+https://github.com/minova-chromium/Minova-Android-Tv-Cinema-Application)")
            .build()
        return client.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "Person metadata returned ${response.code}." }
            val body = checkNotNull(response.body) { "Person metadata response was empty." }
            JsonParser.parseString(body.string()).asJsonObject
        }
    }
}

private fun JsonObject.string(name: String): String? = get(name)?.takeUnless { it.isJsonNull }?.asString

private fun String.urlEncodePath(): String = java.net.URLEncoder.encode(this, Charsets.UTF_8.name()).replace("+", "%20")

private fun imdbSearchUrl(name: String): String = "https://www.imdb.com/find/?q=${java.net.URLEncoder.encode(name, Charsets.UTF_8.name())}&s=nm"
