package com.minova.cinema.ui.player

import com.minova.cinema.data.remote.PlaybackQuality
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackRecoveryPolicyTest {
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
}
