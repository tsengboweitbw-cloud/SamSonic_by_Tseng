package com.example.samsonic.data.offline

import com.example.samsonic.FakeMusicLibrary
import com.example.samsonic.song
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class OfflineOnlyLibraryTest {
    @get:Rule
    val folder = TemporaryFolder()

    private suspend fun library(vararg kept: com.example.samsonic.model.Song): OfflineOnlyLibrary {
        val store = OfflineStore(File(folder.root, "offline.json"))
        store.add("s1", kept.map { it to "key|${it.id}" })
        return OfflineOnlyLibrary(FakeMusicLibrary(), store) { "s1" }
    }

    @Test
    fun albumsAndArtistsAreMadeFromTheKeptSongs() = runTest {
        val library = library(song("a", albumId = "al1"), song("b", albumId = "al1"), song("c", albumId = "al2", artistId = "x"))
        assertEquals(setOf("al1", "al2"), library.getAlbumList("newest", 20).map { it.id }.toSet())
        assertEquals(2, library.getAlbum("al1").second.size)
        assertEquals(setOf("artist", "x"), library.getArtists().map { it.id }.toSet())
    }

    @Test
    fun searchFindsOnlyKeptSongs() = runTest {
        val library = library(song("alpha"), song("beta"))
        assertEquals(listOf("alpha"), library.search("alp").songs.map { it.id })
    }

    @Test
    fun nothingKeptMeansNothingListed() = runTest {
        val library = library()
        assertEquals(emptyList<Any>(), library.getArtists())
        assertEquals(emptyList<Any>(), library.getSongList("random", 10))
    }
}
