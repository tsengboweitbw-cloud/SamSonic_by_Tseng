package com.example.samsonic.playback.dsd

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.roundToLong

private const val DSD64 = 2_822_400

class DsdPackerTest {
    /** Each float back to its 32-bit word, as FloatToIntAudioTrack does for a 32-bit DAC. */
    private fun word(sample: Float): Int = (sample.toDouble() * 2_147_483_648.0).roundToLong().toInt()

    @Test
    fun `DoP words carry the DSD bits under alternating markers`() {
        val packer = DsdPacker(channels = 2, dsdRate = DSD64, lsbFirst = false, dop = true)
        assertEquals(176_400, packer.outputRate)
        // Two frames per sample: left bytes 0x12, 0x34; right bytes 0xAB, 0xCD; then again.
        val input = byteArrayOf(0x12, 0xAB.toByte(), 0x34, 0xCD.toByte(), 0x12, 0xAB.toByte(), 0x34, 0xCD.toByte())
        val out = FloatArray(8)
        assertEquals(2, packer.process(input, frames = 4, out))
        assertEquals(0x05123400, word(out[0]))
        assertEquals(0x05ABCD00, word(out[1]))
        assertEquals(0xFA123400.toInt(), word(out[2]))
        assertEquals(0xFAABCD00.toInt(), word(out[3]))
    }

    @Test
    fun `every DoP word survives the float trip exactly, at 32 and 24 bits`() {
        val packer = DsdPacker(channels = 1, dsdRate = DSD64, lsbFirst = false, dop = true)
        val input = ByteArray(512) { it.toByte() } // every byte value, as both halves
        val out = FloatArray(256)
        val written = packer.process(input, frames = input.size, out)
        for (i in 0 until written) {
            val marker = if (i % 2 == 0) 0x05 else 0xFA
            val bits = ((input[2 * i].toInt() and 0xFF) shl 8) or (input[2 * i + 1].toInt() and 0xFF)
            val expected = (marker shl 24) or (bits shl 8)
            assertEquals(expected, word(out[i]))
            // A 24-bit DAC gets the top three bytes.
            assertEquals(expected shr 8, (out[i].toDouble() * 8_388_608.0).roundToLong().toInt())
        }
    }

    @Test
    fun `a sample split across reads comes out whole, and markers keep alternating`() {
        val packer = DsdPacker(channels = 1, dsdRate = DSD64, lsbFirst = false, dop = true)
        val out = FloatArray(4)
        assertEquals(0, packer.process(byteArrayOf(0x11), frames = 1, out))
        assertEquals(1, packer.process(byteArrayOf(0x22, 0x33), frames = 2, out))
        assertEquals(0x05112200, word(out[0]))
        assertEquals(1, packer.process(byteArrayOf(0x44), frames = 1, out))
        assertEquals(0xFA334400.toInt(), word(out[0]))
        packer.reset()
        assertEquals(1, packer.process(byteArrayOf(0x55, 0x66), frames = 2, out))
        assertEquals(0x05556600, word(out[0]))
    }

    @Test
    fun `DSF's LSB-first bytes are put earliest bit first`() {
        val packer = DsdPacker(channels = 1, dsdRate = DSD64, lsbFirst = true, dop = true)
        val out = FloatArray(1)
        // 0x01 LSB-first is 0x80; 0x0F is 0xF0.
        packer.process(byteArrayOf(0x01, 0x0F), frames = 2, out)
        assertEquals(0x0580F000, word(out[0]))
    }

    @Test
    fun `native packing puts the bits at the top with no marker`() {
        val packer = DsdPacker(channels = 1, dsdRate = DSD64, lsbFirst = false, dop = false)
        val out = FloatArray(1)
        packer.process(byteArrayOf(0x96.toByte(), 0x69), frames = 2, out)
        assertEquals(0x96690000.toInt(), word(out[0]))
    }
}
