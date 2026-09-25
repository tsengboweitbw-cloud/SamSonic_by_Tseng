package com.example.samsonic.ui.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Transform
import androidx.compose.material.icons.filled.Waves
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.samsonic.playback.BitPerfectOutput
import com.example.samsonic.playback.DsdOutputMode
import com.example.samsonic.playback.UsbDac
import com.example.samsonic.ui.player.PanelState
import dev.chrisbanes.haze.HazeState

internal val DsdOutputMode.label: String
    get() = when (this) {
        DsdOutputMode.PCM -> "Convert to PCM"
        DsdOutputMode.DOP -> "DoP"
        DsdOutputMode.NATIVE -> "Native DSD"
    }

private val DsdOutputMode.icon: ImageVector
    get() = when (this) {
        DsdOutputMode.PCM -> Icons.Filled.Transform
        DsdOutputMode.DOP -> Icons.Filled.Inventory2
        DsdOutputMode.NATIVE -> Icons.Filled.Bolt
    }

/**
 * The DSD output row, under the exclusive mode switch: how DSD files go out, opening
 * [menu]. Dimmed and closed while it can't apply: no USB DAC, or exclusive mode off.
 * Before Android 14, like the switch it needs, it isn't there.
 */
@Composable
internal fun DsdOutputRow(bitPerfect: BitPerfectOutput, menu: PanelState) {
    if (!bitPerfect.available) return
    val mode by bitPerfect.dsdMode.collectAsStateWithLifecycle()
    val hasDac = bitPerfect.dac.collectAsStateWithLifecycle().value != null
    val exclusive by bitPerfect.enabled.collectAsStateWithLifecycle()
    NavRow(
        icon = Icons.Filled.Waves,
        title = "DSD output",
        value = mode.label,
        hint = when {
            !hasDac -> "Plug in a USB DAC to choose how DSD files go to it"
            !exclusive -> "Turn on Exclusive USB output to choose how DSD files go to the DAC"
            else -> "How DSD files go to the USB DAC. DoP and native DSD need a DAC that decodes DSD"
        },
        enabled = hasDac && exclusive,
        onClick = { menu.open() },
        modifier = Modifier.menuOrigin(menu),
    )
}

/**
 * The DSD output setting's secondary menu ([SettingsMenu]); each choice's line says what
 * the plugged-in DAC takes. A DSD file the DAC can't take the chosen way falls back: native
 * DSD to DoP, DoP to PCM.
 */
@Composable
internal fun DsdOutputMenu(panel: PanelState, haze: HazeState, bitPerfect: BitPerfectOutput) {
    val current by bitPerfect.dsdMode.collectAsStateWithLifecycle()
    val enabled by bitPerfect.enabled.collectAsStateWithLifecycle()
    val dac by bitPerfect.dac.collectAsStateWithLifecycle()
    SettingsMenu(panel, haze, title = "DSD output") {
        DsdOutputMode.entries.forEach { mode ->
            MenuOption(
                icon = mode.icon,
                label = mode.label,
                selected = mode == current,
                supporting = supporting(mode, enabled, dac),
                onClick = {
                    bitPerfect.setDsdMode(mode)
                    panel.close()
                },
            )
        }
    }
}

/** What [mode] does, and whether the plugged-in [dac] can take it now. */
private fun supporting(mode: DsdOutputMode, exclusiveOn: Boolean, dac: UsbDac?): String {
    if (mode == DsdOutputMode.PCM) return "Filtered to 88.2 kHz PCM in the app. Plays anywhere"
    val what = if (mode == DsdOutputMode.DOP) "DSD packed in PCM, for the DAC to unpack" else "Raw DSD, as Android offers it"
    val now = when {
        !exclusiveOn -> "Needs Exclusive USB output"
        dac == null -> "Plug in a DAC to check it"
        mode == DsdOutputMode.NATIVE && dac.nativeDsd -> "${dac.name} takes it; DoP where it doesn't"
        mode == DsdOutputMode.NATIVE -> "${dac.name} doesn't take it; DoP instead"
        dac.maxDop != null -> "${dac.name} · up to DSD${dac.maxDop}"
        else -> "${dac.name} doesn't take it; PCM instead"
    }
    return "$what. $now"
}
