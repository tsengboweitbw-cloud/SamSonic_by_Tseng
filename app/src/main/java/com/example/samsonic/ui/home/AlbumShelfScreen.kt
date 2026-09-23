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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.samsonic.LocalAppContainer
import com.example.samsonic.model.Album
import com.example.samsonic.ui.common.StateContent
import com.example.samsonic.ui.common.UiState
import com.example.samsonic.ui.common.rememberScreenLoad
import com.example.samsonic.ui.components.AlbumRow
import com.example.samsonic.ui.components.BackButtonClearance
import com.example.samsonic.ui.components.backButtonHazeSource
import com.example.samsonic.ui.components.GlassBackButton
import com.example.samsonic.ui.components.PlayShuffleButtons
import com.example.samsonic.ui.theme.scrollTopFade
import dev.chrisbanes.haze.rememberHazeState

/**
 * The full list behind one of Home's album carousels, opened from its section title.
 * [homeAlbums] is Home's own row for a [AlbumShelf.sharesHomeList] shelf, shown as
 * is; otherwise the page loads the shelf's longer list itself.
 */
@Composable
fun AlbumShelfScreen(
    shelf: AlbumShelf,
    homeAlbums: UiState<List<Album>>?,
    onBack: () -> Unit,
    onAlbumClick: (Album) -> Unit,
    modifier: Modifier = Modifier,
    contentPaddingBottom: Dp = 0.dp,
) {
    val repository = LocalAppContainer.current.repository

    val state = homeAlbums ?: rememberScreenLoad(shelf, errorMessage = "Couldn't load ${shelf.title.lowercase()}") {
        repository.getAlbumList(shelf.type, SHELF_FULL_LIST_SIZE)
    }

    val backHaze = rememberHazeState()
    Box(modifier = modifier.fillMaxSize().statusBarsPadding()) {
        StateContent(state = state, modifier = Modifier.fillMaxSize()) { albums ->
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
                        PlayShuffleButtons(
                            key = albums,
                            loadSongs = { repository.getAlbumsSongs(albums) },
                            modifier = Modifier.padding(horizontal = 24.dp),
                        )
                        Spacer(Modifier.height(12.dp))
                    }
                }
                // Keyed by position too: a server can list the same album twice.
                itemsIndexed(albums, key = { index, album -> "${album.id}#$index" }) { _, album ->
                    AlbumRow(album = album, onClick = { onAlbumClick(album) })
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
        // Floats over the list: rows scroll up under it and fade out at the status bar.
        GlassBackButton(onClick = onBack, hazeState = backHaze, modifier = Modifier.padding(start = 16.dp, top = 8.dp))
    }
}
