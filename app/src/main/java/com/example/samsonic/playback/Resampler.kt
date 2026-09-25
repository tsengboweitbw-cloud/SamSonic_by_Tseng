package com.example.samsonic.playback

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

// Filter taps for each output sample: a transition band of ~2.7kHz from 44.1kHz.
private const val TAPS = 64

// The passband, as a share of the lower rate's Nyquist: 91% is 20kHz from 44.1kHz.
private const val ROLLOFF = 0.91

/** Past this many coefficients (an odd pair of rates), it isn't worth it. */
private const val MAX_COEFFICIENTS = 1 shl 20

/**
 * Changes float PCM from [inRate] to [outRate] for exclusive mode, when the DAC doesn't take
 * the song's own rate: a polyphase windowed-sinc low-pass at the exact ratio, so it never
 * drifts. It also takes mono to stereo ([outChannels] 2 from [inChannels] 1), by copying.
 * At the same rate it only copies, leaving every sample as it was.
 */
internal class Resampler(
    val inRate: Int,
    val outRate: Int,
    val inChannels: Int,
    val outChannels: Int,
) {
    // The ratio in lowest terms: [up] output samples for every [down] input samples.
    private val up: Int
    private val down: Int

    init {
        val common = gcd(inRate, outRate)
        up = outRate / common
        down = inRate / common
    }

    private val copyOnly = up == down
    private val coefficients = if (copyOnly) FloatArray(0) else design(up, inRate, outRate)

    /** The last [process]'s output, [outChannels] interleaved. */
    var output = FloatArray(0)
        private set

    // Input frames still needed: the filter's reach back, then what's come in since.
    private var history = FloatArray(0)
    private var held = 0
    // The absolute input frame history starts at, and the next output frame.
    private var base = 0L
    private var next = 0L

    init {
        reset()
    }

    /** Forgets what came before, for a start or a seek: the filter starts from silence. */
    fun reset() {
        held = if (copyOnly) 0 else TAPS - 1
        base = -held.toLong()
        next = 0
        if (history.size < held * inChannels) history = FloatArray(held * inChannels)
        history.fill(0f, 0, held * inChannels)
    }

    /** Resamples [frames] frames of [input] into [output]; returns how many frames it wrote. */
    fun process(input: FloatArray, frames: Int): Int {
        if (copyOnly) return copy(input, frames)
        append(input, frames)
        val most = (frames.toLong() * up / down + 2).toInt()
        if (output.size < most * outChannels) output = FloatArray(most * outChannels)
        var written = 0
        while (true) {
            val position = next * down
            val newest = position / up - base
            if (newest >= held) break
            val row = (position % up).toInt() * TAPS
            for (channel in 0 until outChannels) {
                val from = if (inChannels == 1) 0 else channel
                var sum = 0f
                var at = newest.toInt() * inChannels + from
                for (tap in 0 until TAPS) {
                    sum += coefficients[row + tap] * history[at]
                    at -= inChannels
                }
                output[written * outChannels + channel] = sum
            }
            written++
            next++
        }
        dropUnneeded()
        return written
    }

    private fun copy(input: FloatArray, frames: Int): Int {
        if (output.size < frames * outChannels) output = FloatArray(frames * outChannels)
        for (frame in 0 until frames) {
            for (channel in 0 until outChannels) {
                output[frame * outChannels + channel] = input[frame * inChannels + if (inChannels == 1) 0 else channel]
            }
        }
        return frames
    }

    private fun append(input: FloatArray, frames: Int) {
        val needed = (held + frames) * inChannels
        if (history.size < needed) history = history.copyOf(needed * 2)
        System.arraycopy(input, 0, history, held * inChannels, frames * inChannels)
        held += frames
    }

    /** Lets go of input older than the next output frame's filter reaches. */
    private fun dropUnneeded() {
        val oldestNeeded = next * down / up - (TAPS - 1)
        val drop = (oldestNeeded - base).toInt().coerceIn(0, held)
        if (drop == 0) return
        System.arraycopy(history, drop * inChannels, history, 0, (held - drop) * inChannels)
        held -= drop
        base += drop
    }

    companion object {
        /** Whether resampling [inRate] to [outRate] is practical; odd pairs need huge filters. */
        fun canResample(inRate: Int, outRate: Int): Boolean =
            inRate == outRate || outRate / gcd(inRate, outRate).toLong() * TAPS <= MAX_COEFFICIENTS

        private fun gcd(a: Int, b: Int): Int = if (b == 0) a else gcd(b, a % b)

        /**
         * The low-pass, run at [inRate] × [up], split into [up] phases of [TAPS] taps: phase p,
         * tap j is the prototype's p + j·up, gained by [up] for the zeros upsampling puts in.
         */
        private fun design(up: Int, inRate: Int, outRate: Int): FloatArray {
            val length = TAPS * up
            val cutoff = 0.5 * min(inRate, outRate) * ROLLOFF / (inRate.toDouble() * up)
            val center = (length - 1) / 2.0
            val prototype = DoubleArray(length) { n ->
                val x = n - center
                val sinc = if (x == 0.0) 2 * cutoff else sin(2 * PI * cutoff * x) / (PI * x)
                val t = 2 * PI * n / (length - 1)
                sinc * (0.35875 - 0.48829 * cos(t) + 0.14128 * cos(2 * t) - 0.01168 * cos(3 * t))
            }
            val gain = up / prototype.sum()
            return FloatArray(length) { i ->
                val phase = i / TAPS
                val tap = i % TAPS
                (prototype[phase + tap * up] * gain).toFloat()
            }
        }
    }
}
