package com.example.samsonic.data.cache

import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** A list as it was saved, and when. */
@Serializable
private class Saved<T>(val savedAt: Long, val items: List<T>)

/** A list read back from the cache, and when it was saved. */
class CachedList<T>(val savedAt: Long, val items: List<T>)

/**
 * Keeps what a server's library listings returned on disk, a file each, so the Library
 * opens at once on a cold start and still browses when the server can't be reached.
 * The system may clear it whenever it likes; the next load just asks the server again.
 */
class LibraryCacheStore(private val directory: File) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun <T> read(name: String, item: KSerializer<T>): CachedList<T>? = withContext(Dispatchers.IO) {
        val file = fileFor(name)
        if (!file.isFile) return@withContext null
        // A file that no longer reads (another version's shape, cut short) is as good as none.
        runCatching {
            val saved = json.decodeFromString(Saved.serializer(item), file.readText())
            CachedList(saved.savedAt, saved.items)
        }.getOrElse {
            file.delete()
            null
        }
    }

    suspend fun <T> write(name: String, item: KSerializer<T>, savedAt: Long, items: List<T>) {
        withContext(Dispatchers.IO) {
            runCatching {
                directory.mkdirs()
                val text = json.encodeToString(Saved.serializer(item), Saved(savedAt, items))
                // Whole or not at all, so a read never meets a half-written list.
                val temp = File(directory, "${sanitise(name)}.tmp")
                temp.writeText(text)
                if (!temp.renameTo(fileFor(name))) temp.delete()
            }
        }
    }

    /** Forgets everything saved under a name that starts with [prefix]. */
    suspend fun clear(prefix: String) {
        withContext(Dispatchers.IO) {
            val start = sanitise(prefix)
            directory.listFiles { file -> file.name.startsWith(start) }?.forEach { it.delete() }
        }
    }

    private fun fileFor(name: String) = File(directory, "${sanitise(name)}.json")

    private fun sanitise(name: String) = name.replace(Regex("[^A-Za-z0-9._-]"), "_")
}
