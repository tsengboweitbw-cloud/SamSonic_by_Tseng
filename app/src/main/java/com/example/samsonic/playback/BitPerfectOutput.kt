package com.example.samsonic.playback

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.annotation.RequiresApi
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import com.example.samsonic.playback.dsd.DsdStream
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * A plugged-in USB DAC and what it takes bit-perfect from this phone: its [rates] (none: it
 * doesn't at all), the highest DSD it takes as DoP ([maxDop], null for none), and whether
 * Android offers native DSD for it.
 */
data class UsbDac(val name: String, val rates: List<Int>, val maxDop: Int?, val nativeDsd: Boolean)

/** Whether the song playing now goes out bit-perfect, and how or why not. */
data class BitPerfectTrack(val on: Boolean, val detail: String)

/** How DSD files go out: filtered to PCM in the app, or untouched to a DAC that takes it. */
enum class DsdOutputMode { PCM, DOP, NATIVE }

/**
 * Bit-perfect playback to a USB DAC, from Android 14: Android's mixer steps aside for the
 * song's format, so the DAC gets its samples untouched, at its own rate, with no system
 * volume or other sounds mixed in. Before 14 there's no way to ask for it, so [available]
 * is false and the player is built as it always was (see BitPerfectRenderers).
 *
 * The preference has to be set before each AudioTrack is made, so [audioTrackProvider] sets
 * it per track, for that track's format. Songs the DAC can't take bit-perfect play normally,
 * except DSD packed for the DAC ([dsdStreamFor]), which is noise to the mixer: it's silenced.
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

    private val _dsdMode = MutableStateFlow(
        DsdOutputMode.entries.firstOrNull { it.name == prefs.getString(KEY_DSD_MODE, null) } ?: DsdOutputMode.PCM,
    )
    val dsdMode: StateFlow<DsdOutputMode> = _dsdMode.asStateFlow()

    private val _dac = MutableStateFlow<UsbDac?>(null)
    /** Null with no USB DAC plugged in. */
    val dac: StateFlow<UsbDac?> = _dac.asStateFlow()

    private val _track = MutableStateFlow<BitPerfectTrack?>(null)
    /** Null while it's off or nothing has played yet. */
    val track: StateFlow<BitPerfectTrack?> = _track.asStateFlow()

    private var player: ExoPlayer? = null
    private val main = Handler(Looper.getMainLooper())

    /** The DAC the preference is set on, to clear it again. */
    @Volatile private var preferredOn: AudioDeviceInfo? = null

    /** The DSD stream of the format the audio sink was last set up for: the next track's. */
    @Volatile private var nextStream: DsdStream? = null
    @Volatile private var nextConversion: ExclusiveConversion? = null

    // The newest track, and its DSD stream if it carries one, to silence it before it can
    // reach Android's mixer.
    @Volatile private var currentTrack: AudioTrack? = null
    @Volatile private var currentStream: DsdStream? = null

    private val deviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) {
            refreshDac()
            // Music moves to a DAC plugged in mid-song without a new track, so make one.
            if (_enabled.value && addedDevices.any { it.type in USB_OUTPUTS }) restartAudio()
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) {
            refreshDac()
            // Its DSD would move to the phone's speaker as noise: cut it, and re-plan as PCM
            // (DsdRouteGuard has usually cut it already).
            if (currentStream != null && removedDevices.any { it.type in USB_OUTPUTS }) replanDsd()
        }
    }

    init {
        if (available) {
            audioManager.registerAudioDeviceCallback(deviceCallback, main)
            refreshDac()
        }
    }

    fun setEnabled(on: Boolean) {
        if (!available || on == _enabled.value) return
        prefs.edit().putBoolean(KEY_ENABLED, on).apply()
        _enabled.value = on
        if (!on) {
            silenceDsd()
            clearPreference()
            _track.value = null
        }
        if (_dac.value != null) restartAudio()
    }

    fun setDsdMode(mode: DsdOutputMode) {
        if (!available || mode == _dsdMode.value) return
        prefs.edit().putString(KEY_DSD_MODE, mode.name).apply()
        _dsdMode.value = mode
        // Only DSD files change, and only when they can go to a DAC.
        if (_enabled.value && _dac.value != null) replanDsd()
    }

    fun attach(player: ExoPlayer) {
        this.player = player
    }

    /** The playback service is going: let other apps have the DAC's mixer back. */
    fun detach() {
        player = null
        silenceDsd()
        clearPreference()
        _track.value = null
    }

    /**
     * How a DSD file of [dsdRate] should go out, asked as it's opened: as DoP or native DSD
     * when bit-perfect is on, [dsdMode] asks for it and the DAC takes it (native falling back
     * to DoP), or null to filter it to PCM.
     */
    fun dsdStreamFor(dsdRate: Int, channels: Int): DsdStream? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE || !_enabled.value) return null
        return DacFormats.of(audioManager, routedUsb() ?: return null).dsdStream(_dsdMode.value, dsdRate, channels)
    }

    /**
     * How exclusive mode fits a song in [format] to the DAC, when the DAC doesn't take it as it
     * is (its rate, or mono): resampled to the rate [DacFormats.resampleTarget] picks, in
     * stereo. Null when it goes out bit-perfect as it is, or can't go to a DAC at all.
     */
    internal fun conversionFor(format: Format): ExclusiveConversion? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE || !_enabled.value) return null
        if (format.sampleMimeType != MimeTypes.AUDIO_RAW || format.customData is DsdStream) return null
        return DacFormats.of(audioManager, routedUsb() ?: return null).conversion(format)
    }

    /** The audio sink is set up for [format], which the next track will play, fitted by [conversion]. */
    internal fun onSinkConfigured(format: Format, conversion: ExclusiveConversion?) {
        nextStream = format.customData as? DsdStream
        nextConversion = conversion
    }

    /** Makes the player's AudioTracks, setting the DAC up bit-perfect for each one first. */
    val audioTrackProvider = DefaultAudioSink.AudioTrackProvider { config, attributes, sessionId ->
        val stream = nextStream
        val conversion = nextConversion
        var track: AudioTrack? = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && _enabled.value && !config.offload) {
            track = bitPerfectTrack(config, stream, conversion, sessionId)
        }
        if (track == null && stream != null) {
            val why = when {
                !_enabled.value -> "Exclusive mode is off"
                else -> _track.value?.detail ?: "Not playing to the USB DAC"
            }
            _track.value = BitPerfectTrack(on = false, detail = "$why. DSD for the DAC can't play, so it's muted")
            track = SilentAudioTrack(
                attributes.audioAttributesV21.audioAttributes,
                AudioFormat.Builder()
                    .setSampleRate(config.sampleRate)
                    .setChannelMask(config.channelConfig)
                    .setEncoding(config.encoding)
                    .build(),
                config.bufferSize,
                sessionId,
            )
        }
        (track ?: DefaultAudioSink.AudioTrackProvider.DEFAULT.getAudioTrack(config, attributes, sessionId)).also {
            currentTrack = it
            currentStream = stream
            if (stream != null && it !is SilentAudioTrack) {
                DsdRouteGuard.watch(it) { main.post { if (currentTrack === it) replanDsd() } }
            }
        }
    }

    /** A track for [config] going to the DAC bit-perfect, or null (and why, in [track]) if it can't. */
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun bitPerfectTrack(
        config: AudioSink.AudioTrackConfig,
        stream: DsdStream?,
        conversion: ExclusiveConversion?,
        sessionId: Int,
    ): AudioTrack? {
        fun off(why: String): AudioTrack? {
            clearPreference()
            _track.value = BitPerfectTrack(on = false, detail = why)
            return null
        }
        val device = routedUsb() ?: run {
            // Not a DAC's song at all (speaker, Bluetooth): no exclusive line, not an "off" one.
            clearPreference()
            _track.value = null
            return null
        }
        val (mixer, whyNot) = DacFormats.of(audioManager, device).forTrack(config.sampleRate, config.encoding, config.channelConfig, stream)
        if (mixer == null) return off(whyNot ?: "The DAC doesn't take it")
        if (!audioManager.setPreferredMixerAttributes(media, device, mixer)) return off("Android declined it")
        preferredOn = device
        val encoding = mixer.format.encoding
        val track = try {
            bitPerfectAudioTrack(media, config, encoding, stream, sessionId)
        } catch (e: RuntimeException) {
            // Android turned the format down after all (native DSD above all, never tried).
            return off("Android couldn't open it: ${e.message}")
        }
        // The constructors the DSD and integer tracks use don't throw on a format Android turns
        // down: the track just never initializes, and the sink would fail on it.
        if (track.state != AudioTrack.STATE_INITIALIZED) {
            track.release()
            return off("Android couldn't open it")
        }
        _track.value = BitPerfectTrack(on = true, detail = describeExclusive(encoding, config.sampleRate, stream, conversion))
        return track
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun routedUsb(): AudioDeviceInfo? =
        audioManager.getAudioDevicesForAttributes(media).firstOrNull { it.type in USB_OUTPUTS }

    private fun refreshDac() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return
        val device = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).firstOrNull { it.type in USB_OUTPUTS }
        _dac.value = device?.let { usb ->
            val formats = DacFormats.of(audioManager, usb)
            UsbDac(
                name = usb.productName?.toString()?.takeIf { it.isNotBlank() } ?: "USB DAC",
                rates = formats.rates,
                maxDop = formats.maxDop,
                nativeDsd = formats.nativeDsd,
            )
        }
    }

    private fun clearPreference() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return
        val device = preferredOn ?: return
        preferredOn = null
        audioManager.clearPreferredMixerAttributes(media, device)
    }

    /** Cuts off a playing track carrying DSD for the DAC, before anything can send it to the mixer. */
    private fun silenceDsd() {
        if (currentStream == null) return
        (currentTrack as? Silenceable)?.silence()
    }

    /** Opens the song again so a DSD file picks its output anew, silencing any DSD first. */
    private fun replanDsd() {
        silenceDsd()
        restartAudio()
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
        const val KEY_DSD_MODE = "dsd_output"
    }
}
