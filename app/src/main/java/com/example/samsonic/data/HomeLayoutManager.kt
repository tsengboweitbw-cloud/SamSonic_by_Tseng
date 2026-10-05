package com.example.samsonic.data

import android.content.Context
import androidx.core.content.edit
import com.example.samsonic.ui.home.HomeShelf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** One Home section as the user has set it: whether it shows, and how many albums or songs. */
data class HomeSection(val shelf: HomeShelf, val visible: Boolean, val count: Int)

/**
 * Persists which of Home's sections show, in what order, and how many items each
 * holds, via plain SharedPreferences like [LibraryLayoutManager]. A section never
 * changed keeps its usual size and shows; one added in a later version slots in at the end.
 */
class HomeLayoutManager(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("samsonic_home", Context.MODE_PRIVATE)

    private val _sections = MutableStateFlow(load())

    /** Every section, in the order Home lists them (hidden ones included). */
    val sections: StateFlow<List<HomeSection>> = _sections.asStateFlow()

    fun setVisible(shelf: HomeShelf, visible: Boolean) {
        prefs.edit { putBoolean(visibleKey(shelf), visible) }
        change(shelf) { it.copy(visible = visible) }
    }

    fun setCount(shelf: HomeShelf, count: Int) {
        val clamped = count.coerceIn(HomeShelf.MIN_COUNT, shelf.previewSize)
        prefs.edit { putInt(countKey(shelf), clamped) }
        change(shelf) { it.copy(count = clamped) }
    }

    /** Moves [shelf] one place up ([by] of -1) or down (1) in the list. */
    fun move(shelf: HomeShelf, by: Int) {
        val current = _sections.value
        val from = current.indexOfFirst { it.shelf == shelf }
        val to = from + by
        if (from < 0 || to !in current.indices) return
        val moved = current.toMutableList().apply { add(to, removeAt(from)) }
        prefs.edit { putString(KEY_ORDER, moved.joinToString(",") { it.shelf.name }) }
        _sections.value = moved
    }

    fun reset() {
        prefs.edit { clear() }
        _sections.value = load()
    }

    val isDefault: Boolean get() = prefs.all.isEmpty()

    private fun change(shelf: HomeShelf, transform: (HomeSection) -> HomeSection) =
        _sections.update { list -> list.map { if (it.shelf == shelf) transform(it) else it } }

    private fun load(): List<HomeSection> {
        val saved = prefs.getString(KEY_ORDER, null)?.split(',')?.mapNotNull(HomeShelf::fromKey).orEmpty()
        return (saved + HomeShelf.entries.filter { it !in saved }).distinct().map { shelf ->
            HomeSection(
                shelf = shelf,
                visible = prefs.getBoolean(visibleKey(shelf), true),
                count = prefs.getInt(countKey(shelf), shelf.defaultCount).coerceIn(HomeShelf.MIN_COUNT, shelf.previewSize),
            )
        }
    }

    private fun visibleKey(shelf: HomeShelf) = "${shelf.name}_visible"
    private fun countKey(shelf: HomeShelf) = "${shelf.name}_count"

    private companion object {
        const val KEY_ORDER = "order"
    }
}
