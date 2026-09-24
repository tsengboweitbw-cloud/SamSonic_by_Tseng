package com.example.samsonic.data

import android.content.Context
import coil3.SingletonImageLoader
import coil3.request.CachePolicy
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.cancellation.CancellationException

// Downloads at once; enough to keep the line busy without crowding out playback.
private const val DOWNLOAD_CONCURRENCY = 4
// The images are only wanted on disk; decoding them this small costs next to nothing.
private const val DECODE_SIZE = 32

sealed interface PrefetchState {
    data object Idle : PrefetchState

    /** Listing the server's albums, songs, artists and playlists. */
    data object Gathering : PrefetchState

    data class Running(val done: Int, val total: Int) : PrefetchState

    data class Finished(val total: Int, val failed: Int) : PrefetchState

    /** The library couldn't be listed (e.g. the server is unreachable). */
    data object Failed : PrefetchState
}

/**
 * Downloads every cover the server has into the disk cache, at each of [CoverArtSizes],
 * so browsing never waits on the network. Runs in the app's scope, so it carries on
 * while the user leaves Settings, until it's done, [cancel]led, or the app is closed.
 * Covers already on disk are skipped, so running it again only fetches what's new.
 */
class CoverArtPrefetcher(
    context: Context,
    private val subsonic: SubsonicRepository,
    private val scope: CoroutineScope,
) {
    private val appContext = context.applicationContext
    private var job: Job? = null

    private val _state = MutableStateFlow<PrefetchState>(PrefetchState.Idle)
    val state: StateFlow<PrefetchState> = _state.asStateFlow()

    fun start() {
        if (job?.isActive == true) return
        job = scope.launch(Dispatchers.IO) { run() }.also { started ->
            // Once every download has stopped, so none reports progress after this.
            started.invokeOnCompletion { cause ->
                if (cause is CancellationException) _state.value = PrefetchState.Idle
            }
        }
    }

    /** Stops the run, if there is one, and forgets the last one's result. */
    fun cancel() {
        val running = job
        job = null
        if (running?.isActive == true) running.cancel() else _state.value = PrefetchState.Idle
    }

    private suspend fun run() {
        _state.value = PrefetchState.Gathering
        val covers = try {
            allCoverArt()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _state.value = PrefetchState.Failed
            return
        }
        val urls = covers.flatMap { id -> CoverArtSizes.All.mapNotNull { subsonic.coverArtUrl(id, it) } }
        val loader = SingletonImageLoader.get(appContext)
        val done = AtomicInteger()
        val failed = AtomicInteger()
        val permits = Semaphore(DOWNLOAD_CONCURRENCY)
        _state.value = PrefetchState.Running(0, urls.size)
        coroutineScope {
            urls.forEach { url ->
                launch {
                    permits.withPermit {
                        if (!isOnDisk(url)) {
                            val request = ImageRequest.Builder(appContext)
                                .data(url)
                                .size(DECODE_SIZE)
                                .memoryCachePolicy(CachePolicy.DISABLED)
                                .build()
                            if (loader.execute(request) is ErrorResult) failed.incrementAndGet()
                        }
                    }
                    _state.value = PrefetchState.Running(done.incrementAndGet(), urls.size)
                }
            }
        }
        _state.value = PrefetchState.Finished(urls.size, failed.get())
    }

    private fun isOnDisk(url: String): Boolean {
        val key = CoverArtCacheKeys.stableKey(url) ?: return false
        val diskCache = SingletonImageLoader.get(appContext).diskCache ?: return false
        return diskCache.openSnapshot(key)?.use { true } ?: false
    }

    /** Every distinct cover id: albums, their songs (which can have their own art), artists and playlists. */
    private suspend fun allCoverArt(): Set<String> = coroutineScope {
        val artists = async { runCatching { subsonic.getArtists() }.getOrDefault(emptyList()) }
        val playlists = async { runCatching { subsonic.getPlaylists() }.getOrDefault(emptyList()) }
        val albums = subsonic.getAllAlbums()
        val songs = subsonic.getAlbumsSongs(albums)
        buildSet {
            albums.forEach { add(it.coverArt) }
            songs.forEach { add(it.coverArt) }
            artists.await().forEach { add(it.coverArt) }
            playlists.await().forEach { add(it.coverArt) }
        }.filterNotNull().filter { it.isNotBlank() }.toSet()
    }
}
