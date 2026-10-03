package com.example.samsonic.playback.usb

import androidx.media3.common.C
import com.example.samsonic.playback.Downmix
import com.example.samsonic.playback.Resampler
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.roundToLong

/**
 * Turns the player's PCM ([inEncoding], [inChannels] at [inRate]) into what a DAC's alt setting
 * takes: [plan]'s sample size, left-justified in its slots, two channels. At the song's own rate
 * nothing is calculated, only widened or copied, so every bit stays. Otherwise the song goes
 * through the [Resampler] first, which is as exact as resampling can be.
 */
internal class UsbPcmPipeline(
    private val inRate: Int,
    private val inChannels: Int,
    private val inEncoding: Int,
    private val plan: UsbPlan,
) {
    private val inBytes = bytesPerSample(inEncoding)
    private val outSlot = plan.subslotBytes
    // Surround is folded to stereo first, so the rest of the pipeline only ever sees one or two channels.
    private val surround = Downmix.canDownmix(inChannels)
    private val gains = if (surround) Downmix.gains(inChannels) else FloatArray(0)
    private val frame = FloatArray(inChannels)
    private val mixed = FloatArray(2)
    private val stageChannels = if (surround) 2 else inChannels
    private val resampler = if (inRate == plan.rate) null else Resampler(inRate, plan.rate, stageChannels, plan.channels)
    private var out: ByteBuffer = ByteBuffer.allocateDirect(0).order(ByteOrder.nativeOrder())
    private var floats = FloatArray(0)

    /** The frames, at the DAC's rate, [process] has produced in all. */
    var producedFrames = 0L
        private set

    /** Forgets the resampler's history, for a seek. */
    fun reset() {
        resampler?.reset()
    }

    /** Converts all of [input]; returns the packed result, valid until the next call. */
    fun process(input: ByteBuffer): ByteBuffer {
        val source = input.duplicate().order(ByteOrder.nativeOrder())
        input.position(input.limit())
        val frames = source.remaining() / (inBytes * inChannels)
        return if (resampler == null) pass(source, frames) else resample(source, frames, resampler)
    }

    private fun pass(source: ByteBuffer, frames: Int): ByteBuffer {
        prepare(frames)
        if (surround) {
            for (n in 0 until frames) {
                mixFrame(source)
                put(scale(mixed[0]))
                put(scale(mixed[1]))
            }
            return finish(frames)
        }
        for (n in 0 until frames) {
            var left = 0
            for (channel in 0 until plan.channels) {
                // Mono is copied to both channels.
                if (channel < inChannels) left = readInt(source)
                put(left)
            }
        }
        return finish(frames)
    }

    private fun resample(source: ByteBuffer, frames: Int, resampler: Resampler): ByteBuffer {
        val samples = frames * stageChannels
        if (floats.size < samples) floats = FloatArray(samples)
        if (surround) {
            for (n in 0 until frames) {
                mixFrame(source)
                floats[n * 2] = mixed[0]
                floats[n * 2 + 1] = mixed[1]
            }
        } else {
            for (i in 0 until samples) floats[i] = readFloat(source)
        }
        val written = resampler.process(floats, frames)
        prepare(written)
        for (i in 0 until written * plan.channels) put(scale(resampler.output[i]))
        return finish(written)
    }

    /** Reads one surround frame from [source] and folds it to stereo in [mixed]. */
    private fun mixFrame(source: ByteBuffer) {
        for (channel in 0 until inChannels) frame[channel] = readFloat(source)
        Downmix.mix(gains, inChannels, frame, mixed)
    }

    private fun prepare(frames: Int) {
        val size = frames * plan.channels * outSlot
        if (out.capacity() < size) out = ByteBuffer.allocateDirect(size * 2).order(ByteOrder.nativeOrder())
        out.clear()
    }

    private fun finish(frames: Int): ByteBuffer {
        producedFrames += frames
        out.flip()
        return out
    }

    /** One sample as a 32-bit integer, widened exactly. */
    private fun readInt(source: ByteBuffer): Int = when (inEncoding) {
        C.ENCODING_PCM_16BIT -> source.getShort().toInt() shl 16
        C.ENCODING_PCM_24BIT -> {
            val value = (source.get().toInt() and 0xFF) or ((source.get().toInt() and 0xFF) shl 8) or (source.get().toInt() shl 16)
            value shl 8
        }
        C.ENCODING_PCM_32BIT -> source.getInt()
        else -> scale(source.getFloat())
    }

    private fun readFloat(source: ByteBuffer): Float = when (inEncoding) {
        C.ENCODING_PCM_16BIT -> source.getShort() / 32_768f
        C.ENCODING_PCM_24BIT -> readInt(source) / 2_147_483_648f
        C.ENCODING_PCM_32BIT -> (source.getInt() / 2_147_483_648.0).toFloat()
        else -> source.getFloat()
    }

    /** Writes the top [outSlot] bytes of [value], low byte first. */
    private fun put(value: Int) {
        when (outSlot) {
            4 -> out.putInt(value)
            3 -> out.put((value shr 8).toByte()).put((value shr 16).toByte()).put((value shr 24).toByte())
            else -> out.put((value shr 16).toByte()).put((value shr 24).toByte())
        }
    }

    companion object {
        /** The PCM encodings the pipeline reads. */
        val SUPPORTED = setOf(C.ENCODING_PCM_16BIT, C.ENCODING_PCM_24BIT, C.ENCODING_PCM_32BIT, C.ENCODING_PCM_FLOAT)

        fun bytesPerSample(encoding: Int): Int = when (encoding) {
            C.ENCODING_PCM_16BIT -> 2
            C.ENCODING_PCM_24BIT -> 3
            else -> 4
        }

        /** The bits a song of [encoding] has to keep. */
        fun sourceBits(encoding: Int): Int = when (encoding) {
            C.ENCODING_PCM_16BIT -> 16
            C.ENCODING_PCM_32BIT -> 32
            else -> 24
        }

        /** [sample] (-1..1) as a 32-bit integer; exact for 16- and 24-bit sources. */
        fun scale(sample: Float): Int =
            (sample.toDouble() * 2_147_483_648.0).roundToLong().coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong()).toInt()
    }
}
