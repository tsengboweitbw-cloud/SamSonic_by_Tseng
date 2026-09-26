package com.example.samsonic.data.device

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceIndexTest {
    private fun track(
        id: Long,
        title: String,
        artist: String,
        album: String,
        albumId: Long,
        track: Int = 1,
        albumArtist: String? = null,
        disc: Int? = null,
        year: Int? = null,
        genre: String? = null,
        dateAdded: Long = 0L,
    ) = DeviceTrack(
        id = id, title = title, artist = artist, albumArtist = albumArtist, album = album, albumId = albumId,
        track = track, disc = disc, durationMs = 180_000, year = year, genre = genre, dateAdded = dateAdded, dateModified = 0L,
        sizeBytes = null, mimeType = null, path = "/music/$title.flac", bitRate = null,
    )

    private val tracks = listOf(
        track(1, "B Side", "Alice", "First", albumId = 10, track = 2, year = 2020, genre = "Pop", dateAdded = 100),
        track(2, "A Side", "Alice", "First", albumId = 10, track = 1, year = 2020, genre = "Pop", dateAdded = 100),
        track(3, "Duet", "Alice", "Hits", albumId = 20, track = 1, albumArtist = "Various Artists", genre = "Rock", dateAdded = 300),
        track(4, "Solo", "Bob", "Hits", albumId = 20, track = 2, albumArtist = "Various Artists", genre = "Rock", dateAdded = 300),
        track(5, "Disc Two", "Bob", "Double", albumId = 30, track = 1, disc = 2, year = 2022, dateAdded = 200),
        track(6, "Disc One", "Bob", "Double", albumId = 30, track = 5, disc = 1, year = 2022, dateAdded = 200),
    )

    private val index = DeviceIndex(
        tracks,
        likedIds = setOf("4"),
        plays = mapOf("1" to DevicePlays(count = 2, lastPlayedMs = 1_000), "5" to DevicePlays(count = 5, lastPlayedMs = 500)),
        formats = mapOf("3" to AudioStreamFormat(sampleRate = 96_000, bitDepth = 24, channels = 2)),
    )

    @Test
    fun playHistoryOrdersRecentAndFrequent() {
        assertEquals(listOf("B Side", "Disc Two"), index.songList("recent").map { it.title })
        assertEquals(listOf("Disc Two", "B Side"), index.songList("frequent").map { it.title })
        assertEquals(listOf("30", "10"), index.albumList("frequent").map { it.id })
        assertEquals(listOf("Disc Two"), index.topSongs("bob").map { it.title })
        assertEquals(2L, index.songsById.getValue("1").playCount)
        assertEquals(null, index.songsById.getValue("2").played)
    }

    @Test
    fun songsCarryTheirProbedFormat() {
        val duet = index.songsById.getValue("3")
        assertEquals(96_000, duet.samplingRate)
        assertEquals(24, duet.bitDepth)
        assertEquals(null, index.songsById.getValue("4").samplingRate)
    }

    @Test
    fun songsPlayInDiscAndTrackOrder() {
        assertEquals(listOf("A Side", "B Side"), index.songsOf("10").map { it.title })
        assertEquals(listOf("Disc One", "Disc Two"), index.songsOf("30").map { it.title })
    }

    @Test
    fun albumArtistComesFromTagsElseTheOneTrackArtist() {
        assertEquals("Alice", index.albumsById.getValue("10").artistName)
        assertEquals("Various Artists", index.albumsById.getValue("20").artistName)
        assertEquals("Bob", index.albumsById.getValue("30").artistName)
    }

    @Test
    fun albumArtistsAndTrackArtistsDiffer() {
        assertEquals(listOf("Alice", "Bob", "Various Artists"), index.albumArtists.map { it.name })
        assertEquals(listOf("Alice", "Bob"), index.trackArtists.map { it.name })
    }

    @Test
    fun artistIdsAreStableAndLinkSongsToArtists() {
        val alice = index.artist(deviceArtistId("alice"))!!
        assertEquals("Alice", alice.name)
        assertEquals(listOf("10"), index.albumsBy(alice.id).map { it.id })
        assertEquals(3, index.songsBy(alice.id).size)
        assertNotEquals(deviceArtistId("Alice"), deviceArtistId("Bob"))
    }

    @Test
    fun newestShelfFollowsDateAdded() {
        assertEquals(listOf("20", "30", "10"), index.albumList("newest").map { it.id })
    }

    @Test
    fun genresCountSongsAndListTheirAlbums() {
        assertEquals(listOf("Pop" to 2, "Rock" to 2), index.genres.map { it.name to it.songCount })
        assertEquals(listOf("20"), index.genreAlbums("Rock").map { it.id })
    }

    @Test
    fun likesAndCoverArtCarryOver() {
        val solo = index.songs.first { it.title == "Solo" }
        assertTrue(solo.liked)
        assertEquals("20", solo.coverArt)
        assertEquals("flac", solo.suffix)
    }
}
