package com.example.samsonic.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.OverscrollEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.unit.Velocity
import kotlin.math.abs

/**
 * A list's overscroll as a plain move of the whole list, vertically, with [pull] as how far
 * (px, down positive). Unlike the system stretch, which only the list draws, the floating
 * row ([FloatingListActions]) can read [pull] and move along with the list.
 */
class PullOverscrollEffect internal constructor() : OverscrollEffect {
    private val state: MutableFloatState = mutableFloatStateOf(0f)

    /** How far the list is pulled past its end, px (positive down). Read it in layout or draw. */
    val pull: Float get() = state.floatValue

    override fun applyToScroll(delta: Offset, source: NestedScrollSource, performScroll: (Offset) -> Offset): Offset {
        if (source != NestedScrollSource.UserInput) return performScroll(delta)
        var remaining = delta.y
        var consumed = 0f
        val current = state.floatValue
        // Pulling back toward rest first undoes the pull.
        if (current != 0f && (remaining > 0f) != (current > 0f)) {
            val undo = if (current > 0f) maxOf(remaining * PullRatio, -current) else minOf(remaining * PullRatio, -current)
            state.floatValue = current + undo
            remaining -= undo / PullRatio
            consumed += undo / PullRatio
        }
        val scrolled = performScroll(Offset(delta.x, remaining))
        val left = remaining - scrolled.y
        if (abs(left) > 0f) state.floatValue += left * PullRatio
        return Offset(scrolled.x, consumed + scrolled.y + left)
    }

    override suspend fun applyToFling(velocity: Velocity, performFling: suspend (Velocity) -> Velocity) {
        performFling(velocity)
        val from = state.floatValue
        if (from != 0f) animate(from, 0f, animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) { value, _ -> state.floatValue = value }
    }

    override val isInProgress: Boolean get() = state.floatValue != 0f

    override val node: DelegatableNode = object : Modifier.Node(), DrawModifierNode {
        override fun ContentDrawScope.draw() {
            translate(top = state.floatValue) { this@draw.drawContent() }
        }
    }
}

// How much of a finger's drag past the end the list moves, so the pull feels held back.
private const val PullRatio = 0.5f

@Composable
fun rememberPullOverscroll(): PullOverscrollEffect = remember { PullOverscrollEffect() }
