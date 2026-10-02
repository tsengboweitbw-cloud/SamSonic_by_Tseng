package com.example.samsonic.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TabFitTest {
    private fun selectedWidth(width: Int, count: Int, extra: Float) = width * (1 + extra) / (count + extra)

    @Test
    fun roomyBarKeepsTheUsualWidening() {
        assertEquals(0.8f, fitExtraWeight(1000, 4, 200f, 48f, 0.8f), 0.0001f)
    }

    @Test
    fun narrowBarWidensTheSelectedTabToFitItsLabel() {
        val extra = fitExtraWeight(600, 4, 260f, 48f, 0.8f)
        assertTrue(extra > 0.8f)
        assertEquals(260f, selectedWidth(600, 4, extra), 0.5f)
    }

    @Test
    fun theOtherTabsKeepRoomForTheirIcons() {
        val extra = fitExtraWeight(400, 4, 380f, 48f, 0.8f)
        val others = 400 / (4 + extra)
        assertTrue("others were $others", others >= 48f - 0.01f)
    }
}
