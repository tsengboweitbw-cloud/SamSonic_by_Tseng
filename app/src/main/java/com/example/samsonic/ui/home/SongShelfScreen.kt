package com.example.samsonic.ui.home

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.samsonic.LocalAppContainer
import com.example.samsonic.model.Song
import com.example.samsonic.playback.LocalPlayerState
import com.example.samsonic.ui.common.StateContent
import com.example.samsonic.ui.common.UiState
import com.example.samsonic.ui.common.rememberScreenLoad
import com.example.samsonic.ui.components.BackButtonClearance
import com.example.samsonic.ui.components.GlassBackButton
import com.example.samsonic.ui.components.PlayShuffleButtons
import com.example.samsonic.ui.components.SongRow
import com.example.samsonic.ui.components.backButtonHazeSource
import com.example.samsonic.ui.theme.scrollTopFade
import dev.chrisbanes.haze.rememberHazeState

/**
 * The full list behind one of Home's song carousels, opened from its section title,
 * laid out like [AlbumShelfScreen]: a tap plays the song, with the rest after it.
 * [homeSongs] is Home's own row for a [HomeShelf.sharesHomeList] shelf (random
 * picks), shown as is; otherwise the page loads the shelf's longer list itself.
 */
@Composable
fun SongShelfScreen(
    shelf: HomeShelf,
    homeSongs: UiState<List<Song>>?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    contentPaddingBottom: Dp = 0.dp,
) {
    val repository = LocalAppContainer.current.repository
    val player = LocalPlayerState.current
    val state = homeSongs ?: rememberScreenLoad(shelf, errorMessage = "Couldn't load ${shelf.title.lowercase()}") {
        repository.getSongList(shelf.type, SHELF_FULL_LIST_SIZE)
    }

    val backHaze = rememberHazeState()
    Box(modifier = modifier.fillMaxSize().statusBarsPadding()) {
        StateContent(state = state, modifier = Modifier.fillMaxSize()) { songs ->
            val listState = rememberLazyListState()
            LazyColumn(
                modifier = Modifier.fillMaxSize().scrollTopFade(listState).backButtonHazeSource(backHaze),
                state = listState,
                contentPadding = PaddingValues(top = BackButtonClearance, bottom = contentPaddingBottom),
            ) {
                item {
                    Column {
                        Text(
                            text = shelf.title,
                            style = MaterialTheme.typography.displaySmall,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 20.dp),
                        )
                        PlayShuffleButtons(songs = songs, modifier = Modifier.padding(horizontal = 24.dp))
                        Spacer(Modifier.height(12.dp))
                    }
                }
                items(songs, key = { it.id }) { song ->
                    SongRow(
                        song = song,
                        isCurrent = player.currentSong?.id == song.id,
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
