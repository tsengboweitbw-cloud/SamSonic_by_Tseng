package com.example.samsonic.playback.dsd

/** How DSD can be sent to a USB DAC without filtering it to PCM first. */
enum class DsdMode {
    /** DSD over PCM: 16 DSD bits per channel in each 24-bit sample, under an alternating marker byte. */
    DOP,

    /** The DAC's raw-data format: 32 DSD bits per channel in each 32-bit sample. */
    NATIVE,
}

/**
 * Carried in [androidx.media3.common.Format.customData] by a track whose "PCM" is really DSD packed for
 * [mode]: only a DAC that takes [mode] may play it, as anything else would hear noise.
 */
class DsdStream(val mode: DsdMode, val dsdRate: Int)

/** DSD bytes per channel in each sample of [mode]. */
internal fun DsdMode.bytesPerSample(): Int = if (this == DsdMode.DOP) 2 else 4

/** The PCM rate DSD at [dsdRate] is sent at in [mode]. */
fun DsdMode.sampleRate(dsdRate: Int): Int = dsdRate / (bytesPerSample() * 8)
