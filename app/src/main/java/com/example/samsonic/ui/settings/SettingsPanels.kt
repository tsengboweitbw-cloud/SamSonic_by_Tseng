package com.example.samsonic.ui.settings

//import androidx.compose.foundation.layout.Arrangement
import dev.chrisbanes.haze.HazeState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
//import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.samsonic.LocalAppContainer
import com.example.samsonic.ui.autodj.AutoDjMenu
import com.example.samsonic.data.ThemeManager
import com.example.samsonic.ui.player.PanelState

/**
 * The panels the settings open (menus growing out of their rows) and the usage figures
 * their rows and menus share, made once for the page and handed to each group.
 */
internal class SettingsPanels(
    val themeMenu: PanelState,
    val audioFormatMenu: PanelState,
    val accentMenu: PanelState,
    val cacheLocationMenu: PanelState,
    val cacheAllMenu: PanelState,
    val clearCacheMenu: PanelState,
    val languageMenu: PanelState,
    val listActionsMenu: PanelState,
    val glassMenu: PanelState,
    val autoDjMenu: PanelState,
    val clearMusicCacheMenu: PanelState,
    val removeOfflineMenu: PanelState,
    val cacheUsage: CacheUsage,
    val musicCacheUsage: MusicCacheUsage,
    val offlineUsage: OfflineUsage,
)

@Composable
internal fun rememberSettingsPanels(): SettingsPanels {
    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    val cacheUsage = rememberCacheUsage()
    val musicCacheUsage = rememberMusicCacheUsage(container.musicCache)
    val offlineUsage = remember { OfflineUsage(container.offlineDownloader) }
    return remember {
        SettingsPanels(
            themeMenu = PanelState(scope),
            audioFormatMenu = PanelState(scope),
            accentMenu = PanelState(scope),
            cacheLocationMenu = PanelState(scope),
            cacheAllMenu = PanelState(scope),
            clearCacheMenu = PanelState(scope),
            languageMenu = PanelState(scope),
            listActionsMenu = PanelState(scope),
            glassMenu = PanelState(scope),
            autoDjMenu = PanelState(scope),
            clearMusicCacheMenu = PanelState(scope),
            removeOfflineMenu = PanelState(scope),
            cacheUsage = cacheUsage,
            musicCacheUsage = musicCacheUsage,
            offlineUsage = offlineUsage,
        )
    }
}

/** Every menu the settings open, over the page (so they can blur it through [haze]). */
@Composable
internal fun SettingsMenus(panels: SettingsPanels, haze: HazeState) {
    val container = LocalAppContainer.current
    val themeMode by container.themeManager.themeMode.collectAsStateWithLifecycle()
    val accentColor by container.themeManager.accentColor.collectAsStateWithLifecycle()
    val audioFormatDisplay by container.themeManager.audioFormatDisplay.collectAsStateWithLifecycle()
    ThemeMenu(panels.themeMenu, haze, current = themeMode, onSelect = container.themeManager::setThemeMode)
    AudioFormatMenu(
        panels.audioFormatMenu,
        haze,
        current = audioFormatDisplay,
        onSelect = container.themeManager::setAudioFormatDisplay,
    )
    AccentColorMenu(
        panels.accentMenu,
        haze,
        initialColor = accentColor,
        defaultColor = ThemeManager.DefaultAccent,
        onConfirm = container.themeManager::setAccentColor,
    )
    CacheLocationMenu(panels.cacheLocationMenu, haze, container.imageCacheSettings)
    CacheAllMenu(panels.cacheAllMenu, haze, container.imageCacheSettings, onConfirm = container.coverArtPrefetcher::start)
    ClearCacheMenu(panels.clearCacheMenu, haze, panels.cacheUsage, container.coverArtPrefetcher)
    ClearMusicCacheMenu(panels.clearMusicCacheMenu, haze, panels.musicCacheUsage)
    RemoveOfflineMusicMenu(panels.removeOfflineMenu, haze, container.offlineStore, container.offlineDownloader)
    LanguageMenu(panels.languageMenu, haze)
    ListActionsMenu(panels.listActionsMenu, haze)
    GlassMenu(panels.glassMenu, haze, container.themeManager)
    AutoDjMenu(panels.autoDjMenu, haze)
}
