package com.example.samsonic.ui.player

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class NowPlayingLayoutTest {
    @Test
    fun aTallTabletKeepsOneColumn() {
        assertEquals(NowPlayingColumns.One, nowPlayingColumnsFor(800.dp, 1280.dp, current = null))
    }

    @Test
    fun aWideTabletGetsTwoColumns() {
        assertEquals(NowPlayingColumns.Two, nowPlayingColumnsFor(1280.dp, 800.dp, current = null))
    }

    @Test
    fun aFoldsInnerScreenGetsWhicheverGivesTheLargerCover() {
        // Z Fold 8, the wide way round: 372dp of cover beside the controls, 104 above them.
        assertEquals(NowPlayingColumns.Two, nowPlayingColumnsFor(933.dp, 704.dp, current = null))
        // The narrow way round: 333dp above the controls, 304 beside them.
        assertEquals(NowPlayingColumns.One, nowPlayingColumnsFor(704.dp, 933.dp, current = null))
    }

    @Test
    fun theLayoutShowingHoldsUntilTheOtherIsClearlyBetter() {
        // A window where two columns give a cover only a little larger than one does.
        val width = 700.dp
        val height = 900.dp
        val one = oneColumnCover(width, height)
        val two = twoColumnCover(width, height)
        assert(two > one && two < one * 1.08f) { "the case should sit inside the margin: one=$one two=$two" }
        assertEquals(NowPlayingColumns.One, nowPlayingColumnsFor(width, height, current = NowPlayingColumns.One))
        assertEquals(NowPlayingColumns.Two, nowPlayingColumnsFor(width, height, current = NowPlayingColumns.Two))
    }
}
