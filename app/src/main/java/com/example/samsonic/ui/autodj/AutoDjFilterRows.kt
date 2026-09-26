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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
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

/** A filter's name, with what it's set to and a way to clear it once set. */
@Composable
private fun FilterTitle(icon: ImageVector, title: String, value: String, onClear: (() -> Unit)?) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = rowIconTint(), modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        if (onClear != null) {
            Text(
                text = stringResource(R.string.auto_dj_clear),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.oneUiRowClickable(onClear).padding(horizontal = 8.dp, vertical = 4.dp),
            )
        } else {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipFlow(content: @Composable () -> Unit) {
    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) { content() }
}

/** Which of [library]'s genres Auto DJ picks from: its biggest, or all of them on asking. */
@Composable
internal fun GenreFilter(library: MusicLibrary, filters: AutoDjFilters, onChange: (Set<String>) -> Unit) {
    val genres by produceState<List<String>?>(null, library) {
        value = runCatching { library.getGenres().map { it.name } }.getOrDefault(emptyList())
    }
    var showAll by rememberSaveable { mutableStateOf(false) }
    val any = stringResource(R.string.auto_dj_any)
    FilterTitle(
        icon = Icons.Filled.Style,
        title = stringResource(R.string.auto_dj_filter_genres),
        value = any,
        onClear = if (filters.genres.isNotEmpty()) ({ onChange(emptySet()) }) else null,
    )
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
    val shown = if (showAll) all else (all.take(GENRES_SHOWN) + all.filter { it in filters.genres }).distinct()
    ChipFlow {
        shown.forEach { genre ->
            val on = genre in filters.genres
            ToggleChip(genre, on, onClick = { onChange(if (on) filters.genres - genre else filters.genres + genre) })
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
internal fun DecadeFilter(filters: AutoDjFilters, onChange: (Set<Int>) -> Unit) {
    FilterTitle(
        icon = Icons.Filled.DateRange,
        title = stringResource(R.string.auto_dj_filter_decades),
        value = stringResource(R.string.auto_dj_any),
        onClear = if (filters.decades.isNotEmpty()) ({ onChange(emptySet()) }) else null,
    )
    ChipFlow {
        AutoDjDecades.forEach { decade ->
            val on = decade in filters.decades
            ToggleChip(
                label = decadeLabel(decade),
                selected = on,
                onClick = { onChange(if (on) filters.decades - decade else filters.decades + decade) },
            )
        }
    }
}

@Composable
internal fun decadeLabel(decade: Int): String =
    if (decade == EARLIER_DECADES) stringResource(R.string.auto_dj_decade_earlier) else stringResource(R.string.auto_dj_decade, decade)

/** Which artists Auto DJ picks from: the chosen ones, and a search of [library] to add more. */
@Composable
internal fun ArtistFilter(library: MusicLibrary, filters: AutoDjFilters, onChange: (List<AutoDjArtist>) -> Unit) {
    FilterTitle(
        icon = Icons.Filled.Person,
        title = stringResource(R.string.auto_dj_filter_artists),
        value = stringResource(R.string.auto_dj_any),
        onClear = if (filters.artists.isNotEmpty()) ({ onChange(emptyList()) }) else null,
    )
    if (filters.artists.isNotEmpty()) {
        ChipFlow {
            filters.artists.forEach { artist ->
                ToggleChip(
                    label = artist.name,
                    selected = true,
                    trailingIcon = Icons.Filled.Close,
                    onClick = { onChange(filters.artists - artist) },
                )
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
    val chosen = filters.artists.map { it.id }.toSet()
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
                        onChange((filters.artists + AutoDjArtist(artist.id, artist.name)).sortedBy { it.name.lowercase() })
                        query = ""
                    })
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            )
        }
    }
}
