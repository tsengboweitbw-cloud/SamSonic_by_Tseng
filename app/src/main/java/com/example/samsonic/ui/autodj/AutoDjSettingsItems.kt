package com.example.samsonic.ui.autodj

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AllInclusive
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FiberNew
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.samsonic.R
import androidx.compose.material.icons.filled.LibraryMusic
import com.example.samsonic.data.AutoDjAlbumCounts
import com.example.samsonic.data.AutoDjConfig
import com.example.samsonic.data.AutoDjSongCounts
import com.example.samsonic.ui.components.ToggleChip
import com.example.samsonic.ui.settings.SliderRow
import kotlin.math.roundToInt
import com.example.samsonic.data.AutoDjFollow
import com.example.samsonic.data.AutoDjMode
import com.example.samsonic.data.AutoDjSettings
import com.example.samsonic.data.MusicLibrary
import com.example.samsonic.ui.settings.GroupLabel
import com.example.samsonic.ui.settings.SettingsCard
import com.example.samsonic.ui.settings.SwitchRow

/**
 * Auto DJ's settings as list items, for its page in Settings and its panel in Now
 * Playing alike: [mode] (each place picks its mode its own way), what of the song
 * playing it follows, and the filters on everything it picks from [library].
 */
@OptIn(ExperimentalLayoutApi::class)
internal fun LazyListScope.autoDjSettingsItems(
    config: AutoDjConfig,
    settings: AutoDjSettings,
    library: MusicLibrary,
    mode: @Composable () -> Unit,
) {
    val filters = config.filters
    item(key = "mode") { mode() }

    // How much is added each time, in the unit of the mode; nothing to set with Auto DJ off.
    if (config.mode != AutoDjMode.OFF) {
        item(key = "amount") {
            val songs = config.mode == AutoDjMode.SONGS
            val range = if (songs) AutoDjSongCounts else AutoDjAlbumCounts
            val count = if (songs) config.songCount else config.albumCount
            // Clear of the mode chips above, which end flush with the item.
            Box(Modifier.padding(top = 16.dp)) {
                SettingsCard {
                    SliderRow(
                        icon = Icons.Filled.LibraryMusic,
                        title = stringResource(if (songs) R.string.auto_dj_amount_songs else R.string.auto_dj_amount_albums),
                        hint = stringResource(R.string.auto_dj_amount_hint),
                        valueLabel = count.toString(),
                        value = count.toFloat(),
                        valueRange = range.first.toFloat()..range.last.toFloat(),
                        steps = range.last - range.first - 1,
                        onValueChange = { value -> if (songs) settings.setSongCount(value.roundToInt()) else settings.setAlbumCount(value.roundToInt()) },
                    )
                }
            }
        }
    }

    item(key = "follow-label") { GroupLabel(stringResource(R.string.auto_dj_group_follow)) }
    item(key = "follow") {
        FlowRow(
            modifier = Modifier.padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf(
                AutoDjFollow.ARTIST to R.string.auto_dj_follow_artist,
                AutoDjFollow.GENRE to R.string.auto_dj_follow_genre,
                AutoDjFollow.ERA to R.string.auto_dj_follow_era,
            ).forEach { (follow, label) ->
                val on = follow in config.follow
                ToggleChip(stringResource(label), selected = on, onClick = { settings.setFollowing(follow, !on) })
            }
        }
    }
    item(key = "follow-note") { Note(stringResource(R.string.auto_dj_follow_note)) }

    item(key = "filters-label") { GroupLabel(stringResource(R.string.auto_dj_group_filters)) }
    item(key = "filters") {
        SettingsCard {
            GenreFilter(library, filters, settings::updateFilters)
            DecadeFilter(filters, settings::updateFilters)
            ArtistFilter(library, filters, settings::updateFilters)
            SwitchRow(
                icon = Icons.Filled.Favorite,
                title = stringResource(R.string.auto_dj_liked_only),
                checked = filters.likedOnly,
                onCheckedChange = { on -> settings.updateFilters { it.copy(likedOnly = on) } },
            )
            SwitchRow(
                icon = Icons.Filled.GraphicEq,
                title = stringResource(R.string.auto_dj_hi_res_only),
                checked = filters.hiResOnly,
                onCheckedChange = { on -> settings.updateFilters { it.copy(hiResOnly = on) } },
                hint = stringResource(R.string.auto_dj_hi_res_only_hint),
            )
            SwitchRow(
                icon = Icons.Filled.FiberNew,
                title = stringResource(R.string.auto_dj_never_played),
                checked = filters.neverPlayed,
                onCheckedChange = { on -> settings.updateFilters { it.copy(neverPlayed = on) } },
                hint = stringResource(R.string.auto_dj_never_played_hint),
            )
        }
    }
    item(key = "filters-note") { Note(stringResource(R.string.auto_dj_filters_note)) }
}

/** A line of explanation under a card. */
@Composable
private fun Note(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 8.dp),
    )
}

/** The name of [mode], as the Settings row, the mode menu and Up next show it. */
@Composable
internal fun autoDjModeLabel(mode: AutoDjMode): String = stringResource(
    when (mode) {
        AutoDjMode.OFF -> R.string.auto_dj_mode_off
        AutoDjMode.SONGS -> R.string.auto_dj_mode_songs
        AutoDjMode.ALBUMS -> R.string.auto_dj_mode_albums
    },
)

/** Auto DJ's own icon: its Settings row and its capsule in Now Playing. */
internal val AutoDjIcon = Icons.Filled.AllInclusive
