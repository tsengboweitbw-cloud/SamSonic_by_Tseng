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
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.samsonic.R
import com.example.samsonic.playback.BitPerfectOutput
import com.example.samsonic.playback.DsdOutputMode
import com.example.samsonic.playback.UsbDac
import com.example.samsonic.ui.player.PanelState
import dev.chrisbanes.haze.HazeState

internal val DsdOutputMode.label: String
    @Composable get() = stringResource(
        when (this) {
            DsdOutputMode.PCM -> R.string.settings_dsd_pcm
            DsdOutputMode.DOP -> R.string.settings_dsd_dop
            DsdOutputMode.NATIVE -> R.string.settings_dsd_native
        },
    )

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
        title = stringResource(R.string.settings_dsd_output),
        value = mode.label,
        hint = when {
            !hasDac -> stringResource(R.string.settings_dsd_hint_no_dac)
            !exclusive -> stringResource(R.string.settings_dsd_hint_not_exclusive)
            else -> stringResource(R.string.settings_dsd_hint)
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
    SettingsMenu(panel, haze, title = stringResource(R.string.settings_dsd_output)) {
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
@Composable
private fun supporting(mode: DsdOutputMode, exclusiveOn: Boolean, dac: UsbDac?): String {
    if (mode == DsdOutputMode.PCM) return stringResource(R.string.settings_dsd_pcm_supporting)
    val what = stringResource(if (mode == DsdOutputMode.DOP) R.string.settings_dsd_dop_what else R.string.settings_dsd_native_what)
    val now = when {
        !exclusiveOn -> stringResource(R.string.settings_dsd_needs_exclusive)
        dac == null -> stringResource(R.string.settings_dsd_no_dac)
        mode == DsdOutputMode.NATIVE && dac.nativeDsd -> stringResource(R.string.settings_dsd_native_yes, dac.name)
        mode == DsdOutputMode.NATIVE -> stringResource(R.string.settings_dsd_native_no, dac.name)
        dac.maxDop != null -> stringResource(R.string.settings_dsd_dop_max, dac.name, dac.maxDop)
        else -> stringResource(R.string.settings_dsd_dop_no, dac.name)
    }
    return stringResource(R.string.settings_sentences, what, now)
}
