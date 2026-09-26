package com.example.samsonic.ui.settings

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.RoundedCorner
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.samsonic.BuildConfig
import com.example.samsonic.LocalAppContainer
import com.example.samsonic.R
import com.example.samsonic.data.ActiveSource
import com.example.samsonic.data.AutoDjMode
import com.example.samsonic.ui.autodj.AutoDjIcon
import com.example.samsonic.ui.autodj.AutoDjMenu
import com.example.samsonic.ui.autodj.autoDjModeLabel
import com.example.samsonic.data.ThemeManager
import com.example.samsonic.playback.LocalPlayerState
import com.example.samsonic.ui.player.PanelState
import com.example.samsonic.ui.theme.OneUiRow
import com.example.samsonic.ui.theme.oneUiRowClickable
import com.example.samsonic.ui.theme.toHexRgb
import com.example.samsonic.ui.common.PageTitle
import com.example.samsonic.ui.common.TitledPage
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    onAddServer: () -> Unit,
    modifier: Modifier = Modifier,
    contentPaddingBottom: Dp = 0.dp,
) {
    val player = LocalPlayerState.current
    val container = LocalAppContainer.current
    val themeMode by container.themeManager.themeMode.collectAsStateWithLifecycle()
    val accentColor by container.themeManager.accentColor.collectAsStateWithLifecycle()
    val albumArtCornerRadius by container.themeManager.albumArtCornerRadius.collectAsStateWithLifecycle()
    val likesEnabled by container.themeManager.likesEnabled.collectAsStateWithLifecycle()
    val autoDjConfig by container.autoDjSettings.config.collectAsStateWithLifecycle()
    val audioFormatDisplay by container.themeManager.audioFormatDisplay.collectAsStateWithLifecycle()
    val albumArtistsOnly by container.libraryLayoutManager.albumArtistsOnly.collectAsStateWithLifecycle()
    val showFavourites by container.libraryLayoutManager.showFavourites.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val themeMenu = remember { PanelState(scope) }
    val audioFormatMenu = remember { PanelState(scope) }
    val accentMenu = remember { PanelState(scope) }
    val cacheLocationMenu = remember { PanelState(scope) }
    val cacheAllMenu = remember { PanelState(scope) }
    val clearCacheMenu = remember { PanelState(scope) }
    val dsdMenu = remember { PanelState(scope) }
    val languageMenu = remember { PanelState(scope) }
    val autoDjMenu = remember { PanelState(scope) }
    val cacheUsage = rememberCacheUsage()
    val clearMusicCacheMenu = remember { PanelState(scope) }
    val musicCacheUsage = rememberMusicCacheUsage(container.musicCache)
    val source by container.sources.active.collectAsStateWithLifecycle()

    // A nav bar tab like Search and Library, so the same fixed large title and no back button.
    TitledPage(
        modifier = modifier,
        title = { PageTitle(stringResource(R.string.settings_title)) },
        overlay = { haze -> CompositionLocalProvider(LocalMenuBottomInset provides contentPaddingBottom) {
            ThemeMenu(themeMenu, haze, current = themeMode, onSelect = container.themeManager::setThemeMode)
            AudioFormatMenu(
                audioFormatMenu,
                haze,
                current = audioFormatDisplay,
                onSelect = container.themeManager::setAudioFormatDisplay,
            )
            AccentColorMenu(
                accentMenu,
                haze,
                initialColor = accentColor,
                defaultColor = ThemeManager.DefaultAccent,
                onConfirm = container.themeManager::setAccentColor,
            )
            CacheLocationMenu(cacheLocationMenu, haze, container.imageCacheSettings)
            CacheAllMenu(cacheAllMenu, haze, container.imageCacheSettings, onConfirm = container.coverArtPrefetcher::start)
            ClearCacheMenu(clearCacheMenu, haze, cacheUsage, container.coverArtPrefetcher)
            ClearMusicCacheMenu(clearMusicCacheMenu, haze, musicCacheUsage)
            DsdOutputMenu(dsdMenu, haze, container.bitPerfect)
            LanguageMenu(languageMenu, haze)
            AutoDjMenu(autoDjMenu, haze)
        } },
    ) { topPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = topPadding, bottom = 24.dp + contentPaddingBottom),
        ) {
            item { GroupLabel(stringResource(R.string.settings_group_music_source)) }
            item { SourcesCard(onAddServer = onAddServer) }

            item { GroupLabel(stringResource(R.string.settings_group_playback)) }
            item {
                SettingsCard {
                    SleepTimerRow(player)
                    NavRow(
                        icon = AutoDjIcon,
                        title = stringResource(R.string.auto_dj_title),
                        value = autoDjConfig.mode.let { mode ->
                            val label = autoDjModeLabel(mode)
                            val filters = autoDjConfig.filters.count
                            if (mode == AutoDjMode.OFF || filters == 0) label
                            else pluralStringResource(R.plurals.auto_dj_filter_count, filters, label, filters)
                        },
                        hint = stringResource(R.string.auto_dj_hint),
                        onClick = { autoDjMenu.open() },
                        modifier = Modifier.menuOrigin(autoDjMenu),
                    )
                    SwitchRow(
                        icon = Icons.Filled.Favorite,
                        title = stringResource(R.string.settings_like_button),
                        checked = likesEnabled,
                        onCheckedChange = container.themeManager::setLikesEnabled,
                        hint = stringResource(R.string.settings_like_button_hint),
                    )
                    // How every song list shows each song's format, or not at all.
                    NavRow(
                        icon = Icons.Filled.GraphicEq,
                        title = stringResource(R.string.settings_audio_format),
                        value = audioFormatDisplay.label,
                        hint = stringResource(R.string.settings_audio_format_hint),
                        onClick = { audioFormatMenu.open() },
                        modifier = Modifier.menuOrigin(audioFormatMenu),
                    )
                    BitPerfectRow(container.bitPerfect)
                    DsdOutputRow(container.bitPerfect, dsdMenu)
                }
            }

            item { GroupLabel(stringResource(R.string.settings_group_gestures)) }
            item { SettingsCard { SwipeGestureRows(container.themeManager) } }

            item { GroupLabel(stringResource(R.string.settings_group_library)) }
            item {
                SettingsCard {
                    // The Artists tab reloads with the other list the next time it shows.
                    SwitchRow(
                        icon = Icons.Filled.Person,
                        title = stringResource(R.string.settings_album_artists_only),
                        checked = albumArtistsOnly,
                        onCheckedChange = container.libraryLayoutManager::setAlbumArtistsOnly,
                        hint = stringResource(R.string.settings_album_artists_only_hint),
                    )
                    // Your liked songs, first in the Playlists tab.
                    SwitchRow(
                        icon = Icons.AutoMirrored.Filled.QueueMusic,
                        title = stringResource(R.string.settings_favourites_playlist),
                        checked = showFavourites,
                        onCheckedChange = container.libraryLayoutManager::setShowFavourites,
                        hint = stringResource(R.string.settings_favourites_playlist_hint),
                    )
                }
            }

            item { GroupLabel(stringResource(R.string.settings_group_appearance)) }
            item {
                SettingsCard {
                    LanguageRow(languageMenu)
                    NavRow(
                        icon = Icons.Filled.DarkMode,
                        title = stringResource(R.string.settings_theme),
                        value = themeMode.label,
                        hint = stringResource(R.string.settings_theme_hint),
                        onClick = { themeMenu.open() },
                        modifier = Modifier.menuOrigin(themeMenu),
                    )
                    NavRow(
                        icon = Icons.Filled.Palette,
                        title = stringResource(R.string.settings_accent_color),
                        value = "#${accentColor.toHexRgb()}",
                        hint = stringResource(R.string.settings_accent_color_hint),
                        onClick = { accentMenu.open() },
                        modifier = Modifier.menuOrigin(accentMenu),
                    )
                    SliderRow(
                        icon = Icons.Filled.RoundedCorner,
                        title = stringResource(R.string.settings_album_art_roundness),
                        hint = stringResource(R.string.settings_album_art_roundness_hint),
                        valueLabel = "${albumArtCornerRadius.value.roundToInt()}dp",
                        value = albumArtCornerRadius.value,
                        valueRange = 0f..48f,
                        onValueChange = { container.themeManager.setAlbumArtCornerRadius(it.dp) },
                    )
                    GlassSliderRows(container.themeManager)
                }
            }

            item { GroupLabel(stringResource(R.string.settings_group_storage)) }
            item {
                SettingsCard {
                    CacheLocationRow(container.imageCacheSettings, cacheLocationMenu)
                    // Only a server's music streams; the phone's own is on hand already.
                    if (source is ActiveSource.Server) {
                        MusicCacheRows(container.musicCache, musicCacheUsage, clearMusicCacheMenu)
                    }
                    ImageCacheRows(
                        settings = container.imageCacheSettings,
                        prefetcher = container.coverArtPrefetcher,
                        cacheAllMenu = cacheAllMenu,
                        usage = cacheUsage,
                        clearMenu = clearCacheMenu,
                        // The music on this phone has its art on hand already.
                        canCacheAll = source is ActiveSource.Server,
                    )
                }
            }

            item { GroupLabel(stringResource(R.string.settings_group_about)) }
            item {
                SettingsCard {
                    AboutRow()
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
internal fun GroupLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 24.dp, top = 20.dp, bottom = 8.dp),
    )
}

/**
 * A row that opens a menu, showing the current [value] before its chevron. The title
 * always keeps its one line; a value too long for the space left ellipsizes instead.
 */
@Composable
internal fun NavRow(
    icon: ImageVector,
    title: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    // Overrides both the icon's accent and the title's usual color.
    tint: androidx.compose.ui.graphics.Color? = null,
    // Shown on a long press (see RowHint).
    hint: String? = null,
    // Not enabled: dimmed, and a tap shows the hint (which should say why) instead of [onClick].
    enabled: Boolean = true,
) {
    val hintState = rememberRowHint()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .hintHold(hintState)
            .then(if (enabled) Modifier else Modifier.semantics { disabled() })
            .oneUiRowClickable(if (enabled) onClick else hintState::show, onLongClick = hintLongPress(hintState, hint))
            .padding(horizontal = 8.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val dim = if (enabled) Modifier else Modifier.alpha(DisabledAlpha)
        Icon(icon, contentDescription = null, tint = tint ?: rowIconTint(), modifier = dim.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = tint ?: MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            modifier = dim,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.End,
            modifier = dim.weight(1f).padding(start = 12.dp),
        )
        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = dim)
        RowHint(hintState, hint)
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
