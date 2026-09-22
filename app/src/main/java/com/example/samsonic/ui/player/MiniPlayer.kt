package com.example.samsonic.ui.player

import com.example.samsonic.playback.LocalPlayerState

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.samsonic.model.artSeed
import com.example.samsonic.ui.components.MediaArt
import com.example.samsonic.ui.theme.GlassAlpha
import com.example.samsonic.ui.theme.LocalHazeState
import com.example.samsonic.ui.theme.OneUiChrome
import com.example.samsonic.ui.theme.OneUiRadius
import com.example.samsonic.ui.theme.glassSurface

@Composable
fun MiniPlayer(
    onExpand: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val player = LocalPlayerState.current
    val song = player.currentSong

    AnimatedVisibility(
        visible = song != null,
        enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
        exit = fadeOut() + slideOutVertically(targetOffsetY = { it }),
        modifier = modifier,
    ) {
        if (song == null) return@AnimatedVisibility
        val hazeState = LocalHazeState.current

        // Art sits flush left of the row; the progress track below starts
        // only after it, so the two never overlap horizontally.
        val rowHorizontalPadding = 16.dp
        val artSize = 44.dp
        val artTextSpacing = 10.dp
        val progressStart = rowHorizontalPadding + artSize + artTextSpacing

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                // Same total thickness as the floating nav bar, so the two
                // bars are literally the same pill height, not just similar.
                .height(OneUiChrome.BarHeight)
                .glassSurface(
                    // Same fully-rounded pill as FloatingNavBar, so the two
                    // floating bars read as one consistent shape language.
                    shape = RoundedCornerShape(OneUiRadius.Pill),
                    hazeState = hazeState,
                    tint = MaterialTheme.colorScheme.surfaceContainerHigh,
                    alpha = GlassAlpha.MiniPlayer,
                )
                .clickable(onClick = onExpand),
            // Centers the row + progress track as one block within the fixed
            // bar height, keeping both clear of the pill's rounded top/bottom.
            verticalArrangement = Arrangement.Center,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = rowHorizontalPadding),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MediaArt(
                    coverArt = song.coverArt,
                    colorSeed = song.id.artSeed(),
                    size = artSize,
                    cornerRadius = OneUiRadius.Chip,
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
                IconButton(onClick = { player.togglePlayPause() }) {
                    Icon(
                        imageVector = if (player.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (player.isPlaying) "Pause" else "Play",
                    )
                }
                IconButton(onClick = { player.skipNext() }) {
                    Icon(Icons.Filled.SkipNext, contentDescription = "Next")
                }
            }

            Spacer(Modifier.height(4.dp))

            val progress = if (song.durationSeconds > 0) {
                (player.positionSeconds / song.durationSeconds).coerceIn(0f, 1f)
            } else 0f
            LinearProgressIndicator(
                progress = { progress },
                // Starts past the art (no horizontal overlap with it) and
                // stays inset from the pill's trailing curve too.
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = progressStart, end = rowHorizontalPadding)
                    .height(2.dp)
                    .clip(RoundedCornerShape(50)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            )
        }
    }
}
