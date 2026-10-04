package com.example.samsonic.data.offline

import com.example.samsonic.FakeMusicLibrary
import com.example.samsonic.model.Playlist
import com.example.samsonic.model.Song
import com.example.samsonic.song
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class OfflineLibraryTest {
    @get:Rule
    val folder = TemporaryFolder()

    private class Server : FakeMusicLibrary() {
        val added = mutableListOf<Pair<String, List<String>>>()
        val playlists = listOf(Playlist("p1", "Evening", "", 3, 600, null))

        override suspend fun getPlaylists() = playlists
        override suspend fun getOwnPlaylists() = playlists
        override suspend fun getPlaylist(id: String) = playlists.first { it.id == id } to emptyList<Song>()
        override suspend fun addToPlaylist(playlistId: String, songIds: List<String>) {
            added += playlistId to songIds
        }

        override fun streamCacheKey(songId: String) = "server|amber|$songId"
    }

    private var serverKey: String? = "s1"
    private var addedCalls = 0

    private fun store() = OfflineStore(File(folder.root, "offline.json"))

    private fun library(server: Server, store: OfflineStore = store()) = OfflineLibrary(
        inner = server,
        store = store,
        serverKey = { serverKey },
        playlistName = { "Offline" },
        onAdded = { addedCalls++ },
    )

    @Test
    fun offlineIsListedWithTheServersOwnPlaylists() = runTest {
        val library = library(Server())
        assertEquals(listOf("p1", OFFLINE_PLAYLIST_ID), library.getPlaylists().map { it.id })
        assertEquals(listOf("p1", OFFLINE_PLAYLIST_ID), library.getOwnPlaylists().map { it.id })
        assertTrue(library.getPlaylists().last().isOffline)
        assertEquals("Offline", library.getPlaylists().last().name)
    }

    @Test
    fun withNoServerThereIsNoOfflinePlaylist() = runTest {
        serverKey = null
        assertEquals(listOf("p1"), library(Server()).getPlaylists().map { it.id })
    }

    @Test
    fun addingSongsKeepsThemAndStartsSavingThem() = runTest {
        val library = library(Server())
        library.addSongsToPlaylist(OFFLINE_PLAYLIST_ID, listOf(song("a"), song("b")))
        val (playlist, songs) = library.getPlaylist(OFFLINE_PLAYLIST_ID)
        assertEquals(listOf("a", "b"), songs.map { it.id })
        assertEquals(2, playlist.songCount)
        assertEquals(400, playlist.durationSeconds)
        assertEquals(1, addedCalls)
    }

    @Test
    fun songsAlreadyKeptAreNotAddedAgain() = runTest {
        val library = library(Server())
        library.addSongsToPlaylist(OFFLINE_PLAYLIST_ID, listOf(song("a")))
        library.addSongsToPlaylist(OFFLINE_PLAYLIST_ID, listOf(song("a")))
        assertEquals(1, library.getPlaylist(OFFLINE_PLAYLIST_ID).second.size)
        // Nothing new, so nothing more to save.
        assertEquals(1, addedCalls)
    }

    @Test
    fun theSongsAreKeptUnderTheCacheKeyThePlayerReadsThemBy() = runTest {
        val store = store()
        library(Server(), store).addSongsToPlaylist(OFFLINE_PLAYLIST_ID, listOf(song("a")))
        assertEquals(setOf("server|amber|a"), store.cacheKeys())
    }

    @Test
    fun eachServerKeepsItsOwnOfflineSongs() = runTest {
        val library = library(Server())
        library.addSongsToPlaylist(OFFLINE_PLAYLIST_ID, listOf(song("a")))
        serverKey = "s2"
        assertEquals(emptyList<Song>(), library.getPlaylist(OFFLINE_PLAYLIST_ID).second)
        library.addSongsToPlaylist(OFFLINE_PLAYLIST_ID, listOf(song("z")))
        serverKey = "s1"
        assertEquals(listOf("a"), library.getPlaylist(OFFLINE_PLAYLIST_ID).second.map { it.id })
    }

    @Test
    fun otherPlaylistsGoToTheServerByTheirSongIds() = runTest {
        val server = Server()
        library(server).addSongsToPlaylist("p1", listOf(song("a"), song("b")))
        assertEquals(listOf("p1" to listOf("a", "b")), server.added)
    }

    @Test
    fun offlineCannotBeAddedToByIdsAlone() = runTest {
        try {
            library(Server()).addToPlaylist(OFFLINE_PLAYLIST_ID, listOf("a"))
            fail("expected an error")
        } catch (e: UnsupportedOperationException) {
            // The songs themselves are needed to keep them.
        }
    }

    @Test
    fun whatIsKeptSurvivesTheAppClosing() = runTest {
        library(Server()).addSongsToPlaylist(OFFLINE_PLAYLIST_ID, listOf(song("a")))
        assertEquals(listOf("a"), OfflineStore(File(folder.root, "offline.json")).songs("s1").map { it.id })
    }

    @Test
    fun removingAServerForgetsItsSongsOnly() = runTest {
        val store = store()
        val library = library(Server(), store)
        library.addSongsToPlaylist(OFFLINE_PLAYLIST_ID, listOf(song("a")))
        serverKey = "s2"
        library.addSongsToPlaylist(OFFLINE_PLAYLIST_ID, listOf(song("z")))
        store.clear("s1")
        assertEquals(setOf("server|amber|z"), store.cacheKeys())
        assertFalse(store.songs("s1").isNotEmpty())
    }

    @Test
    fun aFileThatCannotBeReadIsNothingKept() = runTest {
        File(folder.root, "offline.json").writeText("{ not json")
        assertEquals(emptySet<String>(), store().cacheKeys())
    }
}
