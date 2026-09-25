package com.example.samsonic.ui.settings

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.example.samsonic.ui.theme.accentPalette
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

// Long enough to read a sentence or two.
private const val HintShowMillis = 4000L
private val CornerSize = 18.dp
private val TailHeight = 8.dp
private val TailHalfWidth = 9.dp
// Between the tail's tip and the row.
private val TailGap = 2.dp
// From the touch point up to just clear of the fingertip, where the tail's tip goes:
// a finger covers a good way above the point it touches.
private val FingerLift = 32.dp
// Around the bubble inside its popup, for the shadow to spread into.
private val ShadowRoom = 8.dp

/**
 * Whether a settings row's hint is showing, and where its bubble landed. Settings
 * rows carry no subtitles: what one would say shows in a bubble over the row on a
 * long press instead (see [RowHint]), or on its own when the row must say it now.
 */
@Stable
internal class RowHintState {
    internal val visible = MutableTransitionState(false)

    // Set as the bubble is placed: which side of the row it's on, and how far across
    // the popup (0 to 1) the row's centre is, for the tail and to grow from there.
    internal var above by mutableStateOf(true)
    internal var tailFraction by mutableFloatStateOf(0.5f)
    internal var popupWidth by mutableFloatStateOf(0f)

    // Where the finger went down on the row, in window pixels (see [hintHold]): a long-
    // pressed bubble points its tail right there. Null for one the row shows itself.
    internal var pressPoint by mutableStateOf<Offset?>(null)
    internal var coordinates: LayoutCoordinates? = null

    // Shown by a long press still held down: it stays until the finger lifts, not on a timer.
    internal var held by mutableStateOf(false)
        private set

    /** Shows it for a few seconds, for a row with something it must say now. */
    fun show() {
        held = false
        pressPoint = null
        visible.targetState = true
    }

    /** Shows it until the press that asked for it lets go (see [hintHold]). */
    internal fun showWhileHeld() {
        held = true
        visible.targetState = true
    }

    fun hide() {
        held = false
        visible.targetState = false
    }
}

/**
 * Notes where a finger goes down on the row (for the tail to point at) and hides
 * [state]'s hint as the finger that long-pressed lifts. Goes on the same element as
 * the long press. Watches the press without taking it, so the row's own tap and
 * long press work as ever.
 */
internal fun Modifier.hintHold(state: RowHintState): Modifier = onGloballyPositioned { state.coordinates = it }.pointerInput(state) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        state.pressPoint = state.coordinates?.takeIf { it.isAttached }?.localToWindow(down.position)
        do {
            val event = awaitPointerEvent(PointerEventPass.Initial)
        } while (event.changes.any { it.pressed })
        if (state.held) state.hide()
    }
}

@Composable
internal fun rememberRowHint(): RowHintState = remember { RowHintState() }

/** The long press that shows [state]'s hint, with a haptic tick; null when there's no [hint]. */
@Composable
internal fun hintLongPress(state: RowHintState, hint: String?): (() -> Unit)? {
    val haptics = LocalHapticFeedback.current
    return hint?.let {
        {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            state.showWhileHeld()
        }
    }
}

/**
 * The bubble of a row's [hint], put anywhere inside the row: a callout over it (under
 * it when there's no room above) whose tail points at the row, springing out of that
 * tail and folding back into it. Tinted with [accent] - the app's accent, or e.g. the
 * error color for a warning. A long-pressed hint goes as the finger lifts; one the row
 * shows by itself after a few seconds, or at a touch anywhere (which also covers
 * starting to scroll the page away from its row).
 */
@Composable
internal fun RowHint(state: RowHintState, hint: String?, accent: Color? = null) {
    if (hint == null) return
    val visible = state.visible
    if (!visible.currentState && !visible.targetState) return
    LaunchedEffect(visible.targetState, state.held, hint) {
        if (visible.targetState && !state.held) {
            delay(HintShowMillis)
            state.hide()
        }
    }
    val tint = accent ?: MaterialTheme.accentPalette.primary
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val base = MaterialTheme.colorScheme.surfaceContainerHighest
    // A lit edge easing into a softer wash, so it reads as a lifted, colored piece of glass.
    val fillTop = lerp(base, tint, if (dark) 0.28f else 0.16f)
    val fillBottom = lerp(base, tint, if (dark) 0.14f else 0.07f)
    val density = LocalDensity.current
    val room = with(density) { ShadowRoom.roundToPx() }
    // The shadow room sits between the tail's tip and the popup's edge.
    val gap = with(density) { TailGap.roundToPx() } - room
    val margin = with(density) { 12.dp.roundToPx() }
    val lift = with(density) { FingerLift.roundToPx() }
    // The tail's x inside the bubble, which is the popup less the shadow room.
    val tailX = state.tailFraction * state.popupWidth - room
    val shape = CalloutShape(state.above, tailX, density)
    val origin = TransformOrigin(state.tailFraction, if (state.above) 1f else 0f)

    Popup(
        popupPositionProvider = remember(state, gap, margin, lift) { HintPosition(state, gap, margin, lift) },
        onDismissRequest = state::hide,
        properties = PopupProperties(focusable = false, dismissOnClickOutside = true),
    ) {
        AnimatedVisibility(
            visibleState = visible,
            enter = fadeIn(tween(140)) +
                scaleIn(
                    spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessMediumLow),
                    initialScale = 0.4f,
                    transformOrigin = origin,
                ),
            exit = fadeOut(tween(160)) + scaleOut(tween(180), targetScale = 0.6f, transformOrigin = origin),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .widthIn(max = 300.dp)
                    // Room for the shadow to spread around the outline.
                    .padding(ShadowRoom)
                    .shadow(12.dp, shape, ambientColor = tint, spotColor = tint.copy(alpha = 0.6f))
                    .background(Brush.verticalGradient(listOf(fillTop, fillBottom)), shape)
                    .border(1.dp, tint.copy(alpha = if (dark) 0.45f else 0.35f), shape)
                    // The tail's side gets its height on top of the usual padding.
                    .padding(
                        start = 14.dp,
                        end = 16.dp,
                        top = 10.dp + if (state.above) 0.dp else TailHeight,
                        bottom = 10.dp + if (state.above) TailHeight else 0.dp,
                    ),
            ) {
                Icon(Icons.Rounded.Info, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Text(text = hint, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

/**
 * Its tail's tip [gap] over the finger that long-pressed (just above the fingertip,
 * [lift] up from the touch point, so the hand doesn't hide it), or over the row's
 * centre for a hint the row shows itself. Kept [margin] inside the screen's sides,
 * and flipped under the point if it won't fit above. Tells [state] where it went, so
 * the tail and the grow point line up.
 */
private class HintPosition(
    private val state: RowHintState,
    private val gap: Int,
    private val margin: Int,
    private val lift: Int,
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val press = state.pressPoint
        val pointX = press?.x?.roundToInt() ?: anchorBounds.center.x
        val pointTop = press?.let { it.y.roundToInt() - lift } ?: anchorBounds.top
        val pointBottom = press?.let { it.y.roundToInt() + lift } ?: anchorBounds.bottom
        val x = (pointX - popupContentSize.width / 2)
            .coerceIn(margin, (windowSize.width - popupContentSize.width - margin).coerceAtLeast(margin))
        val aboveY = pointTop - popupContentSize.height - gap
        val above = aboveY >= margin
        val fraction = if (popupContentSize.width > 0) {
            ((pointX - x).toFloat() / popupContentSize.width).coerceIn(0f, 1f)
        } else {
            0.5f
        }
        if (state.above != above) state.above = above
        if (state.tailFraction != fraction) state.tailFraction = fraction
        if (state.popupWidth != popupContentSize.width.toFloat()) state.popupWidth = popupContentSize.width.toFloat()
        return IntOffset(x, if (above) aboveY else pointBottom + gap)
    }
}

/**
 * A rounded bubble with a small rounded tail on its bottom edge ([above] the row) or top
 * edge, centred [tailX] px across. One outline, so the shadow and rim follow the tail too.
 */
private class CalloutShape(
    private val above: Boolean,
    private val tailX: Float,
    private val density: Density,
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val tail = with(this.density) { TailHeight.toPx() }
        val half = with(this.density) { TailHalfWidth.toPx() }
        val corner = with(this.density) { CornerSize.toPx() }
        val bodyTop = if (above) 0f else tail
        val bodyBottom = if (above) size.height - tail else size.height
        val body = Path().apply {
            addRoundRect(RoundRect(0f, bodyTop, size.width, bodyBottom, CornerRadius(corner)))
        }
        // Kept clear of the rounded corners.
        val cx = (if (tailX > 0f) tailX else size.width / 2f).coerceIn(corner + half, (size.width - corner - half).coerceAtLeast(corner + half))
        val base = if (above) bodyBottom else bodyTop
        val tip = if (above) size.height else 0f
        val tailPath = Path().apply {
            moveTo(cx - half, base)
            // Soft sides into a rounded tip rather than a sharp spike.
            quadraticTo(cx - half * 0.35f, base, cx - half * 0.18f, (base + tip) / 2f + (tip - base) * 0.3f)
            quadraticTo(cx, tip, cx + half * 0.18f, (base + tip) / 2f + (tip - base) * 0.3f)
            quadraticTo(cx + half * 0.35f, base, cx + half, base)
            close()
        }
        return Outline.Generic(Path.combine(PathOperation.Union, body, tailPath))
    }
}

/** The toast for a setting that only takes effect once the app starts again. */
internal fun showAppliesOnRestartToast(context: Context) {
    Toast.makeText(context, "Applies the next time SamSonic starts", Toast.LENGTH_SHORT).show()
}
