package com.example.samsonic.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class LibraryViewMode { LIST, GRID }

/** Library tabs whose view the user can change (Genres has no art, so it is always a list). */
enum class LibrarySection(val defaultColumns: Int) { ARTISTS(3), ALBUMS(2), PLAYLISTS(2) }

data class LibraryLayout(val mode: LibraryViewMode, val columns: Int) {
    companion object {
        const val MIN_COLUMNS = 2
        const val MAX_COLUMNS = 4
    }
}

/**
 * Persists how each Library tab is shown - list or grid, and the grid's column
 * count - via plain SharedPreferences, like [ThemeManager].
 */
class LibraryLayoutManager(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("samsonic_library", Context.MODE_PRIVATE)

    private val _layouts = MutableStateFlow(LibrarySection.entries.associateWith(::load))
    val layouts: StateFlow<Map<LibrarySection, LibraryLayout>> = _layouts.asStateFlow()

    fun setLayout(section: LibrarySection, layout: LibraryLayout) {
        val columns = layout.columns.coerceIn(LibraryLayout.MIN_COLUMNS, LibraryLayout.MAX_COLUMNS)
        prefs.edit()
            .putString(modeKey(section), layout.mode.name)
            .putInt(columnsKey(section), columns)
            .apply()
        _layouts.value = _layouts.value + (section to layout.copy(columns = columns))
    }

    private fun load(section: LibrarySection): LibraryLayout = LibraryLayout(
        mode = LibraryViewMode.entries.find { it.name == prefs.getString(modeKey(section), null) }
            ?: LibraryViewMode.GRID,
        columns = prefs.getInt(columnsKey(section), section.defaultColumns)
            .coerceIn(LibraryLayout.MIN_COLUMNS, LibraryLayout.MAX_COLUMNS),
    )

    private fun modeKey(section: LibrarySection) = "${section.name.lowercase()}_view_mode"
    private fun columnsKey(section: LibrarySection) = "${section.name.lowercase()}_columns"
}
