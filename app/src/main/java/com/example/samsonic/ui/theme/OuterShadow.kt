package com.example.samsonic.ui.theme

import android.graphics.BlurMaskFilter
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.unit.Dp

/**
 * A soft drop shadow drawn only outside [shape], for translucent surfaces.
 *
 * [Modifier.shadow]'s elevation shadow also lies under the surface itself, which an
 * opaque surface hides; through glass it shows, along with the seam in the middle of
 * the shadow's geometry (a light bar across a long, thin card). This one is clipped
 * to the outside of the outline, so the glass shows only what's really behind it.
 */
fun Modifier.outerShadow(elevation: Dp, shape: Shape, color: Color): Modifier = drawBehind {
    val radius = elevation.toPx()
    if (radius <= 0f || color.alpha <= 0f) return@drawBehind
    val outline = Path().apply { addOutline(shape.createOutline(size, layoutDirection, this@drawBehind)) }
    // Cast a little downward, as if lit from above, like an elevation shadow.
    val cast = Path().apply {
        addPath(outline)
        translate(Offset(0f, radius / 2f))
    }
    val paint = Paint().apply {
        this.color = color
        asFrameworkPaint().maskFilter = BlurMaskFilter(radius, BlurMaskFilter.Blur.NORMAL)
    }
    clipPath(outline, clipOp = ClipOp.Difference) {
        drawIntoCanvas { it.drawPath(cast, paint) }
    }
}
