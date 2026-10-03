package com.example.samsonic.ui.player

import com.example.samsonic.playback.LocalPlayerState
import com.example.samsonic.playback.RepeatMode

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
//import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.samsonic.LocalAppContainer
import com.example.samsonic.R
import com.example.samsonic.model.Song
import com.example.samsonic.playback.PlayerState
import com.example.samsonic.model.artSeed
import com.example.samsonic.ui.common.LocalWindowLayout
import com.example.samsonic.ui.library.AddToPlaylistState
import com.example.samsonic.ui.components.ChromeButtonIconSize
import com.example.samsonic.ui.components.ChromeButtonSize
import com.example.samsonic.ui.components.PressIconButton
import com.example.samsonic.ui.theme.BlurredArtBackdrop
import com.example.samsonic.ui.theme.LocalGlassSettings
import com.example.samsonic.ui.theme.OneUiRadius
import com.example.samsonic.ui.theme.OneUiSlider
import com.example.samsonic.ui.theme.glassSurface
import com.example.samsonic.util.formatDuration
import dev.chrisbanes.haze.HazeState
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
    val largeSide = columns == NowPlayingColumns.Two && (window.desktop || (window.isTablet)) //&& !window.foldable
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
// between the controls and the actions.
private val OneColumnTopGap = 24.dp
private val OneColumnCoverGap = 28.dp
private val OneColumnActionsGap = 16.dp

// The most of the spare height one column gives each of the last two gaps; the rest goes above the cover.
private val OneColumnMaxExtraGap = 32.dp

/**
 * One column, top to bottom: the [collapse] button, the [cover], the [controls] and the
 * [actions]. The cover is as large as the width allows (square), or the height, where that's
 * less; any height left over is shared evenly by the gaps between them, rather than all
 * pooling above the cover, so the controls aren't crowded at the foot on a tall, narrow
 * screen. Only the first three are a source for [sourceHaze]: the actions blur it.
 */
@Composable
private fun OneColumnBody(
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
private val SideColumnMinGap = 12.dp

/**
 * The song, the seek bar, the transport and the [actions] beside the cover, spread evenly over
 * a band [bandPx] tall (the cover's height, so they start and end level with it), centred in
 * the [roomPx] it has. Where they need more than the band (a phone on its side, or a
 * foldable) the band grows to take them, never with a gap under [SideColumnMinGap]; and where
 * even that is more than the room, it scrolls. Each is a source for [sourceHaze].
 */
@Composable
private fun EvenSideColumn(
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
private val CollapseRowTop = 12.dp
private val CollapseRowHeight = CollapseRowTop + ChromeButtonSize

// How much larger the side beside the cover is on a tablet held wide and in DeX.
private const val LargeSideScale = 1.2f

/**
 * A floating glass button (One UI Gallery style) instead of a bar, folding Now Playing
 * away; Add to playlist is with the other actions at the bottom.
 */
@Composable
private fun CollapseButtonRow(onCollapse: () -> Unit, haze: HazeState?, fillWidth: Boolean = true) {
    Row(modifier = (if (fillWidth) Modifier.fillMaxWidth() else Modifier).padding(top = CollapseRowTop)) {
        GlassCircleButton(onClick = onCollapse, haze = haze) {
            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = stringResource(R.string.player_collapse), modifier = Modifier.size(ChromeButtonIconSize))
        }
    }
}

/** The song's title (and like button), the seek bar and the transport, top to bottom. */
@Composable
private fun SongControls(player: PlayerState, song: Song, likesEnabled: Boolean, haze: HazeState?) {
    SongHeading(player, song, likesEnabled)

    Spacer(Modifier.height(12.dp))

    SongTransport(player, song, haze)
}

/** The song's title and details, and the like button beside them. */
@Composable
private fun SongHeading(player: PlayerState, song: Song, likesEnabled: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NowPlayingTitle(song, Modifier.weight(1f))
        if (likesEnabled) {
            val liked = player.isLiked(song)
            PressIconButton(onClick = { player.toggleLike(song) }) {
                Icon(
                    imageVector = if (liked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    contentDescription = stringResource(if (liked) R.string.components_unlike else R.string.components_like),
                    tint = if (liked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(28.dp),
                )
            }
        }
    }
}

/** The seek bar, and the transport (shuffle, previous, play, next, repeat) below it. */
@Composable
private fun SongTransport(player: PlayerState, song: Song, haze: HazeState?) {
    SeekRow(player, song)

    Spacer(Modifier.height(16.dp))

    TransportRow(player, haze)
}

/** Shuffle, previous, play or pause, next and repeat, on a glass bar. */
@Composable
private fun TransportRow(player: PlayerState, haze: HazeState?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .nowPlayingGlass(haze)
            .padding(horizontal = 8.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PressIconButton(onClick = { player.toggleShuffle() }) {
            Icon(
                Icons.Filled.Shuffle,
                contentDescription = stringResource(R.string.components_shuffle),
                tint = if (player.shuffle) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
        }
        PressIconButton(onClick = { player.skipPrevious() }) {
            Icon(Icons.Filled.SkipPrevious, contentDescription = stringResource(R.string.player_previous), modifier = Modifier.size(36.dp))
        }
        PressIconButton(
            onClick = { player.togglePlayPause() },
            size = 56.dp,
        ) {
            Icon(
                imageVector = if (player.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = stringResource(if (player.isPlaying) R.string.player_pause else R.string.components_play),
                modifier = Modifier.size(44.dp),
            )
        }
        PressIconButton(onClick = { player.skipNext() }) {
            Icon(Icons.Filled.SkipNext, contentDescription = stringResource(R.string.player_next), modifier = Modifier.size(36.dp))
        }
        PressIconButton(onClick = { player.cycleRepeat() }) {
            Icon(
                imageVector = if (player.repeatMode == RepeatMode.ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                contentDescription = stringResource(R.string.player_repeat),
                tint = if (player.repeatMode == RepeatMode.OFF) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/**
 * Elapsed | seek bar | total on one row. Its own scope, so the playback position
 * ticking, and a finger dragging the bar, recompose only this row, not Now Playing;
 * the elapsed time changes only as the whole second does.
 */
@Composable
private fun SeekRow(player: PlayerState, song: Song) {
    var dragPosition by remember(song.id) { mutableFloatStateOf(-1f) }
    val fraction = if (dragPosition >= 0f) dragPosition else {
        if (song.durationSeconds > 0) (player.positionSeconds / song.durationSeconds).coerceIn(0f, 1f) else 0f
    }
    val elapsed by remember(song.id) {
        derivedStateOf {
            val at = if (dragPosition >= 0f) dragPosition * song.durationSeconds else player.positionSeconds
            at.toInt().coerceIn(0, song.durationSeconds.coerceAtLeast(0))
        }
    }
    // Tabular digits keep the bar from jittering as time ticks.
    val timeStyle = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum")
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = formatDuration(elapsed),
            style = timeStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OneUiSlider(
            value = fraction,
            onValueChange = { dragPosition = it },
            onValueChangeFinished = {
                player.seekToFraction(dragPosition)
                dragPosition = -1f
            },
            modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
            trackModifier = Modifier.playerMorphAnchor(PlayerElement.Progress, PlayerSurface.Full),
        )
        Text(
            text = formatDuration(song.durationSeconds),
            style = timeStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun GlassCircleButton(
    onClick: () -> Unit,
    haze: HazeState?,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    PressIconButton(
        onClick = onClick,
        // Matches the back button on the other pages.
        size = ChromeButtonSize,
        modifier = modifier.nowPlayingGlass(haze),
        content = content,
    )
}

/**
 * Now Playing's glass, on the collapse button, the controls and the capsule stack
 * alike: dense and heavily blurred, so a capsule lifted over the controls doesn't
 * show them, only a hint of their colour. [haze] is what it blurs (see
 * [NowPlayingScreen]); the button and the controls, inside its source above the
 * backdrop, blur only the backdrop. Null draws the same glass flat, for where the
 * blur can't be seen, each live blur costing every frame. Its opacity and blur are
 * the player's own settings, not scaled by the app's glass opacity.
 */
@Composable
internal fun Modifier.nowPlayingGlass(haze: HazeState?): Modifier {
    val glass = LocalGlassSettings.current
    return glassSurface(
        shape = RoundedCornerShape(OneUiRadius.Pill),
        hazeState = haze,
        tint = MaterialTheme.colorScheme.surfaceContainerHigh,
        alpha = glass.playerAlpha,
        blurRadius = glass.playerBlur,
        noiseFactor = 0.18f,
        scaleOpacity = false,
    )
}
