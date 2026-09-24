package com.example.samsonic.ui.navigation

import android.net.Uri
import androidx.navigation.NavBackStackEntry

internal object Routes {
    const val LOGIN = "login"
    const val ADD_SERVER = "add-server"
    const val HOME = "home"
    const val LIBRARY = "library"
    const val SEARCH = "search"
    const val SETTINGS = "settings"
    const val ARTIST = "artist/{artistId}"
    const val ARTIST_ALBUMS = "artist/{artistId}/albums"
    const val ARTIST_SONGS = "artist/{artistId}/songs"
    const val ARTIST_APPEARS_ON = "artist/{artistId}/appears-on"
    const val ALBUM = "album/{albumId}"
    const val PLAYLIST = "playlist/{playlistId}"
    const val SHELF = "shelf/{shelfType}"
    const val GENRE = "genre/{genre}"
    const val GENRE_ARTISTS = "genre/{genre}/artists"
    const val GENRE_ALBUMS = "genre/{genre}/albums"
    const val GENRE_SONGS = "genre/{genre}/songs"

    fun artist(id: String) = "artist/$id"
    fun artistAlbums(id: String) = "artist/$id/albums"
    fun artistSongs(id: String) = "artist/$id/songs"
    fun artistAppearsOn(id: String) = "artist/$id/appears-on"
    fun album(id: String) = "album/$id"
    fun playlist(id: String) = "playlist/$id"
    fun shelf(type: String) = "shelf/$type"

    // Genre names are free text (spaces, slashes, "&"), so they travel encoded; the arg arrives decoded.
    fun genre(name: String) = "genre/${Uri.encode(name)}"
    fun genreArtists(name: String) = "genre/${Uri.encode(name)}/artists"
    fun genreAlbums(name: String) = "genre/${Uri.encode(name)}/albums"
    fun genreSongs(name: String) = "genre/${Uri.encode(name)}/songs"
}

/** This entry's route with its arguments filled in, e.g. "album/42", to compare against a built route. */
internal fun NavBackStackEntry.routeWithArgs(): String? {
    val pattern = destination.route ?: return null
    return Regex("""\{(\w+)\}""").replace(pattern) { arguments?.getString(it.groupValues[1]).orEmpty() }
}
