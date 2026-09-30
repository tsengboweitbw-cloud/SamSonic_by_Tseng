package com.example.samsonic.ui.common

import androidx.compose.ui.unit.dp
import com.example.samsonic.data.GridForm
import org.junit.Assert.assertEquals
import org.junit.Test

class LayoutClassTest {
    @Test
    fun phonesAndCoverScreensGetThePhoneLayout() {
        assertEquals(LayoutClass.Phone, layoutClassFor(412.dp, 915.dp)) // phone
        assertEquals(LayoutClass.Phone, layoutClassFor(475.dp, 751.dp)) // Z Fold 8 cover
        assertEquals(LayoutClass.Phone, layoutClassFor(411.dp, 960.dp)) // Z Fold 8 Ultra cover
    }

    @Test
    fun aPhoneOnItsSideStaysAPhone() {
        assertEquals(LayoutClass.Phone, layoutClassFor(915.dp, 412.dp))
    }

    @Test
    fun foldInnerScreensKeepTwoPanesEitherWayRound() {
        assertEquals(LayoutClass.Wide, layoutClassFor(704.dp, 933.dp)) // Z Fold 8, narrow way
        assertEquals(LayoutClass.Wide, layoutClassFor(933.dp, 704.dp)) // Z Fold 8, wide way
        assertEquals(LayoutClass.Wide, layoutClassFor(859.dp, 954.dp)) // Z Fold 8 Ultra
    }

    @Test
    fun tabletsGetTwoPanesEitherWayRound() {
        assertEquals(LayoutClass.Wide, layoutClassFor(800.dp, 1280.dp))
        assertEquals(LayoutClass.Wide, layoutClassFor(1280.dp, 800.dp))
    }

    @Test
    fun between600And700ItsOnePane() {
        assertEquals(LayoutClass.Medium, layoutClassFor(600.dp, 900.dp))
        assertEquals(LayoutClass.Medium, layoutClassFor(699.dp, 900.dp))
        // Wide enough, but too short for two panes.
        assertEquals(LayoutClass.Medium, layoutClassFor(900.dp, 550.dp))
    }

    @Test
    fun gridsCountColumnsByTheKindOfScreen() {
        assertEquals(GridForm.PHONE, gridFormFor(412.dp, 915.dp, foldable = false))
        assertEquals(GridForm.PHONE_LANDSCAPE, gridFormFor(915.dp, 412.dp, foldable = false))
        // A foldable shut is a phone; open, a foldable either way round.
        assertEquals(GridForm.PHONE, gridFormFor(475.dp, 751.dp, foldable = true))
        assertEquals(GridForm.FOLDABLE_LANDSCAPE, gridFormFor(933.dp, 704.dp, foldable = true))
        assertEquals(GridForm.FOLDABLE, gridFormFor(704.dp, 933.dp, foldable = true))
        assertEquals(GridForm.TABLET_LANDSCAPE, gridFormFor(1280.dp, 800.dp, foldable = false))
        assertEquals(GridForm.TABLET_PORTRAIT, gridFormFor(800.dp, 1280.dp, foldable = false))
    }

    @Test
    fun theDefaultColumnCountsAreAsAsked() {
        assertEquals(4, GridForm.FOLDABLE.defaultColumns)
        assertEquals(6, GridForm.TABLET_LANDSCAPE.defaultColumns)
        assertEquals(4, GridForm.TABLET_PORTRAIT.defaultColumns)
        GridForm.entries.forEach { assertEquals(6, it.maxColumns) }
    }
}
