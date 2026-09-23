package com.example.samsonic.ui.theme

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp

/**
 * One UI 9.0 seek/scrub bar: a thin line with no visible thumb at rest, and a
 * round glowing thumb that only appears while actively pressed/dragging -
 * matching Samsung's Now Playing scrubber, rather than Material3's default
 * always-visible pill thumb + thick track + stop-indicator dot. Shared by
 * every slider in the app (Now Playing's seek bar, Settings' roundness
 * slider, the accent picker's R/G/B sliders) for one consistent look.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OneUiSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    onValueChangeFinished: (() -> Unit)? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val dragged by interactionSource.collectIsDraggedAsState()
    val pressed by interactionSource.collectIsPressedAsState()
    val active = dragged || pressed

    val activeColor = MaterialTheme.colorScheme.primary
    val inactiveColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val thumbDiameter by animateDpAsState(if (active) 18.dp else 1.dp, label = "OneUiSliderThumb")

    Slider(
        value = value,
        onValueChange = onValueChange,
        onValueChangeFinished = onValueChangeFinished,
        valueRange = valueRange,
        modifier = modifier,
        interactionSource = interactionSource,
        colors = SliderDefaults.colors(
            thumbColor = activeColor,
            activeTrackColor = activeColor,
            inactiveTrackColor = inactiveColor,
        ),
        thumb = {
            Box(
                modifier = Modifier
                    .size(thumbDiameter)
                    .then(
                        if (active) {
                            Modifier.shadow(
                                elevation = 8.dp,
                                shape = CircleShape,
                                ambientColor = activeColor,
                                spotColor = activeColor,
                            )
                        } else {
                            Modifier
                        },
                    )
                    .background(activeColor, CircleShape),
            )
        },
        track = { sliderState ->
            val fraction = sliderState.coercedValueAsFraction
            Box(
                modifier = Modifier.fillMaxWidth().height(20.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (active) {
                    // Soft glow behind the active portion while scrubbing - a
                    // real native blur (matching how the rest of the app uses
                    // blur), not a faked gradient halo.
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fraction.coerceIn(0.03f, 1f))
                            .height(6.dp)
                            .blur(12.dp)
                            .background(activeColor, CircleShape),
                    )
                }
                Canvas(modifier = Modifier.fillMaxWidth().height(20.dp)) {
                    val strokeWidthPx = 3.dp.toPx()
                    val y = size.height / 2f
                    drawLine(
                        color = inactiveColor,
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = strokeWidthPx,
                        cap = StrokeCap.Round,
                    )
                    drawLine(
                        color = activeColor,
                        start = Offset(0f, y),
                        end = Offset(size.width * fraction, y),
                        strokeWidth = strokeWidthPx,
                        cap = StrokeCap.Round,
                    )
                }
            }
        },
    )
}
