package com.example.samsonic.ui.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Usb
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.samsonic.playback.BitPerfectOutput
import com.example.samsonic.playback.formatKilohertz

/**
 * The bit-perfect USB DAC switch, with what the plugged-in DAC takes. Only from Android 14,
 * the first that can do it; before that the row isn't there.
 */
@Composable
internal fun BitPerfectRow(bitPerfect: BitPerfectOutput) {
    if (!bitPerfect.available) return
    val enabled by bitPerfect.enabled.collectAsStateWithLifecycle()
    val dac by bitPerfect.dac.collectAsStateWithLifecycle()
    val hint = dac.let { usb ->
        when {
            usb == null -> "For USB DACs. Plug one in to check it"
            usb.rates.isEmpty() -> "${usb.name} doesn't offer it on this phone"
            // Android's volume stops applying, and other sounds don't play on the DAC.
            enabled -> "${usb.name} · up to ${formatKilohertz(usb.rates.last())}. Phone volume may not apply: start with the DAC turned down"
            else -> "${usb.name} · up to ${formatKilohertz(usb.rates.last())}"
        }
    }
    SwitchRow(
        icon = Icons.Filled.Usb,
        title = "Bit-perfect USB output",
        checked = enabled,
        onCheckedChange = bitPerfect::setEnabled,
        hint = hint,
        // It warns that the phone's volume may stop applying.
        hintWhenTurnedOn = true,
    )
}
