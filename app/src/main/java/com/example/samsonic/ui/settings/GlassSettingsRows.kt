package com.example.samsonic.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BlurOn
import androidx.compose.material.icons.filled.Opacity
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.samsonic.data.ThemeManager
import com.example.samsonic.ui.theme.OneUiSlider
import kotlin.math.roundToInt

/**
 * Sliders for the global glass look: opacity of every glass surface, blur of
 * the floating frosted chrome, and blur of the Now Playing album-art backdrop.
 */
@Composable
fun GlassSliderRows(themeManager: ThemeManager) {
    val glassOpacity by themeManager.glassOpacity.collectAsStateWithLifecycle()
    val glassBlur by themeManager.glassBlur.collectAsStateWithLifecycle()
    val backdropBlur by themeManager.backdropBlur.collectAsStateWithLifecycle()

    SliderRow(
        icon = Icons.Filled.Opacity,
        title = "Glass opacity",
        valueLabel = "${(glassOpacity * 100).roundToInt()}%",
        value = glassOpacity,
        valueRange = 0.5f..1.3f,
        onValueChange = themeManager::setGlassOpacity,
    )
    SliderRow(
        icon = Icons.Filled.BlurOn,
        title = "Glass blur",
        valueLabel = "${glassBlur.value.roundToInt()}dp",
        value = glassBlur.value,
        valueRange = 0f..80f,
        onValueChange = { themeManager.setGlassBlur(it.dp) },
    )
    SliderRow(
        icon = Icons.Filled.Wallpaper,
        title = "Player background blur",
        valueLabel = "${backdropBlur.value.roundToInt()}dp",
        value = backdropBlur.value,
        valueRange = 0f..100f,
        onValueChange = { themeManager.setBackdropBlur(it.dp) },
    )
}

@Composable
internal fun SliderRow(
    icon: ImageVector,
    title: String,
    valueLabel: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(14.dp))
                Text(text = title, style = MaterialTheme.typography.bodyLarge)
            }
            Text(text = valueLabel, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        OneUiSlider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
