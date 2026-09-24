package com.example.samsonic.ui.player

import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onPlaced
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
import com.example.samsonic.ui.theme.GlassRimWidth
import com.example.samsonic.ui.theme.glassRimBrush
import kotlinx.coroutines.CoroutineScope
import kotlin.math.ceil
import kotlin.math.floor
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

    /**
     * Where the panel sits in the root, from its last layout: kept here, since the panel
     * isn't composed while folded, so its glass has the size it needs from the first frame.
     */
    internal var placedAt by mutableStateOf(Offset.Unspecified)

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

// How far open a panel from a glassless origin is when its glass is fully in (see MorphPanel).
private const val OriginFade = 0.35f

/** Maps [value] from [start]..[end] onto 0..1, clamped. */
private fun ramp(value: Float, start: Float, end: Float) = ((value - start) / (end - start)).coerceIn(0f, 1f)

/**
 * Draws [content] as [panel], morphing from its button into this element's own
 * bounds: the button's circle grows and its corners ease to [radius], its glass
 * veil gives way to [surface] (a background modifier, such as a glass surface),
 * thickened as it grows by [washAlpha] of [wash] over it (so a thin glass, like the
 * button's, becomes the panel's dense one: see [washToReach]), with the glass rim
 * following the shape rather than the full bounds (so [surface] should draw none),
 * and [icon] (if any; a row has none) rides the shape's center, fading as the content fades in. Its top
 * edge moves with [panel]'s drags, so with [dragToClose] a pull down on the
 * panel (or past the top of its list) folds it back up.
 * [originRadius] is for an origin with no glass of its own, such as a list row: the
 * shape folds into its corners rather than a circle, draws no veil, and its glass
 * fades out as it lands (and in as it leaves), since there's no glass button to
 * hand over to - a blurred copy of the row would otherwise pop back to the row.
 * [resizable] is for content that animates its size while open: the glass is laid
 * out once at all the height the panel may take, so the size animation doesn't
 * resize it (and rebuild its blur) every frame.
 * Not composed while folded away.
 */
@Composable
internal fun MorphPanel(
    panel: PanelState,
    icon: ImageVector?,
    surface: Modifier,
    modifier: Modifier = Modifier,
    radius: Dp = 0.dp,
    dragToClose: Boolean = true,
    wash: Color = Color.Transparent,
    washAlpha: Float = 0f,
    originRadius: Dp? = null,
    resizable: Boolean = false,
    content: @Composable () -> Unit,
) {
    val showing by remember(panel) { derivedStateOf { panel.progress > 0f } }
    if (!showing) return
    val veil = if (originRadius == null) MaterialTheme.colorScheme.onSurface.copy(alpha = GlassAlpha.NowPlaying) else Color.Transparent
    val originRadiusPx = originRadius?.let { with(LocalDensity.current) { it.toPx() } }
    // How much of the glass shows: all of it, except near a glassless origin.
    fun glassAlpha() = if (originRadius == null) 1f else ramp(panel.progress, 0f, OriginFade)
    val iconTint = MaterialTheme.colorScheme.onSurface
    val rimBrush = glassRimBrush()
    val rimWidthPx = with(LocalDensity.current) { GlassRimWidth.toPx() }
    val density = LocalDensity.current
    val radiusPx = with(density) { radius.toPx() }
    val iconPx = with(density) { IconSize.toPx() }
    // This element's top-left in root coordinates (to bring the button's bounds into
    // local ones) and its size; a plain holder, read only in placement and draw.
    val placed = remember { floatArrayOf(0f, 0f, 0f, 0f) }
    fun bounds(size: Size): Pair<Rect, Float> {
        val full = Rect(Offset.Zero, size)
        val from = panel.origin.takeIf { it != Rect.Zero }?.translate(-placed[0], -placed[1]) ?: full
        val t = panel.progress
        // t overshoots past 1 on an elastic open, so the shape briefly grows past `full`.
        return lerp(from, full, t) to lerp(originRadiusPx ?: (from.minDimension / 2), radiusPx, t).coerceAtLeast(0f)
    }
    // The glass's top-left in this element's coordinates (see its layout); read in draw.
    val glassAt = remember { floatArrayOf(0f, 0f) }
    // The most height this element may take (see [resizable]); read in the glass's layout.
    val room = remember { intArrayOf(0) }
    val pullDown = remember(panel) { PanelPullDown(panel) }
    val dragState = rememberDraggableState { panel.dragBy(it) }
    Box(
        modifier
            .then(
                if (resizable) {
                    Modifier.layout { measurable, constraints ->
                        if (constraints.hasBoundedHeight) room[0] = constraints.maxHeight
                        val placeable = measurable.measure(constraints)
                        layout(placeable.width, placeable.height) { placeable.place(0, 0) }
                    }
                } else {
                    Modifier
                },
            )
            // onPlaced, not onGloballyPositioned: it runs before the children are placed and
            // before anything draws, so even the first frame after composing uses the real
            // position. Otherwise that frame drew the shape as if at the root's corner - a flash.
            .onPlaced {
                val position = it.positionInRoot()
                placed[0] = position.x
                placed[1] = position.y
                placed[2] = it.size.width.toFloat()
                placed[3] = it.size.height.toFloat()
                if (position != panel.placedAt) panel.placedAt = position
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
            )
            // The button's veil, drawn at the in-between bounds (unclipped, so early on it
            // reaches past this element's edges to where the button is). Worked out at draw
            // time from the latest position, never ahead of it in measure.
            .drawBehind {
                val (rect, corner) = bounds(size)
                drawRoundRect(
                    color = veil.copy(alpha = veil.alpha * (1f - ramp(panel.progress, 0f, 0.5f))),
                    topLeft = rect.topLeft,
                    size = rect.size,
                    cornerRadius = CornerRadius(corner),
                )
            },
    ) {
        // The glass, by contrast, is always laid out at full size and never resizes: the
        // morph only moves a rounded clip over it, as the Library view options' glass does.
        // Resizing a blurred surface every frame rebuilds its blur each frame, which flickered.
        // On the open's overshoot it stretches to the grown bounds instead, since its clip
        // can't reach past its own edges.
        // Nothing fades or layers the blur: with a graphicsLayer around it (even just for
        // the stretch) or a fade on it (even the blur's own alpha), it dropped out about
        // once a second, showing the page behind unblurred for a frame, even with the
        // panel at rest (seen in screen recordings). So the clip and the stretch are done
        // in draw, and the glass shows at full strength inside the window from the start,
        // taking over from the veil as the window grows.
        // It covers the button as well as this element: early on the shape still reaches
        // the button, often outside this element (below a panel over the control row),
        // and glass laid out only over this element left that part as bare veil, a seam
        // across the shape. Still one fixed size for the whole morph.
        Box(
            Modifier
                .matchParentSize()
                .layout { measurable, constraints ->
                    val width = constraints.maxWidth
                    // A resizable panel's glass reaches down all the room it has, so its
                    // growing and shrinking only moves the clip, never resizes the glass.
                    val height = maxOf(constraints.maxHeight, room[0])
                    val origin = panel.origin
                    val at = panel.placedAt
                    val area = if (origin == Rect.Zero || !at.isSpecified) {
                        Rect(0f, 0f, width.toFloat(), height.toFloat())
                    } else {
                        val button = origin.translate(-at.x, -at.y)
                        Rect(
                            minOf(0f, button.left),
                            minOf(0f, button.top),
                            maxOf(width.toFloat(), button.right),
                            maxOf(height.toFloat(), button.bottom),
                        )
                    }
                    val left = floor(area.left).toInt()
                    val top = floor(area.top).toInt()
                    val glass = measurable.measure(
                        Constraints.fixed(ceil(area.right).toInt() - left, ceil(area.bottom).toInt() - top),
                    )
                    glassAt[0] = left.toFloat()
                    glassAt[1] = top.toFloat()
                    // This element's own size, not the glass's: a layout larger than its
                    // constraints is centered on them, which shifted a taller glass up.
                    layout(width, constraints.maxHeight) { glass.place(left, top) }
                }
                .drawWithContent {
                    // This element's bounds and the shape, in the glass's coordinates.
                    val shift = Offset(-glassAt[0], -glassAt[1])
                    val own = Rect(shift, Size(placed[2], placed[3]))
                    val (bounds, corner) = bounds(own.size)
                    val rect = bounds.translate(shift)
                    if (panel.progress > 1f && own.width > 0f && own.height > 0f) {
                        // This element's part of the glass, stretched onto the grown bounds
                        // and clipped to its own edges.
                        withTransform({
                            translate(rect.left, rect.top)
                            scale(rect.width / own.width, rect.height / own.height, pivot = Offset.Zero)
                            translate(-own.left, -own.top)
                        }) {
                            clipPath(Path().apply { addRoundRect(RoundRect(own, CornerRadius(corner))) }) {
                                this@drawWithContent.drawContent()
                                drawRect(wash, own.topLeft, own.size, alpha = washAlpha)
                            }
                        }
                    } else {
                        // A glassless origin's fade. Only near the origin, as a brief layer in
                        // draw: a lasting fade on the glass drops its blur out (see above), but
                        // here the shape is small and nearly gone by then.
                        val fade = glassAlpha()
                        if (fade < 1f) drawContext.canvas.saveLayer(rect, Paint().apply { alpha = fade })
                        clipPath(Path().apply { addRoundRect(RoundRect(rect, CornerRadius(corner))) }) {
                            this@drawWithContent.drawContent()
                            // Thickens the glass as it grows, in draw: a fade on the glass
                            // itself would drop the blur out (see above).
                            drawRect(wash, rect.topLeft, rect.size, alpha = washAlpha * ramp(panel.progress, 0f, 0.8f))
                            // The button's light veil over the glass, fading out: the glass paints
                            // an opaque base, so the veil under it was hidden and the shape turned
                            // dark the moment it left the button, as if its glass had gone.
                            drawRect(veil, rect.topLeft, rect.size, alpha = veil.alpha * (1f - ramp(panel.progress, 0f, 0.6f)))
                        }
                        if (fade < 1f) drawContext.canvas.restore()
                    }
                    // The rim along the shape, as the button has, all the way open.
                    val inset = rimWidthPx / 2
                    drawRoundRect(
                        brush = rimBrush,
                        alpha = glassAlpha(),
                        topLeft = rect.topLeft + Offset(inset, inset),
                        size = Size(rect.width - rimWidthPx, rect.height - rimWidthPx),
                        cornerRadius = CornerRadius((corner - inset).coerceAtLeast(0f)),
                        style = Stroke(rimWidthPx),
                    )
                }
                .then(surface),
        )
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
        if (icon != null) Icon(
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

/** How dense a [MorphPanel]'s glass starts, near a button's thin glass; its wash makes up the rest. */
internal const val MorphGlassBase = 0.2f

/**
 * How much extra [MorphPanel] wash brings a glass of [base] alpha up to [target]:
 * layered, two tints cover 1 - (1 - base)(1 - wash).
 */
internal fun washToReach(base: Float, target: Float): Float =
    (1f - (1f - target.coerceIn(0f, 1f)) / (1f - base)).coerceIn(0f, 1f)

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
