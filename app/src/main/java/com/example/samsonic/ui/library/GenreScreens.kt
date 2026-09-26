package com.example.samsonic.ui.library

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.samsonic.LocalAppContainer
import com.example.samsonic.R
import com.example.samsonic.data.LibrarySection
import com.example.samsonic.model.Album
import com.example.samsonic.model.Artist
import com.example.samsonic.model.artSeed
import com.example.samsonic.playback.LocalPlayerState
import com.example.samsonic.ui.common.StateContent
import com.example.samsonic.ui.common.rememberScreenLoad
import com.example.samsonic.ui.components.BackButtonClearance
import com.example.samsonic.ui.components.GlassBackButton
import com.example.samsonic.ui.components.MediaArt
import com.example.samsonic.ui.components.PlayShuffleButtons
import com.example.samsonic.ui.components.backButtonHazeSource
import com.example.samsonic.ui.theme.scrollTopFade
import dev.chrisbanes.haze.rememberHazeState

/** How many of the genre's songs its page lists; the section's title opens them all. */
private const val SONGS_PREVIEW = 10

// The page picks its preview from one page of the genre's songs, not all of them.
private const val PREVIEW_SONGS_FETCHED = 500

/**
 * A genre's page, laid out like an artist's: a header with play and shuffle,
 * then its artists, albums (in two rows) and songs, each title opening them all.
 */
@Composable
fun GenreDetailScreen(
    genre: String,
    onBack: () -> Unit,
    onArtistClick: (Artist) -> Unit,
    onAlbumClick: (Album) -> Unit,
    onAllArtistsClick: () -> Unit,
    onAllAlbumsClick: () -> Unit,
    onAllSongsClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentPaddingBottom: Dp = 0.dp,
) {
    val container = LocalAppContainer.current
    val repository = container.repository
    val player = LocalPlayerState.current
    val cornerRadius by container.themeManager.albumArtCornerRadius.collectAsStateWithLifecycle()
    val state = rememberScreenLoad(genre, errorMessage = stringResource(R.string.library_genre_load_error)) {
        repository.getGenre(genre, songCount = PREVIEW_SONGS_FETCHED)
    }

    val listState = rememberLazyListState()
    val backHaze = rememberHazeState()
    Box(modifier = modifier.fillMaxSize().statusBarsPadding()) {
        StateContent(state = state, modifier = Modifier.fillMaxSize()) { (albums, artists, songs) ->
            LazyColumn(
                modifier = Modifier.fillMaxSize().scrollTopFade(listState).backButtonHazeSource(backHaze),
                state = listState,
                contentPadding = PaddingValues(top = BackButtonClearance, bottom = contentPaddingBottom),
            ) {
                item(key = "header") {
                    DetailHeader(
                        title = genre,
                        subtitle = stringResource(
                            R.string.library_genre_summary,
                            pluralStringResource(R.plurals.library_artist_count, artists.size, artists.size),
                            pluralStringResource(R.plurals.library_album_count, albums.size, albums.size),
                        ),
                        // No picture of its own: the newest album's cover stands in.
                        art = {
                            MediaArt(
                                coverArt = albums.firstOrNull()?.coverArt,
                                colorSeed = genre.artSeed(),
                                size = 140.dp,
                                cornerRadius = cornerRadius,
                            )
                        },
                    ) {
                        // Plays the whole genre, not just the songs listed here.
                        PlayShuffleButtons(key = genre, loadSongs = { repository.getGenreSongs(genre) })
                    }
                }
                artistShelf("artists", R.string.library_artists, artists, onAllArtistsClick, onArtistClick)
                albumShelf("albums", R.string.library_albums, albums, onAllAlbumsClick, onAlbumClick, rows = 2)
                songSection("songs", R.string.library_songs, songs.take(SONGS_PREVIEW), player, onTitleClick = onAllSongsClick)
                item(key = "end") { Spacer(Modifier.height(24.dp)) }
            }
        }
        // Floats over the list: rows scroll up under it and fade out at the status bar.
        GlassBackButton(onClick = onBack, hazeState = backHaze, modifier = Modifier.padding(start = 16.dp, top = 8.dp))
    }
}

/** All of a genre's album artists, opened from the Artists section of its page. */
@Composable
fun GenreArtistsScreen(
    genre: String,
    onBack: () -> Unit,
    onArtistClick: (Artist) -> Unit,
    modifier: Modifier = Modifier,
    contentPaddingBottom: Dp = 0.dp,
) {
    val repository = LocalAppContainer.current.repository
    val state = rememberScreenLoad(genre, errorMessage = stringResource(R.string.library_artists_load_error)) {
        PageContent(genre, repository.getGenre(genre, songCount = 0).artists)
    }
    ArtistsPage(stringResource(R.string.library_artists), state, onBack, onArtistClick, contentPaddingBottom, modifier)
}

/** All of a genre's albums, opened from the Albums section of its page. */
@Composable
fun GenreAlbumsScreen(
    genre: String,
    onBack: () -> Unit,
    onAlbumClick: (Album) -> Unit,
    modifier: Modifier = Modifier,
    contentPaddingBottom: Dp = 0.dp,
) {
    val repository = LocalAppContainer.current.repository
    val state = rememberScreenLoad(genre, errorMessage = stringResource(R.string.library_albums_load_error)) {
        PageContent(genre, repository.getGenre(genre, songCount = 0).albums)
    }
    AlbumsPage(
        title = stringResource(R.string.library_albums),
        state = state,
        onBack = onBack,
        onAlbumClick = onAlbumClick,
        contentPaddingBottom = contentPaddingBottom,
        modifier = modifier,
        // Its own view, list or grid from its view button, apart from the Library Albums tab's.
        section = LibrarySection.GENRE_ALBUMS,
        ownView = true,
    )
}

/** All of a genre's songs, opened from the Songs section of its page. */
@Composable
fun GenreSongsScreen(
    genre: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    contentPaddingBottom: Dp = 0.dp,
) {
    val repository = LocalAppContainer.current.repository
    val state = rememberScreenLoad(genre, errorMessage = stringResource(R.string.library_songs_load_error)) {
        PageContent(genre, repository.getGenreSongs(genre))
    }
    SongsPage(stringResource(R.string.library_songs), state, onBack, contentPaddingBottom, modifier)
}
