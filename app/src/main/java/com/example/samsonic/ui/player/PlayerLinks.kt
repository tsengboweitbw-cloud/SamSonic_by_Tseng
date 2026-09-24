package com.example.samsonic.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.staticCompositionLocalOf
import com.example.samsonic.LocalAppContainer
import com.example.samsonic.model.Song

/**
 * Opens a song's album or artist page from inside the player. Each folds the
 * sheet back into the mini player first, so the page shows beneath it.
 */
@Immutable
class PlayerLinks(
    val openAlbum: (albumId: String) -> Unit,
    val openArtist: (artistId: String) -> Unit,
)

internal val LocalPlayerLinks = staticCompositionLocalOf { PlayerLinks(openAlbum = {}, openArtist = {}) }

/** An artist to link to: [id] is null when the server gave none, so it can't be opened. */
internal data class ArtistLink(val id: String?, val name: String)

/**
 * The album artist of [song]: straight from the song on OpenSubsonic servers,
 * otherwise from its album, fetched once. Null until known, or if it has no album.
 */
@Composable
internal fun rememberAlbumArtist(song: Song): ArtistLink? {
    val repository = LocalAppContainer.current.repository
    val fromSong = song.albumArtistName?.let { ArtistLink(song.albumArtistId, it) }
    val fromAlbum by produceState<ArtistLink?>(null, song.albumId, fromSong == null) {
        val albumId = song.albumId ?: return@produceState
        if (fromSong != null) return@produceState
        value = runCatching { repository.getAlbum(albumId).first }.getOrNull()
            ?.let { ArtistLink(it.artistId, it.artistName) }
    }
    return fromSong ?: fromAlbum
}
