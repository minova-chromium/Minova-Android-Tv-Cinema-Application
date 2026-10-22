package com.minova.cinema.data.local

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackResumeStoreTest {
    @Test
    fun `fresh local checkpoint wins when Plex still has the old position`() {
        val now = 1_000_000L
        assertEquals(
            23L * 60_000L,
            selectPlaybackResumePosition(
                ratingKey = "episode",
                serverPositionMs = 10L * 60_000L,
                expectedDurationMs = 45L * 60_000L,
                snapshot = PlaybackResumeSnapshot(
                    ratingKey = "episode",
                    positionMs = 23L * 60_000L,
                    durationMs = 45L * 60_000L,
                    savedAtEpochMs = now - 1_000L,
                ),
                nowEpochMs = now,
            ),
        )
    }

    @Test
    fun `checkpoint for another title cannot change playback`() {
        assertEquals(
            10L * 60_000L,
            selectPlaybackResumePosition(
                ratingKey = "episode-two",
                serverPositionMs = 10L * 60_000L,
                expectedDurationMs = 45L * 60_000L,
                snapshot = PlaybackResumeSnapshot("episode-one", 23L * 60_000L, 45L * 60_000L, 1L),
                nowEpochMs = 2L,
            ),
        )
    }

    @Test
    fun `fresh checkpoint preserves an intentional rewind`() {
        val now = 1_000_000L
        assertEquals(
            8L * 60_000L,
            selectPlaybackResumePosition(
                ratingKey = "episode",
                serverPositionMs = 23L * 60_000L,
                expectedDurationMs = 45L * 60_000L,
                snapshot = PlaybackResumeSnapshot(
                    "episode",
                    8L * 60_000L,
                    45L * 60_000L,
                    now - 1_000L,
                ),
                nowEpochMs = now,
            ),
        )
    }

    @Test
    fun `expired checkpoint does not override Plex`() {
        val now = 4_000_000L
        assertEquals(
            10L * 60_000L,
            selectPlaybackResumePosition(
                ratingKey = "episode",
                serverPositionMs = 10L * 60_000L,
                expectedDurationMs = 45L * 60_000L,
                snapshot = PlaybackResumeSnapshot("episode", 23L * 60_000L, 45L * 60_000L, 1L),
                nowEpochMs = now,
            ),
        )
    }

    @Test
    fun `near-complete checkpoint does not resurrect a finished title`() {
        val now = 1_000_000L
        assertEquals(
            0L,
            selectPlaybackResumePosition(
                ratingKey = "movie",
                serverPositionMs = 0L,
                expectedDurationMs = 90L * 60_000L,
                snapshot = PlaybackResumeSnapshot(
                    "movie",
                    90L * 60_000L - 5_000L,
                    90L * 60_000L,
                    now,
                ),
                nowEpochMs = now,
            ),
        )
    }
}
