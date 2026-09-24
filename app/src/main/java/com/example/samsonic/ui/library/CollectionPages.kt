package com.example.samsonic.ui.library

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
import com.example.samsonic.model.Artist
import com.example.samsonic.model.Song
import com.example.samsonic.playback.LocalPlayerState
import com.example.samsonic.ui.common.StateContent
import com.example.samsonic.ui.common.UiState
import com.example.samsonic.ui.components.AlbumCard
import com.example.samsonic.ui.components.AlbumRow
import com.example.samsonic.ui.components.ArtistCard
import com.example.samsonic.ui.components.ArtistRow
import com.example.samsonic.ui.components.BackButtonClearance
import com.example.samsonic.ui.components.GlassBackButton
import com.example.samsonic.ui.components.PlayShuffleButtons
import com.example.samsonic.ui.components.SongRow
import com.example.samsonic.ui.components.backButtonHazeSource
import com.example.samsonic.ui.theme.scrollTopFade
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.rememberHazeState

/** A "see all" page's list, under its owner's name (the artist or genre it belongs to). */
internal data class PageContent<T>(val owner: String, val items: List<T>)

/**
 * Every album of an artist or genre, opened from a section title on its page.
 * Laid out like the Library's Albums tab, in the grid or list of [section]: with
 * [ownView], one the page sets for itself from its view button; otherwise the
 * Library Albums tab's own (a genre's albums).
 */
@Composable
internal fun AlbumsPage(
    title: String,
    state: UiState<PageContent<Album>>,
    onBack: () -> Unit,
    onAlbumClick: (Album) -> Unit,
    contentPaddingBottom: Dp,
    modifier: Modifier = Modifier,
    section: LibrarySection = LibrarySection.ALBUMS,
    ownView: Boolean = false,
) {
    val container = LocalAppContainer.current
    val layouts by container.libraryLayoutManager.layouts.collectAsStateWithLifecycle()
    val gridState = rememberLazyGridState()
    BackButtonPage(
        onBack,
        modifier,
        overlay = if (ownView) { haze -> CollectionViewMenu(section, title, haze) } else null,
    ) { backHaze ->
        StateContent(state = state, modifier = Modifier.fillMaxSize()) { (owner, albums) ->
            LibraryCollection(
                state = UiState.Success(albums),
                gridState = gridState,
                layout = layouts.getValue(section),
                padding = LibraryPadding(top = BackButtonClearance, bottom = contentPaddingBottom),
                key = { it.id },
                card = { album, size -> AlbumCard(album, onClick = { onAlbumClick(album) }, artSize = size) },
                row = { album -> AlbumRow(album, onClick = { onAlbumClick(album) }) },
                header = {
                    PageHeader(owner = owner, title = title) {
                        PlayShuffleButtons(key = albums, loadSongs = { container.repository.getAlbumsSongs(albums) })
                    }
                },
                modifier = Modifier.scrollTopFade(gridState).backButtonHazeSource(backHaze),
            )
        }
    }
}

/** Every artist of a genre, laid out like the Library's Artists tab, in the view picked there. */
@Composable
internal fun ArtistsPage(
    title: String,
    state: UiState<PageContent<Artist>>,
    onBack: () -> Unit,
    onArtistClick: (Artist) -> Unit,
    contentPaddingBottom: Dp,
    modifier: Modifier = Modifier,
) {
    val layouts by LocalAppContainer.current.libraryLayoutManager.layouts.collectAsStateWithLifecycle()
    val gridState = rememberLazyGridState()
    BackButtonPage(onBack, modifier) { backHaze ->
        StateContent(state = state, modifier = Modifier.fillMaxSize()) { (owner, artists) ->
            LibraryCollection(
                state = UiState.Success(artists),
                gridState = gridState,
                layout = layouts.getValue(LibrarySection.ARTISTS),
                padding = LibraryPadding(top = BackButtonClearance, bottom = contentPaddingBottom),
                key = { it.id },
                card = { artist, size -> ArtistCard(artist, onClick = { onArtistClick(artist) }, artSize = size) },
                row = { artist -> ArtistRow(artist, onClick = { onArtistClick(artist) }) },
                header = { PageHeader(owner = owner, title = title) },
                modifier = Modifier.scrollTopFade(gridState).backButtonHazeSource(backHaze),
            )
        }
    }
}

/** Every song of an artist or genre, as rows like Search's; a tap plays it within them. */
@Composable
internal fun SongsPage(
    title: String,
    state: UiState<PageContent<Song>>,
    onBack: () -> Unit,
    contentPaddingBottom: Dp,
    modifier: Modifier = Modifier,
) {
    val player = LocalPlayerState.current
    val listState = rememberLazyListState()
    BackButtonPage(onBack, modifier) { backHaze ->
        StateContent(state = state, modifier = Modifier.fillMaxSize()) { (owner, songs) ->
            LazyColumn(
                modifier = Modifier.fillMaxSize().scrollTopFade(listState).backButtonHazeSource(backHaze),
                state = listState,
                contentPadding = PaddingValues(top = BackButtonClearance, bottom = contentPaddingBottom),
            ) {
                item {
                    PageHeader(owner = owner, title = title, modifier = Modifier.padding(horizontal = 20.dp)) {
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
    }
}

/**
 * A page with the glass back button floating over its [content], which blurs under
 * it, and the [overlay] (e.g. its view menu) over both, blurring the same content.
 */
@Composable
private fun BackButtonPage(
    onBack: () -> Unit,
    modifier: Modifier,
    overlay: (@Composable BoxScope.(haze: HazeState) -> Unit)? = null,
    content: @Composable (backHaze: HazeState) -> Unit,
) {
    val backHaze = rememberHazeState()
    Box(modifier = modifier.fillMaxSize().statusBarsPadding()) {
        content(backHaze)
        // Floats over the list: rows scroll up under it and fade out at the status bar.
        GlassBackButton(onClick = onBack, hazeState = backHaze, modifier = Modifier.padding(start = 16.dp, top = 8.dp))
        overlay?.invoke(this, backHaze)
    }
}

/**
 * The [owner]'s name over the page's large [title], then [actions] (play and
 * shuffle), if any. Starts at the content's 20dp edge; the caller supplies that inset.
 */
@Composable
private fun PageHeader(
    owner: String,
    title: String,
    modifier: Modifier = Modifier,
    actions: (@Composable () -> Unit)? = null,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = owner,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 16.dp),
        )
        Text(
            text = title,
            style = MaterialTheme.typography.displaySmall,
            modifier = Modifier.padding(bottom = 20.dp),
        )
        if (actions != null) {
            Box(Modifier.padding(horizontal = 4.dp)) { actions() }
        }
        Spacer(Modifier.height(12.dp))
    }
}
