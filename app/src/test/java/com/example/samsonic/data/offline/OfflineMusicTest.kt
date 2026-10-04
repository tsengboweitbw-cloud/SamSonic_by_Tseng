package com.example.samsonic.data.offline

import com.example.samsonic.FakeMusicLibrary
import com.example.samsonic.song
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class OfflineMusicTest {
    @get:Rule
    val folder = TemporaryFolder()

    private class Server : FakeMusicLibrary() {
        override fun streamCacheKey(songId: String) = "server|amber|$songId"
    }

    private val serverKey = MutableStateFlow<String?>("s1")
    private var kept = 0

    private fun store() = OfflineStore(File(folder.root, "offline.json"))

    private fun offlineMusic(store: OfflineStore = store(), scope: CoroutineScope) = OfflineMusic(
        store = store,
        library = { Server() },
        serverKey = { serverKey.value },
        serverKeys = serverKey,
        scope = scope,
        onKept = { kept++ },
    )

    @Test
    fun keepingSongsMarksThemAndStartsSavingThem() = runTest {
        val offline = offlineMusic(scope = backgroundScope)
        offline.keep(listOf(song("a"), song("b")))
        runCurrent()
        assertEquals(setOf("a", "b"), offline.keptIds.value)
        assertEquals(1, kept)
    }

    @Test
    fun songsAlreadyKeptAreNotKeptAgain() = runTest {
        val offline = offlineMusic(scope = backgroundScope)
        offline.keep(listOf(song("a")))
        runCurrent()
        offline.keep(listOf(song("a")))
        runCurrent()
        assertEquals(setOf("a"), offline.keptIds.value)
        // Nothing new, so nothing more to save.
        assertEquals(1, kept)
    }

    @Test
    fun removingSongsUnmarksThem() = runTest {
        val offline = offlineMusic(scope = backgroundScope)
        offline.keep(listOf(song("a"), song("b")))
        runCurrent()
        offline.remove(listOf(song("a")))
        runCurrent()
        assertEquals(setOf("b"), offline.keptIds.value)
    }

    @Test
    fun theSongsAreKeptUnderTheCacheKeyThePlayerReadsThemBy() = runTest {
        val store = store()
        offlineMusic(store, backgroundScope).keep(listOf(song("a")))
        assertEquals(setOf("server|amber|a"), store.cacheKeys())
    }

    @Test
    fun eachServerKeepsItsOwnSongs() = runTest {
        val offline = offlineMusic(scope = backgroundScope)
        offline.keep(listOf(song("a")))
        runCurrent()
        serverKey.value = "s2"
        runCurrent()
        assertEquals(emptySet<String>(), offline.keptIds.value)
        offline.keep(listOf(song("z")))
        runCurrent()
        serverKey.value = "s1"
        runCurrent()
        assertEquals(setOf("a"), offline.keptIds.value)
    }

    @Test
    fun withNoServerNothingCanBeKept() = runTest {
        serverKey.value = null
        val offline = offlineMusic(scope = backgroundScope)
        offline.keep(listOf(song("a")))
        runCurrent()
        assertEquals(emptySet<String>(), offline.keptIds.value)
        assertEquals(false, offline.available)
        assertEquals(0, kept)
    }

    @Test
    fun whatIsKeptSurvivesTheAppClosing() = runTest {
        offlineMusic(scope = backgroundScope).keep(listOf(song("a")))
        assertEquals(listOf("a"), OfflineStore(File(folder.root, "offline.json")).songs("s1").map { it.id })
    }

    @Test
    fun removingAServerForgetsItsSongsOnly() = runTest {
        val store = store()
        val offline = offlineMusic(store, backgroundScope)
        offline.keep(listOf(song("a")))
        runCurrent()
        serverKey.value = "s2"
        runCurrent()
        offline.keep(listOf(song("z")))
        runCurrent()
        store.clear("s1")
        assertEquals(setOf("server|amber|z"), store.cacheKeys())
    }

    @Test
    fun aFileThatCannotBeReadIsNothingKept() {
        File(folder.root, "offline.json").writeText("{ not json")
        assertEquals(emptySet<String>(), store().cacheKeys())
    }
}
