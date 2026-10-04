package com.example.samsonic.data.offline

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheEvictor
import androidx.media3.datasource.cache.CacheSpan
import java.util.TreeSet

/**
 * Media3's least-recently-used cache eviction, except for the songs [pinned] (by cache
 * key) which are never evicted: past [maxBytes] the songs played longest ago go, those kept
 * for offline stay, so the cache can hold more than [maxBytes] by what's kept.
 */
@OptIn(UnstableApi::class)
class PinningCacheEvictor(
    private val maxBytes: Long,
    private val pinned: () -> Set<String>,
) : CacheEvictor {
    private val leastRecentlyUsed = TreeSet<CacheSpan>(::compare)
    private var currentSize = 0L

    override fun requiresCacheSpanTouches() = true

    override fun onCacheInitialized() = Unit

    override fun onStartFile(cache: Cache, key: String, position: Long, length: Long) {
        if (length != C.LENGTH_UNSET.toLong()) evict(cache, length)
    }

    override fun onSpanAdded(cache: Cache, span: CacheSpan) {
        leastRecentlyUsed.add(span)
        currentSize += span.length
        evict(cache, 0)
    }

    override fun onSpanRemoved(cache: Cache, span: CacheSpan) {
        leastRecentlyUsed.remove(span)
        currentSize -= span.length
    }

    override fun onSpanTouched(cache: Cache, oldSpan: CacheSpan, newSpan: CacheSpan) {
        onSpanRemoved(cache, oldSpan)
        onSpanAdded(cache, newSpan)
    }

    private fun evict(cache: Cache, requiredSpace: Long) {
        if (currentSize + requiredSpace <= maxBytes) return
        val keep = pinned()
        while (currentSize + requiredSpace > maxBytes) {
            val victim = leastRecentlyUsed.firstOrNull { it.key !in keep } ?: return
            cache.removeSpan(victim)
        }
    }

    private fun compare(a: CacheSpan, b: CacheSpan): Int {
        val lastTouched = a.lastTouchTimestamp - b.lastTouchTimestamp
        if (lastTouched != 0L) return if (lastTouched < 0) -1 else 1
        if (a.key != b.key) return a.key.compareTo(b.key)
        val position = a.position - b.position
        return if (position == 0L) 0 else if (position < 0) -1 else 1
    }
}
