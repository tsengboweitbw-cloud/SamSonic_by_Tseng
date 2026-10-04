package com.example.samsonic.data.offline

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.annotation.OptIn
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheWriter
import androidx.media3.datasource.cache.ContentMetadata
import com.example.samsonic.data.MusicLibrary
import com.example.samsonic.playback.cacheDataSourceFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** How far saving the songs kept for offline has got: [done] of [total] songs, [current] being saved. */
data class OfflineProgress(val total: Int, val done: Int, val current: String?)

/**
 * Saves every song kept for offline ([OfflineStore]) whole into the music cache, one after
 * another, from the server in use. Those already saved cost nothing. A song that can't be
 * fetched (the connection dropped, the server said no) stops the run, which starts again
 * from where it was the next time [start] is called, or when the network changes.
 * With [wifiOnly] on, only on an unmetered network.
 *
 * Runs in a foreground service ([OfflineDownloadService]) so it carries on with the app
 * closed; [progress] is what that shows, null when nothing is being saved.
 */
@OptIn(UnstableApi::class)
class OfflineDownloader(
    private val context: Context,
    private val cache: () -> Cache,
    upstream: DataSource.Factory,
    private val store: OfflineStore,
    // The library in use, for the address of each song's stream (it changes with each request).
    private val library: () -> MusicLibrary,
    private val serverKey: () -> String?,
    private val wifiOnly: StateFlow<Boolean>,
    private val scope: CoroutineScope,
) {
    private val upstreamFactory = upstream
    private val connectivity = context.getSystemService(ConnectivityManager::class.java)
    private var job: Job? = null

    @Volatile
    private var writer: CacheWriter? = null

    private val _progress = MutableStateFlow<OfflineProgress?>(null)
    val progress: StateFlow<OfflineProgress?> = _progress.asStateFlow()

    // Called on a system thread: a network that comes back, or turns unmetered, picks saving up again.
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            if (capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) scope.launch { start() }
        }
    }

    init {
        runCatching { connectivity.registerDefaultNetworkCallback(networkCallback) }
    }

    /** Saves what's kept and not yet saved, if a run isn't already under way. Call it on the main thread. */
    fun start() {
        if (job?.isActive == true) return
        // Off the main thread: working out what's saved reads the cache's index from disk.
        job = scope.launch(Dispatchers.IO) {
            if (wifiOnly.value && connectivity.isActiveNetworkMetered) return@launch
            val todo = pending()
            if (todo.isEmpty()) return@launch
            _progress.value = OfflineProgress(total = todo.size, done = 0, current = null)
            // A notice that keeps the app running while it saves, with the app closed; where the
            // system won't allow one from here, it saves while the app is open.
            runCatching { ContextCompat.startForegroundService(context, Intent(context, OfflineDownloadService::class.java)) }
            try {
                run(todo)
            } finally {
                _progress.value = null
                writer = null
            }
        }
    }

    /** Stops saving, keeping what's saved. */
    fun cancel() {
        writer?.cancel()
        job?.cancel()
        job = null
        _progress.value = null
    }

    private suspend fun run(todo: List<OfflineEntry>) {
        val source = cacheDataSourceFactory(cache(), upstreamFactory)
        var done = 0
        for (entry in todo) {
            // Removed from what's kept while this was saving: nothing to do for it.
            if (store.entries.value.none { it.cacheKey == entry.cacheKey }) continue
            _progress.value = OfflineProgress(todo.size, done, entry.song.title)
            val uri = library().streamUrl(entry.song.id)
            val spec = DataSpec.Builder().setUri(uri).setKey(entry.cacheKey).build()
            val cacheWriter = CacheWriter(source.createDataSource(), spec, null, null)
            writer = cacheWriter
            try {
                cacheWriter.cache()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Cancelled, or the connection dropped: carries on from here at the next start.
                return
            }
            done++
        }
    }

    /** The songs kept for the server in use and not saved whole yet. */
    private fun pending(): List<OfflineEntry> {
        val server = serverKey() ?: return emptyList()
        return store.entries.value.filter { it.serverKey == server && !isComplete(it.cacheKey) }
    }

    /** Whether the cache holds every byte of the song under [key]. */
    fun isComplete(key: String): Boolean {
        val cache = cache()
        val length = ContentMetadata.getContentLength(cache.getContentMetadata(key))
        return length != C.LENGTH_UNSET.toLong() && cache.isCached(key, 0, length)
    }

    /** How many bytes of the songs kept are saved. */
    fun savedBytes(): Long {
        val cache = cache()
        return store.cacheKeys().sumOf { key ->
            val length = ContentMetadata.getContentLength(cache.getContentMetadata(key))
            if (length == C.LENGTH_UNSET.toLong()) 0L else cache.getCachedBytes(key, 0, length)
        }
    }
}
