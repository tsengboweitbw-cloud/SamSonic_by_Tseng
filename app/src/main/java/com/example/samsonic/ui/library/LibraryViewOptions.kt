package com.example.samsonic.ui.library

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.samsonic.R
import androidx.annotation.StringRes
import com.example.samsonic.data.LibraryLayout
import com.example.samsonic.data.LibrarySort
import com.example.samsonic.data.LibraryViewMode
import com.example.samsonic.ui.components.GlassTabBar

private val columnChoices = (LibraryLayout.MIN_COLUMNS..LibraryLayout.MAX_COLUMNS).toList()

/**
 * The settings inside a view options panel: the Library's ([LibraryTabsPanel]), or
 * a page's own ([CollectionViewMenu]), which titles itself, so [sectionName] is null.
 * With a [sort] (the Library's tabs), it offers that tab's other orders too; a
 * [footer] (a page's own sort) goes under the rest.
 */
@Composable
internal fun ViewOptionsPanel(
    sectionName: String?,
    layout: LibraryLayout,
    onLayoutChange: (LibraryLayout) -> Unit,
    modifier: Modifier = Modifier.padding(24.dp),
    sort: LibrarySort? = null,
    onSortChange: (LibrarySort) -> Unit = {},
    footer: (@Composable () -> Unit)? = null,
) {
    Column(modifier = Modifier.fillMaxWidth().then(modifier)) {
        if (sectionName != null) {
            Text(text = stringResource(R.string.library_section_view, sectionName), style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(20.dp))
        }

        PanelLabel(stringResource(R.string.library_layout))
        GlassTabBar(
            labels = listOf(stringResource(R.string.library_list), stringResource(R.string.library_grid)),
            selectedIndex = if (layout.mode == LibraryViewMode.GRID) 1 else 0,
            onSelect = { index ->
                onLayoutChange(layout.copy(mode = if (index == 1) LibraryViewMode.GRID else LibraryViewMode.LIST))
            },
            hazeState = null,
        )
        Spacer(Modifier.height(20.dp))

        // Frozen in list view: the saved count still shows, dimmed, for when grid comes back.
        val grid = layout.mode == LibraryViewMode.GRID
        val frozenAlpha by animateFloatAsState(if (grid) 1f else 0.38f, label = "columnsAlpha")
        Column(Modifier.alpha(frozenAlpha)) {
            PanelLabel(stringResource(R.string.library_grid_columns))
            GlassTabBar(
                labels = columnChoices.map { it.toString() },
                selectedIndex = columnChoices.indexOf(layout.columns).coerceAtLeast(0),
                onSelect = { index -> onLayoutChange(layout.copy(columns = columnChoices[index])) },
                hazeState = null,
                enabled = grid,
            )
        }

        if (sort != null) {
            val options = LibrarySort.of(sort.section)
            Spacer(Modifier.height(20.dp))
            PanelLabel(stringResource(R.string.library_sort_by))
            GlassTabBar(
                labels = options.map { stringResource(it.label) },
                selectedIndex = options.indexOf(sort),
                onSelect = { index -> onSortChange(options[index]) },
                hazeState = null,
            )
        }
        if (footer != null) {
            Spacer(Modifier.height(20.dp))
            footer()
        }
    }
}

private val LibrarySort.label: Int
    @StringRes get() = when (this) {
        LibrarySort.ARTIST_NAME, LibrarySort.PLAYLIST_NAME -> R.string.library_sort_name
        LibrarySort.ARTIST_ALBUM_COUNT -> R.string.library_sort_album_count
        LibrarySort.ALBUM_ARTIST -> R.string.library_sort_artist
        LibrarySort.ALBUM_TITLE -> R.string.library_sort_title
        LibrarySort.ALBUM_YEAR -> R.string.library_sort_year
        LibrarySort.ALBUM_ADDED -> R.string.library_sort_added
        LibrarySort.PLAYLIST_CHANGED -> R.string.library_sort_changed
        LibrarySort.PLAYLIST_SONG_COUNT -> R.string.library_sort_song_count
        LibrarySort.PLAYLIST_DURATION -> R.string.library_sort_duration
    }

@Composable
internal fun PanelLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
    )
}
