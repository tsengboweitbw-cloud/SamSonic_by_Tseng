package com.example.samsonic.data.offline

import com.example.samsonic.song
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class OfflineStoreTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val file get() = File(folder.root, "offline.json")

    @Test
    fun songsKeepTheOrderTheyWereAddedIn() = runTest {
        val store = OfflineStore(file)
        store.add("s1", listOf(song("b") to "k|b", song("a") to "k|a"))
        store.add("s1", listOf(song("c") to "k|c"))
        assertEquals(listOf("b", "a", "c"), store.songs("s1").map { it.id })
    }

    @Test
    fun aSongAddedTwiceInOneGoIsKeptOnce() = runTest {
        val store = OfflineStore(file)
        val added = store.add("s1", listOf(song("a") to "k|a", song("a") to "k|a"))
        assertEquals(1, added)
        assertEquals(1, store.entries.value.size)
    }

    @Test
    fun theSameSongOfAnotherServerIsAnotherEntry() = runTest {
        val store = OfflineStore(file)
        store.add("s1", listOf(song("a") to "one|a"))
        assertEquals(1, store.add("s2", listOf(song("a") to "two|a")))
        assertEquals(setOf("one|a", "two|a"), store.cacheKeys())
    }

    @Test
    fun removingASongNotKeptChangesNothing() = runTest {
        val store = OfflineStore(file)
        store.add("s1", listOf(song("a") to "k|a"))
        store.remove("s1", setOf("zzz"))
        store.remove("s2", setOf("a"))
        assertEquals(listOf("a"), store.songs("s1").map { it.id })
    }

    @Test
    fun clearingEverythingEmptiesItAndTheFile() = runTest {
        val store = OfflineStore(file)
        store.add("s1", listOf(song("a") to "k|a"))
        store.add("s2", listOf(song("b") to "k|b"))
        store.clear()
        assertEquals(emptySet<String>(), store.cacheKeys())
        assertEquals(emptySet<String>(), OfflineStore(file).cacheKeys())
    }

    @Test
    fun savingLeavesNoHalfWrittenFileBehind() = runTest {
        val store = OfflineStore(file)
        store.add("s1", listOf(song("a") to "k|a"))
        assertFalse(File(folder.root, "offline.json.tmp").exists())
    }

    @Test
    fun anEntryOfAnOlderShapeStillReads() {
        // A field added later must not make a saved file unreadable.
        store("""[{"serverKey":"s1","cacheKey":"k|a","extra":1,"song":${songJson("a")}}]""")
        assertEquals(setOf("k|a"), OfflineStore(file).cacheKeys())
    }

    private fun store(text: String) = file.writeText(text)

    private fun songJson(id: String) =
        kotlinx.serialization.json.Json.encodeToString(com.example.samsonic.model.Song.serializer(), song(id))
}
