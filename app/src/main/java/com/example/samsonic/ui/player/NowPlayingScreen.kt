package com.example.samsonic.ui.player

import com.example.samsonic.playback.LocalPlayerState

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
//import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.samsonic.LocalAppContainer
import com.example.samsonic.model.artSeed
import com.example.samsonic.ui.common.LocalWindowLayout
import com.example.samsonic.ui.library.AddToPlaylistState
import com.example.samsonic.ui.components.ChromeButtonSize
import com.example.samsonic.ui.theme.BlurredArtBackdrop
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

@Composable
fun NowPlayingScreen(
    onCollapse: () -> Unit,
    lyrics: PanelState,
    queue: PanelState,
    info: PanelState,
    autoDj: PanelState,
    modifier: Modifier = Modifier,
    // Where the top-right button opens Add to playlist; null hides it (no playlists to add to).
    addToPlaylist: AddToPlaylistState? = null,
    // The cover above the controls (a phone's), or beside them on a wide screen.
    columns: NowPlayingColumns = NowPlayingColumns.One,
) {
    val player = LocalPlayerState.current
    val song = player.currentSong ?: return
    val cornerRadius by LocalAppContainer.current.themeManager.albumArtCornerRadius.collectAsStateWithLifecycle()
    val likesEnabled by LocalAppContainer.current.themeManager.likesEnabled.collectAsStateWithLifecycle()
    // The actions at the bottom as a stack of capsules, or a row of round buttons.
    val stackSetting by LocalAppContainer.current.themeManager.stackPlayerActions.collectAsStateWithLifecycle()
    // Not in DeX: with a mouse, a row of buttons each a click away, rather than a stack
    // swiped through with a finger.
    val window = LocalWindowLayout.current
    val stackActions = stackSetting && !window.desktop
    // Two columns on a tablet held wide, or in DeX: the side beside the cover a size up.
    val largeSide = columns == NowPlayingColumns.Two && (window.desktop || (window.isTablet))
    val horizontalPadding = 24.dp
    // A phone on its side is short: the collapse button gets a column of its own on the far left
    // rather than a row (so the cover and the controls take the whole height), and the gap above
    // and below them is smaller.
    val collapseColumn = window.phoneLandscape
    val sideVerticalPadding = if (collapseColumn) 8.dp else 24.dp
    // What the capsule stack's glass blurs: the backdrop and everything above the
    // stack, sources beside the stack rather than around it (a glass inside its own
    // source draws recursively, so Now Playing's source, for the panels, won't do).
    val stackHaze = rememberHazeState()
    // While a panel is out, Now Playing's glass goes flat: dimmed under the panel, over
    // art already blurred, its blur doesn't show, but the panel's own blur of Now
    // Playing would redo it every frame of the panel's growing.
    val format = LocalInfoPanel.current
    val panelOut by remember(lyrics, queue, info, autoDj, addToPlaylist, format) {
        derivedStateOf {
            listOfNotNull(lyrics, queue, info, autoDj, format, addToPlaylist?.panel).any { it.progress > 0f }
        }
    }
    val glassHaze = stackHaze.takeUnless { panelOut }
    val actions: @Composable (Modifier) -> Unit = { actionsModifier ->
        if (stackActions) {
            NowPlayingActions(
                lyrics = lyrics,
                queue = queue,
                info = info,
                autoDj = autoDj,
                addToPlaylist = addToPlaylist,
                song = song,
                haze = glassHaze,
                modifier = actionsModifier,
            )
        } else {
            NowPlayingActionRow(
                lyrics = lyrics,
                queue = queue,
                info = info,
                autoDj = autoDj,
                addToPlaylist = addToPlaylist,
                song = song,
                modifier = actionsModifier,
            )
        }
    }
    Box(
        modifier = modifier
            .playerMorphRoot(PlayerSurface.Full)
            .fillMaxSize(),
    ) {
        // Crossfade fades the old and new backdrops at the same time, so mid-change
        // neither is opaque and the screen behind the player shows through. An
        // opaque base keeps the page solid while the art swaps.
        Box(Modifier.fillMaxSize().graphicsLayer().hazeSource(stackHaze, zIndex = 0f)) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
            )
            Crossfade(targetState = song, animationSpec = tween(500), label = "backdrop") { s ->
                BlurredArtBackdrop(coverArt = s.coverArt, colorSeed = s.id.artSeed())
            }
        }

        if (columns == NowPlayingColumns.Two) {
            // The cover on one side, as large as the height allows; the song, its
            // controls and the actions on the other, where the panels open too.
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding(),
            ) {
                if (collapseColumn) {
                    Box(Modifier.fillMaxHeight().width(ChromeButtonSize + 24.dp).padding(start = 12.dp)) {
                        CollapseButtonRow(onCollapse, glassHaze, fillWidth = false)
                    }
                }
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(horizontal = horizontalPadding)
                        .graphicsLayer()
                        .hazeSource(stackHaze, zIndex = 1f),
                ) {
                    if (!collapseColumn) CollapseButtonRow(onCollapse, glassHaze)
                    CoverCarousel(
                        player = player,
                        cornerRadius = cornerRadius,
                        bleed = horizontalPadding,
                        alignment = Alignment.Center,
                        modifier = Modifier
                            .weight(1f)
                            .padding(vertical = sideVerticalPadding),
                    )
                }
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(horizontal = horizontalPadding),
                ) {
                    // Beside the cover and as tall: the song, the seek bar, the transport and
                    // the actions spread evenly from its top to its foot. (Its top and foot are
                    // the cover's room's: below the collapse button, 24dp in (8dp on a phone on
                    // its side).) Where the cover's
                    // narrower than its room is tall (a foldable), it's centred there, shorter:
                    // so is this, as tall as it, or as its content needs.
                    BoxWithConstraints(
                        Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(
                                top = if (collapseColumn) sideVerticalPadding else CollapseRowHeight + sideVerticalPadding,
                                bottom = sideVerticalPadding,
                            ),
                    ) {
                        val coverPx = minOf(constraints.maxWidth, constraints.maxHeight)
                        val roomPx = constraints.maxHeight
                        // Larger on a tablet held wide and in DeX, where there's room and it's
                        // seen from further off: text, buttons and their spacing alike.
                        val outer = LocalDensity.current
                        val scale = if (largeSide) LargeSideScale else 1f
                        val scaled = Density(outer.density * scale, outer.fontScale)
                        CompositionLocalProvider(LocalDensity provides scaled) {
                            EvenSideColumn(
                                bandPx = coverPx.coerceAtMost(roomPx),
                                roomPx = roomPx,
                                heading = { SongHeading(player, song, likesEnabled) },
                                seek = { SeekRow(player, song) },
                                transport = { TransportRow(player, glassHaze) },
                                actions = { actions(Modifier) },
                                sourceHaze = stackHaze,
                            )
                        }
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    // On a tablet the column keeps to a phone's width, centred.
                    .widthIn(max = OneColumnMaxWidth + horizontalPadding * 2)
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = horizontalPadding),
            ) {
                OneColumnBody(
                    collapse = { CollapseButtonRow(onCollapse, glassHaze) },
                    cover = {
                        CoverCarousel(
                            player = player,
                            cornerRadius = cornerRadius,
                            bleed = horizontalPadding,
                            modifier = Modifier.fillMaxSize(),
                        )
                    },
                    controls = { SongControls(player, song, likesEnabled, glassHaze) },
                    actions = { actions(Modifier.padding(bottom = 36.dp)) },
                    sourceHaze = stackHaze,
                )
            }
        }
    }
}

// One column's least gaps: below the collapse button, between the cover and the controls, and
/** The song's title (and like button), the seek bar and the transport, top to bottom. */
