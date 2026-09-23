package com.example.samsonic.ui.player

import com.example.samsonic.playback.LocalPlayerState
import com.example.samsonic.playback.RepeatMode

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.samsonic.LocalAppContainer
import com.example.samsonic.model.artSeed
import com.example.samsonic.ui.components.MediaArtFill
import com.example.samsonic.ui.theme.BlurredArtBackdrop
import com.example.samsonic.ui.theme.GlassAlpha
import com.example.samsonic.ui.theme.OneUiRadius
import com.example.samsonic.ui.theme.OneUiSlider
import com.example.samsonic.ui.theme.glassSurface
import com.example.samsonic.util.formatAudioInfo
import com.example.samsonic.util.formatDuration

@Composable
fun NowPlayingScreen(
    onCollapse: () -> Unit,
    onShowQueue: () -> Unit,
    onShowLyrics: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val player = LocalPlayerState.current
    val song = player.currentSong ?: run {
        onCollapse()
        return
    }
    val cornerRadius by LocalAppContainer.current.themeManager.albumArtCornerRadius.collectAsStateWithLifecycle()

    Box(modifier = modifier.fillMaxSize()) {
        BlurredArtBackdrop(coverArt = song.coverArt, colorSeed = song.id.artSeed())

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp),
        ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                // No hazeState: nested inside the NavHost's own hazeSource subtree
                // (see MediaLists.SongRow comment) - falls back to a flat translucent fill.
                .glassSurface(
                    shape = RoundedCornerShape(OneUiRadius.Pill),
                    hazeState = null,
                    tint = MaterialTheme.colorScheme.surfaceContainerHigh,
                    alpha = GlassAlpha.Card,
                )
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            IconButton(onClick = onCollapse) {
                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Collapse")
            }
            Text(
                text = "NOW PLAYING",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            IconButton(onClick = onShowQueue) {
                Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = "Queue")
            }
        }

        Spacer(Modifier.height(28.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(cornerRadius)),
        ) {
            MediaArtFill(coverArt = song.coverArt, colorSeed = song.id.artSeed())
        }

        Spacer(Modifier.height(32.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = song.title,
                    style = MaterialTheme.typography.headlineMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (song.albumTitle.isNotBlank()) {
                    Text(
                        text = song.albumTitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = song.artistName,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val audioInfo = formatAudioInfo(song)
                if (audioInfo != null) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = audioInfo,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            val liked = player.isLiked(song)
            IconButton(onClick = { player.toggleLike(song) }) {
                Icon(
                    imageVector = if (liked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    contentDescription = if (liked) "Unlike" else "Like",
                    tint = if (liked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(28.dp),
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        var dragPosition by remember(song.id) { mutableFloatStateOf(-1f) }
        val fraction = if (dragPosition >= 0f) dragPosition else {
            if (song.durationSeconds > 0) (player.positionSeconds / song.durationSeconds).coerceIn(0f, 1f) else 0f
        }
        OneUiSlider(
            value = fraction,
            onValueChange = { dragPosition = it },
            onValueChangeFinished = {
                player.seekToFraction(dragPosition)
                dragPosition = -1f
            },
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = formatDuration((fraction * song.durationSeconds).toInt()),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = formatDuration(song.durationSeconds),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                // No hazeState: nested inside the NavHost's own hazeSource subtree
                // (see MediaLists.SongRow comment) - falls back to a flat translucent fill.
                .glassSurface(
                    shape = RoundedCornerShape(OneUiRadius.Pill),
                    hazeState = null,
                    tint = MaterialTheme.colorScheme.surfaceContainerHigh,
                    alpha = GlassAlpha.Card,
                )
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { player.toggleShuffle() }) {
                Icon(
                    Icons.Filled.Shuffle,
                    contentDescription = "Shuffle",
                    tint = if (player.shuffle) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                )
            }
            IconButton(onClick = { player.skipPrevious() }, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Filled.SkipPrevious, contentDescription = "Previous", modifier = Modifier.size(36.dp))
            }
            IconButton(
                onClick = { player.togglePlayPause() },
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(36.dp))
                    .background(MaterialTheme.colorScheme.primary),
            ) {
                Icon(
                    imageVector = if (player.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (player.isPlaying) "Pause" else "Play",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(36.dp),
                )
            }
            IconButton(onClick = { player.skipNext() }, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Filled.SkipNext, contentDescription = "Next", modifier = Modifier.size(36.dp))
            }
            IconButton(onClick = { player.cycleRepeat() }) {
                Icon(
                    imageVector = if (player.repeatMode == RepeatMode.ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                    contentDescription = "Repeat",
                    tint = if (player.repeatMode == RepeatMode.OFF) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary,
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 20.dp),
            horizontalArrangement = Arrangement.Center,
        ) {
            Row(
                modifier = Modifier
                    // No hazeState: nested inside the NavHost's own hazeSource subtree
                    // (see MediaLists.SongRow comment) - falls back to a flat translucent fill.
                    .glassSurface(
                        shape = RoundedCornerShape(OneUiRadius.Pill),
                        hazeState = null,
                        tint = MaterialTheme.colorScheme.surfaceContainerHigh,
                        alpha = GlassAlpha.Card,
                    )
                    .clickable(onClick = onShowLyrics)
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Lyrics, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Lyrics")
            }
        }
        }
    }
}
