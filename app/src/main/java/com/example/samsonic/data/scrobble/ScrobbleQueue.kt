package com.example.samsonic.data.scrobble

import com.example.samsonic.data.MusicLibrary
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** A listen the server hasn't been told of: song [songId] of the server [serverKey], heard at [timeMs]. */
@Serializable
data class PendingScrobble(val serverKey: String, val songId: String, val timeMs: Long)

/** The most listens kept waiting; past it the oldest go. */
const val MAX_PENDING_SCROBBLES = 500

/**
 * Listens that couldn't be sent, because the server couldn't be reached, kept in [file] so
 * they survive the app closing, and sent (with the time they happened) once it can be.
 * Not the "now playing" notices, which mean nothing late.
 */
class ScrobbleQueue(private val file: File) {
    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(PendingScrobble.serializer())
    private val lock = Mutex()

    suspend fun add(pending: PendingScrobble) {
        lock.withLock {
            val kept = (read() + pending).takeLast(MAX_PENDING_SCROBBLES)
            write(kept)
        }
    }

    suspend fun pending(): List<PendingScrobble> = lock.withLock { read() }

    /**
     * Sends what's waiting for [serverKey] to [library], oldest first, stopping at the first
     * that fails (the server is still out of reach) and keeping that and everything after it.
     * Returns how many went out.
     */
    suspend fun flush(library: MusicLibrary, serverKey: String): Int = lock.withLock {
        val all = read()
        var sent = 0
        val remaining = all.toMutableList()
        for (pending in all) {
            if (pending.serverKey != serverKey) continue
            try {
                library.scrobble(pending.songId, submission = true, timeMs = pending.timeMs)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                break
            }
            remaining.remove(pending)
            sent++
        }
        if (sent > 0) write(remaining)
        sent
    }

    /** Drops what's waiting for [serverKey]: the server has been removed. */
    suspend fun forget(serverKey: String) {
        lock.withLock { write(read().filter { it.serverKey != serverKey }) }
    }

    private suspend fun read(): List<PendingScrobble> = withContext(Dispatchers.IO) {
        if (!file.isFile) return@withContext emptyList()
        // A file that no longer reads is as good as an empty queue.
        runCatching { json.decodeFromString(serializer, file.readText()) }.getOrDefault(emptyList())
    }

    private suspend fun write(items: List<PendingScrobble>) {
        withContext(Dispatchers.IO) {
            runCatching {
                file.parentFile?.mkdirs()
                val temp = File(file.parentFile, "${file.name}.tmp")
                temp.writeText(json.encodeToString(serializer, items))
                if (!temp.renameTo(file)) temp.delete()
            }
        }
    }
}
