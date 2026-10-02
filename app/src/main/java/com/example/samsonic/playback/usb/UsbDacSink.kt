package com.example.samsonic.playback.usb

import android.os.SystemClock
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.audio.AudioSink
import java.nio.ByteBuffer

/**
 * The player's audio sink for a USB DAC the driver has taken over: it fits each song to the DAC
 * ([UsbPlan], [UsbPcmPipeline]), trims it for gapless playback ([GaplessTrimmer]) and queues it
 * for the native driver. The DAC's volume is the DAC's own, so the player's is ignored, and
 * so are speed changes: audio goes out exactly as decoded.
 *
 * The song's [Format] is applied when its first buffer arrives, not in [configure], so the
 * song before it can finish playing first.
 */
@OptIn(UnstableApi::class)
internal class UsbDacSink(private val openDac: () -> UsbDacConnection?) : AudioSink {
    private class Config(val format: Format, val plan: UsbPlan)

    /** Where a song starts in the DAC's timeline: [frames] played by then, and its media time. */
    private class Anchor(val frames: Long, val mediaUs: Long, val rate: Int)

    private var listener: AudioSink.Listener? = null
    private var attributes = AudioAttributes.DEFAULT
    private var output: UsbDacOutput? = null
    private var started = false
    private var playing = false

    private var active: Config? = null
    private var pending: Config? = null
    private var pipeline: UsbPcmPipeline? = null
    private val trimmer = GaplessTrimmer()
    private var trimStartUs = 0L

    // Converted audio the driver hasn't taken yet.
    private var queued: ByteBuffer? = null
    private var convertedFrames = 0L
    private var playedBase = 0L
    private val anchors = ArrayDeque<Anchor>()
    private var needAnchor = true
    private var endOfStream = false
    private var advancingReported = false

    /** Whether this sink can play [format] on the DAC; opens the DAC to find out, if it isn't open. */
    fun canPlay(format: Format): Boolean = planFor(format) != null

    private fun planFor(format: Format): UsbPlan? {
        if (!isPcm(format)) return null
        val dac = output ?: openDac()?.let { UsbDacOutput.open(it) }?.also { output = it } ?: return null
        return dac.planFor(format.sampleRate, UsbPcmPipeline.sourceBits(format.pcmEncoding))
    }

    override fun setListener(listener: AudioSink.Listener) {
        this.listener = listener
    }

    override fun supportsFormat(format: Format): Boolean = isPcm(format)

    override fun getFormatSupport(format: Format): Int =
        if (isPcm(format)) AudioSink.SINK_FORMAT_SUPPORTED_DIRECTLY else AudioSink.SINK_FORMAT_UNSUPPORTED

    override fun configure(inputFormat: Format, specifiedBufferSize: Int, outputChannels: IntArray?) {
        val plan = planFor(inputFormat)
            ?: throw AudioSink.ConfigurationException("The USB DAC can't play this format", inputFormat)
        pending = Config(inputFormat, plan)
        endOfStream = false
    }

    override fun handleBuffer(buffer: ByteBuffer, presentationTimeUs: Long, encodedAccessUnitCount: Int): Boolean {
        if (!drainQueued()) return false
        if (!applyPending()) return false
        val pipeline = pipeline ?: return true
        val config = active ?: return true
        if (!buffer.hasRemaining()) return true
        if (needAnchor) {
            anchors.addLast(Anchor(convertedFrames, presentationTimeUs + trimStartUs, config.plan.rate))
            trimStartUs = 0
            needAnchor = false
        }
        val trimmed = trimmer.process(buffer)
        if (trimmed.hasRemaining()) {
            val before = pipeline.producedFrames
            val packed = pipeline.process(trimmed)
            convertedFrames += pipeline.producedFrames - before
            queued = packed
            drainQueued()
        }
        return true
    }

    /** Gives the driver what it will take of [queued]; true once all of it is in. */
    private fun drainQueued(): Boolean {
        val data = queued ?: return true
        if (data.hasRemaining()) {
            data.position(data.position() + NativeUsb.write(data, data.position(), data.remaining()))
        }
        return !data.hasRemaining()
    }

    /** Starts the song [configure] was given; false while the one before it is still playing out. */
    private fun applyPending(): Boolean {
        val config = pending ?: return true
        val dac = output ?: throw writeError(config.format, "the DAC went away")
        val current = active
        if (current != null) {
            // The song before ended: its padding goes.
            trimmer.endOfStream()
            if (current.plan != config.plan) {
                if (!allPlayed()) return false
                NativeUsb.stop()
                started = false
            }
        }
        if (!started) {
            val error = dac.start(config.plan)
            if (error.isNotEmpty()) throw writeError(config.format, error)
            started = true
            NativeUsb.setPaused(!playing)
            playedBase = 0
            convertedFrames = 0
            anchors.clear()
        }
        val format = config.format
        pipeline = UsbPcmPipeline(format.sampleRate, format.channelCount, format.pcmEncoding, config.plan)
        val frameBytes = format.channelCount * UsbPcmPipeline.bytesPerSample(format.pcmEncoding)
        val delay = format.encoderDelay.coerceAtLeast(0)
        trimmer.configure(frameBytes, delay, format.encoderPadding.coerceAtLeast(0))
        trimStartUs = delay * C.MICROS_PER_SECOND / format.sampleRate
        needAnchor = true
        active = config
        pending = null
        return true
    }

    private fun writeError(format: Format, message: String) =
        AudioSink.WriteException(/* errorCode = */ -1, format, /* isRecoverable = */ false).also {
            Log.w(TAG, "Can't play: $message")
        }

    private fun played(): Long = NativeUsb.playedFrames() - playedBase

    private fun allPlayed(): Boolean = !started || played() >= convertedFrames

    override fun playToEndOfStream() {
        drainQueued()
        trimmer.endOfStream()
        endOfStream = true
    }

    override fun isEnded(): Boolean = endOfStream && queued?.hasRemaining() != true && allPlayed()

    override fun hasPendingData(): Boolean = queued?.hasRemaining() == true || !allPlayed()

    override fun getCurrentPositionUs(sourceEnded: Boolean): Long {
        if (!started || anchors.isEmpty()) return AudioSink.CURRENT_POSITION_NOT_SET
        val played = played()
        while (anchors.size > 1 && played >= anchors[1].frames) anchors.removeFirst()
        val anchor = anchors.first()
        if (playing && !advancingReported && played > anchor.frames) {
            advancingReported = true
            listener?.onPositionAdvancing(SystemClock.elapsedRealtime())
        }
        return anchor.mediaUs + maxOf(0L, played - anchor.frames) * C.MICROS_PER_SECOND / anchor.rate
    }

    override fun handleDiscontinuity() = Unit

    override fun play() {
        playing = true
        if (started) NativeUsb.setPaused(false)
    }

    override fun pause() {
        playing = false
        advancingReported = false
        if (started) NativeUsb.setPaused(true)
    }

    override fun flush() {
        queued = null
        if (started) {
            NativeUsb.flush()
            playedBase = NativeUsb.playedFrames()
        }
        convertedFrames = 0
        anchors.clear()
        needAnchor = true
        endOfStream = false
        advancingReported = false
        trimmer.flush()
        trimStartUs = 0
        pipeline?.reset()
    }

    /** Lets go of the DAC; the next song opens it again. */
    override fun reset() {
        flush()
        if (started) NativeUsb.stop()
        started = false
        active = null
        pending = null
        pipeline = null
        output?.close()
        output = null
    }

    override fun release() = reset()

    // Audio goes out exactly as decoded: no speed, silence skipping or gain.
    override fun setPlaybackParameters(playbackParameters: PlaybackParameters) = Unit
    override fun getPlaybackParameters(): PlaybackParameters = PlaybackParameters.DEFAULT
    override fun setSkipSilenceEnabled(skipSilenceEnabled: Boolean) = Unit
    override fun getSkipSilenceEnabled(): Boolean = false
    override fun setVolume(volume: Float) = Unit

    override fun setAudioAttributes(audioAttributes: AudioAttributes) {
        attributes = audioAttributes
    }

    override fun getAudioAttributes(): AudioAttributes = attributes
    override fun setAudioSessionId(audioSessionId: Int) = Unit
    override fun setAuxEffectInfo(auxEffectInfo: androidx.media3.common.AuxEffectInfo) = Unit
    override fun enableTunnelingV21() = Unit
    override fun disableTunneling() = Unit

    private fun isPcm(format: Format): Boolean =
        // Raw audio only: a compressed track (FLAC...) also carries a PCM encoding, but isn't PCM yet.
        format.sampleMimeType == MimeTypes.AUDIO_RAW && format.pcmEncoding in UsbPcmPipeline.SUPPORTED &&
            format.channelCount in 1..2 && format.sampleRate > 0
}

private const val TAG = "UsbDacSink"
