package com.example.samsonic.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.samsonic.data.ThemeMode
import com.example.samsonic.ui.player.PanelState
import com.example.samsonic.ui.theme.GlassAlpha
import com.example.samsonic.ui.theme.OneUiRow
import com.example.samsonic.ui.theme.glassSurface
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
            ThemeOption(mode, selected = mode == current) {
                onSelect(mode)
                panel.close()
            }
        }
    }
}

/** One mode, highlighted when [selected] exactly as SongRow marks the playing song. */
@Composable
private fun ThemeOption(mode: ThemeMode, selected: Boolean, onClick: () -> Unit) {
    val accent = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
    Row(
        modifier = Modifier
            .padding(OneUiRow.Inset)
            .fillMaxWidth()
            .clip(OneUiRow.Shape)
            .then(
                if (selected) {
                    Modifier.glassSurface(
                        shape = OneUiRow.Shape,
                        hazeState = null,
                        tint = MaterialTheme.colorScheme.primary,
                        alpha = GlassAlpha.Highlight,
                    )
                } else {
                    Modifier
                },
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = mode.icon,
            contentDescription = null,
            tint = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(14.dp))
        Text(
            text = mode.label,
            style = MaterialTheme.typography.bodyLarge,
            color = accent,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}
