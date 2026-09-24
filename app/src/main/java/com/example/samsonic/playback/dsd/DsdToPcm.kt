package com.example.samsonic.playback.dsd

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

// The PCM rate DSD is brought down to: DSD64, 128, 256... all land on it exactly (the
// 48kHz family on 96kHz). Well past the audible band, and far cheaper than 176.4kHz.
private const val TARGET_RATE = 88_200.0

// Everything below passes; DSD's shaped noise, which climbs steeply above ~25kHz, is cut.
private const val CUTOFF_HZ = 36_000.0

// Filter length per output step, in bytes of DSD history: 16 bytes (128 taps) for each byte
// the output advances. For DSD64 that's 512 taps, a transition band of ~22kHz.
private const val TAP_BYTES_PER_STEP = 16

// DSD's 0dB is 50% modulation, which comes out of the filter at half of full scale; +3dB
// brings that to 0.7 and leaves room for the 70% peaks the format allows before clipping.
private const val GAIN = 1.41f

// A byte of DSD silence (as many 1s as 0s): what the filter starts from, so the first
// samples after a start or seek fade in from nothing rather than a full-scale step.
private const val SILENCE = 0x69

/**
 * Turns DSD (1-bit, at 2.8224MHz and up) into float PCM at [outputRate], one channel set
 * at a time: a windowed-sinc low-pass run as table lookups a byte (8 samples) at a time,
 * decimating as it goes. [lsbFirst] is for DSF, whose bytes hold their earliest bit last.
 */
internal class DsdToPcm(private val channels: Int, dsdRate: Int, private val lsbFirst: Boolean) {
    /** Bytes of each channel's DSD per output sample: the decimation, in bytes. */
    val bytesPerOutput: Int = max(1, (dsdRate / 8.0 / TARGET_RATE).roundToInt())

    val outputRate: Int = dsdRate / 8 / bytesPerOutput

    private val tapBytes = TAP_BYTES_PER_STEP * bytesPerOutput
    private val table = filterTable(dsdRate, tapBytes)

    // Each channel's last [tapBytes] bytes, twice over, so the window is always one straight run.
    private val history = Array(channels) { IntArray(tapBytes * 2) }
    private var position = 0
    private var phase = 0

    init {
        reset()
    }

    /** Forgets what came before, for a start or a seek. */
    fun reset() {
        // The history holds bytes already put in MSB-first order, so silence as it reads there.
        for (h in history) h.fill(SILENCE)
        position = 0
        phase = 0
    }

    /**
     * Filters [frames] frames of [input] (one byte per channel each, channels interleaved)
     * into [out] as interleaved float frames, and returns how many it wrote: one for each
     * [bytesPerOutput] frames in.
     */
    fun process(input: ByteArray, frames: Int, out: FloatArray): Int {
        var written = 0
        var at = 0
        for (frame in 0 until frames) {
            for (channel in 0 until channels) {
                var byte = input[at++].toInt() and 0xFF
                if (lsbFirst) byte = REVERSED[byte]
                val h = history[channel]
                h[position] = byte
                h[position + tapBytes] = byte
            }
            if (++phase == bytesPerOutput) {
                phase = 0
                val base = written * channels
                for (channel in 0 until channels) {
                    val h = history[channel]
                    var sum = 0f
                    // Newest byte first: it pairs with the table's first 256 entries.
                    var index = position + tapBytes
                    var row = 0
                    repeat(tapBytes) {
                        sum += table[row + h[index]]
                        index--
                        row += 256
                    }
                    out[base + channel] = (sum * GAIN).coerceIn(-1f, 1f)
                }
                written++
            }
            if (++position == tapBytes) position = 0
        }
        return written
    }

    companion object {
        /** Each byte value's bits in reverse order. */
        private val REVERSED = IntArray(256) { b ->
            var r = 0
            for (i in 0 until 8) if (b and (1 shl i) != 0) r = r or (0x80 ushr i)
            r
        }

        private val tables = HashMap<Pair<Int, Int>, FloatArray>()

        /**
         * The filter as 256-entry tables, one per byte of history (newest first): entry b is
         * what that byte's 8 samples add to the output, each 1 bit counting +1 and each 0
         * bit -1, times its tap. Built once per rate, as the same few are used over and over.
         */
        @Synchronized
        private fun filterTable(dsdRate: Int, tapBytes: Int): FloatArray = tables.getOrPut(dsdRate to tapBytes) {
            val taps = lowPass(tapBytes * 8, CUTOFF_HZ / dsdRate)
            val table = FloatArray(tapBytes * 256)
            for (k in 0 until tapBytes) {
                for (b in 0 until 256) {
                    var sum = 0.0
                    // Bit 7 is the byte's earliest sample, bit 0 its newest (tap 8k).
                    for (bit in 0 until 8) {
                        val tap = taps[8 * k + bit]
                        sum += if ((b shr bit) and 1 == 1) tap else -tap
                    }
                    table[k * 256 + b] = sum.toFloat()
                }
            }
            table
        }

        /** A windowed-sinc low-pass of [length] taps cutting off at [cutoff] cycles per sample, summing to 1. */
        private fun lowPass(length: Int, cutoff: Double): DoubleArray {
            val center = (length - 1) / 2.0
            val taps = DoubleArray(length) { n ->
                val x = n - center
                val sinc = if (x == 0.0) 2 * cutoff else sin(2 * PI * cutoff * x) / (PI * x)
                sinc * blackmanHarris(n, length)
            }
            val total = taps.sum()
            for (n in taps.indices) taps[n] /= total
            return taps
        }

        private fun blackmanHarris(n: Int, length: Int): Double {
            val t = 2 * PI * n / (length - 1)
            return 0.35875 - 0.48829 * cos(t) + 0.14128 * cos(2 * t) - 0.01168 * cos(3 * t)
        }
    }
}
