package com.example.samsonic.ui.player

import com.example.samsonic.playback.LocalPlayerState

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
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
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .glassSurface(
                    // Same fully-rounded pill as FloatingNavBar, so the two
                    // floating bars read as one consistent shape language.
                    shape = RoundedCornerShape(OneUiRadius.Pill),
                    hazeState = hazeState,
                    tint = MaterialTheme.colorScheme.surfaceContainerHigh,
                    alpha = GlassAlpha.MiniPlayer,
                )
                .clickable(onClick = onExpand),
        ) {
            val progress = if (song.durationSeconds > 0) {
                (player.positionSeconds / song.durationSeconds).coerceIn(0f, 1f)
            } else 0f
            LinearProgressIndicator(
                progress = { progress },
                // Inset from the edges so the pill's rounded ends don't clip
                // the track (a full-bleed bar would vanish under the curve).
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp)
                    .padding(top = 10.dp)
                    .height(2.dp)
                    .clip(RoundedCornerShape(50)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    // Wider horizontal inset than a plain card needs, so the
                    // art and buttons clear the pill's large rounded ends
                    // instead of sitting flush against the curve.
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MediaArt(
                    coverArt = song.coverArt,
                    colorSeed = song.id.artSeed(),
                    size = 48.dp,
                    cornerRadius = OneUiRadius.Chip,
                    shadowElevation = 0.dp,
                )
                Spacer(Modifier.width(10.dp))
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
        }
    }
}
