package com.example.samsonic.ui.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import com.example.samsonic.model.Song
import com.example.samsonic.ui.components.pressClickable
import com.example.samsonic.ui.library.AddToPlaylistState

private val ButtonSize = 52.dp

/**
 * Now Playing's actions side by side as round glass buttons, instead of the stack
 * ([NowPlayingActions]): lyrics, the queue, song info, Auto DJ and Add to playlist,
 * each panel growing out of its own button. Inset so, with all five, each lines up
 * under a button of the controls above.
 */
@Composable
internal fun NowPlayingActionRow(
    lyrics: PanelState,
    queue: PanelState,
    info: PanelState,
    autoDj: PanelState,
    addToPlaylist: AddToPlaylistState?,
    song: Song,
    modifier: Modifier = Modifier,
) {
    val actions = rememberPlayerActions(lyrics, queue, info, autoDj, addToPlaylist, song)
    Row(
        // The controls' pill pads its 48dp buttons by 8dp; 6dp puts these centres under theirs.
        modifier = modifier.fillMaxWidth().padding(horizontal = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        actions.forEach { action -> ActionButton(action) }
    }
}

@Composable
private fun ActionButton(action: PlayerAction) {
    val bounds = remember { arrayOf(Rect.Zero) }
    // Whether its panel is out, changing only as it sets out and as it's back, so the
    // button doesn't redraw every frame of the panel growing.
    val open by remember(action) { derivedStateOf { action.progress() > 0f } }
    Box(
        modifier = Modifier
            .onGloballyPositioned { bounds[0] = it.boundsInRoot() }
            // Hidden while its panel is out, which starts as a copy of it, and catching the
            // panel's close bounce with a small squeeze.
            .graphicsLayer {
                alpha = if (open) 0f else 1f
                val squeeze = 1f - action.landing() * LandingSqueeze
                scaleX = squeeze
                scaleY = squeeze
            }
            .pressClickable(onClick = { action.open(bounds[0]) })
            .size(ButtonSize)
            // Flat: over art already blurred, a blur of its own would cost without showing.
            .nowPlayingGlass(null),
        contentAlignment = Alignment.Center,
    ) {
        Icon(action.icon, contentDescription = action.label, modifier = Modifier.size(24.dp))
    }
}
