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
 * With [dop], which any gain would corrupt, the player's volume changes are ignored, and
 * [silence] cuts it off for good: muted, and zeros written in place of the samples, which it
 * also does itself if Android moves it off the USB DAC. The DoP markers are stamped here, not
 * by each file's packer, so they keep alternating across gapless songs: the DAC sees one
 * stream, and a marker twice running would drop it out of DSD for a click.
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
    private val dop: Boolean = false,
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

    /** The DoP marker of the next frame the track takes. */
    private var marker = DOP_MARKER

    override fun setVolume(gain: Float): Int =
        super.setVolume(if (silenced) 0f else if (dop) 1f else gain)

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
        // DoP moved off the DAC (DsdRouteGuard may not have heard yet) is noise: zeros from here on.
        if (dop && !silenced && leftUsb()) silence()
        val mute = silenced
        val restamp = dop && !mute
        // Reads without moving audioData: only what the track takes counts as consumed.
        val input = audioData.duplicate().order(ByteOrder.nativeOrder())
        for (sample in 0 until frames * channels) {
            // Every sample as a 32-bit integer first: exact from float or 16-bit.
            var value = when {
                mute -> 0
                inBytes == 2 -> input.getShort().toInt() shl 16
                else -> scale(input.getFloat())
            }
            if (restamp) {
                val frameMarker = if ((sample / channels) % 2 == 0) marker else marker xor MARKER_FLIP
                value = (value and 0x00FFFFFF) or (frameMarker shl 24)
            }
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
        // AudioTrack takes whole frames; the next write's markers carry on from the last one taken.
        val framesTaken = written / (channels * outBytes)
        if (restamp && framesTaken % 2 == 1) marker = marker xor MARKER_FLIP
        val consumed = framesTaken * inFrame
        audioData.position(audioData.position() + consumed)
        return consumed
    }

    private companion object {
        // DoP's markers, as in DsdPacker: 0x05, then 0xFA, frame to frame.
        const val DOP_MARKER = 0x05
        const val MARKER_FLIP = 0xFF

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
