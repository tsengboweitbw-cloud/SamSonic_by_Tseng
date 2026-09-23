package com.example.samsonic.ui.player

import com.example.samsonic.playback.LocalPlayerState

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.samsonic.LocalAppContainer
import com.example.samsonic.model.artSeed
import com.example.samsonic.ui.components.MediaArt
import com.example.samsonic.model.Song
import com.example.samsonic.ui.theme.OneUiChrome

/**
 * The mini player's contents: art, title and quick controls over the progress
 * line. The glass pill around it is the collapsed player sheet ([PlayerSheet]),
 * which also handles the tap and the swipe up into Now Playing.
 */
@Composable
fun MiniPlayer(
    song: Song,
    onExpand: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val player = LocalPlayerState.current
    val cornerRadius by LocalAppContainer.current.themeManager.albumArtCornerRadius.collectAsStateWithLifecycle()

    // Art sits flush left of the row; the progress track below starts
    // only after it, so the two never overlap horizontally. It ends at
    // the horizontal middle of the "next" button, not the full width.
    val rowHorizontalPadding = 16.dp
    val artSize = 44.dp
    val artTextSpacing = 10.dp
    val iconButtonSize = 48.dp
    val progressStart = rowHorizontalPadding + artSize + artTextSpacing
    val progressEnd = rowHorizontalPadding + iconButtonSize / 2

    Box(
        modifier = modifier
            .playerMorphRoot(PlayerSurface.Mini)
            .fillMaxWidth()
            .height(OneUiChrome.BarHeight)
            .clickable(onClick = onExpand),
    ) {
        // Centered on the pill's true vertical midpoint - independent of
        // the progress track below, so the art never skews off-center.
        Row(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .padding(horizontal = rowHorizontalPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MediaArt(
                coverArt = song.coverArt,
                colorSeed = song.id.artSeed(),
                modifier = Modifier.playerMorphAnchor(PlayerElement.Art, PlayerSurface.Mini),
                size = artSize,
                cornerRadius = cornerRadius,
                shadowElevation = 0.dp,
            )
            Spacer(Modifier.width(artTextSpacing))
            Column(Modifier.weight(1f)) {
                Text(
                    text = song.title,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = song.artistName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(
                onClick = { player.togglePlayPause() },
                modifier = Modifier.size(iconButtonSize),
            ) {
                Icon(
                    imageVector = if (player.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (player.isPlaying) "Pause" else "Play",
                )
            }
            IconButton(
                onClick = { player.skipNext() },
                modifier = Modifier.size(iconButtonSize),
            ) {
                Icon(Icons.Filled.SkipNext, contentDescription = "Next")
            }
        }

        val progress = if (song.durationSeconds > 0) {
            (player.positionSeconds / song.durationSeconds).coerceIn(0f, 1f)
        } else 0f
        PlayerProgressLine(
            progress = progress,
            // Starts past the art (no horizontal overlap with it) and
            // ends at the "next" button's midpoint, not the full width.
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(bottom = 10.dp)
                .fillMaxWidth()
                .padding(start = progressStart, end = progressEnd)
                .playerMorphAnchor(PlayerElement.Progress, PlayerSurface.Mini)
                .height(2.dp),
        )
    }
}

/**
 * Thin rounded progress line, drawn the same way as the Now Playing seek bar's
 * track so the two read as one line while it morphs between them.
 */
@Composable
private fun PlayerProgressLine(progress: Float, modifier: Modifier = Modifier) {
    val activeColor = MaterialTheme.colorScheme.primary
    val trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
    Canvas(modifier = modifier) {
        // Fixed stroke: mid-morph the bounds are taller than the line itself.
        val stroke = 2.dp.toPx()
        val y = size.height / 2f
        drawLine(trackColor, Offset(0f, y), Offset(size.width, y), stroke, StrokeCap.Round)
        if (progress > 0f) {
            drawLine(activeColor, Offset(0f, y), Offset(size.width * progress, y), stroke, StrokeCap.Round)
        }
    }
}
