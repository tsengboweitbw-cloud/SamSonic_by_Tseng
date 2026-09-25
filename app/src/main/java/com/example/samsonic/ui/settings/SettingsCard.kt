package com.example.samsonic.ui.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.ParentDataModifierNode
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.samsonic.ui.theme.GlassAlpha
import com.example.samsonic.ui.theme.OneUiRadius
import com.example.samsonic.ui.theme.glassSurface

/**
 * A glass card of settings rows, stacked top to bottom with no gaps. A row can
 * ask for extra room against the card's rounded edge with [cardEdgeInset]; the
 * card adds the first row's top inset and the last row's bottom inset, and
 * ignores them for rows in the middle. So a row type sets its own edge spacing
 * once and it is right wherever the row ends up.
 */
@Composable
internal fun SettingsCard(content: @Composable () -> Unit) {
    // No hazeState: nested inside the NavHost's own hazeSource subtree (see
    // MediaLists.SongRow comment) - falls back to a flat translucent fill.
    Layout(
        content = content,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .glassSurface(
                shape = RoundedCornerShape(OneUiRadius.Card),
                hazeState = null,
                tint = MaterialTheme.colorScheme.surfaceContainerHigh,
                alpha = GlassAlpha.Sheet,
            ),
    ) { measurables, constraints ->
        val rowConstraints = constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity)
        val rows = measurables.map { it.measure(rowConstraints) }
        val top = (measurables.firstOrNull()?.parentData as? CardEdgeInsetNode)?.top?.roundToPx() ?: 0
        val bottom = (measurables.lastOrNull()?.parentData as? CardEdgeInsetNode)?.bottom?.roundToPx() ?: 0
        val height = top + rows.sumOf { it.height } + bottom
        layout(constraints.maxWidth, height.coerceIn(constraints.minHeight, constraints.maxHeight)) {
            var y = top
            rows.forEach {
                it.placeRelative(0, y)
                y += it.height
            }
        }
    }
}

/**
 * Extra space this row needs between itself and the card edge when it is the
 * card's first row ([top]) or last row ([bottom]). Goes on the row's outermost
 * layout, as a direct child of [SettingsCard].
 */
internal fun Modifier.cardEdgeInset(top: Dp = 0.dp, bottom: Dp = 0.dp): Modifier =
    this then CardEdgeInsetElement(top, bottom)

private data class CardEdgeInsetElement(val top: Dp, val bottom: Dp) : ModifierNodeElement<CardEdgeInsetNode>() {
    override fun create() = CardEdgeInsetNode(top, bottom)

    override fun update(node: CardEdgeInsetNode) {
        node.top = top
        node.bottom = bottom
    }
}

private class CardEdgeInsetNode(var top: Dp, var bottom: Dp) : Modifier.Node(), ParentDataModifierNode {
    override fun Density.modifyParentData(parentData: Any?): Any = this@CardEdgeInsetNode
}

/** The color of every settings row's leading icon: the accent, the same all down the page. */
@Composable
internal fun rowIconTint(): Color = MaterialTheme.colorScheme.primary
