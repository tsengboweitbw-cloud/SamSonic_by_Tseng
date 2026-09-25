package com.example.samsonic.playback

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import androidx.annotation.OptIn
import androidx.annotation.RequiresApi
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.audio.AudioSink
import com.example.samsonic.playback.dsd.DsdStream

/**
 * The AudioTrack for [config] once the DAC is set up bit-perfect as [encoding]: as it is when
 * that's the player's own encoding, widened to integers ([IntPcmAudioTrack]) when the DAC
 * takes 24- or 32-bit, or raw DSD ([NativeDsdAudioTrack]) for a [DsdStream.Native].
 */
@OptIn(UnstableApi::class)
@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
@SuppressLint("WrongConstant") // The encoding is one the DAC reported.
internal fun bitPerfectAudioTrack(
    attributes: AudioAttributes,
    config: AudioSink.AudioTrackConfig,
    encoding: Int,
    stream: DsdStream?,
    sessionId: Int,
): AudioTrack = when {
    stream is DsdStream.Native ->
        NativeDsdAudioTrack(attributes, stream, config.channelConfig, config.bufferSize, sessionId)
    encoding == config.encoding -> AudioTrack.Builder()
        .setAudioAttributes(attributes)
        .setAudioFormat(
            AudioFormat.Builder()
                .setSampleRate(config.sampleRate)
                .setChannelMask(config.channelConfig)
                .setEncoding(encoding)
                .build(),
        )
        .setTransferMode(AudioTrack.MODE_STREAM)
        .setBufferSizeInBytes(config.bufferSize)
        .setSessionId(sessionId)
        .build()
    else -> IntPcmAudioTrack(
        attributes, config.sampleRate, config.channelConfig, config.encoding, encoding, config.bufferSize, sessionId,
        dop = stream is DsdStream.Dop,
    )
}

/**
 * How Song info puts an exclusive track: "Bit-perfect · 32-bit · 96 kHz",
 * "Resampled 32 → 64 kHz · 32-bit", "Bit-perfect · DoP · DSD128".
 */
internal fun describeExclusive(encoding: Int, sampleRate: Int, stream: DsdStream?, conversion: ExclusiveConversion?): String {
    when (stream) {
        is DsdStream.Dop -> return "Bit-perfect · DoP · DSD${stream.multiple}"
        is DsdStream.Native -> return "Bit-perfect · Native DSD · DSD${stream.multiple}"
        null -> Unit
    }
    val bits = when (encoding) {
        AudioFormat.ENCODING_PCM_16BIT -> "16-bit"
        AudioFormat.ENCODING_PCM_24BIT_PACKED -> "24-bit"
        else -> "32-bit"
    }
    if (conversion == null) return "Bit-perfect · $bits · ${formatKilohertz(sampleRate)}"
    val changes = listOfNotNull(
        if (conversion.fromRate != conversion.sampleRate) {
            "Resampled ${formatKilohertz(conversion.fromRate).removeSuffix(" kHz")} → ${formatKilohertz(conversion.sampleRate)}"
        } else {
            null
        },
        if (conversion.fromChannels != conversion.channels) "mono to stereo" else null,
    ).joinToString(" · ").replaceFirstChar { it.uppercase() }
    val rate = if (conversion.fromRate == conversion.sampleRate) " · ${formatKilohertz(sampleRate)}" else ""
    return "$changes · $bits$rate"
}
