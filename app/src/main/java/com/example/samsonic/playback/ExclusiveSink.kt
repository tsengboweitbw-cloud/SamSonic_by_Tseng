package com.example.samsonic.playback

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.ForwardingAudioSink
import com.example.samsonic.playback.dsd.DsdStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** How exclusive mode fits a song to the DAC: its rate and channels, from the song's own. */
data class ExclusiveConversion(val fromRate: Int, val fromChannels: Int, val sampleRate: Int, val channels: Int)

/**
 * The player's audio sink, with exclusive mode's two jobs in front. It tells [bitPerfect]
 * each format it's set up for, before the track that plays it is made (a DSD file packed for
 * the DAC looks like any float PCM by then, but for its Format). And when the DAC doesn't
 * take a song's rate or channels, it resamples the song ([Resampler]) to a format it does, so
 * the DAC stays in exclusive mode rather than falling back to Android's mixer.
 */
@OptIn(UnstableApi::class)
internal class ExclusiveSink(sink: AudioSink, private val bitPerfect: BitPerfectOutput) : ForwardingAudioSink(sink) {
    private var resampler: Resampler? = null
    private var inEncoding = C.ENCODING_PCM_16BIT

    // Resampled audio the sink hasn't taken yet, and its time.
    private var outBytes: ByteBuffer = ByteBuffer.allocateDirect(0)
    private var pending = false
    private var pendingTimeUs = 0L

    // Output times run from the first input time, so the sink sees one unbroken stream.
    private var startUs = C.TIME_UNSET
    private var framesOut = 0L
    private var floats = FloatArray(0)

    // What the sink's setup, and so its track, is for; and the next song's setup, held back
    // until that track has played out.
    private var plan: TrackPlan? = null
    private var held: Setup? = null

    override fun configure(inputFormat: Format, specifiedBufferSize: Int, outputChannels: IntArray?) {
        val setup = Setup(
            inputFormat, specifiedBufferSize, outputChannels,
            TrackPlan(inputFormat.customData as? DsdStream, bitPerfect.conversionFor(inputFormat)),
        )
        // The sink reuses the playing track for a song of the same PCM format, but a DSD file
        // looks just like float PCM by now, and whether a track carries DSD, or plays
        // resampled, is settled when it's made: DSD would go out through a PCM track as noise.
        // So when that changes, the song waits for the last one to play out, on a new track.
        val current = plan
        if (current != null && setup.plan != current) {
            held = setup
            return
        }
        held = null
        apply(setup)
    }

    private fun apply(setup: Setup) {
        val inputFormat = setup.format
        val specifiedBufferSize = setup.bufferSize
        val outputChannels = setup.outputChannels
        val conversion = setup.plan.conversion
        plan = setup.plan
        bitPerfect.onSinkConfigured(inputFormat, conversion)
        restart()
        if (conversion == null) {
            resampler = null
            super.configure(inputFormat, specifiedBufferSize, outputChannels)
            return
        }
        resampler = Resampler(conversion.fromRate, conversion.sampleRate, conversion.fromChannels, conversion.channels)
        inEncoding = inputFormat.pcmEncoding
        val scale = conversion.sampleRate.toDouble() / conversion.fromRate
        super.configure(
            inputFormat.buildUpon()
                .setSampleRate(conversion.sampleRate)
                .setChannelCount(conversion.channels)
                .setPcmEncoding(C.ENCODING_PCM_FLOAT)
                // Gapless trimming counts frames, which are now at the new rate.
                .setEncoderDelay((inputFormat.encoderDelay * scale).toInt())
                .setEncoderPadding((inputFormat.encoderPadding * scale).toInt())
                .build(),
            /* specifiedBufferSize = */ 0,
            /* outputChannels = */ null,
        )
    }

    override fun handleBuffer(buffer: ByteBuffer, presentationTimeUs: Long, encodedAccessUnitCount: Int): Boolean {
        held?.let { setup ->
            // The song after a change of plan: the last track plays out, then goes, as the sink
            // itself does between formats, so the next buffer makes a new track.
            super.playToEndOfStream()
            if (super.hasPendingData()) return false
            super.flush()
            held = null
            apply(setup)
        }
        val resampler = resampler ?: return super.handleBuffer(buffer, presentationTimeUs, encodedAccessUnitCount)
        // Take the next buffer in only once the last one's output is all out: it's handed on
        // again and again until the sink has it, as the sink requires.
        if (!pending && buffer.hasRemaining()) {
            if (startUs == C.TIME_UNSET) startUs = presentationTimeUs
            val frames = readFloats(buffer, resampler)
            val written = resampler.process(floats, frames)
            if (written > 0) {
                putFloats(resampler.output, written * resampler.outChannels)
                pendingTimeUs = startUs + framesOut * C.MICROS_PER_SECOND / resampler.outRate
                framesOut += written
                pending = true
            }
        }
        if (pending) {
            if (!super.handleBuffer(outBytes, pendingTimeUs, 1)) return false
            pending = false
        }
        return !buffer.hasRemaining()
    }

    override fun hasPendingData(): Boolean = pending || super.hasPendingData()

    override fun flush() {
        restart()
        super.flush()
        applyHeld()
    }

    override fun reset() {
        restart()
        super.reset()
        applyHeld()
    }

    /** With the track gone, nothing is left to play out: a held setup can go ahead. */
    private fun applyHeld() {
        val setup = held ?: return
        held = null
        apply(setup)
    }

    private fun restart() {
        resampler?.reset()
        pending = false
        startUs = C.TIME_UNSET
        framesOut = 0
    }

    /** Reads all of [buffer] into [floats] as float samples; returns its frames. */
    private fun readFloats(buffer: ByteBuffer, resampler: Resampler): Int {
        val source = buffer.duplicate().order(ByteOrder.nativeOrder())
        val bytes = bytesPerSample(inEncoding)
        val samples = source.remaining() / bytes
        if (floats.size < samples) floats = FloatArray(samples)
        for (i in 0 until samples) {
            floats[i] = when (inEncoding) {
                C.ENCODING_PCM_16BIT -> source.getShort() / 32_768f
                C.ENCODING_PCM_24BIT -> {
                    val value = (source.get().toInt() and 0xFF) or
                        ((source.get().toInt() and 0xFF) shl 8) or (source.get().toInt() shl 16)
                    value / 8_388_608f
                }
                C.ENCODING_PCM_32BIT -> (source.getInt() / 2_147_483_648.0).toFloat()
                else -> source.getFloat()
            }
        }
        buffer.position(buffer.position() + samples * bytes)
        return samples / resampler.inChannels
    }

    private fun putFloats(samples: FloatArray, count: Int) {
        if (outBytes.capacity() < count * 4) outBytes = ByteBuffer.allocateDirect(count * 8).order(ByteOrder.nativeOrder())
        outBytes.clear()
        for (i in 0 until count) outBytes.putFloat(samples[i])
        outBytes.flip()
    }

    /** What a track is made for: the DSD it carries, if any, and how the song is resampled, if at all. */
    private data class TrackPlan(val stream: DsdStream?, val conversion: ExclusiveConversion?)

    private class Setup(val format: Format, val bufferSize: Int, val outputChannels: IntArray?, val plan: TrackPlan)

    companion object {
        /** The PCM encodings it can resample. */
        val RESAMPLABLE = setOf(C.ENCODING_PCM_16BIT, C.ENCODING_PCM_24BIT, C.ENCODING_PCM_32BIT, C.ENCODING_PCM_FLOAT)

        private fun bytesPerSample(encoding: Int): Int = when (encoding) {
            C.ENCODING_PCM_16BIT -> 2
            C.ENCODING_PCM_24BIT -> 3
            else -> 4
        }
    }
}
