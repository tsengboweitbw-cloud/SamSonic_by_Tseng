package com.example.samsonic.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A soft shadow or glow of [color] around a round element, [offsetY] lower, fading out
 * over [width]: drawn only outside the circle, never under it.
 *
 * For see-through (glass) circles in place of Modifier.shadow: Android draws an elevation
 * shadow's solid core as a polygon, hidden under an opaque surface but showing through a
 * translucent one as a faint octagon.
 */
internal fun Modifier.circleGlow(color: Color, width: Dp, offsetY: Dp = 0.dp): Modifier = drawBehind {
    val radius = size.minDimension / 2
    val shift = offsetY.toPx()
    val glowCenter = center + Offset(0f, shift)
    val outer = radius + width.toPx()
    // Start far enough in that the shifted glow still meets the circle's edge all round.
    val start = ((radius - shift) / outer).coerceIn(0f, 1f)
    val hole = Path().apply { addOval(Rect(center, radius)) }
    clipPath(hole, ClipOp.Difference) {
        drawCircle(
            brush = Brush.radialGradient(
                start to color,
                // Falling away fast from the edge, as a real shadow does, so it has no bright rim.
                start + (1f - start) * 0.25f to color.copy(alpha = color.alpha * 0.45f),
                start + (1f - start) * 0.6f to color.copy(alpha = color.alpha * 0.12f),
                1f to Color.Transparent,
                center = glowCenter,
                radius = outer,
            ),
            radius = outer,
            center = glowCenter,
        )
    }
}
