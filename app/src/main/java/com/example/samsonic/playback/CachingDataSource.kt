package com.example.samsonic.playback

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSource

/**
 * Where the player reads songs from: a server's stream through the music cache
 * (saved as it's read, and read back from it once saved), anything else - the
 * music on this phone - straight from [direct].
 *
 * Only streams with a cache key go through the cache: a stream's URL changes with
 * every request (it carries a fresh auth salt), so the URL itself can't be the key.
 */
@OptIn(UnstableApi::class)
internal class CachingDataSourceFactory(
    cache: Cache,
    upstream: DataSource.Factory,
    private val direct: DataSource.Factory,
) : DataSource.Factory {
    private val cached = cacheDataSourceFactory(cache, upstream)
        // Waits for a part the music prefetcher is saving right now, rather than
        // downloading the same bytes a second time over a poor connection.
        .setFlags(CacheDataSource.FLAG_BLOCK_ON_CACHE or CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

    override fun createDataSource(): DataSource = RoutingDataSource(cached.createDataSource(), direct.createDataSource())
}

/** A cache-backed source, as the player and the music prefetcher both read through one. */
@OptIn(UnstableApi::class)
internal fun cacheDataSourceFactory(cache: Cache, upstream: DataSource.Factory): CacheDataSource.Factory =
    CacheDataSource.Factory()
        .setCache(cache)
        .setUpstreamDataSourceFactory(upstream)

/** Whether [dataSpec] is a server stream the music cache keeps. */
internal fun isCacheable(dataSpec: DataSpec): Boolean =
    dataSpec.key != null && dataSpec.uri.scheme.let { it == "http" || it == "https" }

@OptIn(UnstableApi::class)
private class RoutingDataSource(private val cached: DataSource, private val direct: DataSource) : DataSource {
    private var open: DataSource? = null

    override fun addTransferListener(transferListener: TransferListener) {
        cached.addTransferListener(transferListener)
        direct.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        val source = if (isCacheable(dataSpec)) cached else direct
        open = source
        return source.open(dataSpec)
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
        checkNotNull(open) { "Not open" }.read(buffer, offset, length)

    override fun getUri(): Uri? = open?.uri

    override fun getResponseHeaders(): Map<String, List<String>> = open?.responseHeaders ?: emptyMap()

    override fun close() {
        try {
            open?.close()
        } finally {
            open = null
        }
    }
}
