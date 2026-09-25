package com.example.samsonic.ui.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Usb
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.samsonic.playback.BitPerfectOutput
import com.example.samsonic.playback.formatKilohertz

/**
 * The exclusive mode switch: music plays through the USB DAC alone, bit-perfect when it takes
 * the song as it is, resampled in the app to a format it does when not. With what the
 * plugged-in DAC takes. Without a USB DAC it stays, dimmed and untoggleable, its setting kept;
 * before Android 14, the first that can do it, it isn't there.
 */
@Composable
internal fun BitPerfectRow(bitPerfect: BitPerfectOutput) {
    if (!bitPerfect.available) return
    val enabled by bitPerfect.enabled.collectAsStateWithLifecycle()
    val usb = bitPerfect.dac.collectAsStateWithLifecycle().value
    val hint = when {
        usb == null -> "Plug in a USB DAC to use it: music then plays through the DAC alone, bit-perfect where it takes the song as it is, resampled where not"
        usb.rates.isEmpty() -> "${usb.name} doesn't offer exclusive mode on this phone"
        // Android's volume stops applying, and other sounds don't play on the DAC.
        enabled -> "${usb.name} · up to ${formatKilohertz(usb.rates.last())}. Phone volume may not apply: start with the DAC turned down"
        else -> "${usb.name} · up to ${formatKilohertz(usb.rates.last())}"
    }
    SwitchRow(
        icon = Icons.Filled.Usb,
        title = "Exclusive USB output",
        checked = enabled,
        onCheckedChange = bitPerfect::setEnabled,
        hint = hint,
        // It warns that the phone's volume may stop applying.
        hintWhenTurnedOn = true,
        enabled = usb != null,
    )
}
