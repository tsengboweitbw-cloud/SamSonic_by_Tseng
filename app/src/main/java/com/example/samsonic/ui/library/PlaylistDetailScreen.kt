package com.example.samsonic.ui.library

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.samsonic.ui.common.rememberScreenLoad
import com.example.samsonic.LocalAppContainer
import com.example.samsonic.model.Playlist
import com.example.samsonic.model.Song
import com.example.samsonic.model.artSeed
import com.example.samsonic.playback.LocalPlayerState
import com.example.samsonic.ui.common.StateContent
import com.example.samsonic.ui.common.UiState
import com.example.samsonic.ui.common.ArtKeys
import com.example.samsonic.ui.common.LocalArtTransitions
import com.example.samsonic.ui.common.sharedArt
import androidx.compose.material3.CircularProgressIndicator
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.samsonic.ui.components.BackButtonClearance
import com.example.samsonic.ui.components.backButtonHazeSource
import com.example.samsonic.ui.components.GlassBackButton
import com.example.samsonic.ui.components.MediaArt
import com.example.samsonic.ui.components.PlayShuffleButtons
import com.example.samsonic.ui.components.SongRow
import com.example.samsonic.ui.theme.scrollTopFade
import dev.chrisbanes.haze.rememberHazeState

@Composable
fun PlaylistDetailScreen(
    playlistId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    contentPaddingBottom: Dp = 0.dp,
) {
    val player = LocalPlayerState.current
    val repository = LocalAppContainer.current.repository
    val cornerRadius by LocalAppContainer.current.themeManager.albumArtCornerRadius.collectAsStateWithLifecycle()

    val state = rememberScreenLoad(playlistId, errorMessage = "Couldn't load playlist") {
        repository.getPlaylist(playlistId)
    }

    // The playlist as the card tapped to open it knew it: its header shows at once, for
    // the cover to grow into while the songs load.
    val preview = LocalArtTransitions.current.preview<Playlist>(ArtKeys.playlist(playlistId))

    val backHaze = rememberHazeState()
    Box(modifier = modifier.fillMaxSize().statusBarsPadding()) {
        if (state is UiState.Loading && preview != null) {
            // Just where the loaded list puts its header, so the two swap unseen.
            Column(Modifier.fillMaxSize().padding(top = BackButtonClearance)) {
                PlaylistHeader(preview, preview.songCount, cornerRadius, actions = null)
                Box(Modifier.fillMaxWidth().padding(top = 24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }
        }
        StateContent(
            state = state,
            modifier = Modifier.fillMaxSize(),
            // The preview above stands in for the spinner.
            loading = if (preview != null) ({}) else null,
        ) { (playlist, songs) ->
            val listState = rememberLazyListState()
            LazyColumn(
                modifier = Modifier.fillMaxSize().scrollTopFade(listState).backButtonHazeSource(backHaze),
                state = listState,
                contentPadding = PaddingValues(top = BackButtonClearance, bottom = contentPaddingBottom),
            ) {
                item {
                    PlaylistHeader(playlist, songs.size, cornerRadius) { PlayShuffleButtons(songs = songs) }
                }
                items(songs, key = { it.id }) { song ->
                    SongRow(
                        song = song,
                        isCurrent = player.currentSong?.id == song.id,
                        liked = player.isLiked(song),
                        onToggleLike = { player.toggleLike(song) },
                        onClick = { player.play(song, songs) },
                    )
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
        // Floats over the list: rows scroll up under it and fade out at the status bar.
        GlassBackButton(onClick = onBack, hazeState = backHaze, modifier = Modifier.padding(start = 16.dp, top = 8.dp))
    }
}

/**
 * The playlist's cover (which the tapped card's cover grows into, rounded like every
 * album cover), name, description and [songCount], over its [actions] (play and
 * shuffle), or none while it's loading.
 */
@Composable
private fun PlaylistHeader(playlist: Playlist, songCount: Int, cornerRadius: Dp, actions: (@Composable () -> Unit)?) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        MediaArt(
            coverArt = playlist.coverArt,
            colorSeed = playlist.id.artSeed(),
            size = 180.dp,
            cornerRadius = cornerRadius,
            smallFirst = true,
            modifier = Modifier.sharedArt(ArtKeys.playlist(playlist.id)),
        )
        Spacer(Modifier.height(16.dp))
        Text(text = playlist.name, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
        if (playlist.description.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = playlist.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = "$songCount songs",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(20.dp))
        if (actions != null) {
            actions()
            Spacer(Modifier.height(8.dp))
        }
    }
}
