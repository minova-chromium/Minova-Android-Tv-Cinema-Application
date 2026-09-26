package com.minova.cinema.cast

import com.minova.cinema.domain.MediaTechnicalInfo
import org.junit.Assert.assertEquals
import org.junit.Test

class CastPlaybackPolicyTest {
    @Test
    fun compatibleSingleTrackMp4DirectPlays() {
        assertEquals(
            CastDeliveryMode.DirectPlay,
            chooseCastDelivery(
                source = source(container = "mp4", video = "h264", audio = "aac"),
                selectedAudioCodec = "aac",
                audioTrackCount = 1,
                hasSelectedSubtitle = false,
            ),
        )
    }

    @Test
    fun compatibleMkvUsesLowCpuDirectStream() {
        assertEquals(
            CastDeliveryMode.DirectStream,
            chooseCastDelivery(
                source = source(container = "mkv", video = "h264", audio = "aac"),
                selectedAudioCodec = "aac",
                audioTrackCount = 1,
                hasSelectedSubtitle = false,
            ),
        )
    }

    @Test
    fun selectedSubtitleUsesCompatibilityTranscode() {
        assertEquals(
            CastDeliveryMode.CompatibilityTranscode,
            chooseCastDelivery(
                source = source(container = "mp4", video = "h264", audio = "aac"),
                selectedAudioCodec = "aac",
                audioTrackCount = 1,
                hasSelectedSubtitle = true,
            ),
        )
    }

    @Test
    fun hevcUsesCompatibilityTranscodeForAllCastGenerations() {
        assertEquals(
            CastDeliveryMode.CompatibilityTranscode,
            chooseCastDelivery(
                source = source(container = "mkv", video = "hevc", audio = "aac", height = 2160),
                selectedAudioCodec = "aac",
                audioTrackCount = 1,
                hasSelectedSubtitle = false,
            ),
        )
    }

    private fun source(
        container: String,
        video: String,
        audio: String,
        height: Int = 1080,
    ) = MediaTechnicalInfo(
        container = container,
        videoCodec = video,
        audioCodec = audio,
        height = height,
    )
}
