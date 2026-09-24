package com.example.samsonic.ui.navigation

import androidx.navigation.NavBackStackEntry

internal object Routes {
    const val LOGIN = "login"
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

    fun artist(id: String) = "artist/$id"
    fun artistAlbums(id: String) = "artist/$id/albums"
    fun artistSongs(id: String) = "artist/$id/songs"
    fun artistAppearsOn(id: String) = "artist/$id/appears-on"
    fun album(id: String) = "album/$id"
    fun playlist(id: String) = "playlist/$id"
    fun shelf(type: String) = "shelf/$type"
}

/** This entry's route with its arguments filled in, e.g. "album/42", to compare against a built route. */
internal fun NavBackStackEntry.routeWithArgs(): String? {
    val pattern = destination.route ?: return null
    return Regex("""\{(\w+)\}""").replace(pattern) { arguments?.getString(it.groupValues[1]).orEmpty() }
}
