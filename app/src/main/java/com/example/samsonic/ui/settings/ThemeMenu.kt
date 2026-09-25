package com.example.samsonic.ui.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.example.samsonic.R
import com.example.samsonic.data.ThemeMode
import com.example.samsonic.ui.player.PanelState
import dev.chrisbanes.haze.HazeState

internal val ThemeMode.label: String
    @Composable get() = stringResource(
        when (this) {
            ThemeMode.SYSTEM -> R.string.settings_theme_system
            ThemeMode.LIGHT -> R.string.settings_theme_light
            ThemeMode.DARK -> R.string.settings_theme_dark
        },
    )

private val ThemeMode.icon: ImageVector
    get() = when (this) {
        ThemeMode.SYSTEM -> Icons.Filled.BrightnessAuto
        ThemeMode.LIGHT -> Icons.Filled.LightMode
        ThemeMode.DARK -> Icons.Filled.DarkMode
    }

/**
 * The Theme setting's secondary menu ([SettingsMenu]), listing the modes. The
 * [current] one wears the playing song row's highlight; picking a mode applies
 * it and folds the menu back into the Theme row.
 */
@Composable
internal fun ThemeMenu(panel: PanelState, haze: HazeState, current: ThemeMode, onSelect: (ThemeMode) -> Unit) {
    SettingsMenu(panel, haze, title = stringResource(R.string.settings_theme)) {
        ThemeMode.entries.forEach { mode ->
            MenuOption(icon = mode.icon, label = mode.label, selected = mode == current, onClick = {
                onSelect(mode)
                panel.close()
            })
        }
    }
}
