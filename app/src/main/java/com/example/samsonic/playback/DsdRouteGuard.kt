package com.example.samsonic.playback

import android.media.AudioDeviceInfo
import android.media.AudioRouting
import android.media.AudioTrack
import android.os.Handler
import android.os.HandlerThread

/** The device types a USB DAC shows up as. */
internal val USB_OUTPUTS = setOf(
    AudioDeviceInfo.TYPE_USB_DEVICE,
    AudioDeviceInfo.TYPE_USB_HEADSET,
    AudioDeviceInfo.TYPE_USB_ACCESSORY,
)

/** Whether Android has moved this track off the USB DAC (false while it doesn't say where it is). */
internal fun AudioTrack.leftUsb(): Boolean = routedDevice?.let { it.type !in USB_OUTPUTS } ?: false

/**
 * Keeps DSD packed for the DAC off every other output. Android can move a track anywhere at
 * any time: to the speaker when the DAC is unplugged, or to Bluetooth headphones connecting
 * mid-song. The moment a watched track is routed off USB it's silenced, on a thread of its own
 * so a busy main thread can't hold that up, and then [onMoved] re-plans the song.
 */
internal object DsdRouteGuard {
    private val handler by lazy { Handler(HandlerThread("dsd-route-guard").apply { start() }.looper) }

    fun watch(track: AudioTrack, onMoved: () -> Unit) {
        track.addOnRoutingChangedListener(
            AudioRouting.OnRoutingChangedListener {
                if (!track.leftUsb()) return@OnRoutingChangedListener
                (track as? Silenceable)?.silence() ?: track.setVolume(0f)
                onMoved()
            },
            handler,
        )
    }
}
