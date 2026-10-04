package com.example.samsonic.data.offline

import com.example.samsonic.data.MusicLibrary
import com.example.samsonic.model.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * Keeping songs of the server in use on the phone, to play without it: [keep] and [remove]
 * what the long-press menu offers, and [keptIds] for the songs to be marked. Each server has
 * its own songs; with none in use (the music on this phone), there's nothing to keep.
 * The songs are saved whole by [OfflineDownloader], which [onKept] starts.
 */
class OfflineMusic(
    private val store: OfflineStore,
    // The library in use, for the key each song is saved under.
    private val library: () -> MusicLibrary,
    private val serverKey: () -> String?,
    serverKeys: Flow<String?>,
    scope: CoroutineScope,
    private val onKept: () -> Unit,
) {
    /** Whether a server is in use, so songs can be kept. */
    val available: Boolean get() = serverKey() != null

    /** The ids of the songs kept for the server in use. */
    val keptIds: StateFlow<Set<String>> = combine(store.entries, serverKeys) { entries, key ->
        if (key == null) emptySet() else entries.filter { it.serverKey == key }.mapTo(HashSet()) { it.song.id }
    }.stateIn(scope, SharingStarted.Eagerly, emptySet())

    /** Keeps [songs] on the phone and starts saving them; those already kept are left as they are. */
    suspend fun keep(songs: List<Song>) {
        val server = serverKey() ?: return
        val keyed = songs.mapNotNull { song -> library().streamCacheKey(song.id)?.let { song to it } }
        if (store.add(server, keyed) > 0) onKept()
    }

    /** Stops keeping [songs]; the music cache may clear them like any other. */
    suspend fun remove(songs: List<Song>) {
        val server = serverKey() ?: return
        store.remove(server, songs.mapTo(HashSet()) { it.id })
    }
}
