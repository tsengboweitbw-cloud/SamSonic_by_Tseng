package com.example.samsonic.ui.player

import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.example.samsonic.ui.theme.GlassAlpha
import kotlinx.coroutines.CoroutineScope
import kotlin.math.roundToInt

/**
 * A panel over Now Playing (lyrics, the queue, song info) that grows out of the
 * round button opening it: 0 = folded into the button, 1 = fully open.
 */
@Stable
class PanelState internal constructor(scope: CoroutineScope) {
    private val track = SpringTrack(scope)

    val progress: Float get() = track.position.coerceAtLeast(0f)

    /**
     * How far the close spring has dipped past folded (0 at rest). The panel is gone by
     * then, so its button plays this part: it squeezes a little and springs back.
     */
    val landing: Float get() = (-track.position).coerceAtLeast(0f)

    /** Whether the panel is open or heading there. */
    var isOpen by mutableStateOf(false)
        private set

    private var originState by mutableStateOf(Rect.Zero)
    private var travelMeasured = false

    /** The opening button's bounds in root coordinates; set by the button. */
    internal var origin: Rect
        get() = originState
        set(value) {
            originState = value
            // Until the panel has laid out once, estimate its top as the screen's: without
            // any travel, a first drag up would throw it fully open in a single step.
            if (!travelMeasured) track.travelPx = value.top.coerceAtLeast(1f)
        }

    /** Pixels the panel's top edge travels from the button's to its own; set by its layout. */
    internal var travelPx: Float
        get() = track.travelPx
        set(value) {
            travelMeasured = true
            track.travelPx = value
        }

    fun open() = settleTo(1f)

    fun close() = settleTo(0f)

    internal fun snapTo(target: Float) = track.snapTo(target)

    internal fun dragBy(deltaPx: Float) = track.dragBy(deltaPx)

    internal fun stop() = track.stop()

    /** Settles after a drag released with [velocityPx] (px/s, positive = downward). */
    internal fun settle(velocityPx: Float) = settleTo(track.targetFor(velocityPx), velocityPx)

    /** Folded away at once, for when the whole sheet collapses. */
    internal fun reset() {
        track.snapTo(0f)
        isOpen = false
    }

    private fun settleTo(target: Float, velocityPx: Float = 0f) {
        isOpen = target == 1f
        // Both overshoot and spring back: open grows a touch past the panel's bounds,
        // close dips past folded, which the button shows as [landing].
        track.animateTo(target, velocityPx, if (isOpen) OpenSpring else CloseSpring)
    }
}

// Visibly underdamped; closing a little less so, as a small landing bounce.
// A spring's duration goes with 1/sqrt(stiffness): 269 runs 25% longer than 420.
private val OpenSpring = spring<Float>(dampingRatio = 0.68f, stiffness = 269f)
private val CloseSpring = spring<Float>(dampingRatio = 0.72f, stiffness = 269f)

private val IconSize = 22.dp

/** Maps [value] from [start]..[end] onto 0..1, clamped. */
private fun ramp(value: Float, start: Float, end: Float) = ((value - start) / (end - start)).coerceIn(0f, 1f)

/**
 * Draws [content] as [panel], morphing from its button into this element's own
 * bounds: the button's circle grows and its corners ease to [radius], its glass
 * veil gives way to [surface] (a background modifier, such as a glass surface),
 * and [icon] rides the shape's center, fading as the content fades in. Its top
 * edge moves with [panel]'s drags, so with [dragToClose] a pull down on the
 * panel (or past the top of its list) folds it back up.
 * Not composed while folded away.
 */
@Composable
internal fun MorphPanel(
    panel: PanelState,
    icon: ImageVector,
    surface: Modifier,
    modifier: Modifier = Modifier,
    radius: Dp = 0.dp,
    dragToClose: Boolean = true,
    content: @Composable () -> Unit,
) {
    val showing by remember(panel) { derivedStateOf { panel.progress > 0f } }
    if (!showing) return
    val veil = MaterialTheme.colorScheme.onSurface.copy(alpha = GlassAlpha.NowPlaying)
    val iconTint = MaterialTheme.colorScheme.onSurface
    val density = LocalDensity.current
    val radiusPx = with(density) { radius.toPx() }
    val iconPx = with(density) { IconSize.toPx() }
    // This element's top-left in root coordinates (to bring the button's bounds into
    // local ones) and its size; a plain holder, read only in layout and draw.
    val placed = remember { floatArrayOf(0f, 0f, 0f, 0f) }
    fun bounds(size: Size): Pair<Rect, Float> {
        val full = Rect(Offset.Zero, size)
        val from = panel.origin.takeIf { it != Rect.Zero }?.translate(-placed[0], -placed[1]) ?: full
        val t = panel.progress
        // t overshoots past 1 on an elastic open, so the shape briefly grows past `full`.
        return lerp(from, full, t) to lerp(from.minDimension / 2, radiusPx, t).coerceAtLeast(0f)
    }
    val pullDown = remember(panel) { PanelPullDown(panel) }
    val dragState = rememberDraggableState { panel.dragBy(it) }
    Box(
        modifier
            .onGloballyPositioned {
                val position = it.positionInRoot()
                placed[0] = position.x
                placed[1] = position.y
                placed[2] = it.size.width.toFloat()
                placed[3] = it.size.height.toFloat()
                // A drag moves the top edge with the finger, from the button's top to ours.
                panel.travelPx = (panel.origin.top - position.y).coerceAtLeast(1f)
            }
            .then(
                if (dragToClose) {
                    Modifier
                        .nestedScroll(pullDown)
                        .draggable(
                            state = dragState,
                            orientation = Orientation.Vertical,
                            onDragStarted = { panel.stop() },
                            onDragStopped = { velocity -> panel.settle(velocity) },
                        )
                } else {
                    Modifier
                },
            ),
    ) {
        // The shape itself, laid out at the in-between bounds rather than drawn inside this
        // element, so early on it can reach past this element's edges to where the button is.
        Box(
            Modifier
                .matchParentSize()
                .layout { measurable, constraints ->
                    val (rect, _) = bounds(Size(constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat()))
                    val placeable = measurable.measure(
                        Constraints.fixed(rect.width.roundToInt().coerceAtLeast(0), rect.height.roundToInt().coerceAtLeast(0)),
                    )
                    layout(constraints.maxWidth, constraints.maxHeight) {
                        placeable.place(rect.left.roundToInt(), rect.top.roundToInt())
                    }
                }
                .graphicsLayer {
                    shape = RoundedCornerShape(bounds(Size(placed[2], placed[3])).second)
                    clip = true
                }
                .drawBehind { drawRect(veil.copy(alpha = veil.alpha * (1f - ramp(panel.progress, 0f, 0.5f)))) },
        ) {
            Box(Modifier.fillMaxSize().graphicsLayer { alpha = ramp(panel.progress, 0f, 0.5f) }.then(surface))
        }
        // The content stays laid out at full size, revealed through the growing shape.
        Box(
            Modifier.graphicsLayer {
                val (rect, corner) = bounds(size)
                shape = MorphShape(rect, corner)
                clip = true
                alpha = ramp(panel.progress, 0.25f, 0.7f)
            },
        ) {
            content()
        }
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier
                .offset {
                    val center = bounds(Size(placed[2], placed[3])).first.center
                    IntOffset((center.x - iconPx / 2).roundToInt(), (center.y - iconPx / 2).roundToInt())
                }
                .size(IconSize)
                .graphicsLayer { alpha = 1f - ramp(panel.progress, 0f, 0.3f) },
        )
    }
}

/** A rounded [rect] inside the layer, rather than the layer's whole bounds. */
private class MorphShape(private val rect: Rect, private val radius: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density) =
        Outline.Rounded(RoundRect(rect, CornerRadius(radius)))
}

/**
 * Lets a panel's list hand its overscroll to the panel: pulled down past the top,
 * the panel follows the finger down; pushed back up, the panel grows before the
 * list scrolls. A release settles the panel open or closed.
 */
private class PanelPullDown(private val panel: PanelState) : NestedScrollConnection {
    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset =
        if (source == NestedScrollSource.UserInput && available.y < 0f && panel.progress < 1f) move(available.y) else Offset.Zero

    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset =
        if (source == NestedScrollSource.UserInput && available.y > 0f) move(available.y) else Offset.Zero

    override suspend fun onPreFling(available: Velocity): Velocity {
        if (panel.progress >= 1f) return Velocity.Zero
        panel.settle(available.y)
        return available
    }

    /** Moves the panel by [deltaY] px and reports how much of it the panel took. */
    private fun move(deltaY: Float): Offset {
        val before = panel.progress
        panel.dragBy(deltaY)
        return Offset(0f, (before - panel.progress) * panel.travelPx)
    }
}
