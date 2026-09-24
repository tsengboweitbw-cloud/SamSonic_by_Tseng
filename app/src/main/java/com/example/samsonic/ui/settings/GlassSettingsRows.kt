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
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.samsonic.data.ThemeManager
import com.example.samsonic.ui.theme.OneUiSlider
import kotlin.math.roundToInt

private val SliderTouchHeight = 32.dp
// Extra room when a slider row is first/last in its card (see cardEdgeInset).
private val SliderEdgeInsetTop = 4.dp
private val SliderEdgeInsetBottom = 6.dp

/**
 * Sliders for the global glass look: opacity of every glass surface, blur of
 * the floating frosted chrome, blur of the Now Playing album-art backdrop, and
 * the opacity and blur of the secondary menus.
 */
@Composable
fun GlassSliderRows(themeManager: ThemeManager) {
    val glassOpacity by themeManager.glassOpacity.collectAsStateWithLifecycle()
    val glassBlur by themeManager.glassBlur.collectAsStateWithLifecycle()
    val backdropBlur by themeManager.backdropBlur.collectAsStateWithLifecycle()
    val panelOpacity by themeManager.panelOpacity.collectAsStateWithLifecycle()
    val panelBlur by themeManager.panelBlur.collectAsStateWithLifecycle()

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
    SliderRow(
        icon = Icons.Filled.Opacity,
        title = "Menu opacity",
        valueLabel = "${(panelOpacity * 100).roundToInt()}%",
        value = panelOpacity,
        valueRange = 0.5f..1.3f,
        onValueChange = themeManager::setPanelOpacity,
    )
    SliderRow(
        icon = Icons.Filled.BlurOn,
        title = "Menu blur",
        valueLabel = "${panelBlur.value.roundToInt()}dp",
        value = panelBlur.value,
        valueRange = 0f..80f,
        onValueChange = { themeManager.setPanelBlur(it.dp) },
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
    onValueChangeFinished: (() -> Unit)? = null,
    steps: Int = 0,
    // A note under the slider, e.g. when the value only applies later.
    supportingText: String? = null,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            // Against a card edge the title and the bare line sit closer than
            // other rows' content does, so ask the card for a little more room.
            .cardEdgeInset(top = SliderEdgeInsetTop, bottom = SliderEdgeInsetBottom)
            .fillMaxWidth()
            // No bottom padding: the slider's own touch area already leaves
            // room under the line.
            .padding(start = 16.dp, end = 16.dp, top = 10.dp),
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
        // Material pads every slider to a 48dp touch area; in a settings list
        // that leaves the line floating over a big gap. 32dp is still easy to
        // drag. (Now Playing's seek bar keeps the full 48dp.)
        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides SliderTouchHeight) {
            OneUiSlider(
                value = value,
                onValueChange = onValueChange,
                onValueChangeFinished = onValueChangeFinished,
                valueRange = valueRange,
                steps = steps,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (supportingText != null) {
            Text(
                text = supportingText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 10.dp),
            )
        }
    }
}
