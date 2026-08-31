package com.minova.cinema.ui.player

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.text.CueGroup
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.AudioCapabilities
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.text.TextOutput
import java.nio.ByteBuffer

/**
 * Adds user-requested positive sync correction. At zero delay the processor is
 * inactive, preserving Dolby/DTS passthrough. Any audio delay requires decoded
 * PCM, which the settings UI explains before applying it.
 */
@OptIn(UnstableApi::class)
internal class CinemaRenderersFactory(
    context: Context,
    private val audioDelayMs: Int,
    private val subtitleDelayMs: Int,
) : DefaultRenderersFactory(context) {
    private var delayedTextOutput: DelayedTextOutput? = null

    override fun buildAudioSink(
        context: Context,
        enableFloatOutput: Boolean,
        enableAudioOutputPlaybackParameters: Boolean,
    ): AudioSink {
        val builder = DefaultAudioSink.Builder(context)
            .setAudioProcessors(arrayOf(AudioDelayProcessor(audioDelayMs)))
            .setEnableFloatOutput(enableFloatOutput)
            .setEnableAudioOutputPlaybackParameters(enableAudioOutputPlaybackParameters)
        if (audioDelayMs > 0) {
            // Force decoder output through the PCM processor. At zero delay we
            // intentionally keep the context-derived HDMI passthrough formats.
            builder.setAudioCapabilities(AudioCapabilities.DEFAULT_AUDIO_CAPABILITIES)
        }
        return builder.build()
    }

    override fun buildTextRenderers(
        context: Context,
        output: TextOutput,
        outputLooper: Looper,
        extensionRendererMode: Int,
        out: ArrayList<Renderer>,
    ) {
        val delayed = if (subtitleDelayMs <= 0) output else DelayedTextOutput(
            delegate = output,
            handler = Handler(outputLooper),
            delayMs = subtitleDelayMs.toLong(),
        ).also { delayedTextOutput = it }
        super.buildTextRenderers(context, delayed, outputLooper, extensionRendererMode, out)
    }

    fun clearPendingSubtitleCues() {
        delayedTextOutput?.clear()
        delayedTextOutput = null
    }
}

@OptIn(UnstableApi::class)
private class AudioDelayProcessor(
    private val delayMs: Int,
) : BaseAudioProcessor() {
    private var silenceBytesRemaining = 0

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (delayMs <= 0 || !Util.isEncodingLinearPcm(inputAudioFormat.encoding)) {
            return AudioProcessor.AudioFormat.NOT_SET
        }
        return inputAudioFormat
    }

    override fun onFlush() {
        silenceBytesRemaining = if (delayMs <= 0 || inputAudioFormat == AudioProcessor.AudioFormat.NOT_SET) {
            0
        } else {
            ((inputAudioFormat.sampleRate.toLong() * inputAudioFormat.bytesPerFrame * delayMs) / 1_000L)
                .coerceAtMost(Int.MAX_VALUE.toLong())
                .toInt()
        }
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (!inputBuffer.hasRemaining()) return
        val inputBytes = inputBuffer.remaining()
        val output = replaceOutputBuffer(silenceBytesRemaining + inputBytes)
        while (silenceBytesRemaining > 0) {
            output.put(0)
            silenceBytesRemaining--
        }
        output.put(inputBuffer)
        output.flip()
    }
}

@OptIn(UnstableApi::class)
private class DelayedTextOutput(
    private val delegate: TextOutput,
    private val handler: Handler,
    private val delayMs: Long,
) : TextOutput {
    override fun onCues(cueGroup: CueGroup) {
        handler.postDelayed({ delegate.onCues(cueGroup) }, delayMs)
    }

    fun clear() {
        handler.removeCallbacksAndMessages(null)
    }
}
