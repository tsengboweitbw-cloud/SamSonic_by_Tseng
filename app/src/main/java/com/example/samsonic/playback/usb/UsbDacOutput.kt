package com.example.samsonic.playback.usb

import android.util.Log
import com.example.samsonic.playback.Resampler

/** How a song is fitted to the DAC: the alt setting it plays on, and the rate it plays at. */
internal data class UsbPlan(val altIndex: Int, val subslotBytes: Int, val rate: Int, val channels: Int)

/**
 * A DAC the driver has open: what it takes, and how to fit a song to it. Closing it hands the
 * DAC back to Android.
 */
internal class UsbDacOutput private constructor(private val connection: UsbDacConnection) : AutoCloseable {
    /** The PCM alt settings with two channels, which is what the player plays. */
    private class Alt(val index: Int, val subslotBytes: Int, val bits: Int, val rates: IntArray)

    private val alts: List<Alt>

    init {
        val flat = NativeUsb.formats()
        alts = (flat.indices step 5)
            .filter { flat[it + 4] == 1 && flat[it + 1] == 2 }
            .map { Alt(flat[it], flat[it + 2], flat[it + 3], NativeUsb.rates(flat[it])) }
    }

    /**
     * The format to play a song of [inRate], [sourceBits] bits (16, 24 or 32) on, or null if the
     * DAC can't be fitted. The narrowest sample size that holds the song, and its own rate if the
     * DAC takes it: otherwise the nearest higher one of the same family (44.1 or 48 kHz), which
     * the song is resampled to.
     */
    fun planFor(inRate: Int, sourceBits: Int): UsbPlan? {
        val holding = alts.filter { it.subslotBytes * 8 >= sourceBits && it.rates.isNotEmpty() }
        val alt = holding.minByOrNull { it.subslotBytes } ?: alts.filter { it.rates.isNotEmpty() }.maxByOrNull { it.subslotBytes }
        ?: return null
        val rate = chooseRate(alt.rates, inRate) ?: return null
        return UsbPlan(alt.index, alt.subslotBytes, rate, channels = 2)
    }

    fun start(plan: UsbPlan): String = NativeUsb.start(plan.altIndex, plan.rate)

    override fun close() {
        NativeUsb.close()
        connection.close()
    }

    companion object {
        private const val TAG = "UsbDacOutput"

        /** Opens [connection]'s DAC for playing, or null (and closes [connection]) if the driver can't. */
        fun open(connection: UsbDacConnection): UsbDacOutput? {
            val error = NativeUsb.open(connection.fd)
            if (error.isNotEmpty()) {
                Log.w(TAG, "Couldn't open the DAC: $error")
                connection.close()
                return null
            }
            return UsbDacOutput(connection)
        }

        internal fun chooseRate(supported: IntArray, inRate: Int): Int? {
            if (inRate in supported) return inRate
            val usable = supported.filter { Resampler.canResample(inRate, it) }
            val family = if (inRate % 44_100 == 0) 44_100 else 48_000
            return usable.filter { it >= inRate && it % family == 0 }.minOrNull()
                ?: usable.filter { it >= inRate }.minOrNull()
                ?: usable.maxOrNull()
        }
    }
}
