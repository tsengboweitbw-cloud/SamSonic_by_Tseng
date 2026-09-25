package com.example.samsonic.ui.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Usb
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.samsonic.R
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
        usb == null -> stringResource(R.string.settings_exclusive_usb_hint_no_dac)
        usb.rates.isEmpty() -> stringResource(R.string.settings_exclusive_usb_hint_unsupported, usb.name)
        // Android's volume stops applying, and other sounds don't play on the DAC.
        enabled -> stringResource(R.string.settings_exclusive_usb_hint_on, usb.name, formatKilohertz(usb.rates.last()))
        else -> stringResource(R.string.settings_exclusive_usb_hint_off, usb.name, formatKilohertz(usb.rates.last()))
    }
    SwitchRow(
        icon = Icons.Filled.Usb,
        title = stringResource(R.string.settings_exclusive_usb),
        checked = enabled,
        onCheckedChange = bitPerfect::setEnabled,
        hint = hint,
        // It warns that the phone's volume may stop applying.
        hintWhenTurnedOn = true,
        enabled = usb != null,
    )
}
