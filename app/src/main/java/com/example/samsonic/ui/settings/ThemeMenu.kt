package com.example.samsonic.ui.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.samsonic.data.ThemeMode
import com.example.samsonic.ui.player.PanelState
import dev.chrisbanes.haze.HazeState

internal val ThemeMode.label: String
    get() = when (this) {
        ThemeMode.SYSTEM -> "System"
        ThemeMode.LIGHT -> "Light"
        ThemeMode.DARK -> "Dark"
    }

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
    SettingsMenu(panel, haze, title = "Theme") {
        ThemeMode.entries.forEach { mode ->
            MenuOption(icon = mode.icon, label = mode.label, selected = mode == current, onClick = {
                onSelect(mode)
                panel.close()
            })
        }
    }
}
