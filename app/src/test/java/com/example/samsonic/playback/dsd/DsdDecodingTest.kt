package com.example.samsonic.playback.dsd

import androidx.media3.common.C
import androidx.media3.common.DataReader
import androidx.media3.common.Format
import androidx.media3.common.util.ParsableByteArray
import androidx.media3.extractor.DefaultExtractorInput
import androidx.media3.extractor.Extractor
import androidx.media3.extractor.ExtractorOutput
import androidx.media3.extractor.PositionHolder
import androidx.media3.extractor.SeekMap
import androidx.media3.extractor.TrackOutput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.sqrt

private const val DSD64 = 2_822_400

class DsdDecodingTest {
    @Test
    fun `a DSD64 sine comes out at its pitch and level`() {
        val seconds = 0.5
        val dsd = modulate(frequency = 1000.0, amplitude = 0.5, samples = (DSD64 * seconds).toInt())
        val converter = DsdToPcm(channels = 1, dsdRate = DSD64, lsbFirst = false)
        assertEquals(88_200, converter.outputRate)
        val pcm = FloatArray(dsd.size / converter.bytesPerOutput + 1)
        val frames = converter.process(dsd, dsd.size, pcm)
        assertEquals(dsd.size / converter.bytesPerOutput, frames)

        // Past the filter's start-up.
        val steady = pcm.copyOfRange(2000, frames)
        val amplitude = sqrt(steady.sumOf { (it * it).toDouble() } / steady.size) * sqrt(2.0)
        // Half modulation, lifted +3dB by the converter's gain: ~0.705.
        assertEquals(0.5 * 1.41, amplitude, 0.02)
        val crossings = steady.toList().zipWithNext().count { (a, b) -> a < 0 && b >= 0 }
        val measured = crossings / (steady.size / 88_200.0)
        assertEquals(1000.0, measured, 5.0)
    }

    @Test
    fun `DSD silence decodes to silence`() {
        val dsd = ByteArray(88_200) { 0x69 }
        val converter = DsdToPcm(channels = 1, dsdRate = DSD64, lsbFirst = false)
        val pcm = FloatArray(dsd.size)
        val frames = converter.process(dsd, dsd.size, pcm)
        assertTrue(pcm.take(frames).all { abs(it) < 0.01f })
    }

    @Test
    fun `DSF's LSB-first bytes decode the same as MSB-first`() {
        val dsd = modulate(frequency = 440.0, amplitude = 0.4, samples = 200_000)
        val reversed = ByteArray(dsd.size) { reverse(dsd[it]) }
        val a = FloatArray(dsd.size)
        val b = FloatArray(dsd.size)
        val n = DsdToPcm(1, DSD64, lsbFirst = false).process(dsd, dsd.size, a)
        DsdToPcm(1, DSD64, lsbFirst = true).process(reversed, reversed.size, b)
        for (i in 0 until n) assertEquals(a[i], b[i], 1e-6f)
    }

    @Test
    fun `decoding keeps well ahead of real time`() {
        val seconds = 2.0
        val dsd = modulate(frequency = 1000.0, amplitude = 0.5, samples = (DSD64 * seconds).toInt())
        // Stereo, as music is: the same channel twice.
        val stereo = ByteArray(dsd.size * 2) { dsd[it / 2] }
        val converter = DsdToPcm(channels = 2, dsdRate = DSD64, lsbFirst = false)
        val pcm = FloatArray(stereo.size)
        converter.process(stereo, dsd.size, pcm) // warm up the JIT
        val start = System.nanoTime()
        converter.process(stereo, dsd.size, pcm)
        val elapsed = (System.nanoTime() - start) / 1e9
        println("DSD64 stereo: ${"%.3f".format(elapsed)}s to decode ${seconds}s")
        assertTrue("took ${elapsed}s for ${seconds}s of audio", elapsed < seconds / 4)
    }

    @Test
    fun `a DSF file plays through the extractor, and seeks`() {
        val dsd = modulate(frequency = 1000.0, amplitude = 0.5, samples = DSD64 / 2)
        val file = dsf(channels = 2, left = dsd, right = dsd)
        val extractor = DsdExtractor()
        val input = input(file, 0)
        assertTrue(extractor.sniff(input))
        val output = FakeOutput()
        extractor.init(output)
        val pcm = readAll(extractor, input(file, 0), output)
        val format = output.track.format!!
        assertEquals(2, format.channelCount)
        assertEquals(88_200, format.sampleRate)
        assertEquals(C.ENCODING_PCM_FLOAT, format.pcmEncoding)
        assertEquals(500_000L, output.seekMap!!.durationUs)
        // One output frame for every 4 bytes of each channel.
        assertEquals(dsd.size / 4 * 2, pcm.size)

        // Seeking lands on a block, and the timestamps carry on from there.
        val point = output.seekMap!!.getSeekPoints(250_000).first
        assertTrue(point.timeUs in 200_000..250_000)
        output.track.times.clear()
        extractor.seek(point.position, point.timeUs)
        readAll(extractor, input(file, point.position), output)
        assertEquals(point.timeUs, output.track.times.first())
    }

    @Test
    fun `a DFF file plays through the extractor`() {
        val dsd = modulate(frequency = 1000.0, amplitude = 0.5, samples = DSD64 / 4)
        val file = dff(channels = 2, left = dsd, right = dsd)
        val extractor = DsdExtractor()
        assertTrue(extractor.sniff(input(file, 0)))
        val output = FakeOutput()
        extractor.init(output)
        val pcm = readAll(extractor, input(file, 0), output)
        assertEquals(88_200, output.track.format!!.sampleRate)
        assertEquals(dsd.size / 4 * 2, pcm.size)
        val steady = pcm.copyOfRange(4000, pcm.size)
        val amplitude = sqrt(steady.sumOf { (it * it).toDouble() } / steady.size) * sqrt(2.0)
        assertEquals(0.705, amplitude, 0.02)
    }

    // ---- Helpers ----

    /** A second-order sigma-delta modulator: [samples] 1-bit samples, MSB-first bytes. */
    private fun modulate(frequency: Double, amplitude: Double, samples: Int): ByteArray {
        val bytes = ByteArray(samples / 8)
        var i1 = 0.0
        var i2 = 0.0
        var y = 1.0
        for (n in 0 until bytes.size * 8) {
            val x = amplitude * sin(2 * PI * frequency * n / DSD64)
            i1 += x - y
            i2 += i1 - y
            y = if (i2 >= 0) 1.0 else -1.0
            if (y > 0) bytes[n / 8] = (bytes[n / 8].toInt() or (0x80 ushr (n % 8))).toByte()
        }
        return bytes
    }

    private fun reverse(b: Byte): Byte {
        var r = 0
        for (i in 0 until 8) if (b.toInt() and (1 shl i) != 0) r = r or (0x80 ushr i)
        return r.toByte()
    }

    /** A DSF file of [left] and [right] (MSB-first here, stored LSB-first as DSF does). */
    private fun dsf(channels: Int, left: ByteArray, right: ByteArray): ByteArray {
        val block = 4096
        val blocks = (left.size + block - 1) / block
        val data = ByteArrayOutputStream()
        for (b in 0 until blocks) {
            for (source in listOf(left, right).take(channels)) {
                for (i in 0 until block) {
                    val at = b * block + i
                    data.write(if (at < source.size) reverse(source[at]).toInt() else 0x69)
                }
            }
        }
        val le = ByteBuffer.allocate(28 + 52 + 12).order(ByteOrder.LITTLE_ENDIAN)
        le.put("DSD ".toByteArray()).putLong(28).putLong(0).putLong(0)
        le.put("fmt ".toByteArray()).putLong(52).putInt(1).putInt(0).putInt(2).putInt(channels)
            .putInt(DSD64).putInt(1).putLong(left.size * 8L).putInt(block).putInt(0)
        le.put("data".toByteArray()).putLong(12L + data.size())
        return le.array() + data.toByteArray()
    }

    /** An uncompressed DSDIFF file of [left] and [right]. */
    private fun dff(channels: Int, left: ByteArray, right: ByteArray): ByteArray {
        val samples = ByteArray(left.size * channels) { if (it % 2 == 0) left[it / 2] else right[it / 2] }
        val prop = ByteBuffer.allocate(4 + 12 + 4 + 12 + 2 + 8 + 12 + 4).order(ByteOrder.BIG_ENDIAN)
        prop.put("SND ".toByteArray())
        prop.put("FS  ".toByteArray()).putLong(4).putInt(DSD64)
        prop.put("CHNL".toByteArray()).putLong(10).putShort(channels.toShort()).put("SLFTSRGT".toByteArray())
        prop.put("CMPR".toByteArray()).putLong(4).put("DSD ".toByteArray())
        val body = ByteBuffer.allocate(4 + 12 + 4 + 12 + prop.capacity() + 12).order(ByteOrder.BIG_ENDIAN)
        body.put("DSD ".toByteArray())
        body.put("FVER".toByteArray()).putLong(4).putInt(0x01050000)
        body.put("PROP".toByteArray()).putLong(prop.capacity().toLong()).put(prop.array())
        body.put("DSD ".toByteArray()).putLong(samples.size.toLong())
        val head = ByteBuffer.allocate(12).order(ByteOrder.BIG_ENDIAN)
        head.put("FRM8".toByteArray()).putLong((body.capacity() + samples.size).toLong())
        return head.array() + body.array() + samples
    }

    private fun input(file: ByteArray, from: Long): DefaultExtractorInput {
        var at = from.toInt()
        val reader = DataReader { buffer, offset, length ->
            if (at >= file.size) return@DataReader C.RESULT_END_OF_INPUT
            val n = minOf(length, file.size - at)
            System.arraycopy(file, at, buffer, offset, n)
            at += n
            n
        }
        return DefaultExtractorInput(reader, from, file.size.toLong())
    }

    private fun readAll(extractor: Extractor, input: DefaultExtractorInput, output: FakeOutput): FloatArray {
        output.track.data.reset()
        val holder = PositionHolder()
        while (extractor.read(input, holder) == Extractor.RESULT_CONTINUE) Unit
        val bytes = output.track.data.toByteArray()
        val floats = FloatArray(bytes.size / 4)
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer().get(floats)
        return floats
    }

    private class FakeOutput : ExtractorOutput {
        val track = FakeTrack()
        var seekMap: SeekMap? = null
        override fun track(id: Int, type: Int): TrackOutput = track
        override fun endTracks() {}
        override fun seekMap(seekMap: SeekMap) {
            this.seekMap = seekMap
        }
    }

    private class FakeTrack : TrackOutput {
        var format: Format? = null
        val data = ByteArrayOutputStream()
        val times = mutableListOf<Long>()
        override fun format(format: Format) {
            this.format = format
        }
        override fun sampleData(input: DataReader, length: Int, allowEndOfInput: Boolean, sampleDataPart: Int): Int =
            throw UnsupportedOperationException()
        override fun sampleData(data: ParsableByteArray, length: Int, sampleDataPart: Int) {
            val bytes = ByteArray(length)
            data.readBytes(bytes, 0, length)
            this.data.write(bytes)
        }
        override fun sampleMetadata(timeUs: Long, flags: Int, size: Int, offset: Int, cryptoData: TrackOutput.CryptoData?) {
            times += timeUs
        }
    }
}
