package com.minova.cinema.showcase

import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.remember
import com.minova.cinema.data.remote.PlaybackQuality
import com.minova.cinema.data.remote.PlexConnection
import com.minova.cinema.domain.MediaContent
import com.minova.cinema.domain.MediaKind
import com.minova.cinema.domain.PlaybackDiagnostics
import com.minova.cinema.domain.PlaybackSource
import com.minova.cinema.domain.PlexPlaybackMode
import com.minova.cinema.ui.player.PlayerScreen
import com.minova.cinema.ui.theme.MinovaCinemaTheme

/** Debug-only host for verifying the real phone player controls without Plex credentials. */
class PlayerSettingsShowcaseActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MinovaCinemaTheme {
                val content = remember {
                    MediaContent(
                        ratingKey = "phone-player-showcase",
                        title = "Minova phone player test",
                        secondaryTitle = null,
                        summary = null,
                        tagline = null,
                        year = 2026,
                        durationMs = 60_000L,
                        viewOffsetMs = 0L,
                        posterUrl = null,
                        backdropUrl = null,
                        contentRating = null,
                        kind = MediaKind.Movie,
                        playback = PlaybackSource(
                            partId = 1L,
                            directUrl = "http://10.0.2.2:8765/docs/assets/minova-cinema-trailer.mp4",
                            metadataKey = "/library/metadata/phone-player-showcase",
                            audioStreams = emptyList(),
                            subtitles = emptyList(),
                        ),
                    )
                }
                PlayerScreen(
                    content = content,
                    preRollTrailers = emptyList(),
                    bumperUri = null,
                    cinemaModeActive = false,
                    showMinovaTrailerPreRoll = false,
                    isTrailerRecordingMode = false,
                    connection = PlexConnection("http://10.0.2.2:8765/", "debug"),
                    autoplayNextEpisode = true,
                    inactivityCheckEnabled = false,
                    inactivityTimeoutMs = Long.MAX_VALUE,
                    lastInteractionAtMs = SystemClock.elapsedRealtime(),
                    onUserInteraction = {},
                    onPlaybackActivityChanged = {},
                    onCinemaPlaybackChanged = {},
                    onAutoplayNextEpisodeChanged = {},
                    onInactivityTimeout = {},
                    onProgress = { _, _, _ -> },
                    onSubtitleStreamSelected = { _, done -> done() },
                    onAudioStreamSelected = { _, done -> done() },
                    initialAudioDelayMs = 0,
                    initialSubtitleDelayMs = 0,
                    onAudioDelayChanged = {},
                    onSubtitleDelayChanged = {},
                    onDiagnosticsRequested = { _, _, _: PlaybackQuality, ready ->
                        ready(
                            PlaybackDiagnostics(
                                mode = PlexPlaybackMode.DirectPlay,
                                reason = "Debug direct play",
                            ),
                        )
                    },
                    onPlaybackEnded = { ready -> ready(null) },
                    onPlayNext = {},
                )
            }
        }
    }
}
