package com.example.samsonic.ui.autodj

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AllInclusive
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FiberNew
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Style
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.samsonic.R
import com.example.samsonic.data.AutoDjConfig
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
internal fun LazyListScope.autoDjSettingsItems(
    config: AutoDjConfig,
    settings: AutoDjSettings,
    library: MusicLibrary,
    mode: @Composable () -> Unit,
) {
    val filters = config.filters
    item(key = "mode") { mode() }

    item(key = "follow-label") { GroupLabel(stringResource(R.string.auto_dj_group_follow)) }
    item(key = "follow") {
        SettingsCard {
            SwitchRow(
                icon = Icons.Filled.Person,
                title = stringResource(R.string.auto_dj_follow_artist),
                checked = AutoDjFollow.ARTIST in config.follow,
                onCheckedChange = { settings.setFollowing(AutoDjFollow.ARTIST, it) },
            )
            SwitchRow(
                icon = Icons.Filled.Style,
                title = stringResource(R.string.auto_dj_follow_genre),
                checked = AutoDjFollow.GENRE in config.follow,
                onCheckedChange = { settings.setFollowing(AutoDjFollow.GENRE, it) },
                hint = stringResource(R.string.auto_dj_follow_genre_hint),
            )
            SwitchRow(
                icon = Icons.Filled.DateRange,
                title = stringResource(R.string.auto_dj_follow_era),
                checked = AutoDjFollow.ERA in config.follow,
                onCheckedChange = { settings.setFollowing(AutoDjFollow.ERA, it) },
                hint = stringResource(R.string.auto_dj_follow_era_hint),
            )
        }
    }
    item(key = "follow-note") { Note(stringResource(R.string.auto_dj_follow_note)) }

    item(key = "filters-label") { GroupLabel(stringResource(R.string.auto_dj_group_filters)) }
    item(key = "filters") {
        SettingsCard {
            GenreFilter(library, filters) { genres -> settings.updateFilters { it.copy(genres = genres) } }
            DecadeFilter(filters) { decades -> settings.updateFilters { it.copy(decades = decades) } }
            ArtistFilter(library, filters) { artists -> settings.updateFilters { it.copy(artists = artists) } }
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
