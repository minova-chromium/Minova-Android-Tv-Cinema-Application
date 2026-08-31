package com.minova.cinema.tvhome

import android.annotation.SuppressLint
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import androidx.core.content.edit
import androidx.tvprovider.media.tv.Channel
import androidx.tvprovider.media.tv.PreviewProgram
import androidx.tvprovider.media.tv.TvContractCompat
import com.minova.cinema.domain.CinemaCatalog
import com.minova.cinema.domain.MediaContent

/** Publishes app-owned, deep-linked shelves to the Android TV launcher. */
@SuppressLint("RestrictedApi") // tvprovider 1.0 exposes its documented builders with this annotation.
class TvHomePublisher(context: Context) {
    private val appContext = context.applicationContext
    private val resolver = appContext.contentResolver
    private val preferences = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun publish(catalog: CinemaCatalog) {
        runCatching {
            publishChannel(
                key = "continue",
                title = "Continue Watching · Minova Cinema",
                media = catalog.continueWatching,
            )
            publishChannel(
                key = "watchlist",
                title = "Plex Watchlist · Minova Cinema",
                media = catalog.myList,
            )
        }
    }

    /** Must be called from a foreground user action so Android TV may show its approval UI. */
    fun requestChannelsBrowsable() {
        listOf("continue", "watchlist").mapNotNull(::existingChannelId).forEach { id ->
            TvContractCompat.requestChannelBrowsable(appContext, id)
        }
    }

    private fun publishChannel(key: String, title: String, media: List<MediaContent>) {
        val channelId = existingChannelId(key) ?: createChannel(key, title)
        resolver.delete(
            TvContractCompat.buildPreviewProgramsUriForChannel(channelId),
            null,
            null,
        )
        media.distinctBy(MediaContent::ratingKey).take(MAX_PROGRAMS).forEach { content ->
            val program = PreviewProgram.Builder()
                .setChannelId(channelId)
                .setInternalProviderId(content.ratingKey)
                .setTitle(content.title)
                .setDescription(content.summary.orEmpty())
                .setPosterArtUri(content.backdropUrl?.let(Uri::parse) ?: content.posterUrl?.let(Uri::parse))
                .setIntentUri(content.deepLink())
                .setType(
                    if (content.kind.name == "Movie") {
                        TvContractCompat.PreviewProgramColumns.TYPE_MOVIE
                    } else {
                        TvContractCompat.PreviewProgramColumns.TYPE_TV_EPISODE
                    },
                )
                .setDurationMillis(content.durationMs?.toInt() ?: 0)
                .build()
            resolver.insert(TvContractCompat.PreviewPrograms.CONTENT_URI, program.toContentValues())
        }
    }

    private fun createChannel(key: String, title: String): Long {
        val channel = Channel.Builder()
            .setType(TvContractCompat.Channels.TYPE_PREVIEW)
            .setDisplayName(title)
            .setInternalProviderId(key)
            .setAppLinkIntentUri(Uri.parse("minova://content"))
            .build()
        val uri = requireNotNull(
            resolver.insert(TvContractCompat.Channels.CONTENT_URI, channel.toContentValues()),
        )
        val id = ContentUris.parseId(uri)
        preferences.edit { putLong("channel_$key", id) }
        return id
    }

    private fun existingChannelId(key: String): Long? = preferences
        .getLong("channel_$key", -1L)
        .takeIf { it > 0L }

    private fun MediaContent.deepLink(): Uri = Uri.Builder()
        .scheme("minova")
        .authority("content")
        .appendPath(ratingKey)
        .build()

    private companion object {
        const val PREFS = "minova_tv_home"
        const val MAX_PROGRAMS = 30
    }
}
