package com.example.samsonic.playback

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.sqrt

class ResamplerTest {
    private fun sine(rate: Int, frequency: Double, amplitude: Double, frames: Int) =
        FloatArray(frames) { (amplitude * sin(2 * PI * frequency * it / rate)).toFloat() }

    private fun rms(samples: FloatArray) = sqrt(samples.sumOf { (it * it).toDouble() } / samples.size)

    private fun frequencyOf(samples: FloatArray, rate: Int): Double {
        val crossings = samples.toList().zipWithNext().count { (a, b) -> a < 0 && b >= 0 }
        return crossings / (samples.size.toDouble() / rate)
    }

    @Test
    fun `44·1 to 48 kHz keeps a tone's pitch and level, at the exact ratio`() {
        val input = sine(44_100, 1000.0, 0.5, 44_100)
        val resampler = Resampler(44_100, 48_000, inChannels = 1, outChannels = 1)
        val written = resampler.process(input, input.size)
        assertEquals(48_000.0, written.toDouble(), 2.0)
        // Past the filter's start-up.
        val steady = resampler.output.copyOfRange(200, written - 200)
        assertEquals(0.5 / sqrt(2.0), rms(steady), 0.005)
        assertEquals(1000.0, frequencyOf(steady, 48_000), 3.0)
    }

    @Test
    fun `a tone above the new Nyquist is filtered out going down`() {
        val input = sine(96_000, 30_000.0, 0.5, 96_000)
        val resampler = Resampler(96_000, 44_100, inChannels = 1, outChannels = 1)
        val written = resampler.process(input, input.size)
        val steady = resampler.output.copyOfRange(200, written - 200)
        assertTrue(rms(steady) < 0.001)
    }

    @Test
    fun `feeding it in pieces gives the same as all at once`() {
        val input = sine(32_000, 440.0, 0.3, 9_000).let { mono ->
            FloatArray(mono.size * 2) { mono[it / 2] * if (it % 2 == 0) 1f else -1f }
        }
        val whole = Resampler(32_000, 44_100, inChannels = 2, outChannels = 2)
        val all = whole.output.let { whole.process(input, input.size / 2) }.let { whole.output.copyOf(it * 2) }

        val pieces = Resampler(32_000, 44_100, inChannels = 2, outChannels = 2)
        val joined = ArrayList<Float>()
        var at = 0
        for (size in generateSequence(1) { it * 3 % 997 + 1 }) {
            if (at >= input.size / 2) break
            val frames = minOf(size, input.size / 2 - at)
            val written = pieces.process(input.copyOfRange(at * 2, (at + frames) * 2), frames)
            for (i in 0 until written * 2) joined += pieces.output[i]
            at += frames
        }
        assertArrayEquals(all, joined.toFloatArray(), 1e-6f)
    }

    @Test
    fun `mono to stereo at the same rate copies every sample exactly`() {
        val input = FloatArray(1000) { (it - 500) / 32_768f }
        val resampler = Resampler(44_100, 44_100, inChannels = 1, outChannels = 2)
        assertEquals(1000, resampler.process(input, input.size))
        for (i in input.indices) {
            assertEquals(input[i], resampler.output[2 * i], 0f)
            assertEquals(input[i], resampler.output[2 * i + 1], 0f)
        }
    }

    @Test
    fun `a reset starts it again from silence`() {
        val resampler = Resampler(44_100, 88_200, inChannels = 1, outChannels = 1)
        resampler.process(FloatArray(500) { 0.9f }, 500)
        resampler.reset()
        val written = resampler.process(FloatArray(500), 500)
        assertTrue((0 until written).all { abs(resampler.output[it]) < 1e-6f })
    }

    @Test
    fun `odd pairs of rates that would need huge filters are turned down`() {
        assertTrue(Resampler.canResample(44_100, 48_000))
        assertTrue(Resampler.canResample(32_000, 44_100))
        assertFalse(Resampler.canResample(44_101, 48_000))
    }
}
