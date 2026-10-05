package com.example.samsonic.ui.settings

//import androidx.compose.foundation.layout.Arrangement
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.SdStorage
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.semantics.selected
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
//import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.samsonic.BuildConfig
import com.example.samsonic.R
import com.example.samsonic.ui.theme.OneUiRow
import com.example.samsonic.ui.theme.oneUiRowClickable
import com.example.samsonic.ui.common.LocalWindowLayout
import com.example.samsonic.ui.common.PageTitle
import com.example.samsonic.ui.common.TitledPage

@Composable
fun SettingsScreen(
    onAddServer: () -> Unit,
    modifier: Modifier = Modifier,
    contentPaddingBottom: Dp = 0.dp,
) {
    val panels = rememberSettingsPanels()
    // The group picked in two columns, kept here rather than there: folding onto a phone's
    // one column and back, or anything else passing through one column, finds it still picked.
    var selectedGroup by rememberSaveable { mutableIntStateOf(0) }

    // A nav bar tab like Search and Library, so the same fixed large title and no back button.
    TitledPage(
        modifier = modifier,
        title = { PageTitle(stringResource(R.string.settings_title)) },
        overlay = { haze ->
            CompositionLocalProvider(LocalMenuBottomInset provides contentPaddingBottom, LocalMenuMatchesOrigin provides true) {
                SettingsMenus(panels, haze)
            }
        },
    ) { topPadding ->
        val groups = listOf(
            SettingsGroup(R.string.settings_group_music_source, Icons.Filled.Dns) {
                SourcesCard(onAddServer = onAddServer)
            },
            SettingsGroup(R.string.settings_group_playback, Icons.Filled.PlayCircle) { PlaybackSettings(panels) },
            SettingsGroup(R.string.settings_group_library, Icons.AutoMirrored.Filled.LibraryBooks) { LibrarySettings(panels) },
            SettingsGroup(R.string.settings_group_appearance, Icons.Filled.Brush) { AppearanceSettings(panels) },
            SettingsGroup(R.string.settings_group_storage, Icons.Filled.SdStorage) { StorageSettings(panels) },
            SettingsGroup(R.string.settings_group_about, Icons.Filled.Info) {
                SettingsCard {
                    AboutRow()
                }
            },
        )
        // In landscape on a tablet or an open foldable (twoPane): the groups listed at the
        // side, the one picked beside them; else one page of every group in turn.
        if (LocalWindowLayout.current.twoPane) {
            TwoPaneSettings(
                groups,
                selected = selectedGroup,
                onSelect = { selectedGroup = it },
                topPadding = topPadding,
                bottomPadding = contentPaddingBottom,
            )
        } else {
            // Coming from two columns (folding shut), the page opens at the group that was
            // picked; going back (unfolding), the group scrolled to is the one picked.
            val listState = rememberLazyListState(initialFirstVisibleItemIndex = selectedGroup * ItemsPerGroup)
            DisposableEffect(listState) {
                onDispose {
                    // The group a quarter of the way down the page, the one being looked at,
                    // rather than whatever sliver of the one above is still showing at the top.
                    val info = listState.layoutInfo
                    val probe = info.viewportStartOffset + (info.viewportEndOffset - info.viewportStartOffset) / 4
                    val index = info.visibleItemsInfo.firstOrNull { it.offset + it.size > probe }?.index
                        ?: listState.firstVisibleItemIndex
                    selectedGroup = (index / ItemsPerGroup).coerceIn(groups.indices)
                }
            }
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = topPadding, bottom = 24.dp + contentPaddingBottom),
            ) {
                groups.forEach { group ->
                    item { GroupLabel(stringResource(group.label)) }
                    item { group.content() }
                }
                item { Spacer(Modifier.height(8.dp)) }
            }
        }
    }
}


/**
 * The About card's row: the app's name, with its version as a subtitle (the one
 * settings row that has one). Nothing to open, so no tap or press ripple.
 */
@Composable
private fun AboutRow() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // The inset the other rows get from oneUiRowClickable, so its icon lines up with theirs.
            .padding(OneUiRow.Inset)
            .padding(horizontal = 8.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Info, contentDescription = null, tint = rowIconTint(), modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Column {
            Text(text = "SamSonic", style = MaterialTheme.typography.bodyLarge)
            Text(
                text = "v${BuildConfig.VERSION_NAME} • Navidrome/Subsonic",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
