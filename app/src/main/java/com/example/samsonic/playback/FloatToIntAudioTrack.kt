package com.example.samsonic.playback

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.roundToLong

/**
 * An AudioTrack that takes the player's float PCM but plays [outEncoding] (32-bit or packed
 * 24-bit integer), which is what a USB DAC takes bit-perfect. The player only writes 16-bit
 * or float, and float holds 16- and 24-bit samples exactly, so this conversion loses nothing
 * for them.
 *
 * Sizes stay in the player's terms: [floatBufferSize] and every write count float bytes, so
 * the player's frame math still holds whatever size the frames here are.
 */
internal class FloatToIntAudioTrack(
    attributes: AudioAttributes,
    sampleRate: Int,
    channelMask: Int,
    private val outEncoding: Int,
    floatBufferSize: Int,
    sessionId: Int,
) : AudioTrack(
    attributes,
    AudioFormat.Builder().setSampleRate(sampleRate).setChannelMask(channelMask).setEncoding(outEncoding).build(),
    floatBufferSize / FLOAT_BYTES * bytesPerSample(outEncoding),
    MODE_STREAM,
    sessionId,
) {
    private val channels = Integer.bitCount(channelMask)
    private val outBytes = bytesPerSample(outEncoding)
    private var scratch: ByteBuffer = ByteBuffer.allocateDirect(0)

    override fun write(audioData: ByteBuffer, sizeInBytes: Int, writeMode: Int): Int {
        val inFrame = FLOAT_BYTES * channels
        val frames = sizeInBytes / inFrame
        val outSize = frames * channels * outBytes
        if (scratch.capacity() < outSize) scratch = ByteBuffer.allocateDirect(outSize).order(ByteOrder.nativeOrder())
        scratch.clear()
        // Reads without moving audioData: only what the track takes counts as consumed.
        val floats = audioData.duplicate().order(ByteOrder.nativeOrder())
        repeat(frames * channels) {
            val sample = floats.getFloat()
            if (outEncoding == AudioFormat.ENCODING_PCM_32BIT) {
                scratch.putInt(scale(sample, Int.MAX_VALUE.toLong()).toInt())
            } else {
                val value = scale(sample, MAX_24).toInt()
                scratch.put(value.toByte()).put((value shr 8).toByte()).put((value shr 16).toByte())
            }
        }
        scratch.flip()
        val written = super.write(scratch, outSize, writeMode)
        if (written < 0) return written
        // AudioTrack takes whole frames.
        val consumed = written / (channels * outBytes) * inFrame
        audioData.position(audioData.position() + consumed)
        return consumed
    }

    private companion object {
        const val FLOAT_BYTES = 4
        const val MAX_24 = 0x7FFFFFL

        fun bytesPerSample(encoding: Int): Int = if (encoding == AudioFormat.ENCODING_PCM_24BIT_PACKED) 3 else 4

        /** [sample] (-1..1) as an integer of full scale [max]; exact for 16- and 24-bit sources. */
        fun scale(sample: Float, max: Long): Long =
            (sample.toDouble() * (max + 1)).roundToLong().coerceIn(-(max + 1), max)
    }
}
