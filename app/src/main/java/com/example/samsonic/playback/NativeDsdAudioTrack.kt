package com.example.samsonic.playback

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTimestamp
import android.media.AudioTrack
import android.os.Build
import androidx.annotation.RequiresApi
import com.example.samsonic.playback.dsd.DsdStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Raw DSD to the DAC, as Android's ENCODING_DSD at the rate it offered ([stream]). The
 * player writes float samples each carrying 16 of a channel's DSD bits at the top (see
 * DsdPacker); this unpacks them to bytes, earliest first, [DsdStream.Native.bytesPerFrame]
 * of each channel per frame.
 *
 * With a byte per frame, each player frame is two here, so positions are halved on the way
 * back: the player counts its own frames, at its own rate. Any gain would corrupt DSD, so the
 * player's volume changes are ignored ([silence] still cuts it off). Untried: shown only when Android
 * says the DAC takes native DSD.
 */
@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
internal class NativeDsdAudioTrack(
    attributes: AudioAttributes,
    stream: DsdStream.Native,
    channelMask: Int,
    floatBufferSize: Int,
    sessionId: Int,
) : AudioTrack(
    attributes,
    AudioFormat.Builder()
        .setSampleRate(stream.mixerRate)
        .setChannelMask(channelMask)
        .setEncoding(AudioFormat.ENCODING_DSD)
        .build(),
    // Two bytes of each channel for every four the player writes.
    floatBufferSize / 2,
    MODE_STREAM,
    sessionId,
), Silenceable {
    @Volatile private var silenced = false
    private val channels = Integer.bitCount(channelMask)
    private val bytesPerFrame = stream.bytesPerFrame

    /** Frames here per player frame. */
    private val ratio = 2 / bytesPerFrame

    // With a byte per frame: the first byte of the next player frame is already out.
    private var halfDone = false
    private var scratch: ByteBuffer = ByteBuffer.allocateDirect(0)

    override fun write(audioData: ByteBuffer, sizeInBytes: Int, writeMode: Int): Int {
        val inFrame = 4 * channels
        val frames = sizeInBytes / inFrame
        val outSize = frames * channels * 2
        if (scratch.capacity() < outSize) scratch = ByteBuffer.allocateDirect(outSize)
        scratch.clear()
        val words = audioData.duplicate().order(ByteOrder.nativeOrder())
        val skipFirstHalf = halfDone && bytesPerFrame == 1
        for (frame in 0 until frames) {
            val base = words.position()
            if (bytesPerFrame == 2) {
                for (channel in 0 until channels) {
                    val bits = bitsAt(words, base + channel * 4)
                    scratch.put((bits shr 8).toByte()).put(bits.toByte())
                }
            } else {
                if (!(frame == 0 && skipFirstHalf)) {
                    for (channel in 0 until channels) scratch.put((bitsAt(words, base + channel * 4) shr 8).toByte())
                }
                for (channel in 0 until channels) scratch.put(bitsAt(words, base + channel * 4).toByte())
            }
            words.position(base + inFrame)
        }
        scratch.flip()
        // Moved off the DAC (DsdRouteGuard may not have heard yet), or cut off: DSD silence from here on.
        if (!silenced && leftUsb()) silence()
        if (silenced) for (i in 0 until scratch.limit()) scratch.put(i, DSD_SILENCE)
        val written = super.write(scratch, scratch.remaining(), writeMode)
        if (written < 0) return written
        // AudioTrack takes whole frames; count the player frames it has all of.
        val trackFrames = written / (channels * bytesPerFrame)
        val done = if (bytesPerFrame == 2) {
            trackFrames
        } else {
            val halves = trackFrames + if (skipFirstHalf) 1 else 0
            halfDone = halves % 2 == 1
            halves / 2
        }
        val consumed = done * inFrame
        audioData.position(audioData.position() + consumed)
        return consumed
    }

    override fun setVolume(gain: Float): Int = super.setVolume(if (silenced) 0f else 1f)

    override fun silence() {
        silenced = true
        super.setVolume(0f)
    }

    override fun flush() {
        halfDone = false
        super.flush()
        head.reset()
    }

    // The track's positions are unsigned 32-bit and wrap, and with a byte per frame it counts
    // twice the player's frames, so they wrap twice as soon. Halving the raw count would leave
    // the player seeing its position fall back halfway to its own wrap. So positions are
    // unwrapped to 64 bits here, halved, and wrapped again at 32 bits, which the player
    // unwraps itself as it would any track's.
    private val head = Unwrapped()
    private val stamp = Unwrapped()

    override fun getPlaybackHeadPosition(): Int =
        (head.of(super.getPlaybackHeadPosition().toLong()) / ratio).toInt()

    override fun getTimestamp(timestamp: AudioTimestamp): Boolean {
        val ok = super.getTimestamp(timestamp)
        if (ok) timestamp.framePosition = stamp.of(timestamp.framePosition) / ratio and UINT_MASK
        return ok
    }

    override fun getSampleRate(): Int = super.getSampleRate() / ratio

    /** The 16 DSD bits in the float sample at [index]: its integer's top half. */
    private fun bitsAt(words: ByteBuffer, index: Int): Int =
        ((words.getFloat(index).toDouble() * 2_147_483_648.0).toLong().toInt() ushr 16) and 0xFFFF

    /** A position that wraps at 32 bits, counted on past each wrap. */
    private class Unwrapped {
        private var last = 0L
        private var wraps = 0L

        fun of(raw: Long): Long {
            val now = raw and UINT_MASK
            // A wrap drops it by nearly 2^32; anything less is the track settling, not a wrap.
            if (last - now > HALF_RANGE) wraps++
            last = now
            return (wraps shl 32) + now
        }

        fun reset() {
            last = 0
            wraps = 0
        }
    }

    private companion object {
        /** The DSD idle pattern: silence to a DAC, where zeros would be full-scale DC. */
        const val DSD_SILENCE: Byte = 0x69

        const val UINT_MASK = 0xFFFFFFFFL
        const val HALF_RANGE = 0x80000000L
    }
}
