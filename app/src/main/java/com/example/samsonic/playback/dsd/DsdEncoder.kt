package com.example.samsonic.playback.dsd

/**
 * Turns DSD, as [DsdExtractor] reads it (one byte per channel per frame, channels
 * interleaved), into the float samples it hands the player: filtered to PCM ([DsdToPcm])
 * or packed untouched for DoP or native DSD ([DsdPacker]).
 */
internal interface DsdEncoder {
    /** Bytes of each channel's DSD per output sample. */
    val bytesPerOutput: Int

    val outputRate: Int

    /** Forgets what came before, for a start or a seek. */
    fun reset()

    /** Turns [frames] frames of [input] into interleaved float frames in [out]; returns how many. */
    fun process(input: ByteArray, frames: Int, out: FloatArray): Int
}

/** Each byte value's bits in reverse order: DSF's bytes hold their earliest bit last. */
internal val ReversedBits = IntArray(256) { b ->
    var r = 0
    for (i in 0 until 8) if (b and (1 shl i) != 0) r = r or (0x80 ushr i)
    r
}
