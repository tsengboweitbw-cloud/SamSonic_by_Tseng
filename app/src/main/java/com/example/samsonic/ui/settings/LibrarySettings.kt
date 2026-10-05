package com.example.samsonic.ui.settings

//import androidx.compose.foundation.layout.Arrangement
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Album
//import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.samsonic.LocalAppContainer
import com.example.samsonic.R

/** The Library group: which lists and names the Library shows, and the list buttons. */
@Composable
internal fun LibrarySettings(panels: SettingsPanels) {
    val container = LocalAppContainer.current
    val albumArtistsOnly by container.libraryLayoutManager.albumArtistsOnly.collectAsStateWithLifecycle()
    val showFavourites by container.libraryLayoutManager.showFavourites.collectAsStateWithLifecycle()
    val showAlbumNames by container.libraryLayoutManager.showAlbumNames.collectAsStateWithLifecycle()
    val showAlbumArtists by container.libraryLayoutManager.showAlbumArtists.collectAsStateWithLifecycle()
    val listActionsMenu = panels.listActionsMenu
    SettingsCard {
        // The Artists tab reloads with the other list the next time it shows.
        SwitchRow(
            icon = Icons.Filled.Person,
            title = stringResource(R.string.settings_album_artists_only),
            checked = albumArtistsOnly,
            onCheckedChange = container.libraryLayoutManager::setAlbumArtistsOnly,
            hint = stringResource(R.string.settings_album_artists_only_hint),
        )
        SwitchRow(
            icon = Icons.Filled.Album,
            title = stringResource(R.string.settings_show_album_names),
            checked = showAlbumNames,
            onCheckedChange = container.libraryLayoutManager::setShowAlbumNames,
            hint = stringResource(R.string.settings_show_album_names_hint),
        )
        SwitchRow(
            icon = Icons.Filled.Person,
            title = stringResource(R.string.settings_show_album_artists),
            checked = showAlbumArtists,
            onCheckedChange = container.libraryLayoutManager::setShowAlbumArtists,
            hint = stringResource(R.string.settings_show_album_artists_hint),
        )
        // Your liked songs, first in the Playlists tab.
        SwitchRow(
            icon = Icons.AutoMirrored.Filled.QueueMusic,
            title = stringResource(R.string.settings_favourites_playlist),
            checked = showFavourites,
            onCheckedChange = container.libraryLayoutManager::setShowFavourites,
            hint = stringResource(R.string.settings_favourites_playlist_hint),
        )
        ListActionsRow(listActionsMenu)
    }
}
