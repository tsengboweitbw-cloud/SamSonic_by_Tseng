package com.example.samsonic.ui.home

import androidx.annotation.StringRes
import com.example.samsonic.R
import com.example.samsonic.model.Album
import com.example.samsonic.model.Song

/** How many albums or songs a shelf's full list page loads. */
const val SHELF_FULL_LIST_SIZE = 100

/** Whether a shelf holds albums or songs. */
enum class ShelfKind { Albums, Songs }

/**
 * A Home carousel of albums or songs ([kind]), backed by one list [type]: for albums
 * Subsonic's `getAlbumList2` type, for songs the same name through
 * [com.example.samsonic.data.MusicLibrary.getSongList]. Home shows a short
 * [previewSize] row; tapping its title opens the full list.
 *
 * A [sharesHomeList] shelf has no longer list: its page shows exactly Home's
 * row. Random picks need this, since every fetch would roll a different set.
 * A [history] shelf only makes sense once there's play history, so Home hides it while empty.
 */
enum class HomeShelf(
    val type: String,
    val kind: ShelfKind,
    @StringRes val title: Int,
    val previewSize: Int,
    val sharesHomeList: Boolean = false,
    val history: Boolean = false,
) {
    PickedForYou("random", ShelfKind.Songs, R.string.home_shelf_picked_for_you, SHELF_FULL_LIST_SIZE, sharesHomeList = true),
    RecentlyAddedAlbums("newest", ShelfKind.Albums, R.string.home_shelf_recently_added_albums, 20),
    RecentlyAddedSongs("newest", ShelfKind.Songs, R.string.home_shelf_recently_added_songs, 20),
    RecentlyPlayedAlbums("recent", ShelfKind.Albums, R.string.home_shelf_recently_played_albums, 12, history = true),
    RecentlyPlayedSongs("recent", ShelfKind.Songs, R.string.home_shelf_recently_played_songs, 20, history = true),
    MostPlayedAlbums("frequent", ShelfKind.Albums, R.string.home_shelf_most_played_albums, 12, history = true),
    MostPlayedSongs("frequent", ShelfKind.Songs, R.string.home_shelf_most_played_songs, 20, history = true);

    /** The shelf page's route argument. */
    val key: String get() = name

    companion object {
        fun fromKey(key: String): HomeShelf? = entries.firstOrNull { it.name == key }
    }
}

/** What a shelf holds: albums or songs, as its [HomeShelf.kind] says. */
sealed interface ShelfItems {
    val isEmpty: Boolean

    data class Albums(val albums: List<Album>) : ShelfItems {
        override val isEmpty get() = albums.isEmpty()
    }

    data class Songs(val songs: List<Song>) : ShelfItems {
        override val isEmpty get() = songs.isEmpty()
    }
}
