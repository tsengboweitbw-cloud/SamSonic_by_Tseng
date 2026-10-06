package com.example.samsonic.ui.settings

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.samsonic.LocalAppContainer
import com.example.samsonic.R
import com.example.samsonic.ui.player.PanelState
import com.example.samsonic.ui.theme.OneUiRow
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.text.NumberFormat

/** How much the library in use holds; each is null if it couldn't be counted. */
private data class LibraryStats(
    val songs: Int?,
    val albums: Int?,
    val artists: Int?,
    val genres: Int?,
    val playlists: Int?,
    val favourites: Int?,
)

/** The Statistics group: a row for each of two menus, what the library holds and what is played most. */
@Composable
internal fun StatisticsSettings(panels: SettingsPanels) {
    SettingsCard {
        NavRow(
            icon = Icons.Filled.BarChart,
            title = stringResource(R.string.settings_stats_library),
            value = "",
            onClick = { panels.statisticsMenu.open() },
            modifier = Modifier.menuOrigin(panels.statisticsMenu),
        )
        NavRow(
            icon = Icons.Filled.Headphones,
            title = stringResource(R.string.settings_group_listening),
            value = "",
            onClick = { panels.listeningMenu.open() },
            modifier = Modifier.menuOrigin(panels.listeningMenu),
        )
    }
}

/** The library menu ([SettingsMenu]): what the library in use holds, counted again when the source changes. */
@Composable
internal fun StatisticsMenu(panel: PanelState, haze: HazeState) {
    SettingsMenu(panel, haze, title = stringResource(R.string.settings_stats_library)) {
        StatisticsCounts()
    }
}

@Composable
private fun StatisticsCounts() {
    val container = LocalAppContainer.current
    val source by container.sources.active.collectAsStateWithLifecycle()
    val stats by produceState<LibraryStats?>(initialValue = null, source) {
        value = null
        val library = container.sources.library
        value = coroutineScope {
            val genres = async { runCatching { library.getGenres() }.getOrNull() }
            val songs = async { runCatching { library.getSongCount() }.getOrNull() }
            val artists = async { runCatching { library.getAlbumArtists() }.getOrNull() }
            val playlists = async { runCatching { library.getPlaylists() }.getOrNull() }
            val liked = async { runCatching { library.getLikedSongs() }.getOrNull() }
            val genreList = genres.await()
            val artistList = artists.await()
            LibraryStats(
                songs = songs.await(),
                // Each album is filed under one album artist, so none is counted twice.
                albums = artistList?.sumOf { it.albumCount },
                artists = artistList?.size,
                genres = genreList?.size,
                playlists = playlists.await()?.size,
                favourites = liked.await()?.size,
            )
        }
    }
    StatRow(Icons.Filled.MusicNote, R.string.settings_stat_songs, stats?.songs, loading = stats == null)
    StatRow(Icons.Filled.Album, R.string.settings_stat_albums, stats?.albums, loading = stats == null)
    StatRow(Icons.Filled.Person, R.string.settings_stat_artists, stats?.artists, loading = stats == null)
    StatRow(Icons.Filled.Category, R.string.settings_stat_genres, stats?.genres, loading = stats == null)
    StatRow(Icons.Filled.QueueMusic, R.string.settings_stat_playlists, stats?.playlists, loading = stats == null)
    StatRow(Icons.Filled.Favorite, R.string.settings_stat_favourites, stats?.favourites, loading = stats == null)
}

/** One count: its icon and name, and the number at the end (a dash while counting or if it couldn't be). */
@Composable
private fun StatRow(icon: ImageVector, label: Int, value: Int?, loading: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // The same padding as the other menus' options (MenuOption).
            .padding(OneUiRow.Inset)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = rowIconTint(), modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Text(text = stringResource(label), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(
            text = if (loading || value == null) "–" else NumberFormat.getInstance().format(value),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
