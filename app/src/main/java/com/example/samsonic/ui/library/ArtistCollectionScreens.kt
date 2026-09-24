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
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.samsonic.LocalAppContainer
import com.example.samsonic.data.LibrarySection
import com.example.samsonic.model.Album
import com.example.samsonic.playback.LocalPlayerState
import com.example.samsonic.ui.common.StateContent
import com.example.samsonic.ui.common.UiState
import com.example.samsonic.ui.common.rememberScreenLoad
import com.example.samsonic.ui.components.AlbumCard
import com.example.samsonic.ui.components.AlbumRow
import com.example.samsonic.ui.components.BackButtonClearance
import com.example.samsonic.ui.components.GlassBackButton
import com.example.samsonic.ui.components.PlayShuffleButtons
import com.example.samsonic.ui.components.SongRow
import com.example.samsonic.ui.components.backButtonHazeSource
import com.example.samsonic.ui.theme.scrollTopFade
import dev.chrisbanes.haze.rememberHazeState

/**
 * All of an artist's albums, opened from the Albums section of their page, or with
 * [appearsOn] the other artists' albums they sing on, from Appears on. Laid out
 * like the Library's Albums tab, in the grid or list the user picked there.
 */
@Composable
fun ArtistAlbumsScreen(
    artistId: String,
    onBack: () -> Unit,
    onAlbumClick: (Album) -> Unit,
    modifier: Modifier = Modifier,
    appearsOn: Boolean = false,
    contentPaddingBottom: Dp = 0.dp,
) {
    val container = LocalAppContainer.current
    val repository = container.repository
    val layouts by container.libraryLayoutManager.layouts.collectAsStateWithLifecycle()
    val state = rememberScreenLoad(artistId, appearsOn, errorMessage = "Couldn't load albums") {
        val (artist, albums) = repository.getArtist(artistId)
        artist to if (appearsOn) repository.getAppearsOn(artist, albums, repository.getSongsBy(artist)) else albums
    }

    val gridState = rememberLazyGridState()
    val backHaze = rememberHazeState()
    Box(modifier = modifier.fillMaxSize().statusBarsPadding()) {
        StateContent(state = state, modifier = Modifier.fillMaxSize()) { (artist, albums) ->
            LibraryCollection(
                state = UiState.Success(albums),
                gridState = gridState,
                layout = layouts.getValue(LibrarySection.ALBUMS),
                padding = LibraryPadding(top = BackButtonClearance, bottom = contentPaddingBottom),
                key = { it.id },
                card = { album, size -> AlbumCard(album, onClick = { onAlbumClick(album) }, artSize = size) },
                row = { album -> AlbumRow(album, onClick = { onAlbumClick(album) }) },
                header = {
                    ArtistListHeader(artistName = artist.name, title = if (appearsOn) "Appears on" else "Albums") {
                        PlayShuffleButtons(key = albums, loadSongs = { repository.getAlbumsSongs(albums) })
                    }
                },
                modifier = Modifier.scrollTopFade(gridState).backButtonHazeSource(backHaze),
            )
        }
        // Floats over the list: rows scroll up under it and fade out at the status bar.
        GlassBackButton(onClick = onBack, hazeState = backHaze, modifier = Modifier.padding(start = 16.dp, top = 8.dp))
    }
}

/** All of an artist's songs, opened from the Songs section of their page; rows as in Search. */
@Composable
fun ArtistSongsScreen(
    artistId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    contentPaddingBottom: Dp = 0.dp,
) {
    val player = LocalPlayerState.current
    val repository = LocalAppContainer.current.repository
    val state = rememberScreenLoad(artistId, errorMessage = "Couldn't load songs") {
        val (artist, albums) = repository.getArtist(artistId)
        artist to repository.getArtistSongs(artist, albums)
    }

    val listState = rememberLazyListState()
    val backHaze = rememberHazeState()
    Box(modifier = modifier.fillMaxSize().statusBarsPadding()) {
        StateContent(state = state, modifier = Modifier.fillMaxSize()) { (artist, songs) ->
            LazyColumn(
                modifier = Modifier.fillMaxSize().scrollTopFade(listState).backButtonHazeSource(backHaze),
                state = listState,
                contentPadding = PaddingValues(top = BackButtonClearance, bottom = contentPaddingBottom),
            ) {
                item {
                    ArtistListHeader(
                        artistName = artist.name,
                        title = "Songs",
                        modifier = Modifier.padding(horizontal = 20.dp),
                    ) {
                        PlayShuffleButtons(songs = songs)
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
        GlassBackButton(onClick = onBack, hazeState = backHaze, modifier = Modifier.padding(start = 16.dp, top = 8.dp))
    }
}

/**
 * The artist's name over the page's large [title], then [actions] (play and
 * shuffle). Starts at the content's 20dp edge; the caller supplies that inset.
 */
@Composable
private fun ArtistListHeader(
    artistName: String,
    title: String,
    modifier: Modifier = Modifier,
    actions: @Composable () -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = artistName,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 16.dp),
        )
        Text(
            text = title,
            style = MaterialTheme.typography.displaySmall,
            modifier = Modifier.padding(bottom = 20.dp),
        )
        Box(Modifier.padding(horizontal = 4.dp)) { actions() }
        Spacer(Modifier.height(12.dp))
    }
}
