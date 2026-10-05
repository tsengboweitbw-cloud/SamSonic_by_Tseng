package com.example.samsonic.ui.library

import androidx.compose.material.icons.filled.Add
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import com.example.samsonic.R
import com.example.samsonic.data.MusicLibrary
import com.example.samsonic.model.Album
import com.example.samsonic.model.ArtistCredit
import com.example.samsonic.model.Song
import com.example.samsonic.ui.player.PanelState
import com.example.samsonic.ui.theme.OneUiRadius
import kotlinx.coroutines.CoroutineScope

/** What the Add to playlist menu adds, to a playlist or the queue: one song, or every song of an album. */
class PlaylistItems internal constructor(
    val title: String,
    // Where the menu's Go to rows lead: a song's album and artists, or an album's artist.
    // The album artist is looked up at the tap where the song doesn't carry it.
    internal val albumId: String? = null,
    internal val artists: List<ArtistCredit> = emptyList(),
    internal val albumArtist: ArtistCredit? = null,
    internal val songs: suspend (MusicLibrary) -> List<Song>,
) {
    internal suspend fun songIds(library: MusicLibrary): List<String> = songs(library).map { it.id }
}

internal fun Song.toPlaylistItems() = PlaylistItems(
    title,
    albumId = albumId,
    artists = artists.ifEmpty { listOf(ArtistCredit(artistId, artistName)) }.filter { it.id != null },
    albumArtist = albumArtistName?.let { ArtistCredit(albumArtistId, it) },
) { listOf(this) }

// Long-pressed from its own page's link, so only its artist is offered to go to.
internal fun Album.toPlaylistItems() = PlaylistItems(
    title,
    artists = listOf(ArtistCredit(artistId, artistName)).filter { it.id != null },
) { library -> library.getAlbum(id).second }

/** Taking a song out of the playlist it's in: [remove] does it, and the page then loads again. */
class SongRemoval(val remove: suspend () -> Unit)

/**
 * Set by a playlist's page: for a song of its list, how to take it out of the playlist,
 * so that long-pressing it offers that; null elsewhere.
 */
val LocalSongRemoval = staticCompositionLocalOf<((Song) -> SongRemoval?)?> { null }

/** What the Add to playlist menu is open for, and the menu's panel. */
class AddToPlaylistState internal constructor(scope: CoroutineScope) {
    internal val panel = PanelState(scope)

    internal var items by mutableStateOf<PlaylistItems?>(null)
        private set

    /**
     * The corner radius of what the menu grows out of, for it to fold back into; null for
     * a round glass button, which the menu folds into as Now Playing's panels do theirs.
     */
    internal var originRadius by mutableStateOf<Dp?>(OneUiRadius.Art)
        private set

    /** How the menu also offers to take the song out of the playlist it was long-pressed in; null elsewhere. */
    internal var removal by mutableStateOf<SongRemoval?>(null)
        private set

    /** Whether the menu also offers to add to the queue: not for the song already playing. */
    internal var offersQueue by mutableStateOf(true)
        private set

    /**
     * Opens the menu for [items], growing out of [from] (bounds in the root): a row or
     * cover with [originRadius] corners, or with null a round glass button. With
     * [offersQueue], it offers Add to queue above the playlists.
     */
    fun open(items: PlaylistItems, from: Rect, originRadius: Dp?, offersQueue: Boolean = true, removal: SongRemoval? = null) {
        this.items = items
        this.removal = removal
        this.originRadius = originRadius
        this.offersQueue = offersQueue
        panel.origin = from
        panel.open()
    }
}

/** Null where nothing can be added to playlists ([MusicLibrary.canEditPlaylists] is false). */
val LocalAddToPlaylist = staticCompositionLocalOf<AddToPlaylistState?> { null }

@Composable
fun rememberAddToPlaylistState(): AddToPlaylistState {
    val scope = rememberCoroutineScope()
    return remember { AddToPlaylistState(scope) }
}

/**
 * A row's or card's way into the Add to playlist menu: [origin] goes on what the
 * menu should grow out of, and [onLongClick] opens it. Both do nothing where
 * nothing can be added to playlists.
 */
class AddToPlaylistLongPress(val origin: Modifier, val onLongClick: (() -> Unit)?, val label: String?)

/** For a song row, which the menu grows out of. */
@Composable
fun rememberAddToPlaylistLongPress(song: Song): AddToPlaylistLongPress =
    rememberAddToPlaylistLongPress(OneUiRadius.Art, removal = LocalSongRemoval.current?.let { forSong -> { forSong(song) } }) { song.toPlaylistItems() }

/** For an album, whose [origin][AddToPlaylistLongPress.origin] has [originRadius] corners (its cover, or its row). */
@Composable
fun rememberAddToPlaylistLongPress(album: Album, originRadius: Dp): AddToPlaylistLongPress =
    rememberAddToPlaylistLongPress(originRadius) { album.toPlaylistItems() }

@Composable
internal fun rememberAddToPlaylistLongPress(
    originRadius: Dp,
    removal: (() -> SongRemoval?)? = null,
    items: () -> PlaylistItems,
): AddToPlaylistLongPress {
    val state = LocalAddToPlaylist.current
    val haptics = LocalHapticFeedback.current
    val currentItems by rememberUpdatedState(items)
    val currentRadius by rememberUpdatedState(originRadius)
    val currentRemoval by rememberUpdatedState(removal)
    // A plain holder, so scrolling doesn't recompose. Only the row's coordinates are kept
    // as it's placed; its bounds are worked out on the long press, not on every scroll frame.
    val coordinates = remember { arrayOfNulls<LayoutCoordinates>(1) }
    val label = stringResource(R.string.library_more_options)
    return remember(state, haptics, label) {
        if (state == null) return@remember AddToPlaylistLongPress(Modifier, null, null)
        AddToPlaylistLongPress(
            origin = Modifier.onPlaced { coordinates[0] = it },
            onLongClick = {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                val bounds = coordinates[0]?.takeIf { it.isAttached }?.boundsInRoot() ?: Rect.Zero
                state.open(currentItems(), bounds, currentRadius, removal = currentRemoval?.invoke())
            },
            label = label,
        )
    }
}
