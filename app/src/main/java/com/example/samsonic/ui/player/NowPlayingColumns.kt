package com.example.samsonic.ui.player


import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.example.samsonic.R
import com.example.samsonic.ui.components.ChromeButtonIconSize
import com.example.samsonic.ui.components.ChromeButtonSize
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource

// between the controls and the actions.
internal val OneColumnTopGap = 24.dp
internal val OneColumnCoverGap = 28.dp
internal val OneColumnActionsGap = 16.dp

// The most of the spare height one column gives each of the last two gaps; the rest goes above the cover.
internal val OneColumnMaxExtraGap = 32.dp

/**
 * One column, top to bottom: the [collapse] button, the [cover], the [controls] and the
 * [actions]. The cover is as large as the width allows (square), or the height, where that's
 * less; any height left over is shared evenly by the gaps between them, rather than all
 * pooling above the cover, so the controls aren't crowded at the foot on a tall, narrow
 * screen. Only the first three are a source for [sourceHaze]: the actions blur it.
 */
@Composable
internal fun OneColumnBody(
    collapse: @Composable () -> Unit,
    cover: @Composable () -> Unit,
    controls: @Composable () -> Unit,
    actions: @Composable () -> Unit,
    sourceHaze: HazeState,
    modifier: Modifier = Modifier,
) {
    Layout(
        content = {
            Box(Modifier.graphicsLayer().hazeSource(sourceHaze, zIndex = 1f)) { collapse() }
            Box(Modifier.graphicsLayer().hazeSource(sourceHaze, zIndex = 1f)) { cover() }
            // A column: the controls are several rows, which a box would lay on top of each other.
            Column(Modifier.graphicsLayer().hazeSource(sourceHaze, zIndex = 1f)) { controls() }
            Box { actions() }
        },
        modifier = modifier.fillMaxSize(),
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val loose = Constraints(maxWidth = width)
        val collapsePlaceable = measurables[0].measure(loose)
        val controlsPlaceable = measurables[2].measure(loose)
        val actionsPlaceable = measurables[3].measure(loose)
        val topGap = OneColumnTopGap.roundToPx()
        val coverGap = OneColumnCoverGap.roundToPx()
        val actionsGap = OneColumnActionsGap.roundToPx()
        val room = constraints.maxHeight - collapsePlaceable.height - controlsPlaceable.height -
            actionsPlaceable.height - topGap - coverGap - actionsGap
        val side = minOf(width, room).coerceAtLeast(0)
        val coverPlaceable = measurables[1].measure(Constraints.fixed(width, side))
        val spare = (room - side).coerceAtLeast(0)
        val extra = minOf(spare / 3, OneColumnMaxExtraGap.roundToPx())
        val topExtra = spare - extra * 2
        layout(width, constraints.maxHeight) {
            var y = 0
            collapsePlaceable.place(0, y)
            y += collapsePlaceable.height + topGap + topExtra
            coverPlaceable.place(0, y)
            y += side + coverGap + extra
            controlsPlaceable.place(0, y)
            y += controlsPlaceable.height + actionsGap + extra
            actionsPlaceable.place(0, y)
        }
    }
}

// The least gap between the song, the seek bar, the transport and the actions beside the cover.
internal val SideColumnMinGap = 12.dp

/**
 * The song, the seek bar, the transport and the [actions] beside the cover, spread evenly over
 * a band [bandPx] tall (the cover's height, so they start and end level with it), centred in
 * the [roomPx] it has. Where they need more than the band (a phone on its side, or a
 * foldable) the band grows to take them, never with a gap under [SideColumnMinGap]; and where
 * even that is more than the room, it scrolls. Each is a source for [sourceHaze].
 */
@Composable
internal fun EvenSideColumn(
    bandPx: Int,
    roomPx: Int,
    heading: @Composable () -> Unit,
    seek: @Composable () -> Unit,
    transport: @Composable () -> Unit,
    actions: @Composable () -> Unit,
    sourceHaze: HazeState,
    modifier: Modifier = Modifier,
) {
    Layout(
        content = {
            Box(Modifier.graphicsLayer().hazeSource(sourceHaze, zIndex = 1f)) { heading() }
            Box(Modifier.graphicsLayer().hazeSource(sourceHaze, zIndex = 1f)) { seek() }
            Box(Modifier.graphicsLayer().hazeSource(sourceHaze, zIndex = 1f)) { transport() }
            Box(Modifier.graphicsLayer().hazeSource(sourceHaze, zIndex = 1f)) { actions() }
        },
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val placeables = measurables.map { it.measure(Constraints(maxWidth = width)) }
        val minGap = SideColumnMinGap.roundToPx()
        val content = placeables.sumOf { it.height }
        val band = maxOf(bandPx, content + minGap * (placeables.size - 1))
        val gap = (band - content) / (placeables.size - 1)
        val height = maxOf(roomPx, band)
        layout(width, height) {
            var y = (height - band) / 2
            placeables.forEach { placeable ->
                placeable.place(0, y)
                y += placeable.height + gap
            }
        }
    }
}

// The collapse button's row: its gap from the top, and all it takes up.
internal val CollapseRowTop = 12.dp
internal val CollapseRowHeight = CollapseRowTop + ChromeButtonSize

// How much larger the side beside the cover is on a tablet held wide and in DeX.
internal const val LargeSideScale = 1.2f

/**
 * A floating glass button (One UI Gallery style) instead of a bar, folding Now Playing
 * away; Add to playlist is with the other actions at the bottom.
 */
@Composable
internal fun CollapseButtonRow(onCollapse: () -> Unit, haze: HazeState?, fillWidth: Boolean = true) {
    Row(modifier = (if (fillWidth) Modifier.fillMaxWidth() else Modifier).padding(top = CollapseRowTop)) {
        GlassCircleButton(onClick = onCollapse, haze = haze) {
            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = stringResource(R.string.player_collapse), modifier = Modifier.size(ChromeButtonIconSize))
        }
    }
}
