package com.example.samsonic.ui.settings

import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.samsonic.BuildConfig
import com.example.samsonic.LocalAppContainer
import com.example.samsonic.data.ActiveSource
import com.example.samsonic.data.ThemeManager
import com.example.samsonic.playback.LocalPlayerState
import com.example.samsonic.ui.player.PanelState
import com.example.samsonic.ui.theme.oneUiRowClickable
import com.example.samsonic.ui.theme.toHexRgb
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
    val cacheUsage = rememberCacheUsage()
    val clearMusicCacheMenu = remember { PanelState(scope) }
    val musicCacheUsage = rememberMusicCacheUsage(container.musicCache)
    val source by container.sources.active.collectAsStateWithLifecycle()

    // A nav bar tab like Search and Library, so the same fixed large title and no back button.
    TitledPage(
        modifier = modifier,
        title = {
            Text(
                text = "Settings",
                style = MaterialTheme.typography.displaySmall,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
            )
        },
        overlay = { haze ->
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
        },
    ) { topPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = topPadding, bottom = 24.dp + contentPaddingBottom),
        ) {
            item { GroupLabel("Music source") }
            item { SourcesCard(onAddServer = onAddServer) }

            item { GroupLabel("Playback") }
            item {
                SettingsCard {
                    SleepTimerRow(player)
                    SwitchRow(
                        icon = Icons.Filled.Favorite,
                        title = "Like button",
                        checked = likesEnabled,
                        onCheckedChange = container.themeManager::setLikesEnabled,
                        hint = "A heart button for liking songs",
                    )
                    // How every song list shows each song's format, or not at all.
                    NavRow(
                        icon = Icons.Filled.GraphicEq,
                        title = "Audio format",
                        value = audioFormatDisplay.label,
                        hint = "How song lists show each song's format",
                        onClick = { audioFormatMenu.open() },
                        modifier = Modifier.menuOrigin(audioFormatMenu),
                    )
                    BitPerfectRow(container.bitPerfect)
                    DsdOutputRow(container.bitPerfect, dsdMenu)
                }
            }

            item { GroupLabel("Now Playing gestures") }
            item { SettingsCard { SwipeGestureRows(container.themeManager) } }

            item { GroupLabel("Library") }
            item {
                SettingsCard {
                    // The Artists tab reloads with the other list the next time it shows.
                    SwitchRow(
                        icon = Icons.Filled.Person,
                        title = "Album artists only",
                        checked = albumArtistsOnly,
                        onCheckedChange = container.libraryLayoutManager::setAlbumArtistsOnly,
                        hint = "The Artists tab lists album artists, not everyone credited on a song",
                    )
                    // Your liked songs, first in the Playlists tab.
                    SwitchRow(
                        icon = Icons.AutoMirrored.Filled.QueueMusic,
                        title = "Favourites playlist",
                        checked = showFavourites,
                        onCheckedChange = container.libraryLayoutManager::setShowFavourites,
                        hint = "Your liked songs, first in the Playlists tab",
                    )
                }
            }

            item { GroupLabel("Appearance") }
            item {
                SettingsCard {
                    NavRow(
                        icon = Icons.Filled.DarkMode,
                        title = "Theme",
                        value = themeMode.label,
                        hint = "Light, dark, or the same as the phone",
                        onClick = { themeMenu.open() },
                        modifier = Modifier.menuOrigin(themeMenu),
                    )
                    NavRow(
                        icon = Icons.Filled.Palette,
                        title = "Accent color",
                        value = "#${accentColor.toHexRgb()}",
                        hint = "The color the app is tinted with, and the ones picked to go with it",
                        onClick = { accentMenu.open() },
                        modifier = Modifier.menuOrigin(accentMenu),
                    )
                    SliderRow(
                        icon = Icons.Filled.RoundedCorner,
                        title = "Album art roundness",
                        hint = "How round the corners of covers are",
                        valueLabel = "${albumArtCornerRadius.value.roundToInt()}dp",
                        value = albumArtCornerRadius.value,
                        valueRange = 0f..48f,
                        onValueChange = { container.themeManager.setAlbumArtCornerRadius(it.dp) },
                    )
                    GlassSliderRows(container.themeManager)
                }
            }

            item { GroupLabel("Storage") }
            item {
                SettingsCard {
                    // Only a server's music streams; the phone's own is on hand already.
                    if (source is ActiveSource.Server) {
                        MusicCacheRows(container.musicCache, musicCacheUsage, clearMusicCacheMenu)
                    }
                    ImageCacheRows(
                        settings = container.imageCacheSettings,
                        prefetcher = container.coverArtPrefetcher,
                        locationMenu = cacheLocationMenu,
                        cacheAllMenu = cacheAllMenu,
                        usage = cacheUsage,
                        clearMenu = clearCacheMenu,
                        // The music on this phone has its art on hand already.
                        canCacheAll = source is ActiveSource.Server,
                    )
                }
            }

            item { GroupLabel("About") }
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
private fun GroupLabel(text: String) {
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

/** The About card's row: the app's name, and its version on a long press. */
@Composable
private fun AboutRow() {
    val hint = "v${BuildConfig.VERSION_NAME} • Navidrome/Subsonic"
    val hintState = rememberRowHint()
    val showHint = hintLongPress(hintState, hint)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .hintHold(hintState)
            // Nothing to open, so no tap (or press ripple): only the long press.
            .pointerInput(showHint) { detectTapGestures(onLongPress = { showHint?.invoke() }) }
            .padding(horizontal = 8.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Info, contentDescription = null, tint = rowIconTint(), modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Text(text = "SamSonic", style = MaterialTheme.typography.bodyLarge)
        RowHint(hintState, hint)
    }
}
