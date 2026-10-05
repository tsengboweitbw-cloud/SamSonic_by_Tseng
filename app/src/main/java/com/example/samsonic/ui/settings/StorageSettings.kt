package com.example.samsonic.ui.settings

//import androidx.compose.foundation.layout.Arrangement
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
//import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.samsonic.LocalAppContainer
import com.example.samsonic.data.ActiveSource

/** The Storage group: the caches, and the music kept for offline. */
@Composable
internal fun StorageSettings(panels: SettingsPanels) {
    val container = LocalAppContainer.current
    val source by container.sources.active.collectAsStateWithLifecycle()
    val cacheLocationMenu = panels.cacheLocationMenu
    val cacheAllMenu = panels.cacheAllMenu
    val clearCacheMenu = panels.clearCacheMenu
    val clearMusicCacheMenu = panels.clearMusicCacheMenu
    val removeOfflineMenu = panels.removeOfflineMenu
    val cacheUsage = panels.cacheUsage
    val musicCacheUsage = panels.musicCacheUsage
    val offlineUsage = panels.offlineUsage
    SettingsCard {
        CacheLocationRow(container.imageCacheSettings, cacheLocationMenu)
        // Only a server's music streams; the phone's own is on hand already.
        if (source is ActiveSource.Server) {
            MusicCacheRows(container.musicCache, musicCacheUsage, clearMusicCacheMenu)
            OfflineMusicRow(container.offlineStore, container.offlineDownloader, removeOfflineMenu, offlineUsage)
            OfflineRefusedRow(container.offlineStore, container.offlineDownloader)
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
}
