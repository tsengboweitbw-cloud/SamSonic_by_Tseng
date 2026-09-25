package com.example.samsonic.playback

import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioMixerAttributes
import android.os.Build
import androidx.annotation.OptIn
import androidx.annotation.RequiresApi
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.util.UnstableApi
import com.example.samsonic.playback.dsd.DsdStream

/**
 * What a USB DAC takes bit-perfect from this phone: the formats Android offers for it with
 * a bit-perfect mixer, which is all a song can be sent as untouched.
 */
@OptIn(UnstableApi::class) // Media3's PCM encodings, on the Format.
@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
internal class DacFormats(private val offered: List<AudioMixerAttributes>) {
    /** Every rate it takes bit-perfect, lowest first. */
    val rates: List<Int> = offered.filter { it.format.encoding != AudioFormat.ENCODING_DSD }
        .map { it.format.sampleRate }.distinct().sorted()

    /** The highest DSD (64, 128...) it takes as DoP in stereo, or null if none. */
    val maxDop: Int? = DSD_MULTIPLES.firstOrNull { dop(44_100 * it, AudioFormat.CHANNEL_OUT_STEREO) != null }

    /** Whether Android offers native DSD for it at all. */
    val nativeDsd: Boolean = offered.any { it.format.encoding == AudioFormat.ENCODING_DSD }

    /** The first of [encodings] it takes at [rate] for [channelMask], or null. */
    fun pcm(rate: Int, channelMask: Int, encodings: List<Int>): AudioMixerAttributes? =
        encodings.firstNotNullOfOrNull { encoding ->
            offered.firstOrNull {
                it.format.sampleRate == rate && it.format.channelMask == channelMask && it.format.encoding == encoding
            }
        }

    /**
     * The rate exclusive mode resamples a song of [rate] to, when the DAC doesn't take it: the
     * lowest whole multiple of it the DAC takes in stereo (32 → 64kHz, just doubling), else the
     * next rate up, else the highest there is. Null if it takes no stereo PCM at all.
     */
    fun resampleTarget(rate: Int): Int? {
        val stereo = offered.filter {
            it.format.channelMask == AudioFormat.CHANNEL_OUT_STEREO && it.format.encoding in INT_PCM
        }.map { it.format.sampleRate }.distinct().filter { Resampler.canResample(rate, it) }
        return stereo.filter { it % rate == 0 }.minOrNull()
            ?: stereo.filter { it > rate }.minOrNull()
            ?: stereo.maxOrNull()
    }

    /** The PCM format DoP of [dsdRate] goes out as, if it takes one: 32- or 24-bit, at [dsdRate] / 16. */
    fun dop(dsdRate: Int, channelMask: Int): AudioMixerAttributes? = pcm(dsdRate / 16, channelMask, INT_PCM)

    /**
     * Native DSD of [dsdRate], if Android offers it: at [dsdRate] / 8 (a byte of each channel
     * per frame) or / 16 (two bytes), the ways a USB DAC's DSD can be framed.
     */
    fun native(dsdRate: Int, channelMask: Int): Pair<AudioMixerAttributes, DsdStream.Native>? {
        for (bytesPerFrame in intArrayOf(1, 2)) {
            val rate = dsdRate / 8 / bytesPerFrame
            val format = pcm(rate, channelMask, listOf(AudioFormat.ENCODING_DSD)) ?: continue
            return format to DsdStream.Native(dsdRate, rate, bytesPerFrame)
        }
        return null
    }

    /** How a DSD file of [dsdRate] goes out in [mode]: native falling back to DoP, or null for PCM. */
    fun dsdStream(mode: DsdOutputMode, dsdRate: Int, channels: Int): DsdStream? {
        if (mode == DsdOutputMode.PCM) return null
        val mask = channelMask(channels) ?: return null
        if (mode == DsdOutputMode.NATIVE) native(dsdRate, mask)?.let { return it.second }
        return if (dop(dsdRate, mask) != null) DsdStream.Dop(dsdRate) else null
    }

    /**
     * How exclusive mode fits a song in PCM [format] to the DAC, when the DAC doesn't take it
     * as it is (its rate, or mono): resampled to [resampleTarget]'s rate, in stereo. Null when
     * it goes out bit-perfect as it is, or can't be fitted.
     */
    fun conversion(format: Format): ExclusiveConversion? {
        if (format.pcmEncoding !in ExclusiveSink.RESAMPLABLE) return null
        val mask = channelMask(format.channelCount) ?: return null
        // What the track goes out as, unchanged: see forTrack.
        val encodings = if (format.pcmEncoding == C.ENCODING_PCM_16BIT) PCM_FOR_16_BIT else INT_PCM
        if (pcm(format.sampleRate, mask, encodings) != null) return null
        val target = resampleTarget(format.sampleRate) ?: return null
        return ExclusiveConversion(format.sampleRate, format.channelCount, target, 2)
    }

    /**
     * The bit-perfect format a track in the player's [encoding] (16-bit or float) goes out as,
     * or null and why not: DSD as its [stream] says, 16-bit as it is or padded, float as 32-
     * or 24-bit integers.
     */
    fun forTrack(sampleRate: Int, encoding: Int, channelMask: Int, stream: DsdStream?): Pair<AudioMixerAttributes?, String?> {
        val format = when (stream) {
            is DsdStream.Native -> native(stream.dsdRate, channelMask)?.first
                ?: return null to "The DAC doesn't take native DSD${stream.multiple}"
            is DsdStream.Dop -> dop(stream.dsdRate, channelMask)
                ?: return null to "The DAC doesn't take DSD${stream.multiple} as DoP"
            null -> {
                val encodings = when (encoding) {
                    C.ENCODING_PCM_16BIT -> PCM_FOR_16_BIT
                    C.ENCODING_PCM_FLOAT -> INT_PCM
                    else -> return null to "Not PCM"
                }
                pcm(sampleRate, channelMask, encodings) ?: return null to "The DAC doesn't take ${formatKilohertz(sampleRate)}"
            }
        }
        return format to null
    }

    companion object {
        /** 32-bit first: the float PCM the player writes converts to either exactly. */
        val INT_PCM = listOf(AudioFormat.ENCODING_PCM_32BIT, AudioFormat.ENCODING_PCM_24BIT_PACKED)

        /** 16-bit as it is, else padded with zeros to 32- or 24-bit: the same values either way. */
        val PCM_FOR_16_BIT = listOf(AudioFormat.ENCODING_PCM_16BIT) + INT_PCM

        private val DSD_MULTIPLES = listOf(512, 256, 128, 64)

        fun of(audioManager: AudioManager, device: AudioDeviceInfo) = DacFormats(
            audioManager.getSupportedMixerAttributes(device).filter {
                it.mixerBehavior == AudioMixerAttributes.MIXER_BEHAVIOR_BIT_PERFECT
            },
        )

        /** The channel mask Android uses for [channels], or null for layouts DSD doesn't come in. */
        fun channelMask(channels: Int): Int? = when (channels) {
            1 -> AudioFormat.CHANNEL_OUT_MONO
            2 -> AudioFormat.CHANNEL_OUT_STEREO
            else -> null
        }
    }
}

/** "44.1 kHz", "96 kHz". */
fun formatKilohertz(hertz: Int): String =
    if (hertz % 1000 == 0) "${hertz / 1000} kHz" else "%.1f kHz".format(hertz / 1000f)
