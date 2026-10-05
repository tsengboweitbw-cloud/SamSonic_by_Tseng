package com.example.samsonic.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.samsonic.LocalAppContainer
import com.example.samsonic.R
import com.example.samsonic.ui.theme.OneUiRow

/** How many of each ranking are listed. */
private const val TOP_COUNT = 5

/** How many of the most played songs the rankings are drawn from. */
private const val SONGS_CONSIDERED = 100

private data class Ranked(val title: String, val subtitle: String?, val plays: Long)

private data class ListeningStats(val songs: List<Ranked>, val artists: List<Ranked>, val albums: List<Ranked>)

/**
 * The Listening group: the most played songs, artists and albums. They're ranked from the
 * most played songs the source reports, so a song played only a little may be missed.
 */
@Composable
internal fun ListeningSettings() {
    val container = LocalAppContainer.current
    val source by container.sources.active.collectAsStateWithLifecycle()
    // Null while loading; empty lists where nothing has been played (or the source keeps no counts).
    val stats by produceState<ListeningStats?>(initialValue = null, source) {
        value = null
        val songs = runCatching { container.sources.library.getSongList("frequent", SONGS_CONSIDERED) }
            .getOrDefault(emptyList())
            .filter { (it.playCount ?: 0L) > 0L }
        value = ListeningStats(
            songs = songs.sortedByDescending { it.playCount }.take(TOP_COUNT)
                .map { Ranked(it.title, it.artistName, it.playCount ?: 0L) },
            artists = songs.groupBy { it.artistName }
                .map { (name, own) -> Ranked(name, null, own.sumOf { it.playCount ?: 0L }) }
                .sortedByDescending { it.plays }.take(TOP_COUNT),
            albums = songs.filter { it.albumId != null }.groupBy { it.albumId }
                .map { (_, own) -> Ranked(own.first().albumTitle, own.first().artistName, own.sumOf { it.playCount ?: 0L }) }
                .sortedByDescending { it.plays }.take(TOP_COUNT),
        )
    }
    val loaded = stats
    SettingsCard {
        if (loaded == null || loaded.songs.isEmpty()) {
            Text(
                text = stringResource(if (loaded == null) R.string.settings_listening_loading else R.string.settings_listening_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(OneUiRow.Inset).padding(horizontal = 8.dp, vertical = 14.dp),
            )
        } else {
            RankingRows(R.string.settings_listening_songs, loaded.songs)
            RankingRows(R.string.settings_listening_artists, loaded.artists)
            RankingRows(R.string.settings_listening_albums, loaded.albums)
        }
    }
}

@Composable
private fun RankingRows(title: Int, items: List<Ranked>) {
    Text(
        text = stringResource(title),
        style = MaterialTheme.typography.titleSmall,
        color = rowIconTint(),
        modifier = Modifier.fillMaxWidth().padding(OneUiRow.Inset).padding(start = 8.dp, end = 8.dp, top = 14.dp, bottom = 2.dp),
    )
    items.forEachIndexed { index, item ->
        Row(
            modifier = Modifier.fillMaxWidth().padding(OneUiRow.Inset).padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = "${index + 1}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(24.dp))
            Column(Modifier.weight(1f)) {
                Text(text = item.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (item.subtitle != null) {
                    Text(
                        text = item.subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Text(
                text = pluralStringResource(R.plurals.settings_listening_plays, item.plays.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(), item.plays.toInt()),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
