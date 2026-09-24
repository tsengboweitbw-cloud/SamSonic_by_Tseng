package com.example.samsonic.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import coil3.SingletonImageLoader
import coil3.compose.LocalPlatformContext
import coil3.disk.DiskCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * How much the cover art disk cache holds, shared by the Clear row that shows it and
 * the menu that confirms clearing it. Both read and clear do disk work, so off the
 * main thread.
 */
@Stable
internal class CacheUsage(private val diskCache: DiskCache?, private val scope: CoroutineScope) {
    /** Null while it's being measured or cleared. */
    var usedBytes by mutableStateOf<Long?>(null)
        private set

    private var clearing = false

    suspend fun refresh() {
        if (clearing) return
        val size = withContext(Dispatchers.IO) { diskCache?.size ?: 0L }
        if (!clearing) usedBytes = size
    }

    /** Disk only: art already on screen stays put rather than reloading. */
    fun clear() {
        if (clearing) return
        clearing = true
        usedBytes = null
        scope.launch {
            usedBytes = withContext(Dispatchers.IO) {
                diskCache?.clear()
                diskCache?.size ?: 0L
            }
            clearing = false
        }
    }
}

@Composable
internal fun rememberCacheUsage(): CacheUsage {
    val diskCache = SingletonImageLoader.get(LocalPlatformContext.current).diskCache
    val scope = rememberCoroutineScope()
    return remember(diskCache) { CacheUsage(diskCache, scope) }
}
