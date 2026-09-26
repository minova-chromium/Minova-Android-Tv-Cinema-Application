package com.minova.cinema.cast

import com.minova.cinema.domain.MediaTechnicalInfo

internal enum class CastDeliveryMode {
    DirectPlay,
    DirectStream,
    CompatibilityTranscode,
}

/** Chooses the least expensive stream that is safe across Chromecast generations. */
internal fun chooseCastDelivery(
    source: MediaTechnicalInfo,
    selectedAudioCodec: String?,
    audioTrackCount: Int,
    hasSelectedSubtitle: Boolean,
): CastDeliveryMode {
    if (hasSelectedSubtitle) return CastDeliveryMode.CompatibilityTranscode

    val container = source.container?.lowercase()
    val videoCodec = source.videoCodec?.lowercase()
    val audioCodec = selectedAudioCodec?.lowercase() ?: source.audioCodec?.lowercase()
    val height = source.height ?: 0
    val safeVideo = videoCodec in setOf("h264", "avc", "avc1") && (height == 0 || height <= 1080)
    val safeAudio = audioCodec in setOf("aac", "mp3", "ac3", "eac3", "ec3")

    return when {
        safeVideo && safeAudio && audioTrackCount <= 1 && container in setOf("mp4", "m4v") -> {
            CastDeliveryMode.DirectPlay
        }
        safeVideo -> CastDeliveryMode.DirectStream
        else -> CastDeliveryMode.CompatibilityTranscode
    }
}
