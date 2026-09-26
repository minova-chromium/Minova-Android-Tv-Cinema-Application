package com.minova.cinema.cast

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaLoadRequestData
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.cast.MediaStatus
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManagerListener
import com.google.android.gms.cast.framework.media.RemoteMediaClient
import com.google.android.gms.common.images.WebImage
import com.minova.cinema.data.remote.PlaybackQuality
import com.minova.cinema.data.remote.PlexConnection
import com.minova.cinema.data.remote.PlexUrlFactory
import com.minova.cinema.domain.MediaContent
import com.minova.cinema.domain.MediaKind
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class CastPlaybackStatus {
    Idle,
    Buffering,
    Playing,
    Paused,
}

data class MinovaCastState(
    val connected: Boolean = false,
    val deviceName: String? = null,
    val content: MediaContent? = null,
    val status: CastPlaybackStatus = CastPlaybackStatus.Idle,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val errorMessage: String? = null,
) {
    val hasMedia: Boolean get() = connected && content != null
}

/** Owns the sender session while the phone remains free to browse Minova. */
class MinovaCastController(context: Context) {
    private val appContext = context.applicationContext
    private val castContext = CastContext.getSharedInstance(appContext)
    private val sessionManager = castContext.sessionManager
    private val mutableState = MutableStateFlow(MinovaCastState())
    val state: StateFlow<MinovaCastState> = mutableState.asStateFlow()

    private var remoteClient: RemoteMediaClient? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var loadGeneration = 0
    private var pendingFallback: PendingFallback? = null
    private val fallbackRunnable = Runnable { triggerFallback(loadGeneration) }

    private val remoteCallback = object : RemoteMediaClient.Callback() {
        override fun onStatusUpdated() = updateRemoteState()
        override fun onMetadataUpdated() = updateRemoteState()
    }
    private val progressListener = RemoteMediaClient.ProgressListener { position, duration ->
        mutableState.value = mutableState.value.copy(
            positionMs = position.coerceAtLeast(0L),
            durationMs = duration.coerceAtLeast(0L),
        )
    }
    private val sessionListener = object : SessionManagerListener<CastSession> {
        override fun onSessionStarting(session: CastSession) = Unit

        override fun onSessionStarted(session: CastSession, sessionId: String) {
            attach(session)
        }

        override fun onSessionStartFailed(session: CastSession, error: Int) {
            mutableState.value = MinovaCastState(errorMessage = "Could not connect to that TV.")
        }

        override fun onSessionEnding(session: CastSession) = Unit

        override fun onSessionEnded(session: CastSession, error: Int) {
            detachRemoteClient()
            mutableState.value = MinovaCastState()
        }

        override fun onSessionResuming(session: CastSession, sessionId: String) = Unit

        override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) {
            attach(session)
        }

        override fun onSessionResumeFailed(session: CastSession, error: Int) {
            detachRemoteClient()
            mutableState.value = MinovaCastState(errorMessage = "The TV connection was lost.")
        }

        override fun onSessionSuspended(session: CastSession, reason: Int) {
            mutableState.value = mutableState.value.copy(
                connected = false,
                status = CastPlaybackStatus.Idle,
                errorMessage = "The TV connection was interrupted.",
            )
        }
    }

    init {
        sessionManager.addSessionManagerListener(sessionListener, CastSession::class.java)
        sessionManager.currentCastSession?.takeIf { it.isConnected }?.let(::attach)
    }

    val isConnected: Boolean
        get() = sessionManager.currentCastSession?.isConnected == true

    fun cast(
        content: MediaContent,
        connection: PlexConnection,
        fromBeginning: Boolean = false,
    ): Boolean {
        val session = sessionManager.currentCastSession?.takeIf { it.isConnected } ?: return false
        val client = session.remoteMediaClient ?: return false
        val playback = content.playback ?: return false
        val selectedAudio = playback.audioStreams.firstOrNull { it.selected }
        val selectedSubtitle = playback.subtitles.firstOrNull { it.selected }
        val delivery = chooseCastDelivery(
            source = playback.technicalInfo,
            selectedAudioCodec = selectedAudio?.codec,
            audioTrackCount = playback.audioStreams.size,
            hasSelectedSubtitle = selectedSubtitle != null,
        )
        val urls = PlexUrlFactory(connection)
        val primaryUrl = when (delivery) {
            CastDeliveryMode.DirectPlay -> urls.rebaseAuthenticated(playback.directUrl)
            CastDeliveryMode.DirectStream -> urls.transcode(
                ratingKey = content.ratingKey,
                quality = PlaybackQuality.Original,
                audioStreamId = selectedAudio?.id,
            )
            CastDeliveryMode.CompatibilityTranscode -> urls.transcode(
                ratingKey = content.ratingKey,
                quality = PlaybackQuality.Hd,
                subtitleStreamId = selectedSubtitle?.id,
                audioStreamId = selectedAudio?.id,
            )
        }
        val primaryContentType = if (delivery == CastDeliveryMode.DirectPlay) {
            CAST_MP4_CONTENT_TYPE
        } else {
            CAST_HLS_CONTENT_TYPE
        }
        val metadata = MediaMetadata(
            if (content.kind == MediaKind.Movie) {
                MediaMetadata.MEDIA_TYPE_MOVIE
            } else {
                MediaMetadata.MEDIA_TYPE_TV_SHOW
            },
        ).apply {
            putString(MediaMetadata.KEY_TITLE, content.title)
            content.secondaryTitle?.let { putString(MediaMetadata.KEY_SUBTITLE, it) }
            content.posterUrl?.let { addImage(WebImage(Uri.parse(it))) }
            content.backdropUrl?.let { addImage(WebImage(Uri.parse(it))) }
        }
        val mediaInfoBuilder = MediaInfo.Builder(primaryUrl)
            .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
            .setContentType(primaryContentType)
            .setMetadata(metadata)
        content.durationMs?.takeIf { it > 0L }?.let(mediaInfoBuilder::setStreamDuration)
        val startPosition = if (fromBeginning) 0L else content.viewOffsetMs.coerceAtLeast(0L)
        val request = MediaLoadRequestData.Builder()
            .setMediaInfo(mediaInfoBuilder.build())
            .setAutoplay(true)
            .setCurrentTime(startPosition)
            .build()
        val fallbackRequest = if (delivery != CastDeliveryMode.CompatibilityTranscode) {
            val fallbackUrl = urls.transcode(
                ratingKey = content.ratingKey,
                quality = PlaybackQuality.Hd,
                subtitleStreamId = selectedSubtitle?.id,
                audioStreamId = selectedAudio?.id,
            )
            val fallbackInfo = MediaInfo.Builder(fallbackUrl)
                .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
                .setContentType(CAST_HLS_CONTENT_TYPE)
                .setMetadata(metadata)
                .also { builder ->
                    content.durationMs?.takeIf { it > 0L }?.let(builder::setStreamDuration)
                }
                .build()
            MediaLoadRequestData.Builder()
                .setMediaInfo(fallbackInfo)
                .setAutoplay(true)
                .setCurrentTime(startPosition)
                .build()
        } else {
            null
        }

        mutableState.value = MinovaCastState(
            connected = true,
            deviceName = session.castDevice?.friendlyName,
            content = content,
            status = CastPlaybackStatus.Buffering,
            positionMs = startPosition,
            durationMs = content.durationMs ?: 0L,
        )
        load(client, request, fallbackRequest)
        return true
    }

    fun togglePlayback() {
        val client = remoteClient ?: return
        if (client.isPlaying) client.pause() else client.play()
    }

    fun stop() {
        cancelFallback()
        sessionManager.endCurrentSession(true)
    }

    fun disconnect() {
        cancelFallback()
        sessionManager.endCurrentSession(true)
    }

    fun clearError() {
        mutableState.value = mutableState.value.copy(errorMessage = null)
    }

    fun openExpandedControls(context: Context) {
        context.startActivity(
            Intent(context, MinovaCastExpandedControlsActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    fun close() {
        cancelFallback()
        detachRemoteClient()
        sessionManager.removeSessionManagerListener(sessionListener, CastSession::class.java)
    }

    private fun attach(session: CastSession) {
        detachRemoteClient()
        remoteClient = session.remoteMediaClient?.also { client ->
            client.registerCallback(remoteCallback)
            client.addProgressListener(progressListener, PROGRESS_INTERVAL_MS)
        }
        mutableState.value = mutableState.value.copy(
            connected = true,
            deviceName = session.castDevice?.friendlyName,
            errorMessage = null,
        )
        updateRemoteState()
    }

    private fun detachRemoteClient() {
        cancelFallback()
        remoteClient?.unregisterCallback(remoteCallback)
        remoteClient?.removeProgressListener(progressListener)
        remoteClient = null
    }

    private fun updateRemoteState() {
        val client = remoteClient ?: return
        val status = when (client.playerState) {
            MediaStatus.PLAYER_STATE_PLAYING -> CastPlaybackStatus.Playing
            MediaStatus.PLAYER_STATE_PAUSED -> CastPlaybackStatus.Paused
            MediaStatus.PLAYER_STATE_BUFFERING,
            MediaStatus.PLAYER_STATE_LOADING,
            -> CastPlaybackStatus.Buffering
            else -> CastPlaybackStatus.Idle
        }
        if (status == CastPlaybackStatus.Playing) {
            cancelFallback()
        } else if (
            client.playerState == MediaStatus.PLAYER_STATE_IDLE &&
            client.idleReason == MediaStatus.IDLE_REASON_ERROR
        ) {
            if (pendingFallback != null) {
                triggerFallback(loadGeneration)
                return
            }
            mutableState.value = mutableState.value.copy(
                status = CastPlaybackStatus.Idle,
                errorMessage = "The TV could not start this video.",
            )
            return
        }
        mutableState.value = mutableState.value.copy(
            status = status,
            positionMs = client.approximateStreamPosition.coerceAtLeast(0L),
            durationMs = client.streamDuration.coerceAtLeast(0L),
        )
    }

    private fun load(
        client: RemoteMediaClient,
        request: MediaLoadRequestData,
        fallback: MediaLoadRequestData?,
    ) {
        cancelFallback()
        val generation = ++loadGeneration
        pendingFallback = fallback?.let { PendingFallback(client, it, generation) }
        if (fallback != null) {
            mainHandler.postDelayed(fallbackRunnable, FAST_START_TIMEOUT_MS)
        }
        client.load(request).setResultCallback { result ->
            if (!result.status.isSuccess) triggerFallback(generation)
        }
    }

    private fun triggerFallback(expectedGeneration: Int) {
        val fallback = pendingFallback
            ?.takeIf { it.generation == expectedGeneration && expectedGeneration == loadGeneration }
            ?: return
        if (fallback.client.isPlaying) {
            cancelFallback()
            return
        }
        pendingFallback = null
        mainHandler.removeCallbacks(fallbackRunnable)
        val generation = ++loadGeneration
        mutableState.value = mutableState.value.copy(
            status = CastPlaybackStatus.Buffering,
            errorMessage = null,
        )
        fallback.client.load(fallback.request).setResultCallback { result ->
            if (!result.status.isSuccess && generation == loadGeneration) {
                mutableState.value = mutableState.value.copy(
                    status = CastPlaybackStatus.Idle,
                    errorMessage = "The TV could not start this video.",
                )
            }
        }
    }

    private fun cancelFallback() {
        pendingFallback = null
        mainHandler.removeCallbacks(fallbackRunnable)
        loadGeneration += 1
    }

    private data class PendingFallback(
        val client: RemoteMediaClient,
        val request: MediaLoadRequestData,
        val generation: Int,
    )

    private companion object {
        const val CAST_HLS_CONTENT_TYPE = "application/x-mpegURL"
        const val CAST_MP4_CONTENT_TYPE = "video/mp4"
        const val PROGRESS_INTERVAL_MS = 5_000L
        const val FAST_START_TIMEOUT_MS = 8_000L
    }
}
