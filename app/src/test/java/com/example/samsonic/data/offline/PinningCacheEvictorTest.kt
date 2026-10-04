package com.example.samsonic.data.offline

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheSpan
import java.lang.reflect.Proxy
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(UnstableApi::class)
class PinningCacheEvictorTest {
    private val pinned = mutableSetOf<String>()
    private var clock = 0L

    /** A cache holding spans, which asks [evictor] to make room and tells it of what it removes. */
    private inner class FakeCache(max: Long) {
        val evictor = PinningCacheEvictor(max) { pinned }
        val spans = mutableListOf<CacheSpan>()
        private val cache: Cache = Proxy.newProxyInstance(Cache::class.java.classLoader, arrayOf(Cache::class.java)) { _, method, args ->
            when (method.name) {
                "removeSpan" -> {
                    val span = args[0] as CacheSpan
                    spans -= span
                    evictor.onSpanRemoved(proxyCache(), span)
                    null
                }
                else -> error("unexpected ${method.name}")
            }
        } as Cache

        private fun proxyCache() = cache

        /** Saves [size] bytes under [key]. */
        fun save(key: String, size: Long) {
            val span = CacheSpan(key, 0, size, ++clock, null)
            spans += span
            evictor.onSpanAdded(cache, span)
        }

        /** [key] is played again: its span is replaced by a newer one. */
        fun touch(key: String) {
            val old = spans.first { it.key == key }
            val touched = CacheSpan(key, 0, old.length, ++clock, null)
            spans -= old
            spans += touched
            evictor.onSpanTouched(cache, old, touched)
        }

        /** A song of [length] bytes is about to be saved: room is made first. */
        fun startFile(key: String, length: Long) = evictor.onStartFile(cache, key, 0, length)

        val keys get() = spans.map { it.key }.toSet()
    }

    @Test
    fun theSongsPlayedLongestAgoGoFirstPastTheLimit() {
        val cache = FakeCache(max = 250)
        cache.save("old", 100)
        cache.save("middle", 100)
        cache.save("new", 100)
        assertEquals(setOf("middle", "new"), cache.keys)
    }

    @Test
    fun aKeptSongIsNeverEvictedHoweverOldItIs() {
        val cache = FakeCache(max = 250)
        pinned += "old"
        cache.save("old", 100)
        cache.save("middle", 100)
        cache.save("new", 100)
        assertEquals(setOf("old", "new"), cache.keys)
    }

    @Test
    fun theCacheMayGrowPastTheLimitByWhatIsKept() {
        val cache = FakeCache(max = 100)
        pinned += setOf("a", "b", "c")
        cache.save("a", 100)
        cache.save("b", 100)
        cache.save("c", 100)
        assertEquals(setOf("a", "b", "c"), cache.keys)
    }

    @Test
    fun withNothingKeptItIsThePlainLeastRecentlyUsedOne() {
        val cache = FakeCache(max = 150)
        cache.save("a", 100)
        cache.save("b", 100)
        assertEquals(setOf("b"), cache.keys)
    }

    @Test
    fun aSongTouchedAgainIsNotTheOldestAnyMore() {
        val cache = FakeCache(max = 250)
        cache.save("a", 100)
        cache.save("b", 100)
        cache.touch("a")
        cache.save("c", 100)
        assertEquals(setOf("a", "c"), cache.keys)
    }

    @Test
    fun makingRoomForASongAboutToBeSavedRespectsWhatIsKept() {
        val cache = FakeCache(max = 200)
        pinned += "old"
        cache.save("old", 100)
        cache.save("middle", 100)
        // 100 more are about to come in: the one not kept goes, the kept one stays.
        cache.startFile("new", length = 100)
        assertEquals(setOf("old"), cache.keys)
    }
}
