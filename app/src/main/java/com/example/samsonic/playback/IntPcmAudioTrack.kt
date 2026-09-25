package com.example.samsonic.playback

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.roundToLong

/**
 * An AudioTrack that takes the player's PCM ([inEncoding]: float, or 16-bit) but plays
 * [outEncoding] (32-bit or packed 24-bit integer), which is what a USB DAC takes bit-perfect.
 * The player only writes 16-bit or float, float holds 16- and 24-bit samples exactly, and
 * 16-bit only gains zeros underneath, so this loses nothing for them.
 *
 * With [fixedVolume] (DoP, which any gain would corrupt), the player's volume changes are
 * ignored, and [silence] cuts it off for good.
 *
 * Sizes stay in the player's terms: [inBufferSize] and every write count the player's
 * bytes, so its frame math still holds whatever size the frames here are.
 */
internal class IntPcmAudioTrack(
    attributes: AudioAttributes,
    sampleRate: Int,
    channelMask: Int,
    private val inEncoding: Int,
    private val outEncoding: Int,
    inBufferSize: Int,
    sessionId: Int,
    private val fixedVolume: Boolean = false,
) : AudioTrack(
    attributes,
    AudioFormat.Builder().setSampleRate(sampleRate).setChannelMask(channelMask).setEncoding(outEncoding).build(),
    inBufferSize / bytesPerSample(inEncoding) * bytesPerSample(outEncoding),
    MODE_STREAM,
    sessionId,
), Silenceable {
    @Volatile private var silenced = false
    private val channels = Integer.bitCount(channelMask)
    private val inBytes = bytesPerSample(inEncoding)
    private val outBytes = bytesPerSample(outEncoding)
    private var scratch: ByteBuffer = ByteBuffer.allocateDirect(0)

    override fun setVolume(gain: Float): Int =
        super.setVolume(if (silenced) 0f else if (fixedVolume) 1f else gain)

    override fun silence() {
        silenced = true
        super.setVolume(0f)
    }

    override fun write(audioData: ByteBuffer, sizeInBytes: Int, writeMode: Int): Int {
        val inFrame = inBytes * channels
        val frames = sizeInBytes / inFrame
        val outSize = frames * channels * outBytes
        if (scratch.capacity() < outSize) scratch = ByteBuffer.allocateDirect(outSize).order(ByteOrder.nativeOrder())
        scratch.clear()
        // Reads without moving audioData: only what the track takes counts as consumed.
        val input = audioData.duplicate().order(ByteOrder.nativeOrder())
        repeat(frames * channels) {
            // Every sample as a 32-bit integer first: exact from float or 16-bit.
            val value = if (inBytes == 2) input.getShort().toInt() shl 16 else scale(input.getFloat())
            if (outEncoding == AudioFormat.ENCODING_PCM_32BIT) {
                scratch.putInt(value)
            } else {
                val top = value shr 8
                scratch.put(top.toByte()).put((top shr 8).toByte()).put((top shr 16).toByte())
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
        fun bytesPerSample(encoding: Int): Int = when (encoding) {
            AudioFormat.ENCODING_PCM_16BIT -> 2
            AudioFormat.ENCODING_PCM_24BIT_PACKED -> 3
            else -> 4
        }

        /** [sample] (-1..1) as a 32-bit integer; exact for 16- and 24-bit sources. */
        fun scale(sample: Float): Int =
            (sample.toDouble() * 2_147_483_648.0).roundToLong().coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong()).toInt()
    }
}
