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
import com.example.samsonic.BuildConfig
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
    private val probed = mutableSetOf<String>()
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
        // A DAC plugged in again is probed again.
        val present = _dacs.value.map { it.device.deviceName }
        probed.removeAll { name -> present.none { name == it || name == "ask:$it" } }
        probeInDebug()
    }

    /**
     * Debug and perfTest builds only, until the driver plays songs: asks for permission to each DAC, then
     * opens it, logs what libusb reads and plays a short quiet tone (tag SamSonicUsb).
     */
    private fun probeInDebug() {
        if (BuildConfig.BUILD_TYPE == "release") return
        for (dac in _dacs.value) {
            if (!dac.hasPermission) {
                if (probed.add("ask:${dac.device.deviceName}")) requestPermission(dac)
            } else if (probed.add(dac.device.deviceName)) {
                Thread {
                    val connection = open(dac)
                    if (connection == null) {
                        Log.w(TAG, "${dac.name}: couldn't open")
                        return@Thread
                    }
                    connection.use {
                        NativeUsb.describe(it.fd).lines().forEach { line -> Log.i(TAG, line) }
                        // A short, quiet tone: the proof that the driver can play.
                        Log.i(TAG, NativeUsb.startTestTone(it.fd, 48_000))
                        Thread.sleep(2_500)
                        NativeUsb.stopTestTone()
                    }
                }.start()
            }
        }
    }

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
