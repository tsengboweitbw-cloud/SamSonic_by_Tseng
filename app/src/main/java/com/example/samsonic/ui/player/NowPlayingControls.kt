package com.example.samsonic.ui.player

import com.example.samsonic.playback.RepeatMode

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.samsonic.R
import com.example.samsonic.model.Song
import com.example.samsonic.playback.PlayerState
import com.example.samsonic.ui.components.ChromeButtonSize
import com.example.samsonic.ui.components.PressIconButton
import com.example.samsonic.ui.theme.LocalGlassSettings
import com.example.samsonic.ui.theme.OneUiRadius
import com.example.samsonic.ui.theme.OneUiSlider
import com.example.samsonic.ui.theme.glassSurface
import com.example.samsonic.util.formatDuration
import dev.chrisbanes.haze.HazeState

@Composable
internal fun SongControls(player: PlayerState, song: Song, likesEnabled: Boolean, haze: HazeState?) {
    SongHeading(player, song, likesEnabled)

    Spacer(Modifier.height(12.dp))

    SongTransport(player, song, haze)
}

/** The song's title and details, and the like button beside them. */
@Composable
internal fun SongHeading(player: PlayerState, song: Song, likesEnabled: Boolean) {
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
internal fun SongTransport(player: PlayerState, song: Song, haze: HazeState?) {
    SeekRow(player, song)

    Spacer(Modifier.height(16.dp))

    TransportRow(player, haze)
}

/** Shuffle, previous, play or pause, next and repeat, on a glass bar. */
@Composable
internal fun TransportRow(player: PlayerState, haze: HazeState?) {
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
internal fun SeekRow(player: PlayerState, song: Song) {
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
internal fun GlassCircleButton(
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
