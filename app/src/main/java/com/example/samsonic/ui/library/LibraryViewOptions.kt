package com.example.samsonic.ui.library

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.samsonic.LocalAppContainer
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.samsonic.R
import androidx.annotation.StringRes
import com.example.samsonic.data.LibraryLayout
import com.example.samsonic.data.LibrarySort
import com.example.samsonic.ui.components.GlassTabBar
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.Alignment
import com.example.samsonic.ui.theme.OneUiSlider
import kotlin.math.abs


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

        // Up to more columns on a larger screen (see GridForm).
        val gridForm by LocalAppContainer.current.libraryLayoutManager.gridForm.collectAsStateWithLifecycle()
        val twoPane by LocalAppContainer.current.libraryLayoutManager.twoPane.collectAsStateWithLifecycle()
        val layoutManager = LocalAppContainer.current.libraryLayoutManager
        val maxColumns = remember(gridForm, twoPane) { layoutManager.maxColumns }
        val columnChoices = remember(maxColumns) { listOf(0) + (LibraryLayout.MIN_COLUMNS..maxColumns).toList() }
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PanelLabel(stringResource(R.string.library_grid_columns))
                val currentLabel = if (layout.isDefault) stringResource(R.string.library_sort_default) else layout.columns.toString()
                Text(
                    text = currentLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp, end = 4.dp),
                )
            }
            val currentValue = if (layout.isDefault) 0f else layout.columns.toFloat()
            // The thumb follows the finger on its own, and the layout changes only as it snaps to
            // another count: a reload of the layout mid-drag cannot hold it.
            var dragValue by remember { mutableStateOf<Float?>(null) }
            CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 32.dp) {
                OneUiSlider(
                    value = dragValue ?: currentValue,
                    onValueChange = { newValue ->
                        dragValue = newValue
                        val closest = columnChoices.minByOrNull { abs(it.toFloat() - newValue) } ?: 0
                        if (closest.toFloat() != currentValue) {
                            onLayoutChange(layout.copy(
                                columns = if (closest == 0) 0 else closest,
                                isDefault = closest == 0,
                            ))
                        }
                    },
                    onValueChangeFinished = { dragValue = null },
                    valueRange = 0f..maxColumns.toFloat(),
                    enabled = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
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
