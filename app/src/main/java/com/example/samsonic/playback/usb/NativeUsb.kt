package com.example.samsonic.playback.usb

/** JNI entry points of the user-space USB DAC driver (`cpp/`). */
object NativeUsb {
    init {
        System.loadLibrary("samsonic_usb")
    }

    /** The version of the libusb the driver is built with. */
    external fun libusbVersion(): String

    /** Wraps [fd] with libusb and lists the DAC's ids, speed and audio formats, or says what failed. Doesn't close [fd]. */
    external fun describe(fd: Int): String

    /**
     * Plays a two-second sine, 30 dB down, to the DAC behind [fd] at [rate] Hz. Returns "playing: ..." or what failed.
     * [stopTestTone] must be called before the connection is closed.
     */
    external fun startTestTone(fd: Int, rate: Int): String

    external fun stopTestTone()
}
