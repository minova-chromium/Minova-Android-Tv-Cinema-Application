package com.minova.cinema.data

import android.media.MediaCodecList
import android.os.Build
import com.minova.cinema.data.remote.PlexConfig
import com.minova.cinema.data.remote.PlexConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

data class PlaybackCapabilityReport(
    val speedMbps: Double,
    val recommendation: String,
    val supportedVideoCodecs: List<String>,
    val tvSummary: String,
)

class PlaybackCapabilityAssistant {
    suspend fun analyze(connection: PlexConnection, sampleUrl: String): PlaybackCapabilityReport =
        withContext(Dispatchers.IO) {
            val speed = measurePlexSpeed(connection, sampleUrl)
            val codecs = supportedVideoCodecs()
            val hevc = "HEVC" in codecs
            val av1 = "AV1" in codecs
            val recommendation = when {
                speed >= 70.0 && hevc -> "Original quality, including 4K HEVC"
                speed >= 35.0 -> "Original quality up to typical 4K bitrates"
                speed >= 15.0 -> "1080p · 12 Mbps"
                speed >= 6.0 -> "720p · 4 Mbps"
                else -> "480p · 2 Mbps"
            }
            PlaybackCapabilityReport(
                speedMbps = speed,
                recommendation = recommendation,
                supportedVideoCodecs = codecs,
                tvSummary = buildString {
                    append(Build.MANUFACTURER.replaceFirstChar(Char::uppercase))
                    append(' ')
                    append(Build.MODEL)
                    append(" · ")
                    append(if (hevc) "HEVC" else "no HEVC")
                    append(" · ")
                    append(if (av1) "AV1" else "no AV1")
                },
            )
        }

    private fun measurePlexSpeed(connection: PlexConnection, sampleUrl: String): Double {
        val client = OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
        val request = Request.Builder()
            .url(sampleUrl)
            .header("Range", "bytes=0-${TEST_BYTES - 1}")
            .apply {
                PlexConfig.requestHeaders(connection).forEach { (name, value) -> header(name, value) }
            }
            .build()
        val started = System.nanoTime()
        var bytes = 0L
        client.newCall(request).execute().use { response ->
            check(response.isSuccessful || response.code == 206) {
                "Plex speed test failed (${response.code})."
            }
            val input = requireNotNull(response.body).byteStream()
            val buffer = ByteArray(64 * 1_024)
            while (bytes < TEST_BYTES) {
                val read = input.read(buffer, 0, minOf(buffer.size.toLong(), TEST_BYTES - bytes).toInt())
                if (read < 0) break
                bytes += read
            }
        }
        val seconds = (System.nanoTime() - started) / 1_000_000_000.0
        check(bytes > 0 && seconds > 0.0) { "Plex returned no test data." }
        return (((bytes * 8.0) / seconds) / 1_000_000.0 * 10.0).roundToInt() / 10.0
    }

    private fun supportedVideoCodecs(): List<String> {
        val mimeTypes = MediaCodecList(MediaCodecList.ALL_CODECS).codecInfos
            .asSequence()
            .filterNot { it.isEncoder }
            .flatMap { it.supportedTypes.asSequence() }
            .map(String::lowercase)
            .toSet()
        return buildList {
            if ("video/hevc" in mimeTypes) add("HEVC")
            if ("video/av01" in mimeTypes) add("AV1")
            if ("video/dolby-vision" in mimeTypes) add("Dolby Vision")
            if ("video/avc" in mimeTypes) add("H.264")
            if ("video/x-vnd.on2.vp9" in mimeTypes) add("VP9")
        }
    }

    private companion object {
        const val TEST_BYTES = 6L * 1_024L * 1_024L
    }
}
