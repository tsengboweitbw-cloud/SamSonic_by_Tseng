package com.example.samsonic.ui.player

import com.example.samsonic.playback.LocalPlayerState
import com.example.samsonic.playback.RepeatMode

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.samsonic.LocalAppContainer
import com.example.samsonic.R
import com.example.samsonic.model.Song
import com.example.samsonic.model.artSeed
import com.example.samsonic.ui.library.AddToPlaylistState
import com.example.samsonic.ui.components.ChromeButtonIconSize
import com.example.samsonic.ui.components.ChromeButtonSize
import com.example.samsonic.ui.components.PressIconButton
import com.example.samsonic.ui.theme.BlurredArtBackdrop
import com.example.samsonic.ui.theme.GlassAlpha
import com.example.samsonic.ui.theme.OneUiRadius
import com.example.samsonic.ui.theme.OneUiSlider
import com.example.samsonic.ui.theme.glassSurface
import com.example.samsonic.util.formatDuration

@Composable
fun NowPlayingScreen(
    onCollapse: () -> Unit,
    lyrics: PanelState,
    queue: PanelState,
    info: PanelState,
    modifier: Modifier = Modifier,
    // Where the top-right button opens Add to playlist; null hides it (no playlists to add to).
    addToPlaylist: AddToPlaylistState? = null,
) {
    val player = LocalPlayerState.current
    val song = player.currentSong ?: return
    val cornerRadius by LocalAppContainer.current.themeManager.albumArtCornerRadius.collectAsStateWithLifecycle()
    val likesEnabled by LocalAppContainer.current.themeManager.likesEnabled.collectAsStateWithLifecycle()
    val horizontalPadding = 24.dp
    Box(
        modifier = modifier
            .playerMorphRoot(PlayerSurface.Full)
            .fillMaxSize(),
    ) {
        // Crossfade fades the old and new backdrops at the same time, so mid-change
        // neither is opaque and the screen behind the player shows through. An
        // opaque base keeps the page solid while the art swaps.
        Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        )
        Crossfade(targetState = song, animationSpec = tween(500), label = "backdrop") { s ->
            BlurredArtBackdrop(coverArt = s.coverArt, colorSeed = s.id.artSeed())
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = horizontalPadding),
        ) {
        // A floating glass button (One UI Gallery style) instead of a bar; Add to
        // playlist is in the capsule stack at the bottom.
        Row(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
            GlassCircleButton(onClick = onCollapse) {
                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = stringResource(R.string.player_collapse), modifier = Modifier.size(ChromeButtonIconSize))
            }
        }

        Spacer(Modifier.height(28.dp))

        // Everything below stacks up from the bottom; the cover takes whatever
        // height is left and grows as large as that space allows.
        CoverCarousel(
            player = player,
            cornerRadius = cornerRadius,
            bleed = horizontalPadding,
            modifier = Modifier.weight(1f),
        )

        Spacer(Modifier.height(32.dp))

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

        Spacer(Modifier.height(12.dp))

        var dragPosition by remember(song.id) { mutableFloatStateOf(-1f) }
        val fraction = if (dragPosition >= 0f) dragPosition else {
            if (song.durationSeconds > 0) (player.positionSeconds / song.durationSeconds).coerceIn(0f, 1f) else 0f
        }
        // Elapsed | seek bar | total on one row; tabular digits keep the bar from jittering as time ticks.
        val timeStyle = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum")
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = formatDuration((fraction * song.durationSeconds).toInt()),
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

        Spacer(Modifier.height(16.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .nowPlayingGlass()
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

        Spacer(Modifier.height(16.dp))

        NowPlayingActions(
            lyrics = lyrics,
            queue = queue,
            info = info,
            addToPlaylist = addToPlaylist,
            song = song,
            modifier = Modifier.padding(bottom = 20.dp),
        )
        }
    }
}

@Composable
private fun GlassCircleButton(onClick: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    PressIconButton(
        onClick = onClick,
        // Matches the back button on the other pages.
        size = ChromeButtonSize,
        modifier = modifier.nowPlayingGlass(),
        content = content,
    )
}

/**
 * Frosted pill for Now Playing. No hazeState: nested inside the NavHost's own
 * hazeSource subtree (see MediaLists.SongRow comment), so this is a flat veil -
 * but the backdrop is already blurred art, so a thin onSurface wash (light in
 * dark theme) lets its colors glow through instead of a dense grey slab.
 */
@Composable
internal fun Modifier.nowPlayingGlass(): Modifier = glassSurface(
    shape = RoundedCornerShape(OneUiRadius.Pill),
    hazeState = null,
    tint = MaterialTheme.colorScheme.onSurface,
    alpha = GlassAlpha.NowPlaying,
)
