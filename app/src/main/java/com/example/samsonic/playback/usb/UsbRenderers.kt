package com.example.samsonic.playback.usb

import android.content.Context
import android.media.MediaFormat
import android.os.Handler
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.audio.AudioRendererEventListener
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.MediaCodecAudioRenderer
import androidx.media3.exoplayer.mediacodec.MediaCodecAdapter
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector

/**
 * The stock renderers, with the audio sink replaced by one that plays to a USB DAC through the
 * driver when [dacs] has one ready, and by the stock sink otherwise.
 */
@OptIn(UnstableApi::class)
internal class UsbRenderersFactory(context: Context, private val dacs: UsbDacManager) : DefaultRenderersFactory(context) {
    override fun buildAudioSink(
        context: Context,
        enableFloatOutput: Boolean,
        enableAudioTrackPlaybackParams: Boolean,
    ): AudioSink = RoutingAudioSink(
        default = DefaultAudioSink.Builder(context)
            .setEnableFloatOutput(enableFloatOutput)
            .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
            .build(),
        usb = UsbDacSink(
            openDac = { dacs.readyDac()?.let { dacs.open(it) } },
            report = { plan, dsdRate ->
                dacs.setOutput(plan?.let { UsbOutput(dacs.readyDac()?.name, it.rate, it.subslotBytes * 8, it.channels, it.dsd, dsdRate) })
            },
        ),
        usbReady = { dacs.readyDac() != null },
        preferNativeDsd = { dacs.preferNativeDsd },
    ).also { dacs.dsdProbe = it::dsdModeFor }

    override fun buildAudioRenderers(
        context: Context,
        extensionRendererMode: Int,
        mediaCodecSelector: MediaCodecSelector,
        enableDecoderFallback: Boolean,
        audioSink: AudioSink,
        eventHandler: Handler,
        eventListener: AudioRendererEventListener,
        out: ArrayList<Renderer>,
    ) {
        super.buildAudioRenderers(
            context, extensionRendererMode, mediaCodecSelector, enableDecoderFallback,
            audioSink, eventHandler, eventListener, out,
        )
        val index = out.indexOfFirst { it is MediaCodecAudioRenderer }
        if (index < 0) return
        out[index] = HighResFloatAudioRenderer(
            context, codecAdapterFactory, mediaCodecSelector, enableDecoderFallback,
            eventHandler, eventListener, audioSink,
        )
    }
}

/**
 * Asks the decoder for float output only for high-res sources. Samsung's FLAC decoder
 * (c2.sec.flac.decoder) says it outputs float for 16-bit files but still sends 16-bit, which
 * plays as noise; 16-bit and lossy sources lose nothing as 16-bit anyway.
 */
@OptIn(UnstableApi::class)
private class HighResFloatAudioRenderer(
    context: Context,
    codecAdapterFactory: MediaCodecAdapter.Factory,
    mediaCodecSelector: MediaCodecSelector,
    enableDecoderFallback: Boolean,
    eventHandler: Handler,
    eventListener: AudioRendererEventListener,
    audioSink: AudioSink,
) : MediaCodecAudioRenderer(
    context, codecAdapterFactory, mediaCodecSelector, enableDecoderFallback, eventHandler, eventListener, audioSink,
) {
    override fun getMediaFormat(
        format: Format,
        codecMimeType: String,
        codecMaxInputSize: Int,
        codecOperatingRate: Float,
    ): MediaFormat = super.getMediaFormat(format, codecMimeType, codecMaxInputSize, codecOperatingRate).apply {
        if (format.pcmEncoding !in HIGH_RES) removeKey(MediaFormat.KEY_PCM_ENCODING)
    }

    private companion object {
        val HIGH_RES = setOf(C.ENCODING_PCM_24BIT, C.ENCODING_PCM_32BIT, C.ENCODING_PCM_FLOAT)
    }
}
