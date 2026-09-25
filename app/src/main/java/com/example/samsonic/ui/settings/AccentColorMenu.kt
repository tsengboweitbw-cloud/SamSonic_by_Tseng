package com.example.samsonic.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.samsonic.ui.player.PanelState
import com.example.samsonic.ui.theme.OneUiSlider
import com.example.samsonic.ui.theme.accentPaletteOf
import com.example.samsonic.ui.theme.parseHexColor
import com.example.samsonic.ui.theme.toHexRgb
import dev.chrisbanes.haze.HazeState
import kotlin.math.roundToInt

/**
 * The Accent color setting's secondary menu ([SettingsMenu]), grown out of its
 * row like the Theme menu: a preview, a hex field and RGB sliders. Apply saves
 * the color and folds the menu back; Cancel, back or a tap outside drops it.
 */
@Composable
internal fun AccentColorMenu(
    panel: PanelState,
    haze: HazeState,
    initialColor: Color,
    defaultColor: Color,
    onConfirm: (Color) -> Unit,
) {
    SettingsMenu(panel, haze, title = "Accent Color") {
        // Inside the menu's content, so every open starts again from the saved color.
        var workingColor by remember { mutableStateOf(initialColor) }
        var hexText by remember { mutableStateOf(initialColor.toHexRgb()) }

        fun applyColor(color: Color) {
            workingColor = color
            hexText = color.toHexRgb()
        }

        Column(Modifier.padding(horizontal = 24.dp)) {
            Spacer(Modifier.height(8.dp))
            // The color being picked, and the three companions the app pairs with it.
            val darkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
            val companions = accentPaletteOf(workingColor, darkTheme).all.drop(1)
            Row(
                modifier = Modifier.align(Alignment.CenterHorizontally),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Swatch(workingColor, 56.dp)
                companions.forEach { Swatch(it, 32.dp) }
            }
            Spacer(Modifier.height(20.dp))

            OutlinedTextField(
                value = hexText,
                onValueChange = { input ->
                    val filtered = input.filter { it in "0123456789abcdefABCDEF" }.take(6)
                    hexText = filtered
                    parseHexColor(filtered)?.let { workingColor = it }
                },
                label = { Text("Hex") },
                prefix = { Text("#") },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(20.dp))

            ColorChannelSlider("R", workingColor.red) { applyColor(workingColor.copy(red = it)) }
            ColorChannelSlider("G", workingColor.green) { applyColor(workingColor.copy(green = it)) }
            ColorChannelSlider("B", workingColor.blue) { applyColor(workingColor.copy(blue = it)) }
            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = { applyColor(defaultColor) }) { Text("Reset") }
                Row {
                    TextButton(onClick = { panel.close() }) { Text("Cancel") }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            onConfirm(workingColor)
                            panel.close()
                        },
                    ) { Text("Apply") }
                }
            }
        }
    }
}

@Composable
private fun Swatch(color: Color, size: Dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(color)
            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
    )
}

@Composable
private fun ColorChannelSlider(label: String, value: Float, onValueChange: (Float) -> Unit) {
    // Label is end-aligned and the value start-aligned in fixed-width boxes,
    // so both gaps around the slider are exactly [gap] wide, while the
    // sliders of all three rows still start and end at the same x.
    val gap = 12.dp
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            textAlign = TextAlign.End,
            modifier = Modifier.width(16.dp),
        )
        Spacer(Modifier.width(gap))
        OneUiSlider(
            value = value,
            onValueChange = onValueChange,
            valueRange = 0f..1f,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(gap))
        Text(
            text = (value * 255).roundToInt().toString(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(32.dp),
        )
    }
}
