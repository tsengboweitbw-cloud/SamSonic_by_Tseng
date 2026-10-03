package com.example.samsonic.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownmixTest {

    @Test
    fun onlySurroundCountsAreFolded() {
        assertFalse(Downmix.canDownmix(1))
        assertFalse(Downmix.canDownmix(2))
        for (channels in 3..8) assertTrue(Downmix.canDownmix(channels))
        assertFalse(Downmix.canDownmix(9))
    }

    @Test
    fun fullScaleInEveryChannelStaysWithinFullScale() {
        for (channels in 3..8) {
            val out = FloatArray(2)
            Downmix.mix(Downmix.gains(channels), channels, FloatArray(channels) { 1f }, out)
            assertTrue("$channels channels: ${out[0]}", out[0] <= 1.0001f && out[1] <= 1.0001f)
        }
    }

    @Test
    fun fiveOneKeepsSidesApartAndDropsTheLfe() {
        val gains = Downmix.gains(6)
        val out = FloatArray(2)
        // Front left only: all on the left.
        Downmix.mix(gains, 6, floatArrayOf(1f, 0f, 0f, 0f, 0f, 0f), out)
        assertTrue(out[0] > 0f)
        assertEquals(0f, out[1], 0f)
        // LFE only: silent.
        Downmix.mix(gains, 6, floatArrayOf(0f, 0f, 0f, 1f, 0f, 0f), out)
        assertEquals(0f, out[0], 0f)
        assertEquals(0f, out[1], 0f)
        // Centre: equally on both sides.
        Downmix.mix(gains, 6, floatArrayOf(0f, 0f, 1f, 0f, 0f, 0f), out)
        assertTrue(out[0] > 0f)
        assertEquals(out[0], out[1], 0f)
    }
}
