package com.example.samsonic.ui.autodj

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Style
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.samsonic.R
import com.example.samsonic.data.AutoDjArtist
import com.example.samsonic.data.AutoDjDecades
import com.example.samsonic.data.AutoDjFilters
import com.example.samsonic.data.EARLIER_DECADES
import com.example.samsonic.data.MusicLibrary
import com.example.samsonic.model.Artist
import com.example.samsonic.ui.components.ToggleChip
import com.example.samsonic.ui.settings.rowIconTint
import com.example.samsonic.ui.theme.OneUiRadius
import com.example.samsonic.ui.theme.oneUiRowClickable
import kotlinx.coroutines.delay

// Genres shown before "Show all": the ones with the most songs.
private const val GENRES_SHOWN = 18
// Artists a search lists at most.
private const val ARTIST_RESULTS = 6
// How long typing pauses before an artist search runs.
private const val SEARCH_DELAY_MS = 300L

/**
 * A filter's name with what it's set to, tapped to open or close its choices. Once
 * open, a set filter offers Clear in place of its summary.
 */
@Composable
private fun FilterTitle(
    icon: ImageVector,
    title: String,
    summary: String?,
    expanded: Boolean,
    onToggle: () -> Unit,
    onClear: (() -> Unit)?,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .oneUiRowClickable(onToggle)
            .padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = rowIconTint(), modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.width(12.dp))
        if (expanded && onClear != null) {
            Spacer(Modifier.weight(1f))
            Text(
                text = stringResource(R.string.auto_dj_clear),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.oneUiRowClickable(onClear).padding(horizontal = 8.dp, vertical = 4.dp),
            )
        } else {
            Text(
                text = summary ?: stringResource(R.string.auto_dj_any),
                style = MaterialTheme.typography.bodyMedium,
                color = if (summary != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(1f),
            )
        }
        Icon(
            Icons.Filled.ExpandMore,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp).size(24.dp).rotate(if (expanded) 180f else 0f),
        )
    }
}

/** The first of [names], with how many more are chosen; null when none are. */
@Composable
private fun chosenSummary(names: List<String>): String? = when (names.size) {
    0 -> null
    1 -> names[0]
    else -> stringResource(R.string.auto_dj_summary_more, names[0], names.size - 1)
}

/** [chosenSummary] of what's kept and what's left out ("Rock +1 · Not Jazz"); null when neither is set. */
@Composable
private fun filterSummary(included: List<String>, excluded: List<String>): String? {
    val kept = chosenSummary(included)
    val left = chosenSummary(excluded)?.let { stringResource(R.string.auto_dj_summary_not, it) }
    return listOfNotNull(kept, left).takeIf { it.isNotEmpty() }?.joinToString(" · ")
}

/** Where a choice stands: not used, the only kind picked, or never picked. */
private enum class Stand { NONE, INCLUDE, EXCLUDE }

/** A choice's chip: lit when it's used either way, and barred when it's left out. */
@Composable
private fun StandChip(label: String, stand: Stand, onClick: () -> Unit) {
    ToggleChip(
        label = label,
        selected = stand != Stand.NONE,
        trailingIcon = if (stand == Stand.EXCLUDE) Icons.Filled.Block else null,
        onClick = onClick,
    )
}

/** [item] with its next stand when tapped: none, then kept, then left out, then none again. */
private fun <T> Set<T>.next(item: T, excluded: Set<T>): Pair<Set<T>, Set<T>> = when {
    item in this -> (this - item) to (excluded + item)
    item in excluded -> this to (excluded - item)
    else -> (this + item) to excluded
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipFlow(content: @Composable () -> Unit) {
    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) { content() }
}

/** Which of [library]'s genres Auto DJ picks from: its biggest, or all of them on asking. */
@Composable
internal fun GenreFilter(library: MusicLibrary, filters: AutoDjFilters, onChange: ((AutoDjFilters) -> AutoDjFilters) -> Unit) {
    val genres by produceState<List<String>?>(null, library) {
        value = runCatching { library.getGenres().map { it.name } }.getOrDefault(emptyList())
    }
    var showAll by rememberSaveable { mutableStateOf(false) }
    var open by rememberSaveable { mutableStateOf(false) }
    FilterTitle(
        icon = Icons.Filled.Style,
        title = stringResource(R.string.auto_dj_filter_genres),
        summary = filterSummary(filters.genres.sorted(), filters.excludedGenres.sorted()),
        expanded = open,
        onToggle = { open = !open },
        onClear = if (filters.genres.isNotEmpty() || filters.excludedGenres.isNotEmpty()) {
            { onChange { it.copy(genres = emptySet(), excludedGenres = emptySet()) } }
        } else {
            null
        },
    )
    if (!open) return
    val all = genres ?: return
    if (all.isEmpty()) {
        Text(
            text = stringResource(R.string.auto_dj_no_genres),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 52.dp, top = 6.dp, bottom = 12.dp),
        )
        return
    }
    // The chosen ones stay in view even when they're not among the biggest.
    val shown = if (showAll) all else (all.take(GENRES_SHOWN) + all.filter { it in filters.genres || it in filters.excludedGenres }).distinct()
    ChipFlow {
        shown.forEach { genre ->
            val stand = when (genre) {
                in filters.genres -> Stand.INCLUDE
                in filters.excludedGenres -> Stand.EXCLUDE
                else -> Stand.NONE
            }
            StandChip(genre, stand) {
                onChange { f ->
                    val (kept, left) = f.genres.next(genre, f.excludedGenres)
                    f.copy(genres = kept, excludedGenres = left)
                }
            }
        }
        if (all.size > GENRES_SHOWN) {
            Text(
                text = stringResource(if (showAll) R.string.auto_dj_show_fewer else R.string.auto_dj_show_all),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.oneUiRowClickable({ showAll = !showAll }).padding(horizontal = 10.dp, vertical = 8.dp),
            )
        }
    }
}

/** Which decades Auto DJ picks from. */
@Composable
internal fun DecadeFilter(filters: AutoDjFilters, onChange: ((AutoDjFilters) -> AutoDjFilters) -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    FilterTitle(
        icon = Icons.Filled.DateRange,
        title = stringResource(R.string.auto_dj_filter_decades),
        summary = filterSummary(
            AutoDjDecades.filter { it in filters.decades }.map { decadeLabel(it) },
            AutoDjDecades.filter { it in filters.excludedDecades }.map { decadeLabel(it) },
        ),
        expanded = open,
        onToggle = { open = !open },
        onClear = if (filters.decades.isNotEmpty() || filters.excludedDecades.isNotEmpty()) {
            { onChange { it.copy(decades = emptySet(), excludedDecades = emptySet()) } }
        } else {
            null
        },
    )
    if (!open) return
    ChipFlow {
        AutoDjDecades.forEach { decade ->
            val stand = when (decade) {
                in filters.decades -> Stand.INCLUDE
                in filters.excludedDecades -> Stand.EXCLUDE
                else -> Stand.NONE
            }
            StandChip(decadeLabel(decade), stand) {
                onChange { f ->
                    val (kept, left) = f.decades.next(decade, f.excludedDecades)
                    f.copy(decades = kept, excludedDecades = left)
                }
            }
        }
    }
}

@Composable
internal fun decadeLabel(decade: Int): String =
    if (decade == EARLIER_DECADES) stringResource(R.string.auto_dj_decade_earlier) else stringResource(R.string.auto_dj_decade, decade)

/** Which artists Auto DJ picks from: the chosen ones, and a search of [library] to add more. */
@Composable
internal fun ArtistFilter(library: MusicLibrary, filters: AutoDjFilters, onChange: ((AutoDjFilters) -> AutoDjFilters) -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    FilterTitle(
        icon = Icons.Filled.Person,
        title = stringResource(R.string.auto_dj_filter_artists),
        summary = filterSummary(filters.artists.map { it.name }, filters.excludedArtists.map { it.name }),
        expanded = open,
        onToggle = { open = !open },
        onClear = if (filters.artists.isNotEmpty() || filters.excludedArtists.isNotEmpty()) {
            { onChange { it.copy(artists = emptyList(), excludedArtists = emptyList()) } }
        } else {
            null
        },
    )
    if (!open) return
    if (filters.artists.isNotEmpty() || filters.excludedArtists.isNotEmpty()) {
        ChipFlow {
            // A kept artist turns to left out when tapped, and a left out one is dropped.
            filters.artists.forEach { artist ->
                StandChip(artist.name, Stand.INCLUDE) {
                    onChange { f ->
                        f.copy(
                            artists = f.artists - artist,
                            excludedArtists = (f.excludedArtists + artist).sortedBy { it.name.lowercase() },
                        )
                    }
                }
            }
            filters.excludedArtists.forEach { artist ->
                StandChip(artist.name, Stand.EXCLUDE) { onChange { f -> f.copy(excludedArtists = f.excludedArtists - artist) } }
            }
        }
    }
    var query by rememberSaveable { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Artist>?>(null) }
    LaunchedEffect(query, library) {
        if (query.isBlank()) {
            results = null
            return@LaunchedEffect
        }
        delay(SEARCH_DELAY_MS)
        results = runCatching { library.search(query.trim()).artists }.getOrDefault(emptyList())
    }
    OutlinedTextField(
        value = query,
        onValueChange = { query = it },
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 12.dp),
        placeholder = { Text(stringResource(R.string.auto_dj_search_artists)) },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        singleLine = true,
        shape = RoundedCornerShape(OneUiRadius.Pill),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f),
        ),
    )
    val found = results ?: return
    val chosen = (filters.artists + filters.excludedArtists).map { it.id }.toSet()
    val addable = found.filter { it.id !in chosen }.take(ARTIST_RESULTS)
    Column(Modifier.padding(bottom = 8.dp)) {
        if (addable.isEmpty()) {
            Text(
                text = stringResource(R.string.auto_dj_no_artists_found),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 24.dp, bottom = 8.dp),
            )
        }
        addable.forEach { artist ->
            Text(
                text = artist.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .oneUiRowClickable({
                        onChange { f ->
                            f.copy(artists = (f.artists + AutoDjArtist(artist.id, artist.name)).sortedBy { it.name.lowercase() })
                        }
                        query = ""
                    })
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            )
        }
    }
}
