package com.example.samsonic.playback.usb

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Cuts the encoder's delay off the start of a song and its padding off the end, so consecutive
 * songs join without a gap. The end isn't known until it comes, so the last [endBytes] are held
 * back; [endOfStream] drops them. Media3 does this inside its own sink, out of reach here.
 */
internal class GaplessTrimmer {
    private var startBytes = 0
    private var endBytes = 0
    private var held = ByteArray(0)
    private var heldCount = 0
    private var out: ByteBuffer = ByteBuffer.allocateDirect(0).order(ByteOrder.nativeOrder())

    /** Starts a song: [startFrames] to cut now, [endFrames] to cut at the end, [frameBytes] to a frame. */
    fun configure(frameBytes: Int, startFrames: Int, endFrames: Int) {
        startBytes = startFrames * frameBytes
        endBytes = endFrames * frameBytes
        held = ByteArray(endBytes)
        heldCount = 0
    }

    /** After a seek: what was held is from the wrong place, and the start isn't cut again. */
    fun flush() {
        startBytes = 0
        heldCount = 0
    }

    /** Takes all of [input] and returns what can go on; valid until the next call. */
    fun process(input: ByteBuffer): ByteBuffer {
        val skip = minOf(startBytes, input.remaining())
        input.position(input.position() + skip)
        startBytes -= skip
        val incoming = input.remaining()
        if (endBytes == 0) return input.slice().also { input.position(input.limit()) }

        val total = heldCount + incoming
        val emit = maxOf(0, total - endBytes)
        if (out.capacity() < emit) out = ByteBuffer.allocateDirect(emit * 2).order(ByteOrder.nativeOrder())
        out.clear()
        // The held bytes come first, then the new ones; the last endBytes stay held.
        val fromHeld = minOf(emit, heldCount)
        out.put(held, 0, fromHeld)
        val fromInput = emit - fromHeld
        val view = input.slice()
        view.limit(fromInput)
        out.put(view)
        out.flip()
        // Held: what's left of the old held bytes, then the input's tail.
        val keepOld = heldCount - fromHeld
        System.arraycopy(held, fromHeld, held, 0, keepOld)
        val tail = incoming - fromInput
        input.position(input.position() + fromInput)
        input.get(held, keepOld, tail)
        heldCount = keepOld + tail
        return out
    }

    /** The song's end: the held padding is dropped. */
    fun endOfStream() {
        heldCount = 0
    }
}
