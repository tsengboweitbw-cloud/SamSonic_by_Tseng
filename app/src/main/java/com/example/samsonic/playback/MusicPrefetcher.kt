package com.example.samsonic.playback

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheWriter
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

// The songs after the current one saved ahead: enough to ride out a long dead spot.
private const val SongsAhead = 2

/**
 * Saves the rest of the playing song and the next [SongsAhead] in the queue (as
 * shuffle and repeat order them) into the music cache, so a poor connection
 * doesn't stop the music: the player reads them back from storage when it gets there.
 *
 * Only while the player isn't loading itself - its own buffer comes first, and on a
 * slow connection the two would just split it - so this pauses the moment the
 * player starts loading and carries on (from where it got to) once it stops.
 *
 * With [wifiOnly] on, only on an unmetered network (Wi-Fi): it stops on moving to
 * mobile data and starts again when Wi-Fi is back.
 */
@OptIn(UnstableApi::class)
internal class MusicPrefetcher(
    private val player: ExoPlayer,
    cache: Cache,
    context: Context,
    upstream: DataSource.Factory,
    private val wifiOnly: StateFlow<Boolean>,
    private val scope: CoroutineScope,
) {
    private val source = cacheDataSourceFactory(cache, upstream)
    private var job: Job? = null
    @Volatile private var writer: CacheWriter? = null
    private val connectivity = context.getSystemService(ConnectivityManager::class.java)
    private var metered = connectivity.isActiveNetworkMetered
    private var network: Network? = null

    // Called on a system thread. Restarts on a new network (so saving picks up again
    // once back online) or one turning metered or back, not on each of the frequent
    // signal-strength updates in between.
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            val nowMetered = !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
            scope.launch {
                if (network == this@MusicPrefetcher.network && nowMetered == metered) return@launch
                this@MusicPrefetcher.network = network
                metered = nowMetered
                restart()
            }
        }
    }

    private val listener = object : Player.Listener {
        override fun onIsLoadingChanged(isLoading: Boolean) = restart()
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) = restart()
        override fun onTimelineChanged(timeline: Timeline, reason: Int) = restart()
        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) = restart()
        override fun onRepeatModeChanged(repeatMode: Int) = restart()
    }

    init {
        player.addListener(listener)
        connectivity.registerDefaultNetworkCallback(networkCallback)
        scope.launch { wifiOnly.drop(1).collect { restart() } }
    }

    fun release() {
        player.removeListener(listener)
        connectivity.unregisterNetworkCallback(networkCallback)
        stop()
    }

    private fun restart() {
        stop()
        if (player.isLoading || player.playbackState == Player.STATE_IDLE) return
        if (wifiOnly.value && metered) return
        val specs = upcoming().mapNotNull { it.cacheSpec() }
        if (specs.isEmpty()) return
        job = scope.launch(Dispatchers.IO) {
            for (spec in specs) {
                val writer = CacheWriter(source.createDataSource(), spec, null, null)
                this@MusicPrefetcher.writer = writer
                try {
                    // Skips what's saved already, so picking up after a pause costs nothing.
                    writer.cache()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // Cancelled, or the connection dropped (or the server said no): try again at the next restart.
                    return@launch
                } finally {
                    this@MusicPrefetcher.writer = null
                }
            }
        }
    }

    private fun stop() {
        writer?.cancel()
        job?.cancel()
        job = null
    }

    /** The playing song and the ones after it, in the order they'll play. */
    private fun upcoming(): List<MediaItem> {
        val timeline = player.currentTimeline
        if (timeline.isEmpty) return emptyList()
        val items = mutableListOf<MediaItem>()
        var index = player.currentMediaItemIndex
        // Repeat one would only find the same song again.
        val repeat = if (player.repeatMode == Player.REPEAT_MODE_ONE) Player.REPEAT_MODE_OFF else player.repeatMode
        while (index != C.INDEX_UNSET && items.size <= SongsAhead) {
            val item = player.getMediaItemAt(index)
            if (items.none { it.mediaId == item.mediaId }) items += item
            index = timeline.getNextWindowIndex(index, repeat, player.shuffleModeEnabled)
            if (index == player.currentMediaItemIndex) break
        }
        return items
    }

    private fun MediaItem.cacheSpec(): DataSpec? {
        val config = localConfiguration ?: return null
        val spec = DataSpec.Builder().setUri(config.uri).setKey(config.customCacheKey).build()
        return spec.takeIf(::isCacheable)
    }
}
