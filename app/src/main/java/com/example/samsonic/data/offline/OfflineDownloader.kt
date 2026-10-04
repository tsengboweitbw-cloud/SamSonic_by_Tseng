package com.example.samsonic.data.offline

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheWriter
import androidx.media3.datasource.cache.ContentMetadata
import com.example.samsonic.R
import com.example.samsonic.data.MusicLibrary
import com.example.samsonic.playback.cacheDataSourceFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * How far saving the songs kept for offline has got: [done] of [total] songs, [current] (the
 * title of the song with id [currentId]) being saved, [currentFraction] of it (0 to 1) there.
 */
data class OfflineProgress(
    val total: Int,
    val done: Int,
    val current: String?,
    val currentId: String? = null,
    val currentFraction: Float = 0f,
)

/** Where a song kept for offline is in being saved, for the mark by its name. */
sealed interface OfflineSongStatus {
    /** Whole on the phone. */
    data object Saved : OfflineSongStatus

    /** Not saved yet, and in the queue (or held until the network allows). */
    data object Waiting : OfflineSongStatus

    /** Being saved now, [fraction] of it (0 to 1) there. */
    data class Saving(val fraction: Float) : OfflineSongStatus

    /** The server refused it; [OfflineDownloader.retry] asks again. */
    data object Refused : OfflineSongStatus
}

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

    // Songs the server refused this launch, left out of the runs after (until [retry]), and whether the lack of room has been said.
    private val refused = HashSet<String>()
    private var toldOutOfSpace = false

    @Volatile
    private var writer: CacheWriter? = null

    private val _progress = MutableStateFlow<OfflineProgress?>(null)
    val progress: StateFlow<OfflineProgress?> = _progress.asStateFlow()

    private val _unsaved = MutableStateFlow<Set<String>>(emptySet())
    private val _refusedIds = MutableStateFlow<Set<String>>(emptySet())

    /** The ids of the songs the server refused since the app started, which [retry] asks for again. */
    val refusedIds: StateFlow<Set<String>> = _refusedIds.asStateFlow()

    /** How far the song [songId] has got, as it changes; each song's mark follows its own. */
    fun statusOf(songId: String): Flow<OfflineSongStatus> = combine(
        _progress.map { p -> if (p?.currentId == songId) p.currentFraction else null }.distinctUntilChanged(),
        _unsaved,
        _refusedIds,
    ) { fraction, unsaved, refusedIds ->
        when {
            songId in refusedIds -> OfflineSongStatus.Refused
            fraction != null -> OfflineSongStatus.Saving(fraction)
            songId in unsaved -> OfflineSongStatus.Waiting
            else -> OfflineSongStatus.Saved
        }
    }.distinctUntilChanged()

    // Called on a system thread: a network that comes back, or turns unmetered, picks saving up again.
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            if (capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) scope.launch { start() }
        }
    }

    init {
        runCatching { connectivity.registerDefaultNetworkCallback(networkCallback) }
        // Turning Wi-Fi only off lets what was held back for it start at once, not at the next network change.
        scope.launch { wifiOnly.drop(1).filter { !it }.collect { start() } }
    }

    /** Saves what's kept and not yet saved, if a run isn't already under way. Call it on the main thread. */
    fun start() {
        if (job?.isActive == true) return
        // Off the main thread: working out what's saved reads the cache's index from disk.
        job = scope.launch(Dispatchers.IO) {
            publishUnsaved()
            if (wifiOnly.value && connectivity.isActiveNetworkMetered) return@launch
            val todo = pending()
            if (todo.isEmpty()) return@launch
            _progress.value = OfflineProgress(total = todo.size, done = 0, current = null)
            // A notice that keeps the app running while it saves, with the app closed; where the
            // system won't allow one from here, it saves while the app is open.
            runCatching { ContextCompat.startForegroundService(context, Intent(context, OfflineDownloadService::class.java)) }
            try {
                // Songs kept or retried while it ran are picked up before it ends.
                var round = todo
                while (run(round)) {
                    round = pending()
                    if (round.isEmpty()) break
                }
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

    /** Saves [todo] in turn. False if it stopped short (the connection dropped, or no room), true if it went through them all. */
    private suspend fun run(todo: List<OfflineEntry>): Boolean {
        val source = cacheDataSourceFactory(cache(), upstreamFactory)
        var done = 0
        for (entry in todo) {
            // Removed from what's kept while this was saving: nothing to do for it.
            if (store.entries.value.none { it.cacheKey == entry.cacheKey }) continue
            val id = entry.song.id
            _progress.value = OfflineProgress(todo.size, done, entry.song.title, currentId = id)
            val uri = library().streamUrl(id)
            val spec = DataSpec.Builder().setUri(uri).setKey(entry.cacheKey).build()
            var shown = 0
            val cacheWriter = CacheWriter(source.createDataSource(), spec, null) { length, cached, _ ->
                // A whole percent at a time, so the marks beside the songs aren't redrawn for every few bytes.
                val percent = if (length > 0) (cached * 100 / length).toInt() else 0
                if (percent != shown) {
                    shown = percent
                    _progress.update { p -> p?.takeIf { it.currentId == id }?.copy(currentFraction = percent / 100f) ?: p }
                }
            }
            writer = cacheWriter
            try {
                cacheWriter.cache()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                when (saveFailureOf(e)) {
                    // One the server won't give out must not hold up the rest; it's tried again by a retry or at the next launch.
                    SaveFailure.SkipSong -> {
                        refused += entry.cacheKey
                        _refusedIds.update { it + id }
                    }
                    // Cancelled, or the connection dropped: carries on from here at the next start.
                    SaveFailure.Stop -> return false
                    SaveFailure.OutOfSpace -> {
                        if (!toldOutOfSpace) {
                            toldOutOfSpace = true
                            withContext(Dispatchers.Main) { Toast.makeText(context, R.string.offline_out_of_space, Toast.LENGTH_LONG).show() }
                        }
                        return false
                    }
                }
                continue
            }
            toldOutOfSpace = false
            done++
            _unsaved.update { it - id }
        }
        return true
    }

    /** Asks the server again for the songs [songIds] it refused (all of them if null), and saves what it now gives. */
    fun retry(songIds: Set<String>? = null) {
        val ids = _refusedIds.value.let { if (songIds == null) it else it.intersect(songIds) }
        if (ids.isEmpty()) return
        val keys = store.entries.value.filter { it.song.id in ids }.mapTo(HashSet()) { it.cacheKey }
        refused -= keys
        _refusedIds.update { it - ids }
        start()
    }

    /** Works out which of the songs kept for the server in use aren't saved whole yet, for the marks. */
    private fun publishUnsaved() {
        _unsaved.value = unsaved().mapTo(HashSet()) { it.song.id }
    }

    private fun unsaved(): List<OfflineEntry> {
        val server = serverKey() ?: return emptyList()
        return store.entries.value.filter { it.serverKey == server && !isComplete(it.cacheKey) }
    }

    /** The songs kept for the server in use and not saved whole yet, bar those the server refused. */
    private fun pending(): List<OfflineEntry> = unsaved().filter { it.cacheKey !in refused }

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
