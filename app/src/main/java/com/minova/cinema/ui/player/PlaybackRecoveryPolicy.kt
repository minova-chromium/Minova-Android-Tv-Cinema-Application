package com.minova.cinema.ui.player

import com.minova.cinema.data.remote.PlaybackQuality

internal enum class PlaybackFailureKind {
    Network,
    Http,
    Decoder,
    StartupTimeout,
    Other,
}

internal sealed interface AutomaticPlaybackRecovery {
    data object RetryCurrent : AutomaticPlaybackRecovery
    data object UseCompatibilityStream : AutomaticPlaybackRecovery
    data class ChangeQuality(val quality: PlaybackQuality) : AutomaticPlaybackRecovery
    data object AskUser : AutomaticPlaybackRecovery
}

/**
 * Keeps automatic recovery bounded and predictable. The player gets one retry
 * for a source, then walks down the same quality ladder on every Android form
 * factor. Once the ladder is exhausted the existing recovery dialog takes over.
 */
internal object PlaybackRecoveryPolicy {
    fun decide(
        failure: PlaybackFailureKind,
        currentQuality: PlaybackQuality,
        currentSourceRetried: Boolean,
        usingCompatibilityStream: Boolean,
        attemptedQualities: Set<PlaybackQuality>,
    ): AutomaticPlaybackRecovery {
        if (failure == PlaybackFailureKind.Network || failure == PlaybackFailureKind.Other) {
            if (!currentSourceRetried) return AutomaticPlaybackRecovery.RetryCurrent
        }

        if (currentQuality == PlaybackQuality.Original && !usingCompatibilityStream) {
            return AutomaticPlaybackRecovery.UseCompatibilityStream
        }

        val nextQuality = qualityLadderAfter(currentQuality)
            .firstOrNull { it !in attemptedQualities }
            ?: return AutomaticPlaybackRecovery.AskUser
        return AutomaticPlaybackRecovery.ChangeQuality(nextQuality)
    }

    private fun qualityLadderAfter(current: PlaybackQuality): List<PlaybackQuality> = when (current) {
        PlaybackQuality.Original,
        PlaybackQuality.UltraHd -> listOf(
            PlaybackQuality.FullHd,
            PlaybackQuality.Hd,
            PlaybackQuality.Sd,
        )
        PlaybackQuality.FullHd -> listOf(PlaybackQuality.Hd, PlaybackQuality.Sd)
        PlaybackQuality.Hd -> listOf(PlaybackQuality.Sd)
        PlaybackQuality.Sd -> emptyList()
    }
}
