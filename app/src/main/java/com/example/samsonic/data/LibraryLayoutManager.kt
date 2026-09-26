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
 * Persists how each Library tab is shown - list or grid, the grid's column
 * count, and which artists the Artists tab lists - via plain SharedPreferences, like [ThemeManager].
 */
class LibraryLayoutManager(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("samsonic_library", Context.MODE_PRIVATE)

    private val _layouts = MutableStateFlow(LibrarySection.entries.associateWith(::load))
    val layouts: StateFlow<Map<LibrarySection, LibraryLayout>> = _layouts.asStateFlow()

    // Whether the Artists tab lists album artists (what albums are filed under) or every artist.
    private val _albumArtistsOnly = MutableStateFlow(prefs.getBoolean(KEY_ALBUM_ARTISTS_ONLY, true))
    val albumArtistsOnly: StateFlow<Boolean> = _albumArtistsOnly.asStateFlow()

    fun setAlbumArtistsOnly(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ALBUM_ARTISTS_ONLY, enabled).apply()
        _albumArtistsOnly.value = enabled
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

    private companion object {
        const val KEY_ALBUM_ARTISTS_ONLY = "artists_album_artists_only"
        const val KEY_SHOW_FAVOURITES = "playlists_show_favourites"
    }
}
