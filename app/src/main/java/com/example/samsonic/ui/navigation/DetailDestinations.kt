package com.example.samsonic.ui.navigation

import androidx.compose.ui.unit.Dp
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import com.example.samsonic.ui.library.AlbumDetailScreen
import com.example.samsonic.ui.library.ArtistAlbumsScreen
import com.example.samsonic.ui.library.ArtistDetailScreen
import com.example.samsonic.ui.library.ArtistSongsScreen
import com.example.samsonic.ui.library.GenreAlbumsScreen
import com.example.samsonic.ui.library.GenreArtistsScreen
import com.example.samsonic.ui.library.GenreDetailScreen
import com.example.samsonic.ui.library.GenreSongsScreen
import com.example.samsonic.ui.library.PlaylistDetailScreen

private fun NavBackStackEntry.arg(name: String): String? = arguments?.getString(name)

/**
 * The pages opened from the tabs: an artist's, album's, playlist's and genre's,
 * and the "see all" lists behind an artist's and genre's sections.
 * [contentPaddingBottom] keeps their content clear of the floating chrome.
 */
internal fun NavGraphBuilder.detailScreens(navController: NavController, contentPaddingBottom: Dp) {
    val back: () -> Unit = { navController.popBackStack() }
    fun open(route: String) = navController.navigate(route)

    screen(Routes.ARTIST) { entry ->
        val artistId = entry.arg("artistId") ?: return@screen
        ArtistDetailScreen(
            artistId = artistId,
            onBack = back,
            onAlbumClick = { open(Routes.album(it.id)) },
            onAllAlbumsClick = { open(Routes.artistAlbums(artistId)) },
            onAllSongsClick = { open(Routes.artistSongs(artistId)) },
            onAppearsOnClick = { open(Routes.artistAppearsOn(artistId)) },
            contentPaddingBottom = contentPaddingBottom,
        )
    }
    screen(Routes.ARTIST_ALBUMS) { entry ->
        val artistId = entry.arg("artistId") ?: return@screen
        ArtistAlbumsScreen(artistId, back, { open(Routes.album(it.id)) }, contentPaddingBottom = contentPaddingBottom)
    }
    screen(Routes.ARTIST_APPEARS_ON) { entry ->
        val artistId = entry.arg("artistId") ?: return@screen
        ArtistAlbumsScreen(
            artistId,
            back,
            { open(Routes.album(it.id)) },
            appearsOn = true,
            contentPaddingBottom = contentPaddingBottom,
        )
    }
    screen(Routes.ARTIST_SONGS) { entry ->
        val artistId = entry.arg("artistId") ?: return@screen
        ArtistSongsScreen(artistId, back, contentPaddingBottom = contentPaddingBottom)
    }
    screen(Routes.ALBUM) { entry ->
        val albumId = entry.arg("albumId") ?: return@screen
        AlbumDetailScreen(albumId = albumId, onBack = back, contentPaddingBottom = contentPaddingBottom)
    }
    screen(Routes.PLAYLIST) { entry ->
        val playlistId = entry.arg("playlistId") ?: return@screen
        PlaylistDetailScreen(playlistId = playlistId, onBack = back, contentPaddingBottom = contentPaddingBottom)
    }
    screen(Routes.GENRE) { entry ->
        val genre = entry.arg("genre") ?: return@screen
        GenreDetailScreen(
            genre = genre,
            onBack = back,
            onArtistClick = { open(Routes.artist(it.id)) },
            onAlbumClick = { open(Routes.album(it.id)) },
            onAllArtistsClick = { open(Routes.genreArtists(genre)) },
            onAllAlbumsClick = { open(Routes.genreAlbums(genre)) },
            onAllSongsClick = { open(Routes.genreSongs(genre)) },
            contentPaddingBottom = contentPaddingBottom,
        )
    }
    screen(Routes.GENRE_ARTISTS) { entry ->
        val genre = entry.arg("genre") ?: return@screen
        GenreArtistsScreen(genre, back, { open(Routes.artist(it.id)) }, contentPaddingBottom = contentPaddingBottom)
    }
    screen(Routes.GENRE_ALBUMS) { entry ->
        val genre = entry.arg("genre") ?: return@screen
        GenreAlbumsScreen(genre, back, { open(Routes.album(it.id)) }, contentPaddingBottom = contentPaddingBottom)
    }
    screen(Routes.GENRE_SONGS) { entry ->
        val genre = entry.arg("genre") ?: return@screen
        GenreSongsScreen(genre, back, contentPaddingBottom = contentPaddingBottom)
    }
}
