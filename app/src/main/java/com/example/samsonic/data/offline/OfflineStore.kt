package com.example.samsonic.data.offline

import com.example.samsonic.model.Song
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * A song kept for offline: [song] of the server [serverKey], saved in the music cache
 * under [cacheKey] (the key the player reads it by).
 */
@Serializable
data class OfflineEntry(val serverKey: String, val song: Song, val cacheKey: String)

/**
 * The songs the user has chosen to keep on the phone, each with its server, in the order
 * they were added; kept in [file] so they outlast the app. The music cache never clears
 * these (see [PinningCacheEvictor]); [OfflineDownloader] saves them whole.
 */
class OfflineStore(private val file: File) {
    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(OfflineEntry.serializer())
    private val lock = Mutex()

    private val _entries = MutableStateFlow(readNow())

    /** Everything kept, across servers; changes as songs are added or removed. */
    val entries: StateFlow<List<OfflineEntry>> = _entries.asStateFlow()

    /** The cache keys of every song kept, for the cache to leave alone. */
    fun cacheKeys(): Set<String> = _entries.value.mapTo(HashSet()) { it.cacheKey }

    /** What's kept for [serverKey]. */
    fun songs(serverKey: String): List<Song> = _entries.value.filter { it.serverKey == serverKey }.map { it.song }

    /** Keeps [added] of [serverKey], leaving out any already kept. Returns how many were new. */
    suspend fun add(serverKey: String, added: List<Pair<Song, String>>): Int = lock.withLock {
        val kept = _entries.value
        val have = kept.mapTo(HashSet()) { it.serverKey to it.song.id }
        val fresh = added
            .filter { (song, _) -> have.add(serverKey to song.id) }
            .map { (song, key) -> OfflineEntry(serverKey, song, key) }
        if (fresh.isNotEmpty()) update(kept + fresh)
        fresh.size
    }

    /** Stops keeping song [songId] of [serverKey]. */
    suspend fun remove(serverKey: String, songId: String) {
        lock.withLock { update(_entries.value.filterNot { it.serverKey == serverKey && it.song.id == songId }) }
    }

    /** Stops keeping everything, or everything of [serverKey] (a removed server). */
    suspend fun clear(serverKey: String? = null) {
        lock.withLock { update(if (serverKey == null) emptyList() else _entries.value.filterNot { it.serverKey == serverKey }) }
    }

    private suspend fun update(entries: List<OfflineEntry>) {
        _entries.value = entries
        withContext(Dispatchers.IO) {
            runCatching {
                file.parentFile?.mkdirs()
                val temp = File(file.parentFile, "${file.name}.tmp")
                temp.writeText(json.encodeToString(serializer, entries))
                if (!temp.renameTo(file)) temp.delete()
            }
        }
    }

    // A file that no longer reads is as good as nothing kept.
    private fun readNow(): List<OfflineEntry> {
        if (!file.isFile) return emptyList()
        return runCatching { json.decodeFromString(serializer, file.readText()) }.getOrDefault(emptyList())
    }
}
