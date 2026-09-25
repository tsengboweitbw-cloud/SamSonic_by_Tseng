package com.example.samsonic.playback.dsd

// DoP's markers, alternating sample to sample, so the DAC can tell DSD from PCM.
private const val DOP_MARKER = 0x05
private const val MARKER_FLIP = 0xFF // 0x05 xor 0xFF is the other marker, 0xFA

// A float sample is a 32-bit integer over 2^31. Every word here has at most 24 significant
// bits, which a float holds exactly, so the output end gets the same integer back.
private const val FULL_SCALE = 2_147_483_648f

/**
 * Carries DSD through the player untouched, with no filtering: each channel's DSD, 16 bits
 * (two bytes, earliest bit first) per float sample, at [dsdRate] / 16. With [dop], each
 * sample is a DoP word: the marker in the top byte, the 16 bits under it. Without, the 16
 * bits sit at the top for the native DSD track to unpack.
 */
internal class DsdPacker(
    private val channels: Int,
    dsdRate: Int,
    private val lsbFirst: Boolean,
    private val dop: Boolean,
) : DsdEncoder {
    override val bytesPerOutput: Int = 2
    override val outputRate: Int = dsdRate / 16

    // Each channel's first byte of a sample, while its second is still to come.
    private val first = IntArray(channels)
    private var haveFirst = false
    private var marker = DOP_MARKER

    override fun reset() {
        haveFirst = false
        marker = DOP_MARKER
    }

    override fun process(input: ByteArray, frames: Int, out: FloatArray): Int {
        var written = 0
        var at = 0
        repeat(frames) {
            if (!haveFirst) {
                for (channel in 0 until channels) first[channel] = byteAt(input, at++)
                haveFirst = true
                return@repeat
            }
            val base = written * channels
            for (channel in 0 until channels) {
                val bits = (first[channel] shl 8) or byteAt(input, at++)
                val word = if (dop) (marker shl 24) or (bits shl 8) else bits shl 16
                out[base + channel] = word.toFloat() / FULL_SCALE
            }
            marker = marker xor MARKER_FLIP
            haveFirst = false
            written++
        }
        return written
    }

    private fun byteAt(input: ByteArray, index: Int): Int {
        val byte = input[index].toInt() and 0xFF
        return if (lsbFirst) ReversedBits[byte] else byte
    }
}
