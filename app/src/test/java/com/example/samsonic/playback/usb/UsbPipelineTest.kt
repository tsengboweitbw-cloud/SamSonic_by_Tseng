package com.example.samsonic.playback.usb

import androidx.media3.common.C
import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class UsbPipelineTest {
    private fun direct(vararg bytes: Int): ByteBuffer =
        ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder()).apply {
            bytes.forEach { put(it.toByte()) }
            flip()
        }

    private fun bytesOf(buffer: ByteBuffer): List<Int> = List(buffer.remaining()) { buffer.get(buffer.position() + it).toInt() and 0xFF }

    @Test
    fun `trimmer cuts the start and holds back the end`() {
        val trimmer = GaplessTrimmer()
        trimmer.configure(frameBytes = 1, startFrames = 2, endFrames = 3)
        // 10 bytes: the first 2 go, the last 3 stay held.
        assertEquals(listOf(2, 3, 4, 5, 6), bytesOf(trimmer.process(direct(0, 1, 2, 3, 4, 5, 6, 7, 8, 9))))
        // The next bytes release the held ones.
        assertEquals(listOf(7, 8, 9, 10), bytesOf(trimmer.process(direct(10, 11, 12, 13))))
        trimmer.endOfStream()
        assertEquals(emptyList<Int>(), bytesOf(trimmer.process(direct())))
    }

    @Test
    fun `trimmer swallows a buffer shorter than the end it holds`() {
        val trimmer = GaplessTrimmer()
        trimmer.configure(frameBytes = 1, startFrames = 0, endFrames = 4)
        assertEquals(emptyList<Int>(), bytesOf(trimmer.process(direct(1, 2))))
        assertEquals(listOf(1, 2), bytesOf(trimmer.process(direct(3, 4, 5, 6))))
    }

    @Test
    fun `trimmer with nothing to trim passes everything`() {
        val trimmer = GaplessTrimmer()
        trimmer.configure(frameBytes = 4, startFrames = 0, endFrames = 0)
        assertEquals(listOf(1, 2, 3, 4), bytesOf(trimmer.process(direct(1, 2, 3, 4))))
    }

    @Test
    fun `16-bit stereo widens into 32-bit slots with every bit kept`() {
        val plan = UsbPlan(altIndex = 0, subslotBytes = 4, rate = 44_100, channels = 2)
        val pipeline = UsbPcmPipeline(44_100, 2, C.ENCODING_PCM_16BIT, plan)
        // Left 0x1234, right 0xFFFE (-2), little-endian.
        val out = pipeline.process(direct(0x34, 0x12, 0xFE, 0xFF))
        assertEquals(listOf(0x00, 0x00, 0x34, 0x12, 0x00, 0x00, 0xFE, 0xFF), bytesOf(out))
        assertEquals(1, pipeline.producedFrames)
    }

    @Test
    fun `24-bit mono is copied to both channels in 24-bit slots`() {
        val plan = UsbPlan(altIndex = 0, subslotBytes = 3, rate = 96_000, channels = 2)
        val pipeline = UsbPcmPipeline(96_000, 1, C.ENCODING_PCM_24BIT, plan)
        val out = pipeline.process(direct(0x01, 0x02, 0x03))
        assertEquals(listOf(1, 2, 3, 1, 2, 3), bytesOf(out))
    }

    @Test
    fun `float samples of 24-bit audio come back exactly`() {
        val plan = UsbPlan(altIndex = 0, subslotBytes = 4, rate = 48_000, channels = 2)
        val pipeline = UsbPcmPipeline(48_000, 2, C.ENCODING_PCM_FLOAT, plan)
        val input = ByteBuffer.allocateDirect(8).order(ByteOrder.nativeOrder())
        input.putFloat(0x123456 / 8_388_608f).putFloat(-1f).flip()
        val out = pipeline.process(input).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(0x12345600, out.getInt(0))
        assertEquals(Int.MIN_VALUE, out.getInt(4))
    }

    @Test
    fun `a DAC that takes the song's rate plays it as it is`() {
        assertEquals(96_000, UsbDacOutput.chooseRate(intArrayOf(44_100, 48_000, 96_000), 96_000))
    }

    @Test
    fun `an unsupported rate goes up within its family`() {
        // 88.2 kHz isn't offered: 44.1 family goes to 176.4, not 96.
        assertEquals(176_400, UsbDacOutput.chooseRate(intArrayOf(48_000, 96_000, 176_400, 192_000), 88_200))
        // 32 kHz counts as the 48 kHz family (a simple 3:2 ratio).
        assertEquals(48_000, UsbDacOutput.chooseRate(intArrayOf(44_100, 48_000), 32_000))
    }

    @Test
    fun `a rate above everything falls back to the highest`() {
        assertEquals(48_000, UsbDacOutput.chooseRate(intArrayOf(44_100, 48_000), 96_000))
    }
}
