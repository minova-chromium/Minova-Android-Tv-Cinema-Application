package com.minova.cinema.ui.player

import androidx.media3.common.Player
import com.minova.cinema.data.remote.PlaybackQuality
import com.minova.cinema.domain.MediaTechnicalInfo
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackRecoveryPolicyTest {
    @Test
    fun dolbyVisionGetsExclusivePlaybackPipeline() {
        val dolbyVision = MediaTechnicalInfo(isDolbyVision = true, dolbyVisionProfile = 8)
        assertFalse(
            PlaybackCodecPolicy.shouldPreloadNextItem(dolbyVision),
        )
        assertTrue(PlaybackCodecPolicy.requiresPlexVideoTranscode(dolbyVision))
        assertTrue(
            PlaybackCodecPolicy.shouldPreloadNextItem(
                MediaTechnicalInfo(videoCodec = "hevc"),
            ),
        )
    }

    @Test
    fun networkFailureRetriesCurrentSourceOnce() {
        assertEquals(
            AutomaticPlaybackRecovery.RetryCurrent,
            PlaybackRecoveryPolicy.decide(
                failure = PlaybackFailureKind.Network,
                currentQuality = PlaybackQuality.Original,
                currentSourceRetried = false,
                usingCompatibilityStream = false,
                attemptedQualities = emptySet(),
            ),
        )
    }

    @Test
    fun repeatedNetworkFailureUsesAnUnconstrainedCompatibilityStream() {
        assertEquals(
            AutomaticPlaybackRecovery.UseCompatibilityStream,
            PlaybackRecoveryPolicy.decide(
                failure = PlaybackFailureKind.Network,
                currentQuality = PlaybackQuality.Original,
                currentSourceRetried = true,
                usingCompatibilityStream = false,
                attemptedQualities = emptySet(),
            ),
        )
    }

    @Test
    fun decoderFailureSkipsAnAlreadyAttemptedQuality() {
        assertEquals(
            AutomaticPlaybackRecovery.ChangeQuality(PlaybackQuality.Hd),
            PlaybackRecoveryPolicy.decide(
                failure = PlaybackFailureKind.Decoder,
                currentQuality = PlaybackQuality.Original,
                currentSourceRetried = false,
                usingCompatibilityStream = true,
                attemptedQualities = setOf(PlaybackQuality.FullHd),
            ),
        )
    }

    @Test
    fun startupTimeoutUsesTheQualityLadder() {
        assertEquals(
            AutomaticPlaybackRecovery.ChangeQuality(PlaybackQuality.Hd),
            PlaybackRecoveryPolicy.decide(
                failure = PlaybackFailureKind.StartupTimeout,
                currentQuality = PlaybackQuality.FullHd,
                currentSourceRetried = false,
                usingCompatibilityStream = false,
                attemptedQualities = emptySet(),
            ),
        )
    }

    @Test
    fun exhaustedSdPlaybackFallsBackToTheRecoveryDialog() {
        assertEquals(
            AutomaticPlaybackRecovery.AskUser,
            PlaybackRecoveryPolicy.decide(
                failure = PlaybackFailureKind.Http,
                currentQuality = PlaybackQuality.Sd,
                currentSourceRetried = true,
                usingCompatibilityStream = false,
                attemptedQualities = setOf(PlaybackQuality.Sd),
            ),
        )
    }

    @Test
    fun markerSkipLeavesAHealthySeekAlone() {
        assertFalse(
            MarkerSkipRecoveryPolicy.shouldRecover(
                request = markerSkipRequest(),
                currentPositionMs = 63_000L,
                playbackState = Player.STATE_READY,
                currentMediaItemIndex = 0,
                playWhenReady = true,
            ),
        )
    }

    @Test
    fun markerSkipRecoversASeekThatNeverAdvanced() {
        assertTrue(
            MarkerSkipRecoveryPolicy.shouldRecover(
                request = markerSkipRequest(),
                currentPositionMs = 60_200L,
                playbackState = Player.STATE_READY,
                currentMediaItemIndex = 0,
                playWhenReady = true,
            ),
        )
    }

    @Test
    fun markerSkipRecoversAPlayerStillBuffering() {
        assertTrue(
            MarkerSkipRecoveryPolicy.shouldRecover(
                request = markerSkipRequest(),
                currentPositionMs = 60_000L,
                playbackState = Player.STATE_BUFFERING,
                currentMediaItemIndex = 0,
                playWhenReady = true,
            ),
        )
    }

    @Test
    fun markerSkipDoesNotRestartAfterTheUserPauses() {
        assertFalse(
            MarkerSkipRecoveryPolicy.shouldRecover(
                request = markerSkipRequest(),
                currentPositionMs = 60_000L,
                playbackState = Player.STATE_READY,
                currentMediaItemIndex = 0,
                playWhenReady = false,
            ),
        )
    }

    @Test
    fun markerSkipDoesNotTouchTheNextPlaylistItem() {
        assertFalse(
            MarkerSkipRecoveryPolicy.shouldRecover(
                request = markerSkipRequest(),
                currentPositionMs = 0L,
                playbackState = Player.STATE_BUFFERING,
                currentMediaItemIndex = 1,
                playWhenReady = true,
            ),
        )
    }

    private fun markerSkipRequest() = MarkerSkipRecoveryRequest(
        id = 1L,
        targetPositionMs = 60_000L,
        mediaItemIndex = 0,
        shouldPlay = true,
    )
}
