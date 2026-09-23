package com.example.samsonic.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.samsonic.data.LibraryLayout
import com.example.samsonic.data.LibraryViewMode
import com.example.samsonic.ui.common.StateContent
import com.example.samsonic.ui.common.UiState

/** Space the tab content keeps clear: under the pinned tab bar, and above the floating chrome. */
internal data class LibraryPadding(val top: Dp, val bottom: Dp)

/**
 * One Library tab's items, shown the way the user picked in [layout]: a list of
 * [row]s, or a grid of [card]s in 2-4 columns. Grid cards get their column's
 * full width as `artSize`, so covers grow and shrink with the column count and
 * every column stays centered with even gutters.
 *
 * Both views are one [LazyVerticalGrid] (a list is one column), so they share
 * [gridState]: switching view keeps roughly the same spot in the list.
 */
@Composable
internal fun <T> LibraryCollection(
    state: UiState<List<T>>,
    gridState: LazyGridState,
    layout: LibraryLayout,
    padding: LibraryPadding,
    key: (T) -> Any,
    card: @Composable (item: T, artSize: Dp) -> Unit,
    row: @Composable (item: T) -> Unit,
) {
    StateContent(state = state, modifier = Modifier.fillMaxSize()) { items ->
        val grid = layout.mode == LibraryViewMode.GRID
        // Tighter gutters as columns get narrower, so covers keep most of the width.
        val gutter = when (layout.columns) {
            2 -> 16.dp
            3 -> 12.dp
            else -> 10.dp
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(if (grid) layout.columns else 1),
            state = gridState,
            contentPadding = if (grid) {
                PaddingValues(start = 20.dp, end = 20.dp, top = padding.top, bottom = 16.dp + padding.bottom)
            } else {
                // List rows carry their own side padding.
                PaddingValues(top = padding.top, bottom = 16.dp + padding.bottom)
            },
            verticalArrangement = Arrangement.spacedBy(if (grid) gutter + 4.dp else 0.dp),
            horizontalArrangement = Arrangement.spacedBy(if (grid) gutter else 0.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(items, key = key) { item ->
                if (grid) {
                    BoxWithConstraints(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                        card(item, maxWidth)
                    }
                } else {
                    row(item)
                }
            }
        }
    }
}
