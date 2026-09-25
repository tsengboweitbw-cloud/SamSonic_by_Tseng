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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.IconButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.samsonic.R
import com.example.samsonic.ui.common.rememberScreenLoad
import com.example.samsonic.LocalAppContainer
import com.example.samsonic.model.Album
import com.example.samsonic.model.Song
import com.example.samsonic.model.artSeed
import com.example.samsonic.playback.LocalPlayerState
import com.example.samsonic.ui.common.StateContent
import com.example.samsonic.ui.common.UiState
import com.example.samsonic.ui.common.ArtKeys
import com.example.samsonic.ui.common.LocalArtTransitions
import com.example.samsonic.ui.common.sharedArt
import com.example.samsonic.ui.components.BackButtonClearance
import com.example.samsonic.ui.components.backButtonHazeSource
import com.example.samsonic.ui.components.GlassBackButton
import com.example.samsonic.ui.components.MediaArt
import com.example.samsonic.ui.components.PlayShuffleButtons
import com.example.samsonic.ui.components.SongRow
import com.example.samsonic.util.formatAlbumDuration
import com.example.samsonic.ui.theme.scrollTopFade
import dev.chrisbanes.haze.rememberHazeState

@Composable
fun AlbumDetailScreen(
    albumId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    contentPaddingBottom: Dp = 0.dp,
) {
    val player = LocalPlayerState.current
    val repository = LocalAppContainer.current.repository
    val cornerRadius by LocalAppContainer.current.themeManager.albumArtCornerRadius.collectAsStateWithLifecycle()

    val state = rememberScreenLoad(albumId, errorMessage = stringResource(R.string.library_album_load_error)) {
        repository.getAlbum(albumId)
    }

    // The album as the card tapped to open it knew it: its header shows at once, for
    // the cover to grow into while the rest loads.
    val preview = LocalArtTransitions.current.preview<Album>(ArtKeys.album(albumId))

    val backHaze = rememberHazeState()
    Box(modifier = modifier.fillMaxSize().statusBarsPadding()) {
        if (state is UiState.Loading && preview != null) {
            // Just where the loaded list puts its header, so the two swap unseen.
            Column(Modifier.fillMaxSize().padding(top = BackButtonClearance)) {
                AlbumHeader(preview, cornerRadius, actions = null)
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
        ) { (album, songs) ->
            val listState = rememberLazyListState()
            LazyColumn(
                modifier = Modifier.fillMaxSize().scrollTopFade(listState).backButtonHazeSource(backHaze),
                state = listState,
                contentPadding = PaddingValues(top = BackButtonClearance, bottom = contentPaddingBottom),
            ) {
                item {
                    AlbumHeader(album, cornerRadius) { PlayShuffleButtons(songs = songs, playlistTitle = album.title) }
                }
                items(songs, key = { it.id }) { song ->
                    SongRow(
                        song = song,
                        isCurrent = player.currentSong?.id == song.id,
                        liked = player.isLiked(song),
                        onToggleLike = { player.toggleLike(song) },
                        onClick = { player.play(song, songs) },
                        leading = {
                            Box(modifier = Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                                Text(
                                    text = song.trackNumber.toString(),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (player.currentSong?.id == song.id) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        },
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
 * The album's cover (which the tapped card's cover grows into), title, artist and
 * details, over its [actions] (play and shuffle), or none while it's loading.
 */
@Composable
private fun AlbumHeader(album: Album, cornerRadius: Dp, actions: (@Composable () -> Unit)?) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        MediaArt(
            coverArt = album.coverArt,
            colorSeed = album.id.artSeed(),
            size = 200.dp,
            cornerRadius = cornerRadius,
            smallFirst = true,
            modifier = Modifier.sharedArt(ArtKeys.album(album.id)),
        )
        Spacer(Modifier.height(16.dp))
        Text(text = album.title, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(4.dp))
        Text(
            text = album.artistName,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = listOfNotNull(album.year?.toString(), album.genre, formatAlbumDuration(album.durationSeconds))
                .joinToString(" • "),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(20.dp))
        if (actions != null) {
            actions()
            Spacer(Modifier.height(12.dp))
        }
    }
}
