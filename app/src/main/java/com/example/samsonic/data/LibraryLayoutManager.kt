package com.example.samsonic.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class LibraryViewMode { LIST, GRID }

/**
 * Album and artist collections whose view the user can change: the Library tabs
 * (Genres has no art, so it is always a list), then an artist page's "see all"
 * pages. Until changed, a section looks like [inheritsFrom], so a new one shows
 * what the user already picked there.
 */
enum class LibrarySection(val defaultColumns: Int, val inheritsFrom: LibrarySection? = null) {
    ARTISTS(3),
    ALBUMS(2),
    PLAYLISTS(2),
    ARTIST_ALBUMS(2, inheritsFrom = ALBUMS),
    APPEARS_ON(2, inheritsFrom = ALBUMS),
    GENRE_ALBUMS(2, inheritsFrom = ALBUMS),
    ;

    companion object {
        /** The Library's tabs that have a view, in pager order. */
        val Tabs = listOf(ARTISTS, ALBUMS, PLAYLISTS)
    }
}

data class LibraryLayout(val mode: LibraryViewMode, val columns: Int) {
    companion object {
        const val MIN_COLUMNS = 2
        const val MAX_COLUMNS = 4
    }
}

/**
 * The orders a Library tab ([section]) can list its items in; the first of each
 * tab's is its default, the order it always had. Names go A to Z, counts, years,
 * lengths and dates added go biggest or newest first.
 */
enum class LibrarySort(val section: LibrarySection) {
    ARTIST_NAME(LibrarySection.ARTISTS),
    ARTIST_ALBUM_COUNT(LibrarySection.ARTISTS),
    ALBUM_ARTIST(LibrarySection.ALBUMS),
    ALBUM_TITLE(LibrarySection.ALBUMS),
    ALBUM_YEAR(LibrarySection.ALBUMS),
    ALBUM_ADDED(LibrarySection.ALBUMS),
    PLAYLIST_NAME(LibrarySection.PLAYLISTS),
    PLAYLIST_CHANGED(LibrarySection.PLAYLISTS),
    PLAYLIST_SONG_COUNT(LibrarySection.PLAYLISTS),
    PLAYLIST_DURATION(LibrarySection.PLAYLISTS),
    ;

    companion object {
        /** [section]'s sorts, in the order the view options show them. */
        fun of(section: LibrarySection): List<LibrarySort> = entries.filter { it.section == section }
    }
}

/**
 * Where a page's Play / Shuffle / Queue buttons go once its header scrolls away:
 * with it ([OFF]), merged into a capsule pinned under the back button ([TOP]), or as
 * that capsule docked just above the mini player ([BOTTOM]), or standing at the
 * right edge above it ([SIDE]), or tucked into a button in the top right corner
 * that opens into it ([CORNER]).
 */
enum class ListActionsPin { OFF, TOP, BOTTOM, SIDE, CORNER }

/** What an opened list can be sorted by; [DEFAULT] is the order it comes in (track, playlist order, ...). */
enum class SortKey { DEFAULT, TITLE, ARTIST, ALBUM, YEAR, DURATION }

/**
 * The lists opened from the Library that the user can re-sort, each with the [keys]
 * that make sense for it, in the order its sort menu shows them. Each kind keeps one
 * sort for all its lists: every album's tracks sort alike.
 */
enum class SortedList(val keys: List<SortKey>) {
    ALBUM_TRACKS(listOf(SortKey.DEFAULT, SortKey.TITLE, SortKey.DURATION)),
    PLAYLIST_SONGS(listOf(SortKey.DEFAULT, SortKey.TITLE, SortKey.ARTIST, SortKey.ALBUM, SortKey.DURATION)),
    // An artist's or a genre's every song.
    SONGS(listOf(SortKey.DEFAULT, SortKey.TITLE, SortKey.ALBUM, SortKey.YEAR, SortKey.DURATION)),
    // An artist's or a genre's every album.
    ALBUMS(listOf(SortKey.DEFAULT, SortKey.TITLE, SortKey.ARTIST, SortKey.YEAR)),
}

/** A [SortedList]'s sort: by [key], reversed when [descending] (Z to A, newest or longest first). */
data class ListSort(val key: SortKey = SortKey.DEFAULT, val descending: Boolean = false)

/**
 * Persists how each Library tab is shown - list or grid, the grid's column
 * count, its sort, and which artists the Artists tab lists - and how each
 * [SortedList] is sorted, via plain SharedPreferences, like [ThemeManager].
 */
class LibraryLayoutManager(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("samsonic_library", Context.MODE_PRIVATE)

    private val _listSorts = MutableStateFlow(SortedList.entries.associateWith(::loadListSort))
    val listSorts: StateFlow<Map<SortedList, ListSort>> = _listSorts.asStateFlow()

    fun setListSort(list: SortedList, sort: ListSort) {
        prefs.edit()
            .putString(listSortKey(list), sort.key.name)
            .putBoolean(listDescendingKey(list), sort.descending)
            .apply()
        _listSorts.value = _listSorts.value + (list to sort)
    }

    private fun loadListSort(list: SortedList): ListSort = ListSort(
        key = list.keys.find { it.name == prefs.getString(listSortKey(list), null) } ?: SortKey.DEFAULT,
        descending = prefs.getBoolean(listDescendingKey(list), false),
    )

    private fun listSortKey(list: SortedList) = "list_${list.name.lowercase()}_sort"
    private fun listDescendingKey(list: SortedList) = "list_${list.name.lowercase()}_descending"

    // Only the Library's own tabs sort this way; the lists opened from it are SortedLists.
    private val _sorts = MutableStateFlow(LibrarySection.Tabs.associateWith(::loadSort))
    val sorts: StateFlow<Map<LibrarySection, LibrarySort>> = _sorts.asStateFlow()

    fun setSort(sort: LibrarySort) {
        prefs.edit().putString(sortKey(sort.section), sort.name).apply()
        _sorts.value = _sorts.value + (sort.section to sort)
    }

    private fun loadSort(section: LibrarySection): LibrarySort {
        val options = LibrarySort.of(section)
        return options.find { it.name == prefs.getString(sortKey(section), null) } ?: options.first()
    }

    private val _layouts = MutableStateFlow(LibrarySection.entries.associateWith(::load))
    val layouts: StateFlow<Map<LibrarySection, LibraryLayout>> = _layouts.asStateFlow()

    // Whether the Artists tab lists album artists (what albums are filed under) or every artist.
    private val _albumArtistsOnly = MutableStateFlow(prefs.getBoolean(KEY_ALBUM_ARTISTS_ONLY, true))
    val albumArtistsOnly: StateFlow<Boolean> = _albumArtistsOnly.asStateFlow()

    fun setAlbumArtistsOnly(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ALBUM_ARTISTS_ONLY, enabled).apply()
        _albumArtistsOnly.value = enabled
    }

    // Where a page's Play / Shuffle / Queue buttons go once its header scrolls away.
    private val _listActionsPin = MutableStateFlow(
        ListActionsPin.entries.find { it.name == prefs.getString(KEY_LIST_ACTIONS_PIN, null) } ?: ListActionsPin.TOP,
    )
    val listActionsPin: StateFlow<ListActionsPin> = _listActionsPin.asStateFlow()

    fun setListActionsPin(pin: ListActionsPin) {
        prefs.edit().putString(KEY_LIST_ACTIONS_PIN, pin.name).apply()
        _listActionsPin.value = pin
    }

    // Whether the Playlists tab starts with Favourites, the liked songs.
    private val _showFavourites = MutableStateFlow(prefs.getBoolean(KEY_SHOW_FAVOURITES, true))
    val showFavourites: StateFlow<Boolean> = _showFavourites.asStateFlow()

    fun setShowFavourites(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SHOW_FAVOURITES, enabled).apply()
        _showFavourites.value = enabled
    }

    fun setLayout(section: LibrarySection, layout: LibraryLayout) {
        val columns = layout.columns.coerceIn(LibraryLayout.MIN_COLUMNS, LibraryLayout.MAX_COLUMNS)
        prefs.edit()
            .putString(modeKey(section), layout.mode.name)
            .putInt(columnsKey(section), columns)
            .apply()
        // Sections still following this one (never changed themselves) follow along.
        val followers = LibrarySection.entries.filter { it.inheritsFrom == section && !prefs.contains(modeKey(it)) }
        _layouts.value = _layouts.value + (listOf(section) + followers).associateWith { layout.copy(columns = columns) }
    }

    private fun load(section: LibrarySection): LibraryLayout {
        val inherited = section.inheritsFrom?.takeUnless { prefs.contains(modeKey(section)) }
        if (inherited != null) return load(inherited)
        return LibraryLayout(
            mode = LibraryViewMode.entries.find { it.name == prefs.getString(modeKey(section), null) }
                ?: LibraryViewMode.GRID,
            columns = prefs.getInt(columnsKey(section), section.defaultColumns)
                .coerceIn(LibraryLayout.MIN_COLUMNS, LibraryLayout.MAX_COLUMNS),
        )
    }

    private fun modeKey(section: LibrarySection) = "${section.name.lowercase()}_view_mode"
    private fun columnsKey(section: LibrarySection) = "${section.name.lowercase()}_columns"
    private fun sortKey(section: LibrarySection) = "${section.name.lowercase()}_sort"

    private companion object {
        const val KEY_ALBUM_ARTISTS_ONLY = "artists_album_artists_only"
        const val KEY_SHOW_FAVOURITES = "playlists_show_favourites"
        const val KEY_LIST_ACTIONS_PIN = "list_actions_pin"
    }
}
