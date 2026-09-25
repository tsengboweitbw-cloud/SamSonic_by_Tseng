package com.example.samsonic.playback

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack

/** A track that can be cut to silence for good, whatever volume the player sets after. */
internal interface Silenceable {
    fun silence()
}

/**
 * A track that never makes a sound: for DSD packed for a DAC (DoP or native) that can't
 * go out bit-perfect after all. Android's mixer would play it as loud noise; this keeps the
 * player running, with the reason in Song info, until the next song or a restart re-plans it.
 */
internal class SilentAudioTrack(
    attributes: AudioAttributes,
    format: AudioFormat,
    bufferSize: Int,
    sessionId: Int,
) : AudioTrack(attributes, format, bufferSize, MODE_STREAM, sessionId), Silenceable {
    init {
        super.setVolume(0f)
    }

    override fun setVolume(gain: Float): Int = super.setVolume(0f)

    override fun silence() {
        super.setVolume(0f)
    }
}
