package com.example.samsonic.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamingBufferTest {
    private val full = BufferPlan(minMs = 60_000, maxMs = 180_000, bytes = 48 * 1024 * 1024)

    @Test
    fun aFlagshipKeepsTheFullBuffer() {
        // The S9280 reports a 256 MB heap.
        assertEquals(full, bufferPlanFor(memoryClassMb = 256, lowRam = false))
        assertEquals(full, bufferPlanFor(memoryClassMb = 512, lowRam = false))
    }

    @Test
    fun aMidRangePhoneHoldsLess() {
        val plan = bufferPlanFor(memoryClassMb = 192, lowRam = false)
        assertEquals(120_000, plan.maxMs)
        assertEquals(32 * 1024 * 1024, plan.bytes)
    }

    @Test
    fun aLowMemoryPhoneHoldsLeast() {
        val small = bufferPlanFor(memoryClassMb = 128, lowRam = false)
        assertEquals(small, bufferPlanFor(memoryClassMb = 512, lowRam = true))
        assertEquals(16 * 1024 * 1024, small.bytes)
    }

    @Test
    fun everyPlanKeepsMinUnderMax() {
        listOf(64, 128, 192, 256, 512).forEach { mb ->
            val plan = bufferPlanFor(mb, lowRam = false)
            assertTrue(plan.minMs < plan.maxMs)
        }
    }
}
