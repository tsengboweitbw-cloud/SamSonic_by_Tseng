package com.example.samsonic.playback.usb

/** JNI entry points of the user-space USB DAC driver (`cpp/`). */
object NativeUsb {
    init {
        System.loadLibrary("samsonic_usb")
    }

    /** The version of the libusb the driver is built with. */
    external fun libusbVersion(): String

    /** Wraps [fd] with libusb and describes the device (ids, configurations), or an error. Doesn't close [fd]. */
    external fun probe(fd: Int): String
}
