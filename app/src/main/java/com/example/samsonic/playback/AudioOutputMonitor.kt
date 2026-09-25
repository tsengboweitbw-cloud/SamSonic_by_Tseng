package com.example.samsonic.playback

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.audio.AudioSink
import com.example.samsonic.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * What the player is sending out: the PCM it hands Android ([sampleRate], [encoding],
 * [channels]), where Android plays it ([device]), and, for the phone's own outputs only,
 * the rate Android's mixer runs them at ([mixerRate]). Android doesn't say that rate for
 * USB or Bluetooth devices, so it's null there rather than a guess.
 */
data class AudioOutput(
    val sampleRate: Int,
    val encoding: Int,
    val channels: Int,
    val offload: Boolean,
    val device: String?,
    val mixerRate: Int?,
)

/** Keeps [output] up to date from the player's audio track and Android's audio routing. */
class AudioOutputMonitor(context: Context) {
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val _output = MutableStateFlow<AudioOutput?>(null)

    /** Null while nothing is set up to play. */
    val output: StateFlow<AudioOutput?> = _output.asStateFlow()

    private var player: ExoPlayer? = null

    @OptIn(UnstableApi::class)
    private val listener = object : AnalyticsListener {
        override fun onAudioTrackInitialized(eventTime: AnalyticsListener.EventTime, config: AudioSink.AudioTrackConfig) {
            val (device, mixerRate) = currentDevice()
            _output.value = AudioOutput(
                sampleRate = config.sampleRate,
                encoding = config.encoding,
                channels = Integer.bitCount(config.channelConfig),
                offload = config.offload,
                device = device,
                mixerRate = mixerRate,
            )
        }

        override fun onAudioTrackReleased(eventTime: AnalyticsListener.EventTime, config: AudioSink.AudioTrackConfig) {
            _output.value = null
        }
    }

    // Plugging in or pulling out a DAC or headphones moves the audio without a new track.
    private val deviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) = refreshDevice()
        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) = refreshDevice()
    }

    @OptIn(UnstableApi::class)
    fun attach(player: ExoPlayer) {
        this.player = player
        player.addAnalyticsListener(listener)
        audioManager.registerAudioDeviceCallback(deviceCallback, Handler(Looper.getMainLooper()))
    }

    @OptIn(UnstableApi::class)
    fun detach() {
        player?.removeAnalyticsListener(listener)
        player = null
        audioManager.unregisterAudioDeviceCallback(deviceCallback)
        _output.value = null
    }

    private fun refreshDevice() {
        val current = _output.value ?: return
        val (device, mixerRate) = currentDevice()
        _output.value = current.copy(device = device, mixerRate = mixerRate)
    }

    /** Where music plays right now, and the mixer's rate if it's one of the phone's own outputs. */
    private fun currentDevice(): Pair<String?, Int?> {
        // Android only says where a kind of audio goes from 13 on.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return null to null
        val media = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
        val device = audioManager.getAudioDevicesForAttributes(media).firstOrNull() ?: return null to null
        val builtIn = device.type in BUILT_IN_OUTPUTS
        val mixerRate = if (builtIn) {
            audioManager.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)?.toIntOrNull()
        } else {
            null
        }
        return describe(device) to mixerRate
    }

    private fun describe(device: AudioDeviceInfo): String {
        val name = device.productName?.toString()?.takeIf { it.isNotBlank() }
        val kind = when (device.type) {
            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> return "Phone speaker"
            AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> return "Phone earpiece"
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_WIRED_HEADSET -> "Wired headphones"
            AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_USB_ACCESSORY -> "USB"
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "Bluetooth"
            AudioDeviceInfo.TYPE_BLE_HEADSET, AudioDeviceInfo.TYPE_BLE_SPEAKER, AudioDeviceInfo.TYPE_BLE_BROADCAST -> "Bluetooth LE"
            AudioDeviceInfo.TYPE_HDMI, AudioDeviceInfo.TYPE_HDMI_ARC, AudioDeviceInfo.TYPE_HDMI_EARC -> "HDMI"
            else -> return name ?: "Other"
        }
        return if (name != null) "$kind · $name" else kind
    }

    private companion object {
        val BUILT_IN_OUTPUTS = setOf(
            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER,
            AudioDeviceInfo.TYPE_BUILTIN_EARPIECE,
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
        )
    }
}

/** "16-bit", "32-bit float"... for a PCM [encoding] as the player reports it. */
fun describeEncoding(context: Context, encoding: Int): String = when (encoding) {
    C.ENCODING_PCM_8BIT -> "8-bit"
    C.ENCODING_PCM_16BIT, C.ENCODING_PCM_16BIT_BIG_ENDIAN -> "16-bit"
    C.ENCODING_PCM_24BIT, C.ENCODING_PCM_24BIT_BIG_ENDIAN -> "24-bit"
    C.ENCODING_PCM_32BIT, C.ENCODING_PCM_32BIT_BIG_ENDIAN -> "32-bit"
    C.ENCODING_PCM_FLOAT -> context.getString(R.string.playback_encoding_float)
    else -> context.getString(R.string.playback_encoding_compressed)
}
