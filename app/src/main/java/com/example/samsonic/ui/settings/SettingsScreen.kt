package com.example.samsonic.ui.settings

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
//import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import com.example.samsonic.ui.components.TabIndicatorSpring
import com.example.samsonic.ui.components.tabProximity
import com.example.samsonic.ui.theme.OneUiRadius
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.SdStorage
import androidx.compose.material.icons.filled.VerticalAlignCenter
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.RoundedCorner
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material3.Icon
//import androidx.compose.material3.IconButton
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
import com.example.samsonic.ui.common.LocalWindowLayout
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
    val stackChrome by container.themeManager.stackChrome.collectAsStateWithLifecycle()
    val railStaysPut by container.themeManager.railStaysPut.collectAsStateWithLifecycle()
    val stackPlayerActions by container.themeManager.stackPlayerActions.collectAsStateWithLifecycle()
    val autoDjConfig by container.autoDjSettings.config.collectAsStateWithLifecycle()
    val usbDriver by container.usbDacs.enabled.collectAsStateWithLifecycle()
    val nativeDsd by container.usbDacs.nativeDsd.collectAsStateWithLifecycle()
    val audioFormatDisplay by container.themeManager.audioFormatDisplay.collectAsStateWithLifecycle()
    val albumArtistsOnly by container.libraryLayoutManager.albumArtistsOnly.collectAsStateWithLifecycle()
    val showFavourites by container.libraryLayoutManager.showFavourites.collectAsStateWithLifecycle()
    val showAlbumNames by container.libraryLayoutManager.showAlbumNames.collectAsStateWithLifecycle()
    val showAlbumArtists by container.libraryLayoutManager.showAlbumArtists.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val themeMenu = remember { PanelState(scope) }
    val audioFormatMenu = remember { PanelState(scope) }
    val accentMenu = remember { PanelState(scope) }
    val cacheLocationMenu = remember { PanelState(scope) }
    val cacheAllMenu = remember { PanelState(scope) }
    val clearCacheMenu = remember { PanelState(scope) }
    val languageMenu = remember { PanelState(scope) }
    val listActionsMenu = remember { PanelState(scope) }
    val glassMenu = remember { PanelState(scope) }
    val autoDjMenu = remember { PanelState(scope) }
    val cacheUsage = rememberCacheUsage()
    val clearMusicCacheMenu = remember { PanelState(scope) }
    val musicCacheUsage = rememberMusicCacheUsage(container.musicCache)
    val removeOfflineMenu = remember { PanelState(scope) }
    val offlineUsage = remember { OfflineUsage(container.offlineDownloader) }
    val source by container.sources.active.collectAsStateWithLifecycle()
    // The group picked in two columns, kept here rather than there: folding onto a phone's
    // one column and back, or anything else passing through one column, finds it still picked.
    var selectedGroup by rememberSaveable { mutableIntStateOf(0) }

    // A nav bar tab like Search and Library, so the same fixed large title and no back button.
    TitledPage(
        modifier = modifier,
        title = { PageTitle(stringResource(R.string.settings_title)) },
        overlay = { haze -> CompositionLocalProvider(LocalMenuBottomInset provides contentPaddingBottom, LocalMenuMatchesOrigin provides true) {
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
            RemoveOfflineMusicMenu(removeOfflineMenu, haze, container.offlineStore, container.offlineDownloader)
            LanguageMenu(languageMenu, haze)
            ListActionsMenu(listActionsMenu, haze)
            GlassMenu(glassMenu, haze, container.themeManager)
            AutoDjMenu(autoDjMenu, haze)
        } },
    ) { topPadding ->
        val groups = listOf(
            SettingsGroup(R.string.settings_group_music_source, Icons.Filled.Dns) {
                SourcesCard(onAddServer = onAddServer)
            },
            SettingsGroup(R.string.settings_group_playback, Icons.Filled.PlayCircle) {
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
                    // The mini player's swipes, with the rest of how the player behaves.
                    SwipeGestureRows(container.themeManager)
                    SwitchRow(
                        icon = Icons.Filled.Usb,
                        title = stringResource(R.string.settings_usb_driver),
                        checked = usbDriver,
                        onCheckedChange = container.usbDacs::setEnabled,
                        hint = stringResource(R.string.settings_usb_driver_hint),
                    )
                    SwitchRow(
                        icon = Icons.Filled.Waves,
                        title = stringResource(R.string.settings_usb_native_dsd),
                        checked = nativeDsd,
                        onCheckedChange = container.usbDacs::setNativeDsd,
                        hint = stringResource(
                            if (usbDriver) R.string.settings_usb_native_dsd_hint else R.string.settings_usb_native_dsd_needs_driver_hint,
                        ),
                        enabled = usbDriver,
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
                }
            },
            SettingsGroup(R.string.settings_group_library, Icons.AutoMirrored.Filled.LibraryBooks) {
                SettingsCard {
                    // The Artists tab reloads with the other list the next time it shows.
                    SwitchRow(
                        icon = Icons.Filled.Person,
                        title = stringResource(R.string.settings_album_artists_only),
                        checked = albumArtistsOnly,
                        onCheckedChange = container.libraryLayoutManager::setAlbumArtistsOnly,
                        hint = stringResource(R.string.settings_album_artists_only_hint),
                    )
                    SwitchRow(
                        icon = Icons.Filled.Album,
                        title = stringResource(R.string.settings_show_album_names),
                        checked = showAlbumNames,
                        onCheckedChange = container.libraryLayoutManager::setShowAlbumNames,
                        hint = stringResource(R.string.settings_show_album_names_hint),
                    )
                    SwitchRow(
                        icon = Icons.Filled.Person,
                        title = stringResource(R.string.settings_show_album_artists),
                        checked = showAlbumArtists,
                        onCheckedChange = container.libraryLayoutManager::setShowAlbumArtists,
                        hint = stringResource(R.string.settings_show_album_artists_hint),
                    )
                    // Your liked songs, first in the Playlists tab.
                    SwitchRow(
                        icon = Icons.AutoMirrored.Filled.QueueMusic,
                        title = stringResource(R.string.settings_favourites_playlist),
                        checked = showFavourites,
                        onCheckedChange = container.libraryLayoutManager::setShowFavourites,
                        hint = stringResource(R.string.settings_favourites_playlist_hint),
                    )
                    ListActionsRow(listActionsMenu)
                }
            },
            SettingsGroup(R.string.settings_group_appearance, Icons.Filled.Brush) {
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
                    // Only on a phone: wider, the nav bar is a rail at the side, with nothing at
                    // the foot to pile the mini player onto. Kept as set, for back on a phone.
                    val phoneLayout = !LocalWindowLayout.current.usesRail
                    SwitchRow(
                        icon = Icons.Filled.Layers,
                        title = stringResource(R.string.settings_stack_chrome),
                        checked = stackChrome,
                        onCheckedChange = container.themeManager::setStackChrome,
                        hint = stringResource(
                            if (phoneLayout) R.string.settings_stack_chrome_hint else R.string.settings_stack_chrome_phone_only_hint,
                        ),
                        // Shown as it's turned on: it warns of the clash with One-handed mode.
                        hintWhenTurnedOn = true,
                        enabled = phoneLayout,
                    )
                    // Its counterpart on wider screens: whether the rail moves as music starts.
                    SwitchRow(
                        icon = Icons.Filled.VerticalAlignCenter,
                        title = stringResource(R.string.settings_rail_stays_put),
                        checked = railStaysPut,
                        onCheckedChange = container.themeManager::setRailStaysPut,
                        hint = stringResource(
                            if (phoneLayout) R.string.settings_rail_stays_put_wide_only_hint else R.string.settings_rail_stays_put_hint,
                        ),
                        enabled = !phoneLayout,
                    )
                    // Not in DeX, where Now Playing always has a row of buttons for the mouse.
                    val desktop = LocalWindowLayout.current.desktop
                    SwitchRow(
                        icon = Icons.Filled.Style,
                        title = stringResource(R.string.settings_stack_player_actions),
                        checked = stackPlayerActions,
                        onCheckedChange = container.themeManager::setStackPlayerActions,
                        hint = stringResource(
                            if (desktop) R.string.settings_stack_player_actions_dex_hint else R.string.settings_stack_player_actions_hint,
                        ),
                        enabled = !desktop,
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
                    GlassRow(glassMenu)
                }
            },
            SettingsGroup(R.string.settings_group_storage, Icons.Filled.SdStorage) {
                SettingsCard {
                    CacheLocationRow(container.imageCacheSettings, cacheLocationMenu)
                    // Only a server's music streams; the phone's own is on hand already.
                    if (source is ActiveSource.Server) {
                        MusicCacheRows(container.musicCache, musicCacheUsage, clearMusicCacheMenu)
                        OfflineMusicRow(container.offlineStore, container.offlineDownloader, removeOfflineMenu, offlineUsage)
                        OfflineOnlyRow(container.offlineOnly)
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
            },
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

// In one column, each group is two items: its label, then its card.
private const val ItemsPerGroup = 2

/** One group of settings: its name and icon (for the list of groups beside it), and its card. */
private class SettingsGroup(
    @param:StringRes val label: Int,
    val icon: ImageVector,
    val content: @Composable () -> Unit,
)

/**
 * Settings on a wide screen: the groups listed in a card at the side, and the one picked
 * shown beside them, from its top each time another is picked. Picking another, the
 * highlight glides over to it in the list, as the rail's indicator glides between tabs,
 * and its settings rise into place as the last group's fade: up from below for a group
 * further down the list, down from above for one higher up. The pick ([selected],
 * changed by [onSelect]) is kept by the caller, through any change of layout.
 */
@Composable
private fun TwoPaneSettings(
    groups: List<SettingsGroup>,
    selected: Int,
    onSelect: (Int) -> Unit,
    topPadding: Dp,
    bottomPadding: Dp,
) {
    val picked = selected.coerceIn(groups.indices)
    // Where the highlight is, in rows; read only as the list is drawn.
    val highlight = remember { Animatable(picked.toFloat()) }
    LaunchedEffect(picked) { highlight.animateTo(picked.toFloat(), TabIndicatorSpring) }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val listWidth = (maxWidth * 0.38f).coerceIn(GroupListMinWidth, GroupListMaxWidth)
        val padding = PaddingValues(top = topPadding + 12.dp, bottom = 24.dp + bottomPadding)
        Row(Modifier.fillMaxSize()) {
            LazyColumn(modifier = Modifier.width(listWidth).fillMaxHeight(), contentPadding = padding) {
                item {
                    SettingsCard {
                        GroupList(
                            groups = groups,
                            picked = picked,
                            highlight = { highlight.value.coerceIn(0f, groups.lastIndex.toFloat()) },
                            onPick = onSelect,
                        )
                    }
                }
            }
            val shiftPx = with(LocalDensity.current) { GroupShift.roundToPx() }
            AnimatedContent(
                targetState = picked,
                modifier = Modifier.weight(1f).fillMaxHeight(),
                transitionSpec = {
                    // Further down the list: up from below; higher up: down from above.
                    val down = targetState > initialState
                    val enter = slideInVertically(tween(GroupInMillis, easing = GroupEasing)) { if (down) shiftPx else -shiftPx } +
                        fadeIn(tween(GroupInMillis - GroupOutMillis, delayMillis = GroupOutMillis / 2, easing = GroupEasing))
                    val exit = slideOutVertically(tween(GroupOutMillis, easing = GroupEasing)) { if (down) -shiftPx / 2 else shiftPx / 2 } +
                        fadeOut(tween(GroupOutMillis, easing = GroupEasing))
                    (enter togetherWith exit).using(SizeTransform(clip = false))
                },
                label = "settingsGroup",
            ) { group ->
                // Each group its own list, so the one coming in starts at its top.
                LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = padding) {
                    item { groups[group].content() }
                }
            }
        }
    }
}

// The list of groups takes about a third of the width, within these.
private val GroupListMinWidth = 240.dp
private val GroupListMaxWidth = 320.dp

// A group's settings coming in travel this far; the last group's, half as far out.
private val GroupShift = 32.dp
private const val GroupInMillis = 340
private const val GroupOutMillis = 140
private val GroupEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

/**
 * The groups, one row each, over one highlight at [highlight] (in rows, gliding between
 * them), tinting each row's label as it passes. As one row of the card, so its edges are
 * the card's first and last row's.
 */
@Composable
private fun GroupList(groups: List<SettingsGroup>, picked: Int, highlight: () -> Float, onPick: (Int) -> Unit) {
    val wash = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
    Column(
        Modifier
            .fillMaxWidth()
            .cardEdgeInset(top = 6.dp, bottom = 6.dp)
            .drawBehind {
                // The rows are all one height; the highlight is a row's press shape, inset as it is.
                val rowHeight = size.height / groups.size
                val inset = OneUiRow.Inset
                val left = inset.calculateLeftPadding(layoutDirection).toPx()
                val right = inset.calculateRightPadding(layoutDirection).toPx()
                val vertical = inset.calculateTopPadding().toPx()
                val radius = OneUiRadius.Art.toPx()
                drawRoundRect(
                    color = wash,
                    topLeft = Offset(left, highlight() * rowHeight + vertical),
                    size = Size(size.width - left - right, rowHeight - vertical * 2),
                    cornerRadius = CornerRadius(radius),
                )
            },
    ) {
        groups.forEachIndexed { index, group ->
            GroupRow(
                group = group,
                selected = index == picked,
                emphasis = { tabProximity(index, highlight()) },
                onClick = { onPick(index) },
            )
        }
    }
}

/**
 * A group in the list beside the settings: tapped, its settings show. Its label takes the
 * accent as the highlight comes over it ([emphasis], 1 under it, 0 a row away).
 */
@Composable
private fun GroupRow(group: SettingsGroup, selected: Boolean, emphasis: () -> Float, onClick: () -> Unit) {
    val onColor = MaterialTheme.colorScheme.primary
    val restColor = MaterialTheme.colorScheme.onSurface
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .oneUiRowClickable(onClick)
            .semantics { this.selected = selected }
            .padding(horizontal = 8.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(group.icon, contentDescription = null, tint = rowIconTint(), modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        // Coloured as it's drawn, following the highlight without recomposing.
        BasicText(
            text = stringResource(group.label),
            style = MaterialTheme.typography.bodyLarge,
            color = { lerp(restColor, onColor, emphasis()) },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun GroupLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
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
