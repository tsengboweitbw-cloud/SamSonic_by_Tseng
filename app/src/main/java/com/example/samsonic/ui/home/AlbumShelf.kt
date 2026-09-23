package com.example.samsonic.ui.home

/** How many albums a shelf's full list page loads. */
const val SHELF_FULL_LIST_SIZE = 100

/**
 * A Home carousel of albums, backed by one Subsonic `getAlbumList2` [type].
 * Home shows a short [previewSize] row; tapping its title opens the full list.
 *
 * A [sharesHomeList] shelf has no longer list: its page shows exactly Home's
 * row. Random picks need this, since every fetch would roll a different set.
 */
enum class AlbumShelf(val type: String, val title: String, val previewSize: Int, val sharesHomeList: Boolean = false) {
    RecentlyAdded("newest", "Recently Added", 20),
    PickedForYou("random", "Picked For You", SHELF_FULL_LIST_SIZE, sharesHomeList = true),
    RecentlyPlayed("recent", "Recently Played", 12),
    MostPlayed("frequent", "Most Played", 12);

    companion object {
        fun fromType(type: String): AlbumShelf? = entries.firstOrNull { it.type == type }
    }
}
