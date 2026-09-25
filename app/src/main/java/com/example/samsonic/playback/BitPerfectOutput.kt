package com.example.samsonic.playback

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioMixerAttributes
import android.media.AudioTrack
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.annotation.RequiresApi
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** A plugged-in USB DAC and the rates it takes bit-perfect from this phone (none: it doesn't). */
data class UsbDac(val name: String, val rates: List<Int>)

/** Whether the song playing now goes out bit-perfect, and how or why not. */
data class BitPerfectTrack(val on: Boolean, val detail: String)

/**
 * Bit-perfect playback to a USB DAC, from Android 14: Android's mixer steps aside for the
 * song's format, so the DAC gets its samples untouched, at its own rate, with no system
 * volume or other sounds mixed in. Before 14 there's no way to ask for it, so [available]
 * is false and the player is built as it always was (see PlaybackService).
 *
 * The preference has to be set before each AudioTrack is made, so [audioTrackProvider] sets
 * it per track, for that track's format. Songs the DAC can't take bit-perfect play normally.
 */
@OptIn(UnstableApi::class) // Media3's AudioTrackProvider.
class BitPerfectOutput(context: Context) {
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val prefs = context.getSharedPreferences("samsonic_audio", Context.MODE_PRIVATE)
    private val media = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()

    val available: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE

    private val _enabled = MutableStateFlow(available && prefs.getBoolean(KEY_ENABLED, false))
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    private val _dac = MutableStateFlow<UsbDac?>(null)
    /** Null with no USB DAC plugged in. */
    val dac: StateFlow<UsbDac?> = _dac.asStateFlow()

    private val _track = MutableStateFlow<BitPerfectTrack?>(null)
    /** Null while it's off or nothing has played yet. */
    val track: StateFlow<BitPerfectTrack?> = _track.asStateFlow()

    private var player: ExoPlayer? = null

    /** The DAC the preference is set on, to clear it again. */
    @Volatile private var preferredOn: AudioDeviceInfo? = null

    private val deviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) {
            refreshDac()
            // Music moves to a DAC plugged in mid-song without a new track, so make one.
            if (_enabled.value && addedDevices.any { it.type in USB_OUTPUTS }) restartAudio()
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) = refreshDac()
    }

    init {
        if (available) {
            audioManager.registerAudioDeviceCallback(deviceCallback, Handler(Looper.getMainLooper()))
            refreshDac()
        }
    }

    fun setEnabled(on: Boolean) {
        if (!available || on == _enabled.value) return
        prefs.edit().putBoolean(KEY_ENABLED, on).apply()
        _enabled.value = on
        if (!on) {
            clearPreference()
            _track.value = null
        }
        if (_dac.value != null) restartAudio()
    }

    fun attach(player: ExoPlayer) {
        this.player = player
    }

    /** The playback service is going: let other apps have the DAC's mixer back. */
    fun detach() {
        player = null
        clearPreference()
        _track.value = null
    }

    /** Makes the player's AudioTracks, setting the DAC up bit-perfect for each one first. */
    val audioTrackProvider = DefaultAudioSink.AudioTrackProvider { config, attributes, sessionId ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && _enabled.value && !config.offload) {
            bitPerfectTrack(config, sessionId)?.let { return@AudioTrackProvider it }
        }
        DefaultAudioSink.AudioTrackProvider.DEFAULT.getAudioTrack(config, attributes, sessionId)
    }

    /** A track for [config] going to the DAC bit-perfect, or null (and why, in [track]) if it can't. */
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    @SuppressLint("WrongConstant") // The encoding is one the DAC reported.
    private fun bitPerfectTrack(config: AudioSink.AudioTrackConfig, sessionId: Int): AudioTrack? {
        val rate = formatKilohertz(config.sampleRate)
        fun off(why: String): AudioTrack? {
            clearPreference()
            _track.value = BitPerfectTrack(on = false, detail = why)
            return null
        }
        val device = audioManager.getAudioDevicesForAttributes(media).firstOrNull { it.type in USB_OUTPUTS }
            ?: return off("Not playing to a USB DAC")
        // 16-bit PCM plays as it is; the rest comes as float, which the DAC takes as integers.
        val encodings = when (config.encoding) {
            C.ENCODING_PCM_16BIT -> listOf(AudioFormat.ENCODING_PCM_16BIT)
            C.ENCODING_PCM_FLOAT -> listOf(AudioFormat.ENCODING_PCM_32BIT, AudioFormat.ENCODING_PCM_24BIT_PACKED)
            else -> return off("Not PCM")
        }
        val offered = bitPerfectFormats(device).filter {
            it.format.sampleRate == config.sampleRate && it.format.channelMask == config.channelConfig
        }
        val mixer = encodings.firstNotNullOfOrNull { encoding -> offered.firstOrNull { it.format.encoding == encoding } }
            ?: return off("The DAC doesn't take $rate")
        if (!audioManager.setPreferredMixerAttributes(media, device, mixer)) return off("Android declined $rate")
        preferredOn = device
        val encoding = mixer.format.encoding
        _track.value = BitPerfectTrack(on = true, detail = "${bitsOf(encoding)}-bit · $rate")
        return if (encoding == AudioFormat.ENCODING_PCM_16BIT) {
            AudioTrack.Builder()
                .setAudioAttributes(media)
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
        } else {
            FloatToIntAudioTrack(media, config.sampleRate, config.channelConfig, encoding, config.bufferSize, sessionId)
        }
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun bitPerfectFormats(device: AudioDeviceInfo): List<AudioMixerAttributes> =
        audioManager.getSupportedMixerAttributes(device).filter {
            it.mixerBehavior == AudioMixerAttributes.MIXER_BEHAVIOR_BIT_PERFECT
        }

    private fun refreshDac() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return
        val device = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).firstOrNull { it.type in USB_OUTPUTS }
        _dac.value = device?.let { usb ->
            UsbDac(
                name = usb.productName?.toString()?.takeIf { it.isNotBlank() } ?: "USB DAC",
                rates = bitPerfectFormats(usb).map { it.format.sampleRate }.distinct().sorted(),
            )
        }
    }

    private fun clearPreference() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return
        val device = preferredOn ?: return
        preferredOn = null
        audioManager.clearPreferredMixerAttributes(media, device)
    }

    /** Replaces the playing track with a new one, which picks up the change, keeping the place. */
    private fun restartAudio() {
        val player = player ?: return
        if (player.playbackState == Player.STATE_IDLE) return
        val index = player.currentMediaItemIndex
        val position = player.currentPosition
        player.stop()
        player.prepare()
        player.seekTo(index, position)
    }

    private companion object {
        const val KEY_ENABLED = "bit_perfect_usb"

        val USB_OUTPUTS = setOf(
            AudioDeviceInfo.TYPE_USB_DEVICE,
            AudioDeviceInfo.TYPE_USB_HEADSET,
            AudioDeviceInfo.TYPE_USB_ACCESSORY,
        )

        fun bitsOf(encoding: Int): Int = when (encoding) {
            AudioFormat.ENCODING_PCM_16BIT -> 16
            AudioFormat.ENCODING_PCM_24BIT_PACKED -> 24
            else -> 32
        }
    }
}

/** "44.1 kHz", "96 kHz". */
fun formatKilohertz(hertz: Int): String =
    if (hertz % 1000 == 0) "${hertz / 1000} kHz" else "%.1f kHz".format(hertz / 1000f)
