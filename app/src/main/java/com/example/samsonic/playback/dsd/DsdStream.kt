package com.example.samsonic.playback.dsd

/**
 * How a DSD file goes to the DAC when it isn't filtered to PCM. The extractor hands the
 * player 16 of each channel's DSD bits per float sample either way (see [DsdPacker]), and
 * puts this on the track's Format as its customData, so the output end knows what the
 * "PCM" really is: played through Android's mixer it would be loud noise.
 */
sealed interface DsdStream {
    val dsdRate: Int

    /** DSD64, DSD128... */
    val multiple: Int get() = dsdRate / 44_100

    /** DSD over PCM: each sample a 0x05/0xFA marker over the 16 bits, as 24-bit PCM at [dsdRate] / 16. */
    data class Dop(override val dsdRate: Int) : DsdStream

    /**
     * Raw DSD as Android's ENCODING_DSD, at the [mixerRate] Android offers for it, which
     * gives [bytesPerFrame] bytes of each channel per frame (1 or 2).
     */
    data class Native(override val dsdRate: Int, val mixerRate: Int, val bytesPerFrame: Int) : DsdStream
}
