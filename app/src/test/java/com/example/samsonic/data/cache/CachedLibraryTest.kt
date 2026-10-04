package com.example.samsonic.data.cache

import com.example.samsonic.FakeMusicLibrary
import com.example.samsonic.album
import com.example.samsonic.model.Album
import com.example.samsonic.model.Artist
import com.example.samsonic.model.Genre
import com.example.samsonic.model.Playlist
import java.io.File
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CachedLibraryTest {
    @get:Rule
    val folder = TemporaryFolder()

    /** A server that answers with [artists] and counts the calls, or fails while [down]. */
    private class Server(var artists: List<Artist> = emptyList()) : FakeMusicLibrary() {
        var calls = 0
        var down = false
        var playlists = emptyList<Playlist>()

        override suspend fun getArtists(): List<Artist> {
            calls++
            if (down) throw IOException("unreachable")
            return artists
        }

        override suspend fun getGenres(): List<Genre> = listOf(Genre("Jazz", 3))
        override suspend fun getPlaylists(): List<Playlist> {
            calls++
            return playlists
        }

        override suspend fun getAlbumList(type: String, size: Int): List<Album> {
            calls++
            return listOf(album("$type-$size"))
        }

        override suspend fun addToPlaylist(playlistId: String, songIds: List<String>) = Unit
    }

    private var time = 1_000_000L
    private var serverKey: String? = "server1"

    private fun artist(name: String) = Artist(name, name, 1, null)

    private fun library(server: Server, dir: File = File(folder.root, "cache")) =
        CachedLibrary(server, LibraryCacheStore(dir), { serverKey }, ttlMs = 10_000, now = { time })

    @Test
    fun aFreshListingComesFromDiskWithoutAskingTheServer() = runTest {
        val server = Server(listOf(artist("a")))
        val library = library(server)
        assertEquals(listOf("a"), library.getArtists().map { it.id })
        time += 5_000
        server.artists = listOf(artist("changed"))
        assertEquals(listOf("a"), library.getArtists().map { it.id })
        assertEquals(1, server.calls)
    }

    @Test
    fun aColdStartStillFindsWhatWasSaved() = runTest {
        val server = Server(listOf(artist("a")))
        library(server).getArtists()
        // A new library over the same directory, as after the app restarts.
        val restarted = Server(listOf(artist("other")))
        assertEquals(listOf("a"), library(restarted).getArtists().map { it.id })
        assertEquals(0, restarted.calls)
    }

    @Test
    fun anOldListingIsFetchedAgain() = runTest {
        val server = Server(listOf(artist("a")))
        val library = library(server)
        library.getArtists()
        time += 10_001
        server.artists = listOf(artist("b"))
        assertEquals(listOf("b"), library.getArtists().map { it.id })
        assertEquals(2, server.calls)
    }

    @Test
    fun whenTheServerIsDownTheOldListingStandsIn() = runTest {
        val server = Server(listOf(artist("a")))
        val library = library(server)
        library.getArtists()
        time += 60_000
        server.down = true
        assertEquals(listOf("a"), library.getArtists().map { it.id })
    }

    @Test
    fun withNothingSavedAFailureIsStillAFailure() = runTest {
        val server = Server().apply { down = true }
        try {
            library(server).getArtists()
            fail("expected the server's error")
        } catch (e: IOException) {
            assertEquals("unreachable", e.message)
        }
    }

    @Test
    fun aPullToRefreshAsksTheServerAgainAtOnce() = runTest {
        val server = Server(listOf(artist("a")))
        val library = library(server)
        library.getArtists()
        server.artists = listOf(artist("b"))
        time += 1
        library.invalidate()
        time += 1
        assertEquals(listOf("b"), library.getArtists().map { it.id })
        // Saved afresh, so the next load is quick again.
        assertEquals(listOf("b"), library.getArtists().map { it.id })
        assertEquals(2, server.calls)
    }

    @Test
    fun aRefreshThatFailsKeepsTheOldListingUp() = runTest {
        val server = Server(listOf(artist("a")))
        val library = library(server)
        library.getArtists()
        time += 1
        library.invalidate()
        time += 1
        server.down = true
        assertEquals(listOf("a"), library.getArtists().map { it.id })
    }

    @Test
    fun eachServerKeepsItsOwnListings() = runTest {
        val server = Server(listOf(artist("a")))
        val library = library(server)
        library.getArtists()
        serverKey = "server2"
        server.artists = listOf(artist("z"))
        assertEquals(listOf("z"), library.getArtists().map { it.id })
    }

    @Test
    fun withNoServerNothingIsKept() = runTest {
        serverKey = null
        val server = Server(listOf(artist("a")))
        val library = library(server)
        library.getArtists()
        library.getArtists()
        assertEquals(2, server.calls)
    }

    @Test
    fun aForgottenServerAsksAgain() = runTest {
        val server = Server(listOf(artist("a")))
        val library = library(server)
        library.getArtists()
        library.forget("server1")
        library.getArtists()
        assertEquals(2, server.calls)
    }

    @Test
    fun aFileThatCannotBeReadIsReplaced() = runTest {
        val server = Server(listOf(artist("a")))
        val dir = File(folder.root, "cache")
        library(server, dir).getArtists()
        dir.listFiles()!!.forEach { it.writeText("{ not json") }
        assertEquals(listOf("a"), library(server, dir).getArtists().map { it.id })
        assertEquals(2, server.calls)
    }

    @Test
    fun editingAPlaylistMakesTheSavedOnesOld() = runTest {
        val server = Server()
        val library = library(server)
        library.getPlaylists()
        time += 1
        library.addToPlaylist("p", listOf("s"))
        time += 1
        library.getPlaylists()
        assertEquals(2, server.calls)
    }

    @Test
    fun homesShortShelvesAreNeverKept() = runTest {
        val server = Server()
        val library = library(server)
        library.getAlbumList("newest", 20)
        library.getAlbumList("newest", 20)
        assertEquals(2, server.calls)
    }

    @Test
    fun theLibrarysAlbumListIsKeptPerTypeAndSize() = runTest {
        val server = Server()
        val library = library(server)
        assertEquals("newest-500", library.getAlbumList("newest", 500).single().id)
        library.getAlbumList("newest", 500)
        assertEquals("alphabeticalByName-500", library.getAlbumList("alphabeticalByName", 500).single().id)
        assertEquals(2, server.calls)
    }

    @Test
    fun savedListingsKeepAllTheirFields() = runTest {
        val playlist = Playlist("p1", "Evening", "calm", 12, 2400, "cover", "2026-10-01T10:00:00Z")
        val server = Server().apply { playlists = listOf(playlist) }
        library(server).getPlaylists()
        assertEquals(listOf(playlist), library(Server()).getPlaylists())
        assertTrue(File(folder.root, "cache").listFiles()!!.isNotEmpty())
    }
}
