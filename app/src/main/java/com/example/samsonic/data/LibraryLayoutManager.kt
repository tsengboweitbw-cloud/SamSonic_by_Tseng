package com.example.samsonic.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class LibraryViewMode { LIST, GRID }

fun defaultColumnsForWidth(widthDp: Float): Int = when {
    widthDp <= 350f -> 2
    widthDp <= 500f -> 3
    widthDp <= 900f -> 4
    else -> 6
}

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

data class LibraryLayout(val columns: Int, val isDefault: Boolean = false) {
    val mode: LibraryViewMode get() = if (columns == 1) LibraryViewMode.LIST else LibraryViewMode.GRID

    companion object {
        const val MIN_COLUMNS = 1

        /** The smallest column count that is still a grid (1 column is the list). */
        const val MIN_GRID_COLUMNS = 2

        /** Pass as [columns] (or set [isDefault]) to ask for the automatic column count. */
        const val AUTO = 0
    }
}

/**
 * The kind of screen a grid is on, each keeping its own column count (the list or
 * grid choice is shared): a phone (or a foldable's cover screen), and a tablet either
 * way round (a foldable's inner screen included). A grid there has up to [maxColumns].
 *
 * [defaultColumns] is only a fallback for before the window width is known; once it is,
 * the automatic count comes from [defaultColumnsForWidth].
 */
enum class GridForm(val defaultColumns: Int?, val maxColumns: Int, internal val keySuffix: String) {
    PHONE(defaultColumns = null, maxColumns = 6, keySuffix = ""),
    PHONE_LANDSCAPE(defaultColumns = null, maxColumns = 6, keySuffix = "_phone_landscape"),
    TABLET_PORTRAIT(defaultColumns = 4, maxColumns = 6, keySuffix = "_tablet_portrait"),
    TABLET_LANDSCAPE(defaultColumns = 6, maxColumns = 6, keySuffix = "_tablet_landscape"),
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

private inline fun <reified E : Enum<E>> SharedPreferences.getEnum(key: String, default: E): E =
    getString(key, null)?.let { name -> enumValues<E>().find { it.name == name } } ?: default

/**
 * Persists how each Library tab is shown - list or grid, the grid's column
 * count, its sort, and which artists the Artists tab lists - and how each
 * [SortedList] is sorted, via plain SharedPreferences, like [ThemeManager].
 *
 * Storage rules for a section's view:
 * - The list/grid choice is stored once per section ([modeKey]) and shared by every [GridForm].
 * - The column count is stored per [GridForm] ([columnsKey]), and only when it is a custom
 *   grid count (2 or more). 0 means "automatic".
 * - A section counts as changed by the user once its mode key exists.
 *
 * @param initialForm the screen kind to start with, if known before the first window measure.
 * @param initialWidthDp the window width to start with, if known; avoids a first frame
 * laid out with the fallback column counts.
 */
class LibraryLayoutManager(
    context: Context,
    initialForm: GridForm = GridForm.PHONE,
    initialWidthDp: Float = 0f,
) {
    private val prefs = context.applicationContext
        .getSharedPreferences("samsonic_library", Context.MODE_PRIVATE)

    // INIT ORDER: [widthBucket] and [_gridForm] must come before [_layouts], whose
    // initializer calls load(), which reads both.

    // The automatic column count for the current window width; 0 until the width is known.
    private var widthBucket: Int = bucketFor(initialWidthDp)

    // The same for the Library as the side pane (see [inPane]).
    private var paneWidthBucket: Int = 0

    // The kind of screen the app is on now, whose column counts [layouts] has.
    private val _gridForm = MutableStateFlow(initialForm)
    val gridForm: StateFlow<GridForm> = _gridForm.asStateFlow()

    // Whether the Library is only the side pane: a two-pane window with a page open beside it.
    // Its own tabs keep their own view and column count then, apart from when they have the
    // whole screen; the pages opened beside it (an artist's albums) are not in the side pane.
    private var windowTwoPane = false
    private var detailOpen = false
    private var windowWidth = Float.NaN
    private val _twoPane = MutableStateFlow(false)
    val twoPane: StateFlow<Boolean> = _twoPane.asStateFlow()

    /** The most columns the slider offers now: fewer in two panes, where the Library is narrower. */
    val maxColumns: Int get() = if (_twoPane.value) PANE_MAX_COLUMNS else _gridForm.value.maxColumns

    private val _layouts = MutableStateFlow(LibrarySection.entries.associateWith(::load))
    val layouts: StateFlow<Map<LibrarySection, LibraryLayout>> = _layouts.asStateFlow()

    /**
     * Switches [layouts] to [form]'s column counts, as the window turns, folds or resizes.
     * Cheap to call on every resize: nothing is reloaded until the form or the automatic
     * column count (a few width steps) actually changes. [height] is unused and kept only
     * so existing callers still compile.
     */
    fun setGridForm(
        form: GridForm,
        width: Float = Float.NaN,
        @Suppress("UNUSED_PARAMETER") height: Float = Float.NaN,
        twoPane: Boolean = false,
    ) {
        windowTwoPane = twoPane
        if (!width.isNaN()) windowWidth = width
        update(form)
    }

    /** Tells whether a page is open beside the Library (only in a two-pane window), as it narrows to the side. */
    fun setDetailOpen(open: Boolean) {
        if (open == detailOpen) return
        detailOpen = open
        update(_gridForm.value)
    }

    private fun update(form: GridForm) {
        val pane = windowTwoPane && detailOpen
        val bucket = if (windowWidth.isNaN()) widthBucket else bucketFor(windowWidth)
        val paneBucket = if (windowWidth.isNaN()) paneWidthBucket else bucketFor(paneWidth(windowWidth))
        if (form == _gridForm.value && bucket == widthBucket && paneBucket == paneWidthBucket && pane == _twoPane.value) return
        _gridForm.value = form
        _twoPane.value = pane
        widthBucket = bucket
        paneWidthBucket = paneBucket
        _layouts.value = LibrarySection.entries.associateWith(::load)
    }

    // ---- Sorting of lists opened from the Library ----

    private val _listSorts = MutableStateFlow(SortedList.entries.associateWith(::loadListSort))
    val listSorts: StateFlow<Map<SortedList, ListSort>> = _listSorts.asStateFlow()

    fun setListSort(list: SortedList, sort: ListSort) {
        prefs.edit {
            putString(listSortKey(list), sort.key.name)
            putBoolean(listDescendingKey(list), sort.descending)
        }
        _listSorts.update { it + (list to sort) }
    }

    private fun loadListSort(list: SortedList): ListSort = ListSort(
        key = list.keys.find { it.name == prefs.getString(listSortKey(list), null) } ?: SortKey.DEFAULT,
        descending = prefs.getBoolean(listDescendingKey(list), false),
    )

    private fun listSortKey(list: SortedList) = "list_${list.name.lowercase()}_sort"
    private fun listDescendingKey(list: SortedList) = "list_${list.name.lowercase()}_descending"

    // ---- Sorting of the Library's own tabs ----

    // Only the Library's own tabs sort this way; the lists opened from it are SortedLists.
    private val _sorts = MutableStateFlow(LibrarySection.Tabs.associateWith(::loadSort))
    val sorts: StateFlow<Map<LibrarySection, LibrarySort>> = _sorts.asStateFlow()

    fun setSort(sort: LibrarySort) {
        prefs.edit { putString(sortKey(sort.section), sort.name) }
        _sorts.update { it + (sort.section to sort) }
    }

    private fun loadSort(section: LibrarySection): LibrarySort {
        val options = LibrarySort.of(section)
        return options.find { it.name == prefs.getString(sortKey(section), null) } ?: options.first()
    }

    // ---- Other Library options ----

    // Whether the Artists tab lists album artists (what albums are filed under) or every artist.
    private val _albumArtistsOnly = MutableStateFlow(prefs.getBoolean(KEY_ALBUM_ARTISTS_ONLY, true))
    val albumArtistsOnly: StateFlow<Boolean> = _albumArtistsOnly.asStateFlow()

    fun setAlbumArtistsOnly(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_ALBUM_ARTISTS_ONLY, enabled) }
        _albumArtistsOnly.value = enabled
    }

    // Whether album covers in the Library carry the album's name, and its album artist's.
    private val _showAlbumNames = MutableStateFlow(prefs.getBoolean(KEY_SHOW_ALBUM_NAMES, true))
    val showAlbumNames: StateFlow<Boolean> = _showAlbumNames.asStateFlow()

    fun setShowAlbumNames(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_SHOW_ALBUM_NAMES, enabled) }
        _showAlbumNames.value = enabled
    }

    private val _showAlbumArtists = MutableStateFlow(prefs.getBoolean(KEY_SHOW_ALBUM_ARTISTS, true))
    val showAlbumArtists: StateFlow<Boolean> = _showAlbumArtists.asStateFlow()

    fun setShowAlbumArtists(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_SHOW_ALBUM_ARTISTS, enabled) }
        _showAlbumArtists.value = enabled
    }

    // Where a page's Play / Shuffle / Queue buttons go once its header scrolls away.
    private val _listActionsPin = MutableStateFlow(prefs.getEnum(KEY_LIST_ACTIONS_PIN, ListActionsPin.OFF))
    val listActionsPin: StateFlow<ListActionsPin> = _listActionsPin.asStateFlow()

    fun setListActionsPin(pin: ListActionsPin) {
        prefs.edit { putString(KEY_LIST_ACTIONS_PIN, pin.name) }
        _listActionsPin.value = pin
    }

    // Whether the Playlists tab starts with Favourites, the liked songs.
    private val _showFavourites = MutableStateFlow(prefs.getBoolean(KEY_SHOW_FAVOURITES, true))
    val showFavourites: StateFlow<Boolean> = _showFavourites.asStateFlow()

    fun setShowFavourites(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_SHOW_FAVOURITES, enabled) }
        _showFavourites.value = enabled
    }

    // ---- Grid / list layout ----

    /** Sets [section]'s view, and its column count on this kind of screen ([gridForm]). */
    fun setLayout(section: LibrarySection, layout: LibraryLayout) {
        val form = _gridForm.value
        val useDefault = layout.isDefault || layout.columns == LibraryLayout.AUTO
        val columns = if (useDefault) {
            computeDefaultColumns(section, form)
        } else {
            layout.columns.coerceIn(LibraryLayout.MIN_COLUMNS, maxColumns)
        }
        val pane = inPane(section)
        // The list is a mode, not a column count: 1 column is never stored, so the mode
        // (shared by every screen kind) stays the single source of truth for list vs grid.
        val storedCols = if (useDefault || columns == LibraryLayout.MIN_COLUMNS) LibraryLayout.AUTO else columns
        prefs.edit {
            putString(
                modeKey(section, pane),
                if (columns == LibraryLayout.MIN_COLUMNS) LibraryViewMode.LIST.name else LibraryViewMode.GRID.name,
            )
            putInt(columnsKey(section, pane), storedCols)
        }
        // Sections still following this one (never changed themselves) follow along; not from
        // the side pane, which the pages following it are never shown in.
        val followers = if (pane) emptyList() else LibrarySection.entries.filter { it.inheritsFrom == section && !isCustomised(it, false) }
        _layouts.update { current ->
            current + (listOf(section) + followers).associateWith { target ->
                LibraryLayout(
                    columns = if (useDefault) computeDefaultColumns(target, form) else columns,
                    isDefault = useDefault,
                )
            }
        }
    }

    private fun load(section: LibrarySection): LibraryLayout = load(section, inPane(section))

    private fun load(section: LibrarySection, pane: Boolean): LibraryLayout {
        if (!isCustomised(section, pane)) {
            section.inheritsFrom?.let { return load(it, pane) }
        }
        val form = _gridForm.value
        val savedMode = prefs.getString(modeKey(section, pane), null)
        val savedCols = prefs.getInt(columnsKey(section, pane), LibraryLayout.AUTO)
        return when {
            savedMode == LibraryViewMode.LIST.name -> LibraryLayout(columns = 1)
            savedCols >= LibraryLayout.MIN_GRID_COLUMNS ->
                LibraryLayout(columns = savedCols.coerceIn(LibraryLayout.MIN_GRID_COLUMNS, maxColumns))
            else -> LibraryLayout(columns = computeDefaultColumns(section, form), isDefault = true)
        }
    }

    // The automatic count follows the window width once it is known; the per-form and
    // per-section defaults are only the fallback until then.
    private fun computeDefaultColumns(section: LibrarySection, form: GridForm): Int {
        if (section == LibrarySection.PLAYLISTS) return 1   // Playlists 預設一律 list
        val bucket = if (inPane(section)) paneWidthBucket else widthBucket
        return if (bucket > 0) bucket else form.defaultColumns ?: section.defaultColumns
    }

    // The side pane's width in a window of [windowDp] (see TabHost's ListPaneShare and its limits).
    private fun paneWidth(windowDp: Float) = (windowDp * 0.42f).coerceIn(340f, 480f)

    private fun bucketFor(widthDp: Float): Int = if (widthDp > 0f) defaultColumnsForWidth(widthDp) else 0

    // The mode key is written on every setLayout and shared by all screen kinds, so its
    // presence alone says whether the user ever changed this section.
    private fun isCustomised(section: LibrarySection, pane: Boolean) = prefs.contains(modeKey(section, pane))

    // Only the Library's own tabs are ever in the side pane.
    private fun inPane(section: LibrarySection) = _twoPane.value && section in LibrarySection.Tabs

    private fun paneSuffix(pane: Boolean) = if (pane) "_pane" else ""

    private fun modeKey(section: LibrarySection, pane: Boolean) = "${section.name.lowercase()}_view_mode${paneSuffix(pane)}"

    // A phone's under the name it always had, so its count carries on; each other kind of screen its own.
    private fun columnsKey(section: LibrarySection, pane: Boolean) =
        "${section.name.lowercase()}_columns${_gridForm.value.keySuffix}${paneSuffix(pane)}"
    private fun sortKey(section: LibrarySection) = "${section.name.lowercase()}_sort"

    private companion object {
        const val PANE_MAX_COLUMNS = 6
        const val KEY_ALBUM_ARTISTS_ONLY = "artists_album_artists_only"
        const val KEY_SHOW_ALBUM_NAMES = "albums_show_names"
        const val KEY_SHOW_ALBUM_ARTISTS = "albums_show_artists"
        const val KEY_SHOW_FAVOURITES = "playlists_show_favourites"
        const val KEY_LIST_ACTIONS_PIN = "list_actions_pin"
    }
}