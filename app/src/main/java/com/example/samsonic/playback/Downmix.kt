package com.example.samsonic.playback

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Folds surround music (up to 7.1) down to stereo, the way ITU-R BS.775 does: the centre and
 * each side or back channel join their own side at -3dB, and the LFE is left out. Channels are
 * in WAVE order (front left, front right, centre, LFE, back left, back right, side left, side right),
 * which is what Android's decoders produce.
 */
object Downmix {
    const val MAX_CHANNELS = 8

    private const val HALF_POWER = 0.7071f

    /** Whether [channels] is a count that [gains] can fold down. */
    fun canDownmix(channels: Int): Boolean = channels in 3..MAX_CHANNELS

    /**
     * How much of each input channel goes to the left and right output: [channels] pairs, left
     * then right. Scaled so that full-scale in every channel can't exceed full scale out.
     */
    fun gains(channels: Int): FloatArray {
        require(canDownmix(channels)) { "No downmix for $channels channels" }
        val gains = FloatArray(channels * 2)
        fun set(channel: Int, left: Float, right: Float) {
            if (channel < channels) {
                gains[channel * 2] = left
                gains[channel * 2 + 1] = right
            }
        }
        when (channels) {
            3 -> { // left, right, centre
                set(0, 1f, 0f); set(1, 0f, 1f); set(2, HALF_POWER, HALF_POWER)
            }
            4 -> { // quad: front pair, back pair
                set(0, 1f, 0f); set(1, 0f, 1f); set(2, HALF_POWER, 0f); set(3, 0f, HALF_POWER)
            }
            5 -> { // 5.0: front pair, centre, back pair
                set(0, 1f, 0f); set(1, 0f, 1f); set(2, HALF_POWER, HALF_POWER)
                set(3, HALF_POWER, 0f); set(4, 0f, HALF_POWER)
            }
            else -> { // 5.1, 6.1 and 7.1
                set(0, 1f, 0f); set(1, 0f, 1f); set(2, HALF_POWER, HALF_POWER)
                if (channels == 7) { // 6.1: the single back channel goes to both sides
                    set(4, HALF_POWER * HALF_POWER, HALF_POWER * HALF_POWER); set(5, HALF_POWER, 0f); set(6, 0f, HALF_POWER)
                } else {
                    set(4, HALF_POWER, 0f); set(5, 0f, HALF_POWER)
                    set(6, HALF_POWER, 0f); set(7, 0f, HALF_POWER)
                }
            }
        }
        var left = 0f
        var right = 0f
        for (channel in 0 until channels) {
            left += gains[channel * 2]
            right += gains[channel * 2 + 1]
        }
        val scale = 1f / maxOf(left, right)
        for (i in gains.indices) gains[i] *= scale
        return gains
    }

    /** Mixes one frame, [channels] samples in [frame], into [out] (left, right). */
    fun mix(gains: FloatArray, channels: Int, frame: FloatArray, out: FloatArray) {
        var left = 0f
        var right = 0f
        for (channel in 0 until channels) {
            left += frame[channel] * gains[channel * 2]
            right += frame[channel] * gains[channel * 2 + 1]
        }
        out[0] = left
        out[1] = right
    }
}

/**
 * The stock sink's step for surround: every song of three to eight channels goes on as stereo,
 * because an `AudioTrack` can't be counted on to take, or fold down, a 5.1 stream. 16-bit stays
 * 16-bit; anything finer goes on as float.
 */
@OptIn(UnstableApi::class)
internal class DownmixAudioProcessor : BaseAudioProcessor() {
    private var gains = FloatArray(0)
    private var frame = FloatArray(0)
    private val mixed = FloatArray(2)

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (!Downmix.canDownmix(inputAudioFormat.channelCount)) return AudioProcessor.AudioFormat.NOT_SET
        if (inputAudioFormat.encoding !in HANDLED) throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        gains = Downmix.gains(inputAudioFormat.channelCount)
        frame = FloatArray(inputAudioFormat.channelCount)
        val encoding = if (inputAudioFormat.encoding == C.ENCODING_PCM_16BIT) C.ENCODING_PCM_16BIT else C.ENCODING_PCM_FLOAT
        return AudioProcessor.AudioFormat(inputAudioFormat.sampleRate, 2, encoding)
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val channels = inputAudioFormat.channelCount
        val encoding = inputAudioFormat.encoding
        val frames = inputBuffer.remaining() / inputAudioFormat.bytesPerFrame
        val source = inputBuffer.duplicate().order(ByteOrder.nativeOrder())
        inputBuffer.position(inputBuffer.limit())
        val out = replaceOutputBuffer(frames * outputAudioFormat.bytesPerFrame)
        repeat(frames) {
            for (channel in 0 until channels) frame[channel] = read(source, encoding)
            Downmix.mix(gains, channels, frame, mixed)
            if (outputAudioFormat.encoding == C.ENCODING_PCM_16BIT) {
                for (side in 0..1) out.putShort((mixed[side] * 32_768f).toInt().coerceIn(-32_768, 32_767).toShort())
            } else {
                for (side in 0..1) out.putFloat(mixed[side])
            }
        }
        out.flip()
    }

    private fun read(source: ByteBuffer, encoding: Int): Float = when (encoding) {
        C.ENCODING_PCM_16BIT -> source.getShort() / 32_768f
        C.ENCODING_PCM_24BIT -> {
            val value = (source.get().toInt() and 0xFF) or ((source.get().toInt() and 0xFF) shl 8) or (source.get().toInt() shl 16)
            value / 8_388_608f
        }
        C.ENCODING_PCM_32BIT -> (source.getInt() / 2_147_483_648.0).toFloat()
        else -> source.getFloat()
    }

    private companion object {
        val HANDLED = setOf(C.ENCODING_PCM_16BIT, C.ENCODING_PCM_24BIT, C.ENCODING_PCM_32BIT, C.ENCODING_PCM_FLOAT)
    }
}
