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
    }

    override fun getPlaybackHeadPosition(): Int = super.getPlaybackHeadPosition() / ratio

    override fun getTimestamp(timestamp: AudioTimestamp): Boolean {
        val ok = super.getTimestamp(timestamp)
        if (ok) timestamp.framePosition /= ratio
        return ok
    }

    override fun getSampleRate(): Int = super.getSampleRate() / ratio

    /** The 16 DSD bits in the float sample at [index]: its integer's top half. */
    private fun bitsAt(words: ByteBuffer, index: Int): Int =
        ((words.getFloat(index).toDouble() * 2_147_483_648.0).toLong().toInt() ushr 16) and 0xFFFF
}
