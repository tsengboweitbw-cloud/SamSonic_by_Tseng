package com.example.samsonic.data.offline

import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Test

class OfflineSaveFailureTest {
    private fun answered(code: Int) = saveFailureFor(code, listOf("Response code: $code"))

    @Test
    fun aSongTheServerSaysIsMissingIsSkipped() {
        assertEquals(SaveFailure.SkipSong, answered(404))
        assertEquals(SaveFailure.SkipSong, answered(403))
    }

    @Test
    fun aBusyOrSlowServerStopsTheRunInsteadOfSkipping() {
        assertEquals(SaveFailure.Stop, answered(429))
        assertEquals(SaveFailure.Stop, answered(408))
        assertEquals(SaveFailure.Stop, answered(500))
    }

    @Test
    fun aDroppedConnectionStopsTheRun() {
        assertEquals(SaveFailure.Stop, saveFailureOf(IOException("Connection reset")))
        assertEquals(SaveFailure.Stop, saveFailureOf(IOException()))
    }

    @Test
    fun aFullPhoneIsToldApartEvenWhenWrapped() {
        val full = IOException("write failed: ENOSPC (No space left on device)")
        assertEquals(SaveFailure.OutOfSpace, saveFailureOf(full))
        assertEquals(SaveFailure.OutOfSpace, saveFailureOf(RuntimeException("cache", full)))
    }

    @Test
    fun noRoomOutranksAnAnswerFromTheServer() {
        assertEquals(SaveFailure.OutOfSpace, saveFailureFor(404, listOf("No space left on device")))
    }
}
