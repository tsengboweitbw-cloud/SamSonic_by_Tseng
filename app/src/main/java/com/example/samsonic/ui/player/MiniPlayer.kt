package com.example.samsonic.ui.player

import com.example.samsonic.playback.LocalPlayerState

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.samsonic.LocalAppContainer
import com.example.samsonic.R
import com.example.samsonic.model.artSeed
import com.example.samsonic.ui.components.MediaArt
import com.example.samsonic.ui.components.PressIconButton
import com.example.samsonic.ui.components.marqueeWhenLong
import com.example.samsonic.ui.components.pressClickable
import com.example.samsonic.model.Song
import com.example.samsonic.ui.theme.OneUiChrome
import com.example.samsonic.ui.theme.accentPalette
import com.example.samsonic.ui.theme.progressBrush

/**
 * The mini player's contents: art, title and quick controls over the progress
 * line. The glass pill around it is the collapsed player sheet ([PlayerSheet]),
 * which also handles the tap and the swipe up into Now Playing. Swiping the
 * song sideways changes it, when that's switched on ([SongStrip]).
 */
@Composable
fun MiniPlayer(
    song: Song,
    onExpand: () -> Unit,
    modifier: Modifier = Modifier,
    // Under the nav rail: the rail's width, standing on end ([RailMiniPlayer]).
    compact: Boolean = false,
    // Under a short rail (a phone on its side): the same, shorter.
    short: Boolean = false,
) {
    if (compact) {
        RailMiniPlayer(song, onExpand, modifier, short)
        return
    }
    val player = LocalPlayerState.current
    val cornerRadius by LocalAppContainer.current.themeManager.albumArtCornerRadius.collectAsStateWithLifecycle()

    // The progress track below starts only after the art, so the two never
    // overlap horizontally. It ends at the horizontal middle of the last button
    // ("next", or play/pause when swiping changes song), not the full width.
    val rowHorizontalPadding = 12.dp
    val artSize = 44.dp
    val artTextSpacing = 10.dp
    val iconButtonSize = 48.dp
    // The art is inset as far from the pill's left edge as the last button's glyph is
    // from its right edge: the row padding, the button's padding around its
    // 24dp icon, and the blank strip inside the icon right of the glyph (the
    // SkipNext and Pause glyphs both span x = 6..18 of their 24 viewport).
    val artStartPadding = rowHorizontalPadding + (iconButtonSize - 24.dp) / 2 + 6.dp
    val progressStart = artStartPadding + artSize + artTextSpacing
    val progressEnd = rowHorizontalPadding + iconButtonSize / 2

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(OneUiChrome.BarHeight)
            // A tap shrinks the contents a touch inside the still glass pill (the sheet
            // draws the glass). Ahead of the morph root, so the morph measures the art and
            // progress line unscaled.
            .pressClickable(onExpand, pressedScale = 0.96f)
            .playerMorphRoot(PlayerSurface.Mini),
    ) {
        val swipeForSong by LocalAppContainer.current.themeManager.swipeMiniForSong.collectAsStateWithLifecycle()
        // Centered on the pill's true vertical midpoint - independent of
        // the progress track below, so the art never skews off-center.
        Row(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .padding(end = rowHorizontalPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // From the pill's edge, so a song swiped aside slides out under the glass's rim.
            SongStrip(
                song = song,
                swipeEnabled = swipeForSong,
                modifier = Modifier.weight(1f),
            ) { shown, isCurrent ->
                Row(
                    modifier = Modifier.padding(start = artStartPadding),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MediaArt(
                        coverArt = shown.coverArt,
                        colorSeed = shown.id.artSeed(),
                        // Only the song playing travels into Now Playing.
                        modifier = if (isCurrent) Modifier.playerMorphAnchor(PlayerElement.Art, PlayerSurface.Mini) else Modifier,
                        size = artSize,
                        cornerRadius = cornerRadius,
                        shadowElevation = 0.dp,
                    )
                    Spacer(Modifier.width(artTextSpacing))
                    // The bar is a fixed height, so the text stops growing with the system
                    // font size at a point where two lines still clear the progress line.
                    val density = LocalDensity.current
                    CompositionLocalProvider(
                        LocalDensity provides Density(density.density, fontScale = minOf(density.fontScale, MaxTextFontScale)),
                    ) {
                        // Nudged up so the artist line clears the progress line.
                        Column(Modifier.padding(bottom = 6.dp)) {
                            // Only the song playing scrolls a line too long to fit; the ones
                            // either side, seen only mid-swipe, are cut short.
                            val scroll = if (isCurrent) Modifier.marqueeWhenLong() else Modifier
                            Text(
                                text = shown.title,
                                style = MaterialTheme.typography.labelLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = scroll,
                            )
                            Text(
                                text = shown.artistName,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = scroll,
                            )
                        }
                    }
                }
            }
            PressIconButton(
                onClick = { player.togglePlayPause() },
                size = iconButtonSize,
            ) {
                Icon(
                    imageVector = if (player.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = stringResource(if (player.isPlaying) R.string.player_pause else R.string.components_play),
                )
            }
            // Swiping takes the next button's place, leaving the song more room.
            if (!swipeForSong) {
                PressIconButton(
                    onClick = { player.skipNext() },
                    size = iconButtonSize,
                ) {
                    Icon(Icons.Filled.SkipNext, contentDescription = stringResource(R.string.player_next))
                }
            }
        }

        PlayerProgressLine(
            // Read as the line is drawn, so the playback tick redraws only the line.
            progress = {
                if (song.durationSeconds > 0) (player.positionSeconds / song.durationSeconds).coerceIn(0f, 1f) else 0f
            },
            // Starts past the art (no horizontal overlap with it) and
            // ends at the last button's midpoint, not the full width.
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(bottom = 7.dp)
                .fillMaxWidth()
                .padding(start = progressStart, end = progressEnd)
                .playerMorphAnchor(PlayerElement.Progress, PlayerSurface.Mini)
                .height(2.dp),
        )
    }
}

/** The largest font scale the mini player's text follows; past it the two lines would reach the progress line. */
private const val MaxTextFontScale = 1.15f

/** How tall the mini player is under the nav rail, as wide as the rail. */
val RailMiniPlayerHeight = 176.dp

/**
 * The mini player under the nav rail, as narrow as the rail and standing on end like
 * it: the art, set down from the capsule's round top so its corners keep clear of the
 * curve, and play/pause below in a ring that fills as the song plays, sitting in the
 * round foot. A tap opens Now Playing, which the art grows into, as from the wide mini
 * player (the ring, a circle, has no seek bar to become: that fades in with the rest).
 */
@Composable
private fun RailMiniPlayer(song: Song, onExpand: () -> Unit, modifier: Modifier = Modifier, short: Boolean = false) {
    val player = LocalPlayerState.current
    val cornerRadius by LocalAppContainer.current.themeManager.albumArtCornerRadius.collectAsStateWithLifecycle()
    if (short) {
        // A phone on its side: just the cover, round, with the progress running round it;
        // a tap opens Now Playing, a long press plays or pauses.
        Box(
            modifier = modifier
                .fillMaxSize()
                .pressClickable(onExpand, pressedScale = 0.96f, onLongClick = { player.togglePlayPause() })
                .playerMorphRoot(PlayerSurface.Mini),
            contentAlignment = Alignment.Center,
        ) {
            ProgressRing(
                progress = {
                    if (song.durationSeconds > 0) (player.positionSeconds / song.durationSeconds).coerceIn(0f, 1f) else 0f
                },
                modifier = Modifier.size(58.dp),
            )
            MediaArt(
                coverArt = song.coverArt,
                colorSeed = song.id.artSeed(),
                modifier = Modifier.playerMorphAnchor(PlayerElement.Art, PlayerSurface.Mini),
                size = 46.dp,
                cornerRadius = 23.dp,
                shadowElevation = 0.dp,
            )
        }
        return
    }
    Column(
        modifier = modifier
            .fillMaxSize()
            .pressClickable(onExpand, pressedScale = 0.96f)
            .playerMorphRoot(PlayerSurface.Mini)
            .padding(top = 30.dp, bottom = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        MediaArt(
            coverArt = song.coverArt,
            colorSeed = song.id.artSeed(),
            modifier = Modifier.playerMorphAnchor(PlayerElement.Art, PlayerSurface.Mini),
            size = 52.dp,
            cornerRadius = cornerRadius,
            shadowElevation = 0.dp,
        )
        Spacer(Modifier.weight(1f))
        Box(contentAlignment = Alignment.Center) {
            ProgressRing(
                // Read as the ring is drawn, so the playback tick redraws only the ring.
                progress = {
                    if (song.durationSeconds > 0) (player.positionSeconds / song.durationSeconds).coerceIn(0f, 1f) else 0f
                },
                modifier = Modifier.size(56.dp),
            )
            PressIconButton(onClick = { player.togglePlayPause() }, size = 48.dp) {
                Icon(
                    imageVector = if (player.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = stringResource(if (player.isPlaying) R.string.player_pause else R.string.components_play),
                )
            }
        }
    }
}

/**
 * The progress line bent into a ring: its track all the way round, and the part played
 * so far clockwise from the top, drawn as the line and the seek bar are.
 */
@Composable
private fun ProgressRing(progress: () -> Float, modifier: Modifier = Modifier) {
    val palette = MaterialTheme.accentPalette
    val trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
    Canvas(modifier = modifier) {
        val stroke = 2.5.dp.toPx()
        val inset = stroke / 2
        val arcSize = Size(size.width - stroke, size.height - stroke)
        val topLeft = Offset(inset, inset)
        drawArc(trackColor, startAngle = 0f, sweepAngle = 360f, useCenter = false, topLeft = topLeft, size = arcSize, style = Stroke(stroke))
        val at = progress()
        if (at > 0f) {
            drawArc(
                brush = progressBrush(palette, 0f, size.width),
                startAngle = -90f,
                sweepAngle = 360f * at,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
        }
    }
}

/**
 * Thin rounded progress line, drawn the same way as the Now Playing seek bar's
 * track so the two read as one line while it morphs between them.
 */
@Composable
private fun PlayerProgressLine(progress: () -> Float, modifier: Modifier = Modifier) {
    val palette = MaterialTheme.accentPalette
    val trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
    Canvas(modifier = modifier) {
        // Fixed stroke: mid-morph the bounds are taller than the line itself.
        val stroke = 2.dp.toPx()
        val y = size.height / 2f
        drawLine(trackColor, Offset(0f, y), Offset(size.width, y), stroke, StrokeCap.Round)
        val at = progress()
        if (at > 0f) {
            val active = progressBrush(palette, 0f, size.width)
            drawLine(active, Offset(0f, y), Offset(size.width * at, y), stroke, StrokeCap.Round)
        }
    }
}
