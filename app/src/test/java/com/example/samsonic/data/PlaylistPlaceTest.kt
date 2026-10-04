package com.example.samsonic.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaylistPlaceTest {
    @Test
    fun theExpectedPlaceStandsWhenTheSongIsStillThere() {
        assertEquals(1, placeToRemove(listOf("a", "b", "c"), "b", 1))
    }

    @Test
    fun aSongThatMovedIsFoundWhereItIsNow() {
        assertEquals(0, placeToRemove(listOf("b", "c"), "b", 1))
    }

    @Test
    fun ofTwoCopiesTheNearestIsTaken() {
        assertEquals(3, placeToRemove(listOf("a", "x", "b", "x"), "x", 4))
        assertEquals(1, placeToRemove(listOf("a", "x", "b", "x"), "x", 0))
    }

    @Test
    fun aSongThatIsGoneHasNoPlace() {
        assertNull(placeToRemove(listOf("a", "c"), "b", 1))
    }
}
