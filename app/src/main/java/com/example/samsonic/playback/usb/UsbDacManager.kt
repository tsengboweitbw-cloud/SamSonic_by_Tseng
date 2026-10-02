package com.example.samsonic.playback.usb

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.example.samsonic.playback.dsd.DsdMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** A USB audio device that's plugged in, and whether the app may open it yet. */
data class UsbDac(val device: UsbDevice, val name: String, val hasPermission: Boolean)

/**
 * Keeps [dacs] up to date with the USB audio devices plugged in, asks for permission to use
 * them, and opens them for the native driver. Android's own USB audio driver is bound to
 * these devices; [open] takes them over, so nothing is opened until the driver needs one.
 */
class UsbDacManager(context: Context) {
    private val appContext = context.applicationContext
    private val usbManager = appContext.getSystemService(UsbManager::class.java)
    private val asked = mutableSetOf<String>()
    private val prefs = appContext.getSharedPreferences("samsonic_usb_dac", Context.MODE_PRIVATE)
    private val _enabled = MutableStateFlow(prefs.getBoolean(KEY_ENABLED, false))
    private val _nativeDsd = MutableStateFlow(prefs.getBoolean(KEY_NATIVE_DSD, false))
    private val _dacs = MutableStateFlow<List<UsbDac>>(emptyList())
    val dacs: StateFlow<List<UsbDac>> = _dacs.asStateFlow()

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = refresh()
    }

    init {
        val filter = IntentFilter().apply {
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
            addAction(ACTION_PERMISSION)
        }
        ContextCompat.registerReceiver(appContext, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        refresh()
    }

    private fun refresh() {
        _dacs.value = usbManager.deviceList.values
            .filter { it.isAudioDevice() }
            .map { UsbDac(it, it.productName ?: it.deviceName, usbManager.hasPermission(it)) }
        // With the driver on, each DAC's permission is asked for at once, once for each plug-in.
        if (_enabled.value) {
            val present = _dacs.value.map { it.device.deviceName }
            asked.retainAll(present.toSet())
            for (dac in _dacs.value) {
                if (!dac.hasPermission && asked.add(dac.device.deviceName)) requestPermission(dac)
            }
        }
    }

    /** The first plugged-in DAC the app may open, if the driver is on and there is one. */
    fun readyDac(): UsbDac? = if (_enabled.value) _dacs.value.firstOrNull { it.hasPermission } else null

    /** Whether the driver plays to a plugged-in USB DAC itself, instead of Android's own output. */
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    fun setEnabled(enabled: Boolean) {
        _enabled.value = enabled
        prefs.edit { putBoolean(KEY_ENABLED, enabled) }
        refresh()
    }

    /** Whether DSD goes to a DAC that takes it in its raw-data format, rather than as DoP. */
    val nativeDsd: StateFlow<Boolean> = _nativeDsd.asStateFlow()

    fun setNativeDsd(native: Boolean) {
        _nativeDsd.value = native
        prefs.edit { putBoolean(KEY_NATIVE_DSD, native) }
    }

    val preferNativeDsd: Boolean get() = _nativeDsd.value

    /** Set by the player's audio sink: how DSD of a rate can go to the DAC as it is, or null. */
    @Volatile
    var dsdProbe: (Int) -> DsdMode? = { null }

    /** Called from the loader's thread, when a DSD file is opened. */
    fun dsdModeFor(dsdRate: Int): DsdMode? = dsdProbe(dsdRate)

    /** Shows Android's permission dialog; [dacs] updates when it's answered. */
    fun requestPermission(dac: UsbDac) {
        // Explicit and mutable: Android fills the result into this intent.
        val intent = Intent(ACTION_PERMISSION).setPackage(appContext.packageName)
        val pending = PendingIntent.getBroadcast(
            appContext, 0, intent, PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        usbManager.requestPermission(dac.device, pending)
    }

    /** Opens [dac] and claims its audio interfaces, or null without permission or when it won't open. */
    fun open(dac: UsbDac): UsbDacConnection? {
        if (!usbManager.hasPermission(dac.device)) return null
        val connection = usbManager.openDevice(dac.device) ?: return null
        val claimed = mutableListOf<UsbInterface>()
        for (i in 0 until dac.device.interfaceCount) {
            val intf = dac.device.getInterface(i)
            // Alternate settings share an id; claiming one claims them all.
            if (intf.interfaceClass != UsbConstants.USB_CLASS_AUDIO || claimed.any { it.id == intf.id }) continue
            // `force` detaches Android's USB audio driver from the interface.
            if (connection.claimInterface(intf, true)) {
                claimed += intf
            } else {
                Log.w(TAG, "Couldn't claim interface ${intf.id} of ${dac.name}")
            }
        }
        if (claimed.isEmpty()) {
            connection.close()
            return null
        }
        return UsbDacConnection(dac.device, connection, claimed)
    }

    private fun UsbDevice.isAudioDevice(): Boolean = (0 until interfaceCount).any {
        val intf = getInterface(it)
        intf.interfaceClass == UsbConstants.USB_CLASS_AUDIO && intf.interfaceSubclass == SUBCLASS_AUDIO_STREAMING
    }

    private companion object {
        const val TAG = "UsbDacManager"
        const val KEY_ENABLED = "enabled"
        const val KEY_NATIVE_DSD = "native_dsd"
        const val ACTION_PERMISSION = "com.example.samsonic.USB_PERMISSION"
        const val SUBCLASS_AUDIO_STREAMING = 2
    }
}

/** An opened DAC with its audio interfaces claimed. [close] hands them back to Android. */
class UsbDacConnection internal constructor(
    val device: UsbDevice,
    private val connection: UsbDeviceConnection,
    private val claimed: List<UsbInterface>,
) : AutoCloseable {
    /** The usbfs file descriptor the native driver wraps. Valid until [close]. */
    val fd: Int get() = connection.fileDescriptor

    override fun close() {
        claimed.forEach { connection.releaseInterface(it) }
        connection.close()
    }
}
