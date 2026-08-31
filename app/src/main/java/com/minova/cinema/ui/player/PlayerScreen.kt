package com.minova.cinema.ui.player

import android.os.SystemClock
import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.C
import androidx.media3.common.AudioAttributes
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioCapabilities
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Button
import androidx.tv.material3.Icon
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.minova.cinema.data.remote.PlaybackQuality
import com.minova.cinema.data.remote.PlexConfig
import com.minova.cinema.data.remote.PlexConnection
import com.minova.cinema.data.remote.PlexUrlFactory
import com.minova.cinema.domain.MediaContent
import com.minova.cinema.domain.MediaKind
import com.minova.cinema.domain.AudioStream
import com.minova.cinema.domain.SubtitleStream
import com.minova.cinema.domain.PlaybackDiagnostics
import com.minova.cinema.domain.PlexPlaybackMode
import com.minova.cinema.domain.MediaChapter
import com.minova.cinema.ui.theme.MinovaCyan
import com.minova.cinema.ui.theme.MinovaMuted
import com.minova.cinema.ui.theme.MinovaNightDeep
import com.minova.cinema.ui.theme.MinovaSurface
import com.minova.cinema.ui.theme.MinovaTeal
import kotlinx.coroutines.delay
import java.util.UUID

private const val NEXT_EPISODE_COUNTDOWN_SECONDS = 10
private const val INACTIVITY_PROMPT_SECONDS = 30
private const val PLAYLIST_PRELOAD_DURATION_US = 30L * 1_000_000L

private data class SubtitleTrackOption(
    val id: String,
    val label: String,
    val language: String?,
    val group: Tracks.Group,
    val trackIndex: Int,
)

private data class AudioTrackOption(
    val id: String,
    val label: String,
    val language: String?,
    val codec: String?,
    val channels: Int?,
    val passthroughSupported: Boolean,
    val group: Tracks.Group,
    val trackIndex: Int,
)

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    content: MediaContent,
    preRollTrailers: List<MediaContent>,
    bumperUri: String?,
    cinemaModeActive: Boolean,
    connection: PlexConnection,
    autoplayNextEpisode: Boolean,
    inactivityCheckEnabled: Boolean,
    inactivityTimeoutMs: Long,
    lastInteractionAtMs: Long,
    onUserInteraction: () -> Unit,
    onPlaybackActivityChanged: (Boolean) -> Unit,
    onCinemaPlaybackChanged: (Boolean) -> Unit,
    onAutoplayNextEpisodeChanged: (Boolean) -> Unit,
    onInactivityTimeout: () -> Unit,
    onProgress: (positionMs: Long, durationMs: Long, state: String) -> Unit,
    onSubtitleStreamSelected: (subtitleStreamId: Long?, onComplete: () -> Unit) -> Unit,
    onAudioStreamSelected: (audioStreamId: Long, onComplete: () -> Unit) -> Unit,
    initialAudioDelayMs: Int,
    initialSubtitleDelayMs: Int,
    onAudioDelayChanged: (Int) -> Unit,
    onSubtitleDelayChanged: (Int) -> Unit,
    onDiagnosticsRequested: (
        content: MediaContent,
        sessionId: String?,
        quality: PlaybackQuality,
        onReady: (PlaybackDiagnostics) -> Unit,
    ) -> Unit,
    onPlaybackEnded: (onReady: (MediaContent?) -> Unit) -> Unit,
    onPlayNext: (MediaContent) -> Unit,
) {
    val playback = content.playback
    if (playback == null) {
        Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            Text("This item has no playable Plex media part.")
        }
        return
    }

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val latestProgress by rememberUpdatedState(onProgress)
    val latestPlaybackEnded by rememberUpdatedState(onPlaybackEnded)
    val latestPlaybackActivityChanged by rememberUpdatedState(onPlaybackActivityChanged)
    val latestDiagnosticsRequested by rememberUpdatedState(onDiagnosticsRequested)
    val urlFactory = remember(connection) { PlexUrlFactory(connection) }
    var playerView by remember { mutableStateOf<PlayerView?>(null) }
    var controlsVisible by remember { mutableStateOf(true) }
    var requestBottomFocus by remember { mutableStateOf(false) }
    var bottomControlsFocused by remember { mutableStateOf(false) }
    var controlsInteractionId by remember { mutableStateOf(0L) }
    var selectedQuality by remember { mutableStateOf(PlaybackQuality.Original) }
    var settingsVisible by remember { mutableStateOf(false) }
    var subtitleTracks by remember { mutableStateOf<List<SubtitleTrackOption>>(emptyList()) }
    var selectedSubtitleId by remember { mutableStateOf<String?>(null) }
    var audioTracks by remember { mutableStateOf<List<AudioTrackOption>>(emptyList()) }
    var selectedAudioTrackId by remember { mutableStateOf<String?>(null) }
    var selectedPlexAudioId by remember(playback.audioStreams) {
        mutableStateOf(playback.audioStreams.firstOrNull { it.selected }?.id)
    }
    var selectedPlexSubtitleId by remember(playback.subtitles) {
        mutableStateOf(playback.subtitles.firstOrNull { it.selected }?.id)
    }
    var playbackTimeLeftMs by remember(content.ratingKey) { mutableStateOf(content.timeLeftMs ?: 0L) }
    var playbackPositionMs by remember(content.ratingKey) {
        mutableStateOf(content.viewOffsetMs.coerceAtLeast(0L))
    }
    var playbackDurationMs by remember(content.ratingKey) {
        mutableStateOf(content.durationMs?.coerceAtLeast(0L) ?: 0L)
    }
    var activeVideoResolution by remember(content.ratingKey) { mutableStateOf<String?>(null) }
    var playbackMessage by remember(content.ratingKey) { mutableStateOf<String?>(null) }
    var diagnostics by remember(content.ratingKey) {
        mutableStateOf(
            PlaybackDiagnostics(
                mode = PlexPlaybackMode.DirectPlay,
                reason = "The TV is playing the original file without conversion",
                source = playback.technicalInfo,
            ),
        )
    }
    var audioDelayMs by remember(content.ratingKey) { mutableStateOf(initialAudioDelayMs) }
    var subtitleDelayMs by remember(content.ratingKey) { mutableStateOf(initialSubtitleDelayMs) }
    var restartPositionMs by remember(content.ratingKey) { mutableStateOf<Long?>(null) }
    var restartPlaylistIndex by remember(content.ratingKey) { mutableStateOf<Int?>(null) }
    var nextEpisode by remember(content.ratingKey) { mutableStateOf<MediaContent?>(null) }
    var nextUpLoading by remember(content.ratingKey) { mutableStateOf(false) }
    var inactivityPromptVisible by remember { mutableStateOf(false) }
    var resumeAfterInactivityPrompt by remember { mutableStateOf(false) }
    var endHandled by remember(content.ratingKey) { mutableStateOf(false) }
    var playlistInitialized by remember(content.ratingKey) { mutableStateOf(false) }
    var mainResumeApplied by remember(content.ratingKey) { mutableStateOf(false) }
    var activePlaylistIndex by remember(content.ratingKey) { mutableStateOf(0) }
    var activePlaylistTitle by remember(content.ratingKey) { mutableStateOf(content.title) }
    var lastAppliedSourceKey by remember(content.ratingKey) { mutableStateOf("") }
    var lastDiagnosticsKey by remember(content.ratingKey) { mutableStateOf("") }
    val settingsFocusRequester = remember { FocusRequester() }
    val markerFocusRequester = remember { FocusRequester() }
    val latestSelectedQuality by rememberUpdatedState(selectedQuality)
    val playableTrailers = remember(preRollTrailers) {
        preRollTrailers.filter { it.playback != null }.take(2)
    }
    val localBumperUri = remember(bumperUri) { bumperUri?.takeIf(String::isNotBlank) }
    val mainFeatureIndex = playableTrailers.size + if (localBumperUri != null) 1 else 0
    val isMainFeatureActive = activePlaylistIndex == mainFeatureIndex
    val playbackSessionId = remember(content.ratingKey) { UUID.randomUUID().toString() }

    // Movie/media attributes select Android's HDMI/optical media route and
    // request audio focus. Avoiding AudioProcessors keeps encoded passthrough
    // available for Dolby and DTS tracks.
    val cinemaAudioAttributes = remember {
        AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
            .setUsage(C.USAGE_MEDIA)
            .build()
    }

    // Read the formats advertised by the connected TV or receiver. Media3's
    // context-aware DefaultAudioSink uses the same system capabilities.
    val audioCapabilities = remember(context, cinemaAudioAttributes) {
        AudioCapabilities.getCapabilities(
            context,
            cinemaAudioAttributes,
            null,
            emptyList(),
        )
    }

    val renderersFactory = remember(content.ratingKey, audioDelayMs, subtitleDelayMs) {
        CinemaRenderersFactory(context, audioDelayMs, subtitleDelayMs)
    }
    val player = remember(content.ratingKey, connection, renderersFactory) {
        val httpFactory = DefaultHttpDataSource.Factory()
            .setDefaultRequestProperties(PlexConfig.requestHeaders(connection))
            .setConnectTimeoutMs(10_000)
            .setReadTimeoutMs(45_000)
            .setAllowCrossProtocolRedirects(true)
        val dataSourceFactory = DefaultDataSource.Factory(context, httpFactory)
        val sourceFactory = DefaultMediaSourceFactory(context).setDataSourceFactory(dataSourceFactory)

        // Prefer cinema bitstream formats when Plex exposes multiple default
        // audio choices. The renderer still rejects unsupported formats and
        // falls back normally instead of forcing an invalid bitstream.
        val trackSelector = DefaultTrackSelector(context).apply {
            setParameters(
                buildUponParameters()
                    .setPreferredAudioMimeTypes(
                        MimeTypes.AUDIO_E_AC3_JOC,
                        MimeTypes.AUDIO_E_AC3,
                        MimeTypes.AUDIO_AC3,
                        MimeTypes.AUDIO_DTS_X,
                        MimeTypes.AUDIO_DTS_HD,
                        MimeTypes.AUDIO_DTS,
                        MimeTypes.AUDIO_TRUEHD,
                    )
                    .setAllowAudioMixedMimeTypeAdaptiveness(false)
                    .build(),
            )
        }

        ExoPlayer.Builder(
            context,
            renderersFactory,
        )
            .setMediaSourceFactory(sourceFactory)
            .setTrackSelector(trackSelector)
            .setAudioAttributes(cinemaAudioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            // Pass the exact source cadence to Android's video Surface. A TV
            // with Match Content Frame Rate enabled can select 24/50/60 Hz.
            .setVideoChangeFrameRateStrategy(
                C.VIDEO_CHANGE_FRAME_RATE_STRATEGY_ONLY_IF_SEAMLESS,
            )
            .build()
            .apply {
                repeatMode = Player.REPEAT_MODE_OFF
                playWhenReady = true
                // Media3 1.10 preloads the next playlist period while the
                // current trailer/bumper is playing.
                preloadConfiguration = ExoPlayer.PreloadConfiguration(
                    PLAYLIST_PRELOAD_DURATION_US,
                )
            }
    }

    fun mainFeatureMediaItem(quality: PlaybackQuality): MediaItem {
        val uri = if (quality == PlaybackQuality.Original) {
            playback.directUrl.toUri().buildUpon()
                .appendQueryParameter("X-Plex-Session-Identifier", playbackSessionId)
                .build()
                .toString()
        } else {
            urlFactory.transcode(
                ratingKey = content.ratingKey,
                quality = quality,
                // A new Plex transcoder session prevents a previous quality or
                // burned-subtitle choice from being reused by the server.
                sessionId = playbackSessionId,
                subtitleStreamId = selectedPlexSubtitleId,
                audioStreamId = selectedPlexAudioId,
            )
        }
        return MediaItem.Builder()
            .setUri(uri)
            .setMediaId("feature:${content.ratingKey}")
            .setMediaMetadata(MediaMetadata.Builder().setTitle(content.title).build())
            .setSubtitleConfigurations(playback.subtitles.mapNotNull(::subtitleConfiguration))
            .build()
    }

    fun trailerMediaItem(trailer: MediaContent): MediaItem = MediaItem.Builder()
        .setUri(requireNotNull(trailer.playback).directUrl)
        .setMediaId("trailer:${trailer.ratingKey}")
        .setMediaMetadata(
            MediaMetadata.Builder().setTitle("Trailer · ${trailer.title}").build(),
        )
        .build()

    fun bumperMediaItem(uri: String): MediaItem = MediaItem.Builder()
        .setUri(uri)
        .setMediaId("bumper")
        .setMediaMetadata(MediaMetadata.Builder().setTitle("Minova Cinema").build())
        .build()

    fun activeSourceKey(): String = if (selectedQuality == PlaybackQuality.Original) {
        selectedQuality.name
    } else {
        "${selectedQuality.name}:$selectedPlexSubtitleId:$selectedPlexAudioId"
    }

    LaunchedEffect(
        player,
        content.ratingKey,
        playableTrailers,
        localBumperUri,
    ) {
        val items = buildList {
            playableTrailers.forEach { add(trailerMediaItem(it)) }
            localBumperUri?.let { add(bumperMediaItem(it)) }
            add(mainFeatureMediaItem(selectedQuality))
        }
        player.setMediaItems(items)
        activePlaylistIndex = 0
        activePlaylistTitle = items.firstOrNull()?.mediaMetadata?.title?.toString() ?: content.title
        val restoredIndex = restartPlaylistIndex?.coerceIn(0, items.lastIndex)
        val restoredPosition = restartPositionMs
        if (restoredIndex != null && restoredPosition != null) {
            player.seekTo(restoredIndex, restoredPosition)
            restartPlaylistIndex = null
            restartPositionMs = null
            mainResumeApplied = restoredIndex == mainFeatureIndex
        } else if (mainFeatureIndex == 0 && content.viewOffsetMs > 0L) {
            player.seekTo(0, content.viewOffsetMs)
            mainResumeApplied = true
        }
        player.prepare()
        player.playWhenReady = true
        lastAppliedSourceKey = activeSourceKey()
        playlistInitialized = true
    }

    LaunchedEffect(selectedQuality, selectedPlexSubtitleId, selectedPlexAudioId, player) {
        if (!playlistInitialized) return@LaunchedEffect
        val sourceKey = activeSourceKey()
        if (sourceKey == lastAppliedSourceKey) return@LaunchedEffect
        val position = player.currentPosition.coerceAtLeast(0L)
        val currentIndex = player.currentMediaItemIndex
        player.replaceMediaItem(mainFeatureIndex, mainFeatureMediaItem(selectedQuality))
        if (currentIndex == mainFeatureIndex) player.seekTo(mainFeatureIndex, position)
        lastAppliedSourceKey = sourceKey
    }

    fun resumeSynchronized() {
        val resumePosition = player.currentPosition.coerceAtLeast(0L)
        player.seekTo(resumePosition)
        player.play()
    }

    // Tracks can change after preparation, a Plex transcode change, or a new
    // period in an HLS stream. Rebuild the D-pad menu from Player.Tracks each
    // time so selection is always based on Media3's real active track groups.
    DisposableEffect(player) {
        fun reportPlaybackActivity() {
            val activelyPlaying = player.playbackState == Player.STATE_READY && player.isPlaying
            latestPlaybackActivityChanged(activelyPlaying)
            if (cinemaModeActive) {
                when {
                    activelyPlaying -> onCinemaPlaybackChanged(true)
                    !player.playWhenReady ||
                        player.playbackState == Player.STATE_ENDED ||
                        player.playbackState == Player.STATE_IDLE -> onCinemaPlaybackChanged(false)
                    // Keep the lights dark during a short buffer between
                    // preloaded items instead of pulsing them up and down.
                    else -> Unit
                }
            }
        }

        val listener = object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                activePlaylistIndex = player.currentMediaItemIndex.coerceAtLeast(0)
                activePlaylistTitle = mediaItem?.mediaMetadata?.title?.toString()
                    ?: content.title
                endHandled = false
                if (
                    activePlaylistIndex == mainFeatureIndex &&
                    !mainResumeApplied &&
                    content.viewOffsetMs > 0L
                ) {
                    mainResumeApplied = true
                    player.seekTo(activePlaylistIndex, content.viewOffsetMs)
                }
            }

            override fun onTracksChanged(tracks: Tracks) {
                activeVideoResolution = tracks.groups
                    .asSequence()
                    .filter { it.type == C.TRACK_TYPE_VIDEO }
                    .flatMap { group ->
                        (0 until group.length)
                            .asSequence()
                            .filter(group::isTrackSelected)
                            .map(group::getTrackFormat)
                    }
                    .mapNotNull { format ->
                        videoResolutionLabel(format.width, format.height)
                    }
                    .firstOrNull()
                selectedSubtitleId = null
                subtitleTracks = tracks.groups.flatMapIndexed { groupIndex, group ->
                    if (group.type != C.TRACK_TYPE_TEXT) return@flatMapIndexed emptyList()
                    (0 until group.length).map { trackIndex ->
                        val format = group.getTrackFormat(trackIndex)
                        SubtitleTrackOption(
                            id = "$groupIndex:$trackIndex",
                            label = format.label
                                ?: format.language
                                ?: "Subtitle ${trackIndex + 1}",
                            language = format.language,
                            group = group,
                            trackIndex = trackIndex,
                        ).also { option ->
                            if (group.isTrackSelected(trackIndex)) selectedSubtitleId = option.id
                        }
                    }
                }
                selectedAudioTrackId = null
                audioTracks = tracks.groups.flatMapIndexed { groupIndex, group ->
                    if (group.type != C.TRACK_TYPE_AUDIO) return@flatMapIndexed emptyList()
                    (0 until group.length).map { trackIndex ->
                        val format = group.getTrackFormat(trackIndex)
                        AudioTrackOption(
                            id = "$groupIndex:$trackIndex",
                            label = format.label
                                ?: format.language
                                ?: "Audio ${trackIndex + 1}",
                            language = format.language,
                            codec = format.codecs ?: format.sampleMimeType?.substringAfter('/'),
                            channels = format.channelCount.takeIf { it > 0 },
                            passthroughSupported = audioCapabilities
                                .isPassthroughPlaybackSupported(format, cinemaAudioAttributes),
                            group = group,
                            trackIndex = trackIndex,
                        ).also { option ->
                            if (group.isTrackSelected(trackIndex)) selectedAudioTrackId = option.id
                        }
                    }
                }
            }

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                // The rendered size is the most accurate value after Plex has
                // switched quality or transcoded the source.
                activeVideoResolution = videoResolutionLabel(
                    width = videoSize.width,
                    height = videoSize.height,
                ) ?: activeVideoResolution
            }

            override fun onPlayerError(error: PlaybackException) {
                if (player.currentMediaItemIndex < mainFeatureIndex && player.hasNextMediaItem()) {
                    // A missing/incompatible trailer or user-provided bumper
                    // must never prevent the paid-for main feature from starting.
                    player.seekToNextMediaItem()
                    player.prepare()
                    player.play()
                    return
                }
                val fallback = fallbackQualityFor(latestSelectedQuality, error)
                if (fallback != null) {
                    playbackMessage = "${latestSelectedQuality.label} is not supported by this TV. Switching to ${fallback.label}…"
                    selectedQuality = fallback
                } else {
                    playbackMessage = "Playback failed: ${error.errorCodeName}. Try another quality or audio track."
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                reportPlaybackActivity()
                if (playbackState == Player.STATE_READY) {
                    playbackMessage = null
                    if (player.currentMediaItemIndex == mainFeatureIndex) {
                        val key = "${selectedQuality.name}:$playbackSessionId"
                        if (lastDiagnosticsKey != key) {
                            lastDiagnosticsKey = key
                            latestDiagnosticsRequested(
                                content,
                                playbackSessionId,
                                selectedQuality,
                            ) { diagnostics = it }
                        }
                    }
                }
                if (playbackState == Player.STATE_ENDED && !endHandled) {
                    endHandled = true
                    controlsVisible = false
                    nextUpLoading = content.kind == MediaKind.Episode
                    latestPlaybackEnded { resolvedNext ->
                        nextUpLoading = false
                        nextEpisode = resolvedNext
                    }
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                reportPlaybackActivity()
            }
        }
        player.addListener(listener)
        reportPlaybackActivity()
        onDispose {
            player.removeListener(listener)
            latestPlaybackActivityChanged(false)
            if (cinemaModeActive) onCinemaPlaybackChanged(false)
        }
    }

    LaunchedEffect(player) {
        var secondsSinceReport = 0
        while (true) {
            delay(1_000)
            val duration = player.duration.takeIf { it > 0 } ?: content.durationMs ?: 0L
            val position = player.currentPosition.coerceAtLeast(0L)
            val currentIsFeature = player.currentMediaItemIndex == mainFeatureIndex
            playbackPositionMs = position
            playbackDurationMs = if (currentIsFeature) duration else player.duration.coerceAtLeast(0L)
            playbackTimeLeftMs = (playbackDurationMs - position).coerceAtLeast(0L)
            secondsSinceReport += 1
            if (secondsSinceReport >= 10 && currentIsFeature) {
                latestProgress(
                    player.currentPosition,
                    duration,
                    if (player.isPlaying) "playing" else "paused",
                )
                secondsSinceReport = 0
            }
        }
    }

    LaunchedEffect(requestBottomFocus) {
        if (requestBottomFocus) {
            delay(40)
            runCatching { settingsFocusRequester.requestFocus() }
            requestBottomFocus = false
        }
    }

    LaunchedEffect(
        controlsVisible,
        bottomControlsFocused,
        settingsVisible,
        controlsInteractionId,
    ) {
        if (controlsVisible && !bottomControlsFocused && !settingsVisible) {
            delay(5_000)
            controlsVisible = false
        }
    }

    // The interaction timestamp lives above the player route, so autoplaying
    // into another episode does not reset the configured binge-session timer.
    LaunchedEffect(
        inactivityCheckEnabled,
        inactivityTimeoutMs,
        lastInteractionAtMs,
        content.ratingKey,
    ) {
        if (!inactivityCheckEnabled) {
            inactivityPromptVisible = false
            return@LaunchedEffect
        }
        val elapsed = (SystemClock.elapsedRealtime() - lastInteractionAtMs).coerceAtLeast(0L)
        val remaining = (inactivityTimeoutMs - elapsed).coerceAtLeast(0L)
        delay(remaining)
        if (!inactivityPromptVisible) {
            resumeAfterInactivityPrompt = player.isPlaying
            player.pause()
            controlsVisible = false
            settingsVisible = false
            inactivityPromptVisible = true
        }
    }

    fun showControlsForInteraction() {
        controlsVisible = true
        // Incrementing this value restarts the auto-hide timer even when the
        // controls were already visible. Holding Left/Right therefore keeps
        // the timeline on screen until seeking has finished.
        controlsInteractionId += 1L
    }

    fun seekBy(deltaMs: Long) {
        val duration = player.duration.takeIf { it > 0 }
            ?: content.durationMs?.takeIf { isMainFeatureActive }
            ?: 0L
        val current = player.currentPosition.coerceAtLeast(0L)
        val target = if (duration > 0L) {
            (current + deltaMs).coerceIn(0L, duration)
        } else {
            (current + deltaMs).coerceAtLeast(0L)
        }
        player.seekTo(target)
        playbackPositionMs = target
        playbackDurationMs = duration
        playbackTimeLeftMs = (duration - target).coerceAtLeast(0L)
        showControlsForInteraction()
    }

    // Pause when the app loses the foreground and release every decoder,
    // surface and AudioTrack when this Composable leaves navigation.
    DisposableEffect(player, lifecycleOwner) {
        var resumePlayback = true
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> {
                    resumePlayback = player.playWhenReady
                    player.pause()
                }
                Lifecycle.Event.ON_START -> if (resumePlayback) resumeSynchronized()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            if (player.currentMediaItemIndex == mainFeatureIndex) {
                val duration = player.duration.takeIf { it > 0 } ?: content.durationMs ?: 0L
                latestProgress(player.currentPosition, duration, "stopped")
            }
            lifecycleOwner.lifecycle.removeObserver(observer)
            playerView?.player = null
            player.release()
            renderersFactory.clearPendingSubtitleCues()
        }
    }

    BackHandler(enabled = settingsVisible) {
        settingsVisible = false
        playerView?.requestFocus()
    }

    BackHandler(enabled = inactivityPromptVisible) {
        onInactivityTimeout()
    }

    fun applySyncDelays(newAudioDelayMs: Int = audioDelayMs, newSubtitleDelayMs: Int = subtitleDelayMs) {
        restartPlaylistIndex = player.currentMediaItemIndex.coerceAtLeast(0)
        restartPositionMs = player.currentPosition.coerceAtLeast(0L)
        if (newAudioDelayMs != audioDelayMs) {
            audioDelayMs = newAudioDelayMs.coerceIn(0, 500)
            onAudioDelayChanged(audioDelayMs)
        }
        if (newSubtitleDelayMs != subtitleDelayMs) {
            subtitleDelayMs = newSubtitleDelayMs.coerceIn(0, 5_000)
            onSubtitleDelayChanged(subtitleDelayMs)
        }
    }

    val activeIntroMarker = content.markers.firstOrNull { marker ->
        isMainFeatureActive &&
            marker.type.equals("intro", ignoreCase = true) &&
            playbackPositionMs >= marker.startTimeOffsetMs &&
            playbackPositionMs < marker.endTimeOffsetMs
    }
    val activeCreditsMarker = content.markers.firstOrNull { marker ->
        isMainFeatureActive &&
        marker.type.equals("credits", ignoreCase = true) &&
            playbackPositionMs >= marker.startTimeOffsetMs &&
            playbackPositionMs < marker.endTimeOffsetMs
    }
    val activeSkipMarker = activeIntroMarker ?: activeCreditsMarker

    LaunchedEffect(
        activeSkipMarker?.startTimeOffsetMs,
        controlsVisible,
        settingsVisible,
        inactivityPromptVisible,
    ) {
        if (activeSkipMarker != null && !settingsVisible && !inactivityPromptVisible) {
            delay(80)
            runCatching { markerFocusRequester.requestFocus() }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.nativeKeyEvent.repeatCount == 0) {
                    onUserInteraction()
                }
                false
            },
    ) {
        AndroidView(
            factory = { viewContext ->
                PlayerView(viewContext).apply {
                    this.player = player
                    // Compose owns the TV controls. This guarantees that OK is
                    // play/pause instead of merely revealing PlayerView chrome.
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    // Preloading handles normal transitions; keep the cinema
                    // presentation clean instead of flashing a spinner between items.
                    setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                    setKeepContentOnPlayerReset(true)
                    keepScreenOn = true
                    isFocusable = true
                    isFocusableInTouchMode = true
                    contentDescription = "Playing $activePlaylistTitle"
                    requestFocus()
                    playerView = this
                }
            },
            update = { if (it.player !== player) it.player = player },
            modifier = Modifier
                .fillMaxSize()
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown || settingsVisible) {
                        return@onPreviewKeyEvent false
                    }
                    when (event.nativeKeyEvent.keyCode) {
                        KeyEvent.KEYCODE_MENU,
                        KeyEvent.KEYCODE_SETTINGS,
                        KeyEvent.KEYCODE_DPAD_DOWN,
                        -> {
                            showControlsForInteraction()
                            requestBottomFocus = true
                            true
                        }
                        KeyEvent.KEYCODE_DPAD_LEFT -> {
                            seekBy(-player.seekBackIncrement)
                            true
                        }
                        KeyEvent.KEYCODE_DPAD_RIGHT -> {
                            seekBy(player.seekForwardIncrement)
                            true
                        }
                        KeyEvent.KEYCODE_DPAD_CENTER,
                        KeyEvent.KEYCODE_ENTER,
                        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                        -> {
                            if (event.nativeKeyEvent.repeatCount == 0) {
                                if (player.isPlaying) player.pause() else resumeSynchronized()
                                showControlsForInteraction()
                            }
                            true
                        }
                        KeyEvent.KEYCODE_MEDIA_PLAY -> { resumeSynchronized(); true }
                        KeyEvent.KEYCODE_MEDIA_PAUSE -> { player.pause(); true }
                        else -> false
                    }
                },
        )

        AnimatedVisibility(
            visible = controlsVisible && !settingsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.94f))),
                    )
                    .padding(start = 34.dp, end = 34.dp, top = 55.dp, bottom = 24.dp)
                    .onPreviewKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown &&
                            event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_UP
                        ) {
                            bottomControlsFocused = false
                            playerView?.requestFocus()
                            true
                        } else false
                    },
            ) {
                Text(activePlaylistTitle, color = Color.White, style = MaterialTheme.typography.titleMedium)
                PlaybackTimeline(
                    positionMs = playbackPositionMs,
                    durationMs = playbackDurationMs,
                    modifier = Modifier.padding(top = 12.dp, bottom = 8.dp),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    playbackTimeLeftMs.takeIf { it > 0L }?.let {
                        Text(formatPlayerTimeLeft(it), color = MinovaTeal, style = MaterialTheme.typography.bodyMedium)
                    }
                    activeVideoResolution?.let { resolution ->
                        Text(resolution, color = MinovaCyan, style = MaterialTheme.typography.bodyMedium)
                    }
                    Text(
                        diagnostics.mode.label,
                        color = when (diagnostics.mode) {
                            PlexPlaybackMode.DirectPlay -> MinovaTeal
                            PlexPlaybackMode.DirectStream -> MinovaCyan
                            PlexPlaybackMode.Transcode -> Color(0xFFFFC857)
                            PlexPlaybackMode.Unknown -> MinovaMuted
                        },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text("Settings are available below", color = MinovaMuted, style = MaterialTheme.typography.bodyMedium)
                }
                Button(
                    onClick = {
                        bottomControlsFocused = false
                        settingsVisible = true
                    },
                    modifier = Modifier
                        .padding(top = 14.dp)
                        .focusRequester(settingsFocusRequester)
                        .onFocusChanged { bottomControlsFocused = it.isFocused },
                ) {
                    Text("Playback settings")
                }
            }
        }

        playbackMessage?.let { message ->
            Text(
                text = message,
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(MinovaNightDeep.copy(alpha = 0.94f), RoundedCornerShape(8.dp))
                    .border(1.dp, MinovaCyan, RoundedCornerShape(8.dp))
                    .padding(horizontal = 24.dp, vertical = 16.dp),
            )
        }

        if (nextUpLoading) {
            Text(
                "Finding the next episode…",
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(MinovaNightDeep.copy(alpha = 0.92f), RoundedCornerShape(8.dp))
                    .padding(22.dp),
            )
        }

        nextEpisode?.let { episode ->
            NextUpOverlay(
                episode = episode,
                autoplayEnabled = autoplayNextEpisode,
                countdownEnabled = !inactivityPromptVisible,
                onPlay = { onPlayNext(episode) },
                onAutoplayChanged = onAutoplayNextEpisodeChanged,
                modifier = Modifier.align(Alignment.Center),
            )
        }

        if (
            activeSkipMarker != null &&
            nextEpisode == null &&
            !nextUpLoading &&
            !settingsVisible &&
            !inactivityPromptVisible
        ) {
            Button(
                onClick = {
                    val duration = player.duration.takeIf { it > 0 }
                        ?: content.durationMs
                        ?: activeSkipMarker.endTimeOffsetMs
                    val target = activeSkipMarker.endTimeOffsetMs.coerceAtMost(duration)
                    player.seekTo(target)
                    playbackPositionMs = target
                    onUserInteraction()
                    playerView?.requestFocus()
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 48.dp, bottom = 150.dp)
                    .focusRequester(markerFocusRequester),
            ) {
                Text(if (activeIntroMarker != null) "Skip Intro" else "Skip Credits")
            }
        }

        // This panel is entered only through Playback settings at the bottom.
        if (settingsVisible) {
            PlaybackSettingsPanel(
                selectedQuality = selectedQuality,
                diagnostics = diagnostics,
                chapters = content.chapters,
                audioDelayMs = audioDelayMs,
                subtitleDelayMs = subtitleDelayMs,
                audioTracks = audioTracks,
                selectedAudioTrackId = selectedAudioTrackId,
                plexAudioStreams = playback.audioStreams,
                selectedPlexAudioId = selectedPlexAudioId,
                subtitleTracks = subtitleTracks,
                selectedSubtitleId = selectedSubtitleId,
                plexSubtitles = playback.subtitles,
                selectedPlexSubtitleId = selectedPlexSubtitleId,
                onQualitySelected = {
                    selectedQuality = it
                    settingsVisible = false
                },
                onAudioSelected = { option ->
                    selectedAudioTrackId = option.id
                    player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                        .clearOverridesOfType(C.TRACK_TYPE_AUDIO)
                        .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)
                        .addOverride(TrackSelectionOverride(option.group.mediaTrackGroup, option.trackIndex))
                        .build()
                    val matchingPlexStream = playback.audioStreams.firstOrNull { stream ->
                        option.language != null && stream.language.equals(option.language, ignoreCase = true) &&
                            (option.codec == null || stream.codec.equals(option.codec, ignoreCase = true))
                    } ?: playback.audioStreams.firstOrNull { stream ->
                        stream.label.equals(option.label, ignoreCase = true)
                    }
                    matchingPlexStream?.let { stream ->
                        onAudioStreamSelected(stream.id) { selectedPlexAudioId = stream.id }
                    }
                },
                onPlexAudioSelected = { stream ->
                    onAudioStreamSelected(stream.id) { selectedPlexAudioId = stream.id }
                },
                onSubtitleSelected = { option ->
                    selectedSubtitleId = option?.id
                    val builder = player.trackSelectionParameters.buildUpon()
                        .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                    if (option == null) {
                        builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                    } else {
                        builder
                            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                            .addOverride(
                                TrackSelectionOverride(option.group.mediaTrackGroup, option.trackIndex),
                            )
                    }
                    player.trackSelectionParameters = builder.build()
                    val matchingPlexStream = option?.let { selectedTrack ->
                        playback.subtitles.firstOrNull { stream ->
                            selectedTrack.language != null &&
                                stream.language.equals(selectedTrack.language, ignoreCase = true)
                        } ?: playback.subtitles.firstOrNull { stream ->
                            stream.label.equals(selectedTrack.label, ignoreCase = true)
                        }
                    }
                    onSubtitleStreamSelected(matchingPlexStream?.id) {
                        selectedPlexSubtitleId = matchingPlexStream?.id ?: 0L
                    }
                },
                onPlexSubtitleSelected = { stream ->
                    onSubtitleStreamSelected(stream?.id) {
                        selectedPlexSubtitleId = stream?.id ?: 0L
                    }
                },
                onChapterSelected = { chapter ->
                    player.seekTo(chapter.startTimeOffsetMs)
                    playbackPositionMs = chapter.startTimeOffsetMs
                    settingsVisible = false
                    playerView?.requestFocus()
                },
                onAudioDelayChanged = { applySyncDelays(newAudioDelayMs = it) },
                onSubtitleDelayChanged = { applySyncDelays(newSubtitleDelayMs = it) },
                onClose = {
                    settingsVisible = false
                    playerView?.requestFocus()
                },
                modifier = Modifier.align(Alignment.CenterEnd),
            )
        }

        if (inactivityPromptVisible) {
            ContinueWatchingOverlay(
                onContinue = {
                    inactivityPromptVisible = false
                    onUserInteraction()
                    if (resumeAfterInactivityPrompt) resumeSynchronized()
                    playerView?.requestFocus()
                },
                onTimeout = onInactivityTimeout,
                modifier = Modifier.align(Alignment.Center),
            )
        }
    }
}

/** Premium end-of-episode prompt. The Play button receives focus immediately. */
@Composable
private fun NextUpOverlay(
    episode: MediaContent,
    autoplayEnabled: Boolean,
    countdownEnabled: Boolean,
    onPlay: () -> Unit,
    onAutoplayChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val playFocus = remember { FocusRequester() }
    var secondsRemaining by remember(episode.ratingKey) { mutableStateOf(NEXT_EPISODE_COUNTDOWN_SECONDS) }
    var playRequested by remember(episode.ratingKey) { mutableStateOf(false) }
    LaunchedEffect(episode.ratingKey) {
        delay(80)
        playFocus.requestFocus()
    }
    LaunchedEffect(episode.ratingKey, autoplayEnabled, countdownEnabled) {
        secondsRemaining = NEXT_EPISODE_COUNTDOWN_SECONDS
        if (!autoplayEnabled || !countdownEnabled) return@LaunchedEffect
        while (secondsRemaining > 0) {
            delay(1_000)
            secondsRemaining -= 1
        }
        if (!playRequested) {
            playRequested = true
            onPlay()
        }
    }
    Row(
        modifier = modifier
            .width(760.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MinovaNightDeep.copy(alpha = 0.97f))
            .border(2.dp, MinovaCyan, RoundedCornerShape(14.dp))
            .padding(24.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = episode.backdropUrl ?: episode.posterUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .width(310.dp)
                .height(174.dp)
                .clip(RoundedCornerShape(9.dp)),
        )
        Column(Modifier.weight(1f)) {
            Text(
                if (autoplayEnabled) "NEXT UP · PLAYING IN ${secondsRemaining}s" else "NEXT UP",
                color = MinovaCyan,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                episode.secondaryTitle ?: episode.title,
                color = Color.White,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(top = 7.dp),
            )
            Text(
                episode.title,
                color = MinovaMuted,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 3.dp),
            )
            episode.timeLeftLabel?.let {
                Text(it, color = MinovaTeal, modifier = Modifier.padding(top = 5.dp))
            }
            Row(
                modifier = Modifier.padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = {
                        if (!playRequested) {
                            playRequested = true
                            onPlay()
                        }
                    },
                    modifier = Modifier.focusRequester(playFocus),
                ) {
                    Text("Play now")
                }
                OutlinedButton(onClick = { onAutoplayChanged(!autoplayEnabled) }) {
                    if (autoplayEnabled) {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = null,
                            modifier = Modifier.padding(end = 7.dp),
                        )
                    }
                    Text("Autoplay")
                }
            }
        }
    }
}

@Composable
private fun ContinueWatchingOverlay(
    onContinue: () -> Unit,
    onTimeout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val continueFocusRequester = remember { FocusRequester() }
    var secondsRemaining by remember { mutableStateOf(INACTIVITY_PROMPT_SECONDS) }

    LaunchedEffect(Unit) {
        delay(80)
        continueFocusRequester.requestFocus()
    }
    LaunchedEffect(Unit) {
        while (secondsRemaining > 0) {
            delay(1_000)
            secondsRemaining -= 1
        }
        onTimeout()
    }

    Column(
        modifier = modifier
            .width(620.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MinovaNightDeep.copy(alpha = 0.98f))
            .border(2.dp, MinovaCyan, RoundedCornerShape(14.dp))
            .padding(horizontal = 34.dp, vertical = 30.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Continue watching?", color = Color.White, style = MaterialTheme.typography.headlineMedium)
        Text(
            "Playback will stop and return Home in $secondsRemaining seconds because there has been no remote activity.",
            color = MinovaMuted,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(top = 10.dp),
        )
        Row(
            modifier = Modifier.padding(top = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Button(
                onClick = onContinue,
                modifier = Modifier.focusRequester(continueFocusRequester),
            ) {
                Text("Continue watching")
            }
            OutlinedButton(onClick = onTimeout) {
                Text("Stop playback")
            }
        }
    }
}

/**
 * Full-runtime playback timeline used by the TV transport overlay.
 *
 * The filled cyan/teal portion represents the absolute playback position,
 * rather than the size of the latest seek jump. This makes a long press on
 * Left/Right readable for both short episodes and multi-hour films.
 */
@Composable
private fun PlaybackTimeline(
    positionMs: Long,
    durationMs: Long,
    modifier: Modifier = Modifier,
) {
    val safeDuration = durationMs.coerceAtLeast(0L)
    val safePosition = if (safeDuration > 0L) {
        positionMs.coerceIn(0L, safeDuration)
    } else {
        positionMs.coerceAtLeast(0L)
    }
    val progress = if (safeDuration > 0L) {
        safePosition.toFloat() / safeDuration.toFloat()
    } else {
        0f
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = formatPlayerTimestamp(safePosition),
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = if (safeDuration > 0L) formatPlayerTimestamp(safeDuration) else "--:--",
                color = MinovaMuted,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp)
                .height(7.dp)
                .clip(RoundedCornerShape(50))
                .background(Color.White.copy(alpha = 0.22f)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress.coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .background(
                        Brush.horizontalGradient(listOf(MinovaCyan, MinovaTeal)),
                    ),
            )
        }
    }
}

@OptIn(UnstableApi::class)
private fun subtitleConfiguration(stream: SubtitleStream): MediaItem.SubtitleConfiguration? {
    val uri = stream.key?.toUri() ?: return null
    val mimeType = when (stream.codec?.lowercase()) {
        "srt", "subrip" -> MimeTypes.APPLICATION_SUBRIP
        "ass", "ssa" -> MimeTypes.TEXT_SSA
        "vtt", "webvtt" -> MimeTypes.TEXT_VTT
        "ttml" -> MimeTypes.APPLICATION_TTML
        else -> return null
    }
    return MediaItem.SubtitleConfiguration.Builder(uri)
        .setMimeType(mimeType)
        .setLanguage(stream.language)
        .setLabel(stream.label)
        .setSelectionFlags(if (stream.selected) C.SELECTION_FLAG_DEFAULT else 0)
        .build()
}

@Composable
private fun PlaybackSettingsPanel(
    selectedQuality: PlaybackQuality,
    diagnostics: PlaybackDiagnostics,
    chapters: List<MediaChapter>,
    audioDelayMs: Int,
    subtitleDelayMs: Int,
    audioTracks: List<AudioTrackOption>,
    selectedAudioTrackId: String?,
    plexAudioStreams: List<AudioStream>,
    selectedPlexAudioId: Long?,
    subtitleTracks: List<SubtitleTrackOption>,
    selectedSubtitleId: String?,
    plexSubtitles: List<SubtitleStream>,
    selectedPlexSubtitleId: Long?,
    onQualitySelected: (PlaybackQuality) -> Unit,
    onAudioSelected: (AudioTrackOption) -> Unit,
    onPlexAudioSelected: (AudioStream) -> Unit,
    onSubtitleSelected: (SubtitleTrackOption?) -> Unit,
    onPlexSubtitleSelected: (SubtitleStream?) -> Unit,
    onChapterSelected: (MediaChapter) -> Unit,
    onAudioDelayChanged: (Int) -> Unit,
    onSubtitleDelayChanged: (Int) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(80)
        firstFocus.requestFocus()
    }

    Column(
        modifier = modifier
            .width(430.dp)
            .fillMaxHeight()
            .background(MinovaNightDeep.copy(alpha = 0.96f))
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 34.dp, vertical = 30.dp),
    ) {
        Text("Playback settings", style = MaterialTheme.typography.headlineMedium)
        Text(
            "${diagnostics.mode.label} · ${diagnostics.reason.orEmpty()}",
            color = when (diagnostics.mode) {
                PlexPlaybackMode.DirectPlay -> MinovaTeal
                PlexPlaybackMode.DirectStream -> MinovaCyan
                PlexPlaybackMode.Transcode -> Color(0xFFFFC857)
                PlexPlaybackMode.Unknown -> MinovaMuted
            },
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 7.dp),
        )
        diagnostics.source?.let { source ->
            Text(
                listOfNotNull(
                    source.videoResolution?.uppercase(),
                    source.videoCodec?.uppercase(),
                    source.audioCodec?.uppercase(),
                    source.bitrateKbps?.let { "${it / 1_000f} Mbps" },
                ).joinToString("  •  "),
                color = MinovaMuted,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 3.dp),
            )
        }
        Text(
            "QUALITY",
            color = MinovaCyan,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 28.dp, bottom = 8.dp),
        )
        PlaybackQuality.entries.forEachIndexed { index, quality ->
            SettingOption(
                title = quality.label,
                detail = quality.detail,
                selected = quality == selectedQuality,
                onClick = { onQualitySelected(quality) },
                modifier = if (index == 0) Modifier.focusRequester(firstFocus) else Modifier,
            )
        }

        Text(
            "AUDIO",
            color = MinovaCyan,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 25.dp, bottom = 8.dp),
        )
        if (selectedQuality == PlaybackQuality.Original && audioTracks.isNotEmpty()) {
            audioTracks.forEach { track ->
                SettingOption(
                    title = track.label,
                    detail = audioDetail(
                        track.language,
                        track.codec,
                        track.channels,
                        track.passthroughSupported,
                    ),
                    selected = selectedAudioTrackId == track.id,
                    onClick = { onAudioSelected(track) },
                )
            }
        } else if (plexAudioStreams.isNotEmpty()) {
            plexAudioStreams.forEach { stream ->
                SettingOption(
                    title = stream.label,
                    detail = audioDetail(stream.language, stream.codec, stream.channels),
                    selected = selectedPlexAudioId == stream.id,
                    onClick = { onPlexAudioSelected(stream) },
                )
            }
        } else {
            Text(
                "No selectable audio tracks were found.",
                color = MinovaMuted,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(vertical = 12.dp),
            )
        }

        Text(
            "SUBTITLES",
            color = MinovaCyan,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 25.dp, bottom = 8.dp),
        )
        SettingOption(
            title = "Off",
            detail = "No subtitles",
            selected = selectedSubtitleId == null &&
                (selectedPlexSubtitleId == null || selectedPlexSubtitleId == 0L),
            onClick = { onSubtitleSelected(null) },
        )
        if (subtitleTracks.isNotEmpty()) {
            subtitleTracks.forEach { track ->
                SettingOption(
                    title = track.label,
                    detail = track.language?.uppercase() ?: "Text track",
                    selected = selectedSubtitleId == track.id,
                    onClick = { onSubtitleSelected(track) },
                )
            }
        } else {
            plexSubtitles.forEach { stream ->
                SettingOption(
                    title = stream.label,
                    detail = listOfNotNull(stream.language?.uppercase(), stream.codec?.uppercase())
                        .joinToString("  •  "),
                    selected = selectedPlexSubtitleId == stream.id,
                    onClick = { onPlexSubtitleSelected(stream) },
                )
            }
        }
        if (subtitleTracks.isEmpty() && plexSubtitles.isEmpty()) {
            Text(
                "No selectable subtitle tracks were found.",
                color = MinovaMuted,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(vertical = 12.dp),
            )
        }

        Text(
            "SYNC CORRECTION",
            color = MinovaCyan,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 25.dp, bottom = 8.dp),
        )
        SettingStepper(
            title = "Audio delay",
            value = if (audioDelayMs == 0) "Off" else "+${audioDelayMs} ms",
            detail = if (audioDelayMs == 0) {
                "Bitstream passthrough remains available"
            } else {
                "Delays decoded audio; passthrough is disabled while active"
            },
            onPrevious = { onAudioDelayChanged((audioDelayMs - 25).coerceAtLeast(0)) },
            onNext = { onAudioDelayChanged((audioDelayMs + 25).coerceAtMost(500)) },
        )
        SettingStepper(
            title = "Subtitle delay",
            value = if (subtitleDelayMs == 0) "Off" else "+${subtitleDelayMs} ms",
            detail = "Use when subtitles appear before the dialogue",
            onPrevious = { onSubtitleDelayChanged((subtitleDelayMs - 250).coerceAtLeast(0)) },
            onNext = { onSubtitleDelayChanged((subtitleDelayMs + 250).coerceAtMost(5_000)) },
        )

        if (chapters.isNotEmpty()) {
            Text(
                "CHAPTERS",
                color = MinovaCyan,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 25.dp, bottom = 8.dp),
            )
            chapters.forEach { chapter ->
                SettingOption(
                    title = chapter.title,
                    detail = formatChapterTime(chapter.startTimeOffsetMs),
                    selected = false,
                    onClick = { onChapterSelected(chapter) },
                )
            }
        }
        Spacer(Modifier.height(18.dp))
        SettingOption(
            title = "Close",
            detail = "Return to playback",
            selected = false,
            onClick = onClose,
        )
    }
}

@Composable
private fun SettingOption(
    title: String,
    detail: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(7.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .onFocusChanged { focused = it.isFocused }
            .clip(shape)
            .background(if (focused) MinovaSurface.copy(alpha = 0.78f) else MinovaSurface)
            .border(
                if (focused || selected) 2.dp else 1.dp,
                when {
                    focused -> Color.White
                    selected -> MinovaCyan
                    else -> Color.Transparent
                },
                shape,
            )
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 15.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(
                title,
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                detail,
                color = if (focused) Color.White.copy(alpha = 0.78f) else MinovaMuted,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        if (selected) Text("●", color = if (focused) Color.Black else MinovaCyan)
    }
}

@Composable
private fun SettingStepper(
    title: String,
    value: String,
    detail: String,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(7.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .onFocusChanged { focused = it.isFocused }
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.nativeKeyEvent.keyCode) {
                    KeyEvent.KEYCODE_DPAD_LEFT -> { onPrevious(); true }
                    KeyEvent.KEYCODE_DPAD_RIGHT -> { onNext(); true }
                    else -> false
                }
            }
            .clip(shape)
            .background(if (focused) MinovaSurface.copy(alpha = 0.78f) else MinovaSurface)
            .border(if (focused) 2.dp else 1.dp, if (focused) Color.White else Color.Transparent, shape)
            .clickable(role = Role.Button, onClick = onNext)
            .padding(horizontal = 15.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, style = MaterialTheme.typography.titleMedium)
            Text(detail, color = MinovaMuted, style = MaterialTheme.typography.bodyMedium)
        }
        Text("‹  $value  ›", color = MinovaCyan, style = MaterialTheme.typography.titleMedium)
    }
}

private fun formatChapterTime(timeMs: Long): String {
    val totalSeconds = timeMs.coerceAtLeast(0L) / 1_000L
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return "%d:%02d".format(minutes, seconds)
}

private fun audioDetail(
    language: String?,
    codec: String?,
    channels: Int?,
    passthroughSupported: Boolean = false,
): String = buildList {
    language?.takeIf { it.isNotBlank() }?.uppercase()?.let(::add)
    codec?.takeIf { it.isNotBlank() }?.uppercase()?.let(::add)
    channels?.let { add(if (it == 1) "Mono" else "$it channels") }
    if (passthroughSupported) add("Direct HDMI")
}.ifEmpty { listOf("Audio track") }.joinToString("  •  ")

private fun fallbackQualityFor(
    current: PlaybackQuality,
    error: PlaybackException,
): PlaybackQuality? {
    val decoderFailure = error.errorCode == PlaybackException.ERROR_CODE_DECODING_FAILED ||
        error.errorCode == PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES ||
        error.errorCode == PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED
    if (!decoderFailure) return null
    return when (current) {
        PlaybackQuality.Original, PlaybackQuality.UltraHd -> PlaybackQuality.FullHd
        PlaybackQuality.FullHd -> PlaybackQuality.Hd
        PlaybackQuality.Hd -> PlaybackQuality.Sd
        PlaybackQuality.Sd -> null
    }
}

private fun formatPlayerTimeLeft(remainingMs: Long): String {
    val totalMinutes = (remainingMs + 59_999L) / 60_000L
    val hours = totalMinutes / 60L
    val minutes = totalMinutes % 60L
    return when {
        hours > 0L && minutes > 0L -> "${hours}h ${minutes}m left"
        hours > 0L -> "${hours}h left"
        else -> "${minutes.coerceAtLeast(1L)} min left"
    }
}

private fun formatPlayerTimestamp(positionMs: Long): String {
    val totalSeconds = positionMs.coerceAtLeast(0L) / 1_000L
    val hours = totalSeconds / 3_600L
    val minutes = (totalSeconds % 3_600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}

private fun videoResolutionLabel(width: Int, height: Int): String? {
    val longEdge = maxOf(width, height)
    val shortEdge = minOf(width, height)
    if (longEdge <= 0 || shortEdge <= 0) return null
    return when {
        longEdge >= 3_800 || shortEdge >= 2_100 -> "4K UHD"
        shortEdge >= 1_400 -> "1440p"
        shortEdge >= 1_000 -> "1080p"
        shortEdge >= 700 -> "720p"
        shortEdge >= 470 -> "480p"
        else -> "${shortEdge}p"
    }
}
