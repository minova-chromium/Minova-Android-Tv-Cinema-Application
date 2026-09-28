package com.minova.cinema.data.remote

import androidx.core.net.toUri
import com.minova.cinema.BuildConfig
import java.net.URI
import java.util.UUID

data class PlexConnection(
    val baseUrl: String,
    val token: String,
)

object PlexConfig {
    const val HEADER_ACCEPT = "Accept"
    const val HEADER_TOKEN = "X-Plex-Token"
    const val HEADER_CLIENT_ID = "X-Plex-Client-Identifier"
    const val HEADER_PMS_API_VERSION = "X-Plex-Pms-Api-Version"
    const val CLIENT_IDENTIFIER = BuildConfig.PLEX_CLIENT_ID

    fun normalizeServerAddress(input: String): String {
        var value = input.trim().trimEnd('/')
        require(value.isNotBlank()) { "Enter your Plex server address." }
        if (!value.startsWith("http://", true) && !value.startsWith("https://", true)) {
            value = "http://$value"
        }

        val parsed = runCatching { URI(value) }.getOrNull()
        require(parsed != null && parsed.scheme?.lowercase() in setOf("http", "https")) {
            "Use an http:// or https:// Plex server address."
        }
        require(!parsed.host.isNullOrBlank()) { "The Plex server address is not valid." }
        // A bare LAN address and explicit HTTP use Plex's normal local port.
        // Explicit HTTPS URLs may be reverse proxies or Tailscale Serve
        // endpoints, where the standard TLS port (443) must be preserved.
        if (parsed.port == -1 && parsed.scheme.equals("http", ignoreCase = true)) {
            value = URI(
                parsed.scheme,
                parsed.userInfo,
                parsed.host,
                32400,
                parsed.path,
                parsed.query,
                parsed.fragment,
            ).toString()
        }
        return value.trimEnd('/') + "/"
    }

    fun requestHeaders(connection: PlexConnection): Map<String, String> = mapOf(
        HEADER_ACCEPT to "application/json",
        HEADER_TOKEN to connection.token,
        HEADER_CLIENT_ID to CLIENT_IDENTIFIER,
        "X-Plex-Product" to "Minova Cinema",
        "X-Plex-Version" to BuildConfig.VERSION_NAME,
        "X-Plex-Platform" to "Android",
        "X-Plex-Device" to "Android",
        "X-Plex-Device-Name" to "Minova Cinema",
        "X-Plex-Provides" to "player,controller",
        "X-Plex-Language" to "en",
        "X-Plex-Sync-Version" to "2",
        "X-Plex-Features" to "external-media",
    )
}

class PlexUrlFactory(
    private val connection: PlexConnection,
) {
    fun authenticated(path: String): String {
        val absoluteUrl = if (path.startsWith("http://") || path.startsWith("https://")) {
            path
        } else {
            connection.baseUrl.trimEnd('/') + "/" + path.trimStart('/')
        }
        val uri = absoluteUrl.toUri()
        if (uri.getQueryParameter(PlexConfig.HEADER_TOKEN) != null) return absoluteUrl
        return uri.buildUpon()
            .appendQueryParameter(PlexConfig.HEADER_TOKEN, connection.token)
            .build()
            .toString()
    }

    /** Rebuilds an authenticated media URL on this connection's receiver-accessible host. */
    fun rebaseAuthenticated(sourceUrl: String): String {
        val source = sourceUrl.toUri()
        val builder = connection.baseUrl.toUri().buildUpon()
        source.encodedPath?.trim('/')?.takeIf(String::isNotBlank)?.let(builder::appendEncodedPath)
        source.queryParameterNames
            .filterNot { it.equals(PlexConfig.HEADER_TOKEN, ignoreCase = true) }
            .forEach { name ->
                source.getQueryParameters(name).forEach { value ->
                    builder.appendQueryParameter(name, value)
                }
            }
        return builder
            .appendQueryParameter(PlexConfig.HEADER_TOKEN, connection.token)
            .build()
            .toString()
    }

    fun transcode(
        ratingKey: String,
        quality: PlaybackQuality,
        sessionId: String = UUID.randomUUID().toString(),
        subtitleStreamId: Long? = null,
        audioStreamId: Long? = null,
    ): String {
        val builder = connection.baseUrl.toUri().buildUpon()
            .appendEncodedPath("video/:/transcode/universal/start.m3u8")
            .appendQueryParameter("path", "http://127.0.0.1:32400/library/metadata/$ratingKey")
            .appendQueryParameter("mediaIndex", "0")
            .appendQueryParameter("partIndex", "0")
            .appendQueryParameter("protocol", "hls")
            .appendQueryParameter("offset", "0")
            .appendQueryParameter("fastSeek", "1")
            .appendQueryParameter("directPlay", "0")
            .appendQueryParameter("directStream", "1")
            .appendQueryParameter("subtitleSize", "100")
            .appendQueryParameter("audioBoost", "100")
            .appendQueryParameter("location", "lan")
            .appendQueryParameter("session", sessionId)
            .appendQueryParameter(PlexConfig.HEADER_TOKEN, connection.token)
            .appendQueryParameter(PlexConfig.HEADER_CLIENT_ID, PlexConfig.CLIENT_IDENTIFIER)
            .appendQueryParameter("X-Plex-Product", "Minova Cinema")
            .appendQueryParameter("X-Plex-Version", BuildConfig.VERSION_NAME)
            .appendQueryParameter("X-Plex-Platform", "Android")
        if (quality != PlaybackQuality.Original) {
            builder
                .appendQueryParameter("videoQuality", "100")
                .appendQueryParameter("videoResolution", quality.resolution)
                .appendQueryParameter("maxVideoBitrate", quality.maxBitrateKbps.toString())
        }
        audioStreamId?.takeIf { it > 0L }?.let {
            builder.appendQueryParameter("audioStreamID", it.toString())
        }
        if (subtitleStreamId != null && subtitleStreamId > 0L) {
            builder.appendQueryParameter("subtitles", "burn")
        } else {
            builder.appendQueryParameter("skipSubtitles", "1")
        }
        return builder.build()
            .toString()
    }
}

enum class PlaybackQuality(
    val label: String,
    val detail: String,
    val resolution: String,
    val maxBitrateKbps: Int,
) {
    Original("Original", "Direct play", "", 0),
    UltraHd("4K", "40 Mbps", "3840x2160", 40_000),
    FullHd("1080p", "12 Mbps", "1920x1080", 12_000),
    Hd("720p", "4 Mbps", "1280x720", 4_000),
    Sd("480p", "2 Mbps", "854x480", 2_000),
}
