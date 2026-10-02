package com.example.samsonic.ui.common

import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.unit.dp

/**
 * A mouse's right button on this element does [action], as a long press does: with a
 * mouse (DeX, a tablet's keyboard cover) there's no holding a finger down. Nothing
 * without an action. Goes before the element's clickable, so the right button isn't
 * also taken as a tap.
 */
fun Modifier.onSecondaryClick(action: (() -> Unit)?): Modifier {
    if (action == null) return this
    return pointerInput(action) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (event.type == PointerEventType.Press && event.buttons.isSecondaryPressed) {
                    event.changes.forEach { it.consume() }
                    action()
                }
            }
        }
    }
}

// How strongly a mouse over a button without a ripple (see pressClickable and the tab
// bars) lights it: a touch under a press's glow.
const val HoverGlowAlpha = 0.05f

/** Whether a mouse is over what [interaction] belongs to: for a glow of its own. */
@Composable
fun hovered(interaction: MutableInteractionSource): Boolean {
    val hovered by interaction.collectIsHoveredAsState()
    return hovered
}

// How far one notch of a mouse wheel scrolls a row sideways.
private val WheelNotch = 64.dp

/**
 * Shift and a mouse wheel scroll this row sideways ([state] is the row's): a plain wheel
 * over it still scrolls the page it's on, as it passes over.
 */
fun Modifier.shiftWheelScrollsSideways(state: ScrollableState): Modifier = pointerInput(state) {
    val notch = WheelNotch.toPx()
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            if (event.type != PointerEventType.Scroll || !event.keyboardModifiers.isShiftPressed) continue
            val delta = event.changes.sumOf { it.scrollDelta.y.toDouble() }.toFloat()
            if (delta == 0f) continue
            state.dispatchRawDelta(delta * notch)
            event.changes.forEach { it.consume() }
        }
    }
}
