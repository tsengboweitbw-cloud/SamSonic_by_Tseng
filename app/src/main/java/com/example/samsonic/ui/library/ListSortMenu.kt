package com.example.samsonic.ui.library

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SortByAlpha
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.samsonic.LocalAppContainer
import com.example.samsonic.R
import com.example.samsonic.data.ListSort
import com.example.samsonic.data.SortKey
import com.example.samsonic.data.SortedList
import com.example.samsonic.model.Album
import com.example.samsonic.model.Song
import com.example.samsonic.ui.components.BackButtonClearance
import com.example.samsonic.ui.components.ChromeButtonSize
import com.example.samsonic.ui.components.GlassTabBar
import com.example.samsonic.ui.settings.MenuOption
import dev.chrisbanes.haze.HazeState

/** [list]'s sort, as the user last set it. */
@Composable
internal fun rememberListSort(list: SortedList): ListSort {
    val sorts by LocalAppContainer.current.libraryLayoutManager.listSorts.collectAsStateWithLifecycle()
    return sorts.getValue(list)
}

/**
 * Runs [onChange] when [sort] changes; not when the page is only shown again (back
 * from a page opened from it), which keeps its scroll where it was left.
 */
@Composable
internal fun OnSortChange(sort: ListSort, onChange: suspend () -> Unit) {
    val id = "${sort.key.name}:${sort.descending}"
    var shown by rememberSaveable { mutableStateOf(id) }
    LaunchedEffect(id) {
        if (id == shown) return@LaunchedEffect
        shown = id
        onChange()
    }
}

/**
 * A song list's sort button, floating top right across from the back button (and
 * the same size), which grows into the sort menu: [list]'s sorts, one per row,
 * and the order, ascending or descending. [haze] is the list's haze source, which
 * the button and the menu blur, as the back button does.
 */
@Composable
internal fun BoxScope.ListSortMenu(list: SortedList, haze: HazeState) {
    CornerMenu(
        icon = Icons.AutoMirrored.Filled.Sort,
        contentDescription = stringResource(R.string.library_sort),
        haze = haze,
        panelTop = BackButtonClearance + 8.dp,
        buttonSize = ChromeButtonSize,
        buttonHaze = haze,
    ) {
        val manager = LocalAppContainer.current.libraryLayoutManager
        val sort = rememberListSort(list)
        Column(Modifier.fillMaxWidth().padding(vertical = 20.dp)) {
            Text(
                text = stringResource(R.string.library_sort_by),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            Spacer(Modifier.height(12.dp))
            list.keys.forEach { key ->
                MenuOption(
                    icon = key.icon(list),
                    label = stringResource(key.label(list)),
                    selected = key == sort.key,
                    onClick = { manager.setListSort(list, sort.copy(key = key)) },
                )
            }
            Spacer(Modifier.height(16.dp))
            Column(Modifier.padding(horizontal = 24.dp)) { OrderTabs(list, sort) }
        }
    }
}

/** [list]'s sorts as a row of tabs, then the order: for a view options panel ([CollectionViewMenu]). */
@Composable
internal fun ListSortOptions(list: SortedList) {
    val manager = LocalAppContainer.current.libraryLayoutManager
    val sort = rememberListSort(list)
    PanelLabel(stringResource(R.string.library_sort_by))
    GlassTabBar(
        labels = list.keys.map { stringResource(it.label(list)) },
        selectedIndex = list.keys.indexOf(sort.key),
        onSelect = { index -> manager.setListSort(list, sort.copy(key = list.keys[index])) },
        hazeState = null,
    )
    Spacer(Modifier.height(20.dp))
    OrderTabs(list, sort)
}

@Composable
private fun OrderTabs(list: SortedList, sort: ListSort) {
    val manager = LocalAppContainer.current.libraryLayoutManager
    PanelLabel(stringResource(R.string.library_sort_order))
    GlassTabBar(
        labels = listOf(stringResource(R.string.library_sort_ascending), stringResource(R.string.library_sort_descending)),
        selectedIndex = if (sort.descending) 1 else 0,
        onSelect = { index -> manager.setListSort(list, sort.copy(descending = index == 1)) },
        hazeState = null,
    )
}

/** The name of [list]'s own order (its tracks', a playlist's) or of another sort. */
@StringRes
private fun SortKey.label(list: SortedList): Int = when (this) {
    SortKey.DEFAULT -> when (list) {
        SortedList.ALBUM_TRACKS -> R.string.library_sort_track
        SortedList.PLAYLIST_SONGS -> R.string.library_sort_playlist_order
        else -> R.string.library_sort_default
    }
    SortKey.TITLE -> R.string.library_sort_title
    SortKey.ARTIST -> R.string.library_sort_artist
    SortKey.ALBUM -> R.string.library_sort_album
    SortKey.YEAR -> R.string.library_sort_year
    SortKey.DURATION -> R.string.library_sort_duration
}

private fun SortKey.icon(list: SortedList): ImageVector = when (this) {
    SortKey.DEFAULT -> when (list) {
        SortedList.ALBUM_TRACKS -> Icons.Filled.FormatListNumbered
        SortedList.PLAYLIST_SONGS -> Icons.AutoMirrored.Filled.QueueMusic
        else -> Icons.AutoMirrored.Filled.Sort
    }
    SortKey.TITLE -> Icons.Filled.SortByAlpha
    SortKey.ARTIST -> Icons.Filled.Person
    SortKey.ALBUM -> Icons.Filled.Album
    SortKey.YEAR -> Icons.Filled.CalendarMonth
    SortKey.DURATION -> Icons.Filled.Schedule
}

/**
 * [loaded] in [sort]'s order, and, with the Offline only filter on, just the songs kept for
 * offline. What a song list plays and shows.
 */
@Composable
internal fun rememberShownSongs(loaded: List<Song>, sort: ListSort): List<Song> {
    val offline = LocalAppContainer.current.offlineMusic
    val onlyOffline by offline.onlyOffline.collectAsStateWithLifecycle()
    val kept by offline.keptIds.collectAsStateWithLifecycle()
    val filter = onlyOffline && offline.available
    return remember(loaded, sort, filter, kept) {
        loaded.sortedFor(sort).let { sorted -> if (filter) sorted.filter { it.id in kept } else sorted }
    }
}

/** These songs in [sort]'s order. */
internal fun List<Song>.sortedFor(sort: ListSort): List<Song> = sortedByKey(sort) { key ->
    when (key) {
        SortKey.DEFAULT -> null
        SortKey.TITLE -> title.lowercase()
        SortKey.ARTIST -> artistName.lowercase()
        SortKey.ALBUM -> albumTitle.lowercase()
        SortKey.YEAR -> year
        SortKey.DURATION -> durationSeconds
    }
}

/** These albums in [sort]'s order. */
internal fun List<Album>.sortedAlbumsFor(sort: ListSort): List<Album> = sortedByKey(sort) { key ->
    when (key) {
        SortKey.DEFAULT -> null
        SortKey.TITLE, SortKey.ALBUM -> title.lowercase()
        SortKey.ARTIST -> artistName.lowercase()
        SortKey.YEAR -> year
        SortKey.DURATION -> durationSeconds
    }
}

/**
 * Sorted by each one's [value] for [sort]'s key, the order the list came in reversed
 * for a descending [SortKey.DEFAULT]. Stable, so ties (an album's tracks, a year's
 * albums) keep the order they came in; ones with no value (no year) go last either way.
 */
private fun <T> List<T>.sortedByKey(sort: ListSort, value: T.(SortKey) -> Comparable<*>?): List<T> {
    if (sort.key == SortKey.DEFAULT) return if (sort.descending) reversed() else this
    return map { it to it.value(sort.key) }
        .sortedWith { (_, a), (_, b) ->
            when {
                a == null && b == null -> 0
                a == null -> 1
                b == null -> -1
                else -> compareValues(a, b).let { if (sort.descending) -it else it }
            }
        }
        .map { it.first }
}
