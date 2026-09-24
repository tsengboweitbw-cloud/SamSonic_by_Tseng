package com.example.samsonic.ui.player

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import com.example.samsonic.ui.components.PressIconButton

/** Each panel's button icon, which the panel also carries as it grows out of the button. */
internal object PanelIcons {
    val Lyrics = Icons.Filled.Lyrics
    val Queue = Icons.AutoMirrored.Filled.QueueMusic
    val Info = Icons.Outlined.Info
}

/** Now Playing's bottom row: round glass buttons that grow into lyrics, the queue and song info. */
@Composable
internal fun NowPlayingActions(
    lyrics: PanelState,
    queue: PanelState,
    info: PanelState,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PanelButton(lyrics, PanelIcons.Lyrics, "Lyrics")
        PanelButton(queue, PanelIcons.Queue, "Queue")
        PanelButton(info, PanelIcons.Info, "Song info")
    }
}

// How much a button shrinks per unit of its panel's close overshoot (a few % at most).
private const val LandingSqueeze = 3f

@Composable
private fun PanelButton(panel: PanelState, icon: ImageVector, label: String) {
    PressIconButton(
        onClick = { panel.open() },
        size = 52.dp,
        modifier = Modifier
            .onGloballyPositioned { panel.origin = it.boundsInRoot() }
            // Hidden while its panel is out: the panel starts as a copy of it.
            // Then catches the panel's close bounce: a small squeeze back to full size.
            .graphicsLayer {
                alpha = if (panel.progress > 0f) 0f else 1f
                val squeeze = 1f - panel.landing * LandingSqueeze
                scaleX = squeeze
                scaleY = squeeze
            }
            .nowPlayingGlass(),
    ) {
        Icon(icon, contentDescription = label, modifier = Modifier.size(22.dp))
    }
}

// How far across the screen (as a share of its width) a sideways swipe pulls a panel fully open.
private const val SwipeTravel = 0.5f

/**
 * A sideways swipe on Now Playing pulls a panel out of its button with the finger:
 * [swipeLeft] for a swipe to the left, [swipeRight] for one to the right. Its first
 * step picks the panel; a release settles it open or closed, as a drag on the panel
 * would. The cover and the seek bar take their own sideways drags first.
 */
@Composable
internal fun Modifier.swipeOpensPanels(swipeLeft: PanelState, swipeRight: PanelState): Modifier {
    // Plain holders, read only inside the gesture: the panel being pulled, and our width.
    val pulling = remember { arrayOfNulls<PanelState>(1) }
    val width = remember { floatArrayOf(1f) }
    // Panels move by their top edge's travel; scale sideways pixels onto it.
    fun PanelState.scale() = travelPx / (width[0] * SwipeTravel)
    // A panel grows for negative (upward) drags, so a swipe toward the right is flipped.
    fun PanelState.opening(sideways: Float) = if (this === swipeLeft) sideways else -sideways
    val state = rememberDraggableState { delta ->
        val panel = pulling[0] ?: when {
            delta < 0f -> swipeLeft
            delta > 0f -> swipeRight
            else -> return@rememberDraggableState
        }.also { pulling[0] = it }
        panel.dragBy(panel.opening(delta) * panel.scale())
    }
    return onSizeChanged { width[0] = it.width.toFloat() }
        .draggable(
            state = state,
            orientation = Orientation.Horizontal,
            onDragStarted = { pulling[0] = null },
            onDragStopped = { velocity ->
                val panel = pulling[0] ?: return@draggable
                pulling[0] = null
                panel.settle(panel.opening(velocity) * panel.scale())
            },
        )
}
