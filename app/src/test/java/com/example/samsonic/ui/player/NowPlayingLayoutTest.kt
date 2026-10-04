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
    fun aNearSquareOrTallWindowIsAlwaysOneColumn() {
        // Two columns would leave both sides narrow, whatever layout is showing.
        assertEquals(NowPlayingColumns.One, nowPlayingColumnsFor(700.dp, 900.dp, current = NowPlayingColumns.Two))
        assertEquals(NowPlayingColumns.One, nowPlayingColumnsFor(1000.dp, 900.dp, current = NowPlayingColumns.Two))
    }

    @Test
    fun theLayoutShowingHoldsUntilTheOtherIsClearlyBetter() {
        // 933 by 704: two columns' cover is far larger, so the switch happens from either.
        assertEquals(NowPlayingColumns.Two, nowPlayingColumnsFor(933.dp, 704.dp, current = NowPlayingColumns.One))
        // Wide and short, but one column's cover is still the larger by more than the margin.
        assertEquals(NowPlayingColumns.Two, nowPlayingColumnsFor(1280.dp, 800.dp, current = NowPlayingColumns.Two))
    }
}
