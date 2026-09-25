package com.example.samsonic.data

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The on-device cache of streamed music: every song played from a server is kept
 * as it's heard, and the player saves the next ones ahead (see MusicPrefetcher), so
 * a poor connection doesn't stop the music and a song heard before needs none.
 * Music on the phone itself is never cached.
 *
 * Its size limit is one of [ImageCacheSettings.Steps]; past it, the songs played
 * longest ago make room. It lives where the cover art cache does ([locations]).
 * Media3's cache is built once with its folder and limit, so a new one applies from
 * the next app start; [activeStep] is the one in use until then.
 */
@OptIn(UnstableApi::class)
class MusicCache(context: Context, private val locations: ImageCacheSettings) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("samsonic_cache", Context.MODE_PRIVATE)
    private val databaseProvider by lazy { StandaloneDatabaseProvider(appContext) }

    private val _maxSizeStep = MutableStateFlow(
        prefs.getInt(KEY_MAX_SIZE_STEP, DefaultStep).coerceIn(ImageCacheSettings.Steps.indices),
    )
    val maxSizeStep: StateFlow<Int> = _maxSizeStep.asStateFlow()

    val activeStep: Int = _maxSizeStep.value

    private val _prefetchWifiOnly = MutableStateFlow(prefs.getBoolean(KEY_PREFETCH_WIFI_ONLY, true))

    /**
     * Whether songs are saved ahead only on Wi-Fi (any unmetered network). The song
     * playing is still kept as it streams either way: that costs no extra data.
     */
    val prefetchWifiOnly: StateFlow<Boolean> = _prefetchWifiOnly.asStateFlow()

    fun setPrefetchWifiOnly(on: Boolean) {
        prefs.edit().putBoolean(KEY_PREFETCH_WIFI_ONLY, on).apply()
        _prefetchWifiOnly.value = on
    }

    /**
     * The cache itself, opened on first use. Media3 allows one instance per folder
     * for the whole process, so everything shares this one. Off the main thread:
     * opening it reads its index from disk.
     */
    val cache: Cache by lazy {
        SimpleCache(
            // Out of the system's cache folder, so it isn't emptied behind the index's back.
            locations.activeLocation.musicDir,
            LeastRecentlyUsedCacheEvictor(ImageCacheSettings.Steps[activeStep]),
            databaseProvider,
        )
    }

    /**
     * Deletes the music caches left at locations no longer in use (after a move), with
     * their index. Not while standing in for a missing SD card, whose cache comes back
     * with the card. Off the main thread.
     */
    fun deleteUnusedCaches() {
        if (locations.usingFallback) return
        locations.locations()
            .filter { it.id != locations.activeLocation.id && it.musicDir.exists() }
            .forEach { runCatching { SimpleCache.delete(it.musicDir, databaseProvider) } }
    }

    fun setMaxSizeStep(step: Int) {
        val clamped = step.coerceIn(ImageCacheSettings.Steps.indices)
        if (clamped == _maxSizeStep.value) return
        prefs.edit().putInt(KEY_MAX_SIZE_STEP, clamped).apply()
        _maxSizeStep.value = clamped
    }

    /** How much it holds. Off the main thread. */
    fun usedBytes(): Long = cache.cacheSpace

    /**
     * Empties it, but for the parts of songs being read right now (the playing one,
     * one being saved ahead), which go the next time. Off the main thread.
     */
    fun clear() {
        cache.keys.toList().forEach { key -> runCatching { cache.removeResource(key) } }
    }

    private companion object {
        const val KEY_MAX_SIZE_STEP = "music_cache_max_size_step"
        const val KEY_PREFETCH_WIFI_ONLY = "music_cache_prefetch_wifi_only"

        /** 4 GB: about a hundred lossless songs, several times that compressed. */
        const val DefaultStep = 5
    }
}
