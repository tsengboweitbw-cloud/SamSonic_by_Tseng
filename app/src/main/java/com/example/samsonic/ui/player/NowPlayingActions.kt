package com.example.samsonic.ui.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.LibraryAdd
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.layout.layout
import com.example.samsonic.R
import com.example.samsonic.model.Song
import com.example.samsonic.ui.autodj.AutoDjIcon
import com.example.samsonic.ui.components.CardPileState
import com.example.samsonic.ui.components.contentAlpha
import com.example.samsonic.ui.components.pickThresholdPx
import com.example.samsonic.ui.components.pileCard
import com.example.samsonic.ui.components.pileSwipe
import com.example.samsonic.ui.components.pressClickable
import com.example.samsonic.ui.components.rememberCardPileState
import com.example.samsonic.ui.library.AddToPlaylistState
import com.example.samsonic.ui.library.toPlaylistItems
import dev.chrisbanes.haze.HazeState

/** Each panel's button icon, which the panel also carries as it grows out of the button. */
internal object PanelIcons {
    val Lyrics = Icons.Filled.Lyrics
    val Queue = Icons.AutoMirrored.Filled.QueueMusic
    val Info = Icons.Outlined.Info
}

/** One of Now Playing's actions, a capsule of the stack or a button of the row: what it shows, and the panel (or card) it opens. */
internal class PlayerAction(
    val icon: ImageVector,
    val label: String,
    /** How far its panel is open, and its close bounce ([PanelState.landing]). */
    val progress: () -> Float,
    val landing: () -> Float,
    /** Opens it, growing out of the capsule or button at [from] (in root coordinates). */
    val open: (from: Rect) -> Unit,
)

/** Now Playing's actions, in order: lyrics, the queue, song info, Auto DJ and Add to playlist ([addToPlaylist], if there are playlists to add to). */
@Composable
internal fun rememberPlayerActions(
    lyrics: PanelState,
    queue: PanelState,
    info: PanelState,
    autoDj: PanelState,
    addToPlaylist: AddToPlaylistState?,
    song: Song,
): List<PlayerAction> {
    val lyricsLabel = stringResource(R.string.player_lyrics)
    val queueLabel = stringResource(R.string.player_queue)
    val infoLabel = stringResource(R.string.player_song_info)
    val addLabel = stringResource(R.string.components_add_to_playlist)
    val autoDjLabel = stringResource(R.string.auto_dj_title)
    val currentSong by rememberUpdatedState(song)
    return remember(lyrics, queue, info, autoDj, addToPlaylist, lyricsLabel, queueLabel, infoLabel, addLabel, autoDjLabel) {
        fun PanelState.action(icon: ImageVector, label: String) =
            PlayerAction(icon, label, { progress }, { landing }) { from ->
                origin = from
                open()
            }
        listOfNotNull(
            lyrics.action(PanelIcons.Lyrics, lyricsLabel),
            queue.action(PanelIcons.Queue, queueLabel),
            info.action(PanelIcons.Info, infoLabel),
            autoDj.action(AutoDjIcon, autoDjLabel),
            addToPlaylist?.let { state ->
                PlayerAction(Icons.Filled.LibraryAdd, addLabel, { state.panel.progress }, { state.panel.landing }) { from ->
                    state.open(currentSong.toPlaylistItems(), from, originRadius = null, offersQueue = false)
                }
            },
        )
    }
}

private val CapsuleWidth = 220.dp
private val CapsuleHeight = 52.dp

// How much a capsule (or a row's button) shrinks per unit of its panel's close overshoot (a few % at most).
internal const val LandingSqueeze = 3f

/**
 * Now Playing's bottom capsules, [icon] and text, stacked like the lock screen's
 * Now Brief ([CardPileState]): the front one is full size, the rest peek out behind
 * it, a pile of cards. A swipe up picks the front capsule up, and it follows the
 * finger while the rest of the pile stays; let go high enough it goes to the bottom
 * of the pile and the next comes forward, one capsule a swipe; lower, it drops back.
 * A drag down is left to Now Playing. A tap opens the front one: lyrics, the queue,
 * song info, Auto DJ, or Add to playlist ([addToPlaylist], if there are playlists to
 * add to), each growing out of the front capsule as the rest of the pile stays.
 */
@Composable
internal fun NowPlayingActions(
    lyrics: PanelState,
    queue: PanelState,
    info: PanelState,
    autoDj: PanelState,
    addToPlaylist: AddToPlaylistState?,
    song: Song,
    haze: HazeState?,
    modifier: Modifier = Modifier,
) {
    val capsules = rememberPlayerActions(lyrics, queue, info, autoDj, addToPlaylist, song)
    val count = capsules.size
    val pile = rememberCardPileState(count)
    // The one capsule that can be lifted over the controls, the front one or the one on its
    // way from there to the back, is the only one whose glass blurs; the rest show only an
    // edge, over art already blurred, where a blur of their own would cost without showing.
    val liftable by remember(pile) { derivedStateOf { pile.liftable } }
    val bounds = remember { arrayOf(Rect.Zero) }

    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                // The capsules peeking out behind are drawn below it, past its bounds, in the
                // bottom padding: the stack takes no more height than the row of buttons does.
                .size(CapsuleWidth, CapsuleHeight)
                .onGloballyPositioned { coordinates ->
                    // Every panel grows out of the stack.
                    val at = coordinates.boundsInRoot()
                    bounds[0] = at
                    lyrics.origin = at
                    queue.origin = at
                    info.origin = at
                    autoDj.origin = at
                }
                // A swipe up picks the front capsule up. Only up: a drag that sets off
                // downward is left alone, for Now Playing to close.
                .pileSwipe(pile, CapsuleHeight)
                .pressClickable(onClick = { capsules[pile.front].open(bounds[0]) }, pressedScale = 0.96f),
        ) {
            // Whether each capsule's panel is out, changing only as it sets out and as it's
            // back: read instead of how far open it is, so the stack doesn't redraw every
            // frame of the panel growing, which, blurring Now Playing, would redo its blur.
            val open = remember(capsules) { capsules.map { derivedStateOf { it.progress() > 0f } } }
            // Out, or still landing: its close springs on past folded, the capsule squeezing
            // in and back. Until that's done, the one behind isn't cut where it covers it;
            // cut to the capsule's resting outline as it squeezed, it was cut off short.
            val away = remember(capsules) { capsules.map { derivedStateOf { it.progress() > 0f || it.landing() > 0f } } }
            capsules.forEachIndexed { index, capsule ->
                StackedCapsule(
                    capsule = capsule,
                    pile = pile,
                    index = index,
                    isOpen = { open[index].value },
                    inFrontOpen = { open[Math.floorMod(index - 1, count)].value },
                    inFrontAway = { away[Math.floorMod(index - 1, count)].value },
                    haze = haze.takeIf { index == liftable },
                    // Set as it's placed, so the pile reordering only re-places it.
                    modifier = Modifier.layout { measurable, constraints ->
                        val placeable = measurable.measure(constraints)
                        layout(placeable.width, placeable.height) { placeable.place(0, 0, zIndex = pile.zIndex(index)) }
                    },
                )
            }
        }
    }
}

/**
 * Capsule [index] of the [pile], its contents only on the front one. Where the
 * capsule just in front of it covers it, it's cut away, so through the translucent
 * glass only its peeking edge shows, not its outline.
 */
@Composable
private fun StackedCapsule(
    capsule: PlayerAction,
    pile: CardPileState,
    index: Int,
    // Whether its own panel is out, and that of the one in front: while that one's is
    // (and so that one hidden), this one shows whole, with its contents.
    isOpen: () -> Boolean,
    inFrontOpen: () -> Boolean,
    // Whether the one in front's panel is out or still landing, its capsule squeezing: until
    // it's done, this one isn't cut to that capsule's resting outline, which it no longer fills.
    inFrontAway: () -> Boolean,
    haze: HazeState?,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .pileCard(
                pile,
                index,
                CapsuleHeight,
                cut = { if (inFrontAway()) 0f else 1f },
                // Hidden while its panel is out, which starts as a copy of it; the rest of
                // the pile stays in place behind, the next one, left in front, as clear as
                // a front one. Each switch is made under the panel's copy of the capsule.
                alpha = { pose ->
                    when {
                        isOpen() -> 0f
                        pile.depth(index) == 1f && inFrontOpen() -> 1f
                        else -> pose.alpha
                    }
                },
                // Catching its panel's close bounce with a small squeeze; the rest of the
                // pile stays still.
                squeeze = { 1f - capsule.landing() * LandingSqueeze },
            )
            .nowPlayingGlass(haze),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier.graphicsLayer { alpha = pile.contentAlpha(index, pickThresholdPx(), inFrontAway = inFrontOpen()) },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(capsule.icon, contentDescription = null, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(10.dp))
            Text(
                text = capsule.label,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
        }
    }
}
