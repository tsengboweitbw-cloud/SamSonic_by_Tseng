package com.example.samsonic.ui.library

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.samsonic.LocalAppContainer
import com.example.samsonic.R
import com.example.samsonic.data.LibrarySection
import com.example.samsonic.model.Album
import com.example.samsonic.ui.common.rememberScreenLoad

/**
 * All of an artist's albums, opened from the Albums section of their page, or with
 * [appearsOn] the other artists' albums they sing on, from Appears on.
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
    val repository = LocalAppContainer.current.repository
    val state = rememberScreenLoad(artistId, appearsOn, errorMessage = stringResource(R.string.library_albums_load_error)) {
        val (artist, albums) = repository.getArtist(artistId)
        val shown = if (appearsOn) repository.getAppearsOn(artist, albums, repository.getSongsBy(artist)) else albums
        PageContent(artist.name, shown)
    }
    AlbumsPage(
        title = stringResource(if (appearsOn) R.string.library_appears_on else R.string.library_albums),
        state = state,
        onBack = onBack,
        onAlbumClick = onAlbumClick,
        contentPaddingBottom = contentPaddingBottom,
        modifier = modifier,
        // Its own view, apart from the Library Albums tab's and from each other's.
        section = if (appearsOn) LibrarySection.APPEARS_ON else LibrarySection.ARTIST_ALBUMS,
        ownView = true,
    )
}

/** All of an artist's songs, opened from the Songs section of their page. */
@Composable
fun ArtistSongsScreen(
    artistId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    contentPaddingBottom: Dp = 0.dp,
) {
    val repository = LocalAppContainer.current.repository
    val state = rememberScreenLoad(artistId, errorMessage = stringResource(R.string.library_songs_load_error)) {
        val (artist, albums) = repository.getArtist(artistId)
        PageContent(artist.name, repository.getArtistSongs(artist, albums))
    }
    SongsPage(title = stringResource(R.string.library_songs), state = state, onBack = onBack, contentPaddingBottom = contentPaddingBottom, modifier = modifier)
}
