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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.samsonic.ui.theme.LocalHazeState
import com.example.samsonic.ui.theme.GlassAlpha
import com.example.samsonic.ui.theme.OneUiRadius
import com.example.samsonic.ui.theme.OneUiSlider
import com.example.samsonic.ui.theme.glassSurface
import com.example.samsonic.ui.theme.parseHexColor
import com.example.samsonic.ui.theme.toHexRgb
import kotlin.math.roundToInt

@Composable
fun AccentColorPickerDialog(
    initialColor: Color,
    defaultColor: Color,
    onDismiss: () -> Unit,
    onConfirm: (Color) -> Unit,
) {
    var workingColor by remember { mutableStateOf(initialColor) }
    var hexText by remember { mutableStateOf(initialColor.toHexRgb()) }

    fun applyColor(color: Color) {
        workingColor = color
        hexText = color.toHexRgb()
    }

    // Grabbed outside the Dialog: its content lives in a separate window, but
    // Haze can still sample the main window's hazeSource from there, so
    // only the panel itself is frosted - the rest of the screen stays sharp.
    val hazeState = LocalHazeState.current

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 28.dp)
                .glassSurface(
                    shape = RoundedCornerShape(OneUiRadius.Card),
                    hazeState = hazeState,
                    tint = MaterialTheme.colorScheme.surfaceContainerHigh,
                    alpha = GlassAlpha.Sheet,
                    // Fixed, stronger than the chrome's user-set blur: a modal
                    // panel must stay readable at any slider setting.
                    blurRadius = 16.dp,
                    scaleOpacity = false,
                )
                .padding(24.dp),
        ) {
            Text(text = "Accent Color", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(20.dp))

            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(workingColor)
                    .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
            )
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
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = { onConfirm(workingColor) }) { Text("Apply") }
                }
            }
        }
    }
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
