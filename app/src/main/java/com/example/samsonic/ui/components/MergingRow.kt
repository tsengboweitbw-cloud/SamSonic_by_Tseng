package com.example.samsonic.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.util.lerp
import kotlin.math.roundToInt
import com.example.samsonic.ui.theme.AccentSheen
import com.example.samsonic.ui.theme.LocalChromeBlurScale
import com.example.samsonic.ui.theme.GlassAlpha
import com.example.samsonic.ui.theme.GlassRimWidth
import com.example.samsonic.ui.theme.glassRimBrush
import com.example.samsonic.ui.theme.glassSurface

// Merged into the capsule, each button shrinks to this, this far from the next, and the
// capsule wraps them with this much padding.
internal val MergedButtonSize = 44.dp
internal val MergedGap = 2.dp
internal val CapsulePadding = 4.dp

// Pinned at the top, the capsule's left edge: past the back button (16dp in, 48dp wide) and a gap.
internal val PinnedCapsuleStart = 16.dp + ChromeButtonSize + 8.dp

/**
 * The buttons in a centred row that, as [merge] goes from 0 to 1 (a floating row
 * pinning, see [FloatingListActions]), slide together and shrink into one glass
 * capsule drawn in behind them: the chrome's blurred glass where the row has a haze
 * to blur ([LocalListActionsHaze]), else a flat fill. [merge] is read only in layout
 * and draw.
 */
@Composable
internal fun MergingRow(merge: () -> Float, modifier: Modifier, content: @Composable () -> Unit) {
    val tint = MaterialTheme.colorScheme.surfaceContainerHigh
    val rimBrush = glassRimBrush()
    // Docked on its own (standing at the side, or in the corner capsule), merged and sized
    // to fit; the row's own modifier (its side margins in a page) doesn't apply there.
    when (LocalListActionsDock.current) {
        DockShape.ROW -> return MergedCapsule(vertical = false, tint, content)
        DockShape.COLUMN -> return MergedCapsule(vertical = true, tint, content)
        DockShape.NONE -> {}
    }
    val haze = LocalListActionsHaze.current
    val gather = LocalListActionsGather.current
    // How many buttons and where the glass sits in the row, handed from layout to draw.
    val count = remember { IntArray(1) }
    val glassLeft = remember { FloatArray(1) }
    val window = remember { Path() }
    Layout(
        content = {
            if (haze != null) {
                // Laid out once at the widest the capsule shows (as it starts to fade in) and
                // never resized, so its blur isn't rebuilt every frame; the merge only moves a
                // rounded window over it, and the rim along that window.
                Box(
                    Modifier
                        .drawWithContent {
                            val m = merge()
                            val (left, width) = rowSpan(count[0], size.width + glassLeft[0] * 2, m)
                            val pad = CapsulePadding.toPx()
                            val x = left - pad - glassLeft[0]
                            val w = width + pad * 2
                            val radius = CornerRadius(size.height / 2)
                            window.rewind()
                            window.addRoundRect(RoundRect(x, 0f, x + w, size.height, radius))
                            clipPath(window) { this@drawWithContent.drawContent() }
                            val inset = GlassRimWidth.toPx() / 2
                            drawRoundRect(
                                brush = rimBrush,
                                topLeft = Offset(x + inset, inset),
                                size = Size(w - inset * 2, size.height - inset * 2),
                                cornerRadius = CornerRadius(size.height / 2 - inset),
                                style = Stroke(GlassRimWidth.toPx()),
                            )
                        }
                        .glassSurface(
                            shape = RoundedCornerShape(percent = 50),
                            hazeState = haze,
                            tint = tint,
                            alpha = GlassAlpha.Nav,
                            rim = false,
                            sheen = AccentSheen.Chrome,
                            inputScale = LocalChromeBlurScale.current,
                        ),
                )
            }
            content()
        },
        modifier = Modifier
            // Pinning, the capsule moves from the middle to stand beside the back button.
            .graphicsLayer {
                val m = merge()
                if (m > 0f && count[0] > 0) {
                    val span = count[0] * MergedButtonSize.toPx() + (count[0] - 1) * MergedGap.toPx()
                    val capsuleLeft = (size.width - span) / 2 - CapsulePadding.toPx()
                    translationX = m * (PinnedCapsuleStart.toPx() - capsuleLeft)
                }
            }
            .then(modifier)
            .fillMaxWidth()
            .then(
                if (haze != null) {
                    Modifier
                } else {
                    Modifier.drawBehind {
                        val m = merge()
                        val alpha = ramp(m, CapsuleFadeStart, 1f)
                        if (alpha <= 0f || count[0] == 0) return@drawBehind
                        val (left, width) = rowSpan(count[0], size.width, m)
                        val pad = CapsulePadding.toPx()
                        val height = MergedButtonSize.toPx() + pad * 2
                        val topLeft = Offset(left - pad, (size.height - height) / 2)
                        val capsule = Size(width + pad * 2, height)
                        drawRoundRect(tint, topLeft, capsule, CornerRadius(height / 2), alpha = GlassAlpha.Nav * alpha)
                        val inset = GlassRimWidth.toPx() / 2
                        drawRoundRect(
                            brush = rimBrush,
                            topLeft = topLeft + Offset(inset, inset),
                            size = Size(capsule.width - inset * 2, capsule.height - inset * 2),
                            cornerRadius = CornerRadius(height / 2 - inset),
                            alpha = alpha,
                            style = Stroke(GlassRimWidth.toPx()),
                        )
                    }
                },
            ),
    ) { measurables, constraints ->
        val size = ListActionButtonSize.roundToPx()
        val glass = if (haze != null) measurables.first() else null
        val buttons = measurables.drop(if (glass != null) 1 else 0).map { it.measure(Constraints.fixed(size, size)) }
        count[0] = buttons.size
        val rowWidth = constraints.maxWidth.toFloat()
        val glassPlaceable = glass?.let {
            val (left, width) = rowSpan(buttons.size, rowWidth, CapsuleFadeStart)
            val pad = CapsulePadding.toPx()
            glassLeft[0] = left - pad
            it.measure(Constraints.fixed((width + pad * 2).roundToInt(), (MergedButtonSize.toPx() + pad * 2).roundToInt()))
        }
        layout(constraints.maxWidth, size) {
            val m = merge()
            // Only once it starts to show: its blur costs a frame's work even unseen.
            if (glassPlaceable != null && m > CapsuleFadeStart) {
                glassPlaceable.placeWithLayer(glassLeft[0].roundToInt(), (size - glassPlaceable.height) / 2) {
                    alpha = ramp(m, CapsuleFadeStart, 1f)
                }
            }
            val scale = lerp(1f, MergedButtonSize / ListActionButtonSize, m)
            val (left, _) = rowSpan(buttons.size, rowWidth, m)
            val width = size * scale
            val gap = lerp(ListActionButtonGap.toPx(), MergedGap.toPx(), m)
            // Heading for the corner button: each button slides to its spot and shrinks to its size,
            // stacking up with Play on top.
            val g = gather?.progress ?: 0f
            val cornerScale = lerp(1f, ChromeButtonSize / ListActionButtonSize, g)
            // Arrives sooner than the shrinking ends, so the stack is whole before the corner button takes over.
            val reach = 1f - (1f - g) * (1f - g)
            buttons.forEachIndexed { i, button ->
                // Scaled about its centre, so it's placed where its centre goes.
                val centre = left + i * (width + gap) + width / 2
                var x = centre - size / 2f
                var y = 0f
                if (gather != null && g > 0f) {
                    val targetX = gather.x - (gather.rowWidth - constraints.maxWidth) / 2f
                    x = lerp(x, targetX - size / 2f, reach)
                    y = lerp(0f, gather.y - size / 2f, reach)
                }
                button.placeWithLayer(x.roundToInt(), y.roundToInt(), zIndex = (buttons.size - i).toFloat()) {
                    scaleX = scale * cornerScale
                    scaleY = scale * cornerScale
                }
            }
        }
    }
}

// How far into the merge the capsule starts to fade in behind the buttons.
internal const val CapsuleFadeStart = 0.3f

/**
 * The buttons merged into a glass capsule sized to fit them: standing, top to bottom,
 * if [vertical], else lying, left to right.
 */
@Composable
internal fun MergedCapsule(vertical: Boolean, tint: Color, content: @Composable () -> Unit) {
    Layout(
        content = content,
        // The chrome's glass (the nav bar's and mini player's): its blur of the page, its
        // opacity and its accent sheen.
        modifier = Modifier.glassSurface(
            shape = RoundedCornerShape(percent = 50),
            hazeState = LocalListActionsHaze.current,
            tint = tint,
            alpha = GlassAlpha.Nav,
            sheen = AccentSheen.Chrome,
            inputScale = LocalChromeBlurScale.current,
        ),
    ) { measurables, _ ->
        val size = ListActionButtonSize.roundToPx()
        val buttons = measurables.map { it.measure(Constraints.fixed(size, size)) }
        val pad = CapsulePadding.toPx()
        val button = MergedButtonSize.toPx()
        val gap = MergedGap.toPx()
        val thickness = (button + pad * 2).roundToInt()
        val length = (buttons.size * button + (buttons.size - 1).coerceAtLeast(0) * gap + pad * 2).roundToInt()
        layout(if (vertical) thickness else length, if (vertical) length else thickness) {
            val scale = MergedButtonSize / ListActionButtonSize
            buttons.forEachIndexed { i, b ->
                // Scaled about its centre, so it's placed where its centre goes.
                val along = (pad + i * (button + gap) + button / 2 - size / 2f).roundToInt()
                val across = ((thickness - size) / 2f).roundToInt()
                b.placeWithLayer(if (vertical) across else along, if (vertical) along else across) {
                    scaleX = scale
                    scaleY = scale
                }
            }
        }
    }
}

/** How long a capsule of [count] merged buttons is ([MergedCapsule]): its height standing. */
internal fun mergedCapsuleLength(count: Int): Dp = MergedButtonSize * count + MergedGap * (count - 1) + CapsulePadding * 2

/** How thick a capsule of merged buttons is ([MergedCapsule]): its height lying. */
internal val MergedCapsuleThickness = MergedButtonSize + CapsulePadding * 2

/** Where [count] buttons, [merge] of the way to merged, start in a [rowWidth] row, and how wide they span. */
internal fun Density.rowSpan(count: Int, rowWidth: Float, merge: Float): Pair<Float, Float> {
    val width = lerp(ListActionButtonSize.toPx(), MergedButtonSize.toPx(), merge)
    val gap = lerp(ListActionButtonGap.toPx(), MergedGap.toPx(), merge)
    val span = count * width + (count - 1) * gap
    return (rowWidth - span) / 2 to span
}

/** 0 up to [from], 1 from [to], and linear between. */
private fun ramp(value: Float, from: Float, to: Float): Float = ((value - from) / (to - from)).coerceIn(0f, 1f)
