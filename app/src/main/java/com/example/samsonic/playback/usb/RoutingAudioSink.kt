package com.example.samsonic.playback.usb

import android.media.AudioDeviceInfo
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.AuxEffectInfo
import androidx.media3.common.Format
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.util.Clock
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.analytics.PlayerId
import androidx.media3.exoplayer.audio.AudioOffloadSupport
import androidx.media3.exoplayer.audio.AudioSink
import com.example.samsonic.playback.dsd.DsdMode
import com.example.samsonic.playback.dsd.DsdStream
import java.nio.ByteBuffer

/**
 * The player's audio sink: [usb] when a DAC that the driver can use is plugged in ([usbReady]),
 * otherwise the stock [default] sink. The choice is made for each song as it's configured.
 * Settings go to [default] always, so it's ready whenever it takes over.
 */
@OptIn(UnstableApi::class)
internal class RoutingAudioSink(
    private val default: AudioSink,
    private val usb: UsbDacSink,
    private val usbReady: () -> Boolean,
    private val preferNativeDsd: () -> Boolean,
) : AudioSink {
    private var current: AudioSink = default
    private var playing = false

    override fun setListener(listener: AudioSink.Listener) {
        default.setListener(listener)
        usb.setListener(listener)
    }

    override fun setPlayerId(playerId: PlayerId?) = default.setPlayerId(playerId)
    override fun setClock(clock: Clock) = default.setClock(clock)

    // A format the DAC takes is supported straight away, without opening it: the decoder
    // is set up from this, before any song is configured.
    override fun supportsFormat(format: Format): Boolean =
        (usbReady() && usb.supportsFormat(format)) || default.supportsFormat(format)

    override fun getFormatSupport(format: Format): Int =
        if (usbReady() && usb.supportsFormat(format)) AudioSink.SINK_FORMAT_SUPPORTED_DIRECTLY
        else default.getFormatSupport(format)

    override fun getFormatOffloadSupport(format: Format): AudioOffloadSupport = default.getFormatOffloadSupport(format)

    /** How DSD of [dsdRate] can go to the DAC as it is, or null (no DAC, or one that can't take it). */
    fun dsdModeFor(dsdRate: Int): DsdMode? = if (usbReady()) usb.dsdModeFor(dsdRate, preferNativeDsd()) else null

    override fun configure(inputFormat: Format, specifiedBufferSize: Int, outputChannels: IntArray?) {
        val target = if (usbReady() && usb.canPlay(inputFormat)) usb else default
        // DSD packed for the DAC is noise to anything else: better to fail than to play it.
        if (target === default && inputFormat.customData is DsdStream) {
            throw AudioSink.ConfigurationException("This DSD can only play on the USB DAC it was prepared for", inputFormat)
        }
        if (target !== current) {
            current.flush()
            current.reset()
            current = target
            if (playing) current.play()
        }
        current.configure(inputFormat, specifiedBufferSize, outputChannels)
    }

    override fun getCurrentPositionUs(sourceEnded: Boolean): Long = current.getCurrentPositionUs(sourceEnded)
    override fun getAudioTrackBufferSizeUs(): Long = current.audioTrackBufferSizeUs

    override fun play() {
        playing = true
        current.play()
    }

    override fun pause() {
        playing = false
        current.pause()
    }

    override fun handleDiscontinuity() = current.handleDiscontinuity()

    override fun handleBuffer(buffer: ByteBuffer, presentationTimeUs: Long, encodedAccessUnitCount: Int): Boolean =
        current.handleBuffer(buffer, presentationTimeUs, encodedAccessUnitCount)

    override fun playToEndOfStream() = current.playToEndOfStream()
    override fun isEnded(): Boolean = current.isEnded()
    override fun hasPendingData(): Boolean = current.hasPendingData()
    override fun flush() = current.flush()

    override fun reset() {
        current.reset()
        current = default
    }

    override fun release() {
        usb.release()
        default.release()
        current = default
    }

    override fun setPlaybackParameters(playbackParameters: PlaybackParameters) =
        default.setPlaybackParameters(playbackParameters)

    override fun getPlaybackParameters(): PlaybackParameters = current.playbackParameters
    override fun setSkipSilenceEnabled(skipSilenceEnabled: Boolean) = default.setSkipSilenceEnabled(skipSilenceEnabled)
    override fun getSkipSilenceEnabled(): Boolean = current.skipSilenceEnabled

    override fun setAudioAttributes(audioAttributes: AudioAttributes) {
        default.setAudioAttributes(audioAttributes)
        usb.setAudioAttributes(audioAttributes)
    }

    override fun getAudioAttributes(): AudioAttributes? = default.audioAttributes
    override fun setAudioSessionId(audioSessionId: Int) = default.setAudioSessionId(audioSessionId)
    override fun setAuxEffectInfo(auxEffectInfo: AuxEffectInfo) = default.setAuxEffectInfo(auxEffectInfo)
    override fun setPreferredDevice(audioDeviceInfo: AudioDeviceInfo?) = default.setPreferredDevice(audioDeviceInfo)
    override fun setOutputStreamOffsetUs(outputStreamOffsetUs: Long) = default.setOutputStreamOffsetUs(outputStreamOffsetUs)
    override fun enableTunnelingV21() = default.enableTunnelingV21()
    override fun disableTunneling() = default.disableTunneling()
    override fun setOffloadMode(offloadMode: Int) = default.setOffloadMode(offloadMode)
    override fun setOffloadDelayPadding(delayInFrames: Int, paddingInFrames: Int) =
        default.setOffloadDelayPadding(delayInFrames, paddingInFrames)

    override fun setVolume(volume: Float) = default.setVolume(volume)
}
