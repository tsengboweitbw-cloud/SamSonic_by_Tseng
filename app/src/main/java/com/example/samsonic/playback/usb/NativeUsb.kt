package com.example.samsonic.playback.usb

import java.nio.ByteBuffer

/**
 * JNI entry points of the user-space USB DAC driver (`cpp/`). One DAC is open at a time. Call
 * everything from the player's thread, except [libusbVersion].
 */
internal object NativeUsb {
    init {
        System.loadLibrary("samsonic_usb")
    }

    /** The version of the libusb the driver is built with. */
    external fun libusbVersion(): String

    /** Wraps the usbfs descriptor [fd] and reads the DAC; "" on success, otherwise what went wrong. Doesn't close [fd]. */
    external fun open(fd: Int): String

    /** The DAC's alt settings that play audio, five ints each: index, channels, bytes per sample, bits, 1 if PCM. */
    external fun formats(): IntArray

    /** The common rates alt setting [index] takes. */
    external fun rates(index: Int): IntArray

    /** Starts streaming alt setting [index] at [rate]; "" on success, otherwise what went wrong. */
    external fun start(index: Int, rate: Int): String

    /** Queues up to [length] bytes of whole frames from the direct [buffer]; returns the bytes taken. */
    external fun write(buffer: ByteBuffer, offset: Int, length: Int): Int

    external fun freeBytes(): Int
    external fun bufferedBytes(): Int

    /** Real frames played since [start], less those dropped by [flush]. */
    external fun playedFrames(): Long

    /** While paused the DAC gets silence and queued audio waits. */
    external fun setPaused(paused: Boolean)

    /** What the DAC gets when there's nothing to play: [SILENCE_ZERO] (PCM), [SILENCE_DSD] or [SILENCE_DOP]. */
    external fun setSilence(mode: Int)

    const val SILENCE_ZERO = 0
    const val SILENCE_DSD = 1
    const val SILENCE_DOP = 2

    /** Drops the queued audio; returns once the driver has. */
    external fun flush()

    /** Stops streaming but keeps the DAC open, for the next format. */
    external fun stop()

    /** Stops streaming and lets go of the DAC; the caller closes the connection after. */
    external fun close()
}
