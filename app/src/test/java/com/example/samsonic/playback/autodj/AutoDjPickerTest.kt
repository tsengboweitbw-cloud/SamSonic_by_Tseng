package com.example.samsonic.playback.autodj

import com.example.samsonic.FakeMusicLibrary
import com.example.samsonic.album
import com.example.samsonic.data.AutoDjArtist
import com.example.samsonic.data.AutoDjConfig
import com.example.samsonic.data.AutoDjFilters
import com.example.samsonic.data.AutoDjFollow
import com.example.samsonic.data.AutoDjMode
import com.example.samsonic.model.Album
import com.example.samsonic.model.Artist
import com.example.samsonic.model.Song
import com.example.samsonic.song
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoDjPickerTest {
    /** Serves [random] for any random pick, and records the year range and genre asked for. */
    private class Library(
        val random: List<Song> = emptyList(),
        val liked: List<Song> = emptyList(),
        val artistAlbums: Map<String, List<Album>> = emptyMap(),
        val albumSongs: Map<String, List<Song>> = emptyMap(),
        val randomAlbumList: List<Album> = emptyList(),
    ) : FakeMusicLibrary() {
        var askedYears: Pair<Int?, Int?>? = null
        var askedGenre: String? = null

        override suspend fun randomSongs(count: Int, genre: String?, fromYear: Int?, toYear: Int?): List<Song> {
            askedYears = fromYear to toYear
            askedGenre = genre
            return random
        }

        override suspend fun randomAlbums(count: Int, genre: String?, fromYear: Int?, toYear: Int?) = randomAlbumList
        override suspend fun getLikedSongs() = liked
        override suspend fun getArtist(id: String) =
            Artist(id, id, 0, null) to artistAlbums.getValue(id)

        override suspend fun getAlbumsSongs(albums: List<Album>) = albums.flatMap { albumSongs.getValue(it.id) }
        override suspend fun getAlbum(id: String) = album(id) to albumSongs.getValue(id)
    }

    private val seed = song("seed", year = 1985, genre = "Rock", artistId = "a1")

    private fun config(
        mode: AutoDjMode = AutoDjMode.SONGS,
        follow: Set<AutoDjFollow> = emptySet(),
        filters: AutoDjFilters = AutoDjFilters(),
    ) = AutoDjConfig(mode = mode, follow = follow, filters = filters)

    @Test
    fun offPicksNothing() = runTest {
        val library = Library(random = listOf(song("x")))
        assertEquals(emptyList<Song>(), pickAutoDj(library, config(AutoDjMode.OFF), seed, emptySet(), emptySet()))
    }

    @Test
    fun songModeTakesAFewSongsAndNeverOnesAlreadyQueued() = runTest {
        val pool = (1..20).map { song("s$it") }
        val library = Library(random = pool)
        val queued = pool.take(10).map { it.id }.toSet()
        val picked = pickAutoDj(library, config(), seed, exclude = queued, excludeAlbums = emptySet())
        assertEquals(5, picked.size)
        assertTrue(picked.none { it.id in queued })
        assertEquals(picked.size, picked.map { it.id }.distinct().size)
    }

    @Test
    fun followingTheEraAsksForTheSeedsDecade() = runTest {
        val library = Library(random = listOf(song("x", year = 1983)))
        pickAutoDj(library, config(follow = setOf(AutoDjFollow.ERA)), seed, emptySet(), emptySet())
        assertEquals(1980 to 1989, library.askedYears)
    }

    @Test
    fun followingTheGenreAsksForTheSeedsGenre() = runTest {
        val library = Library(random = listOf(song("x", genre = "Rock")))
        pickAutoDj(library, config(follow = setOf(AutoDjFollow.GENRE)), seed, emptySet(), emptySet())
        assertEquals("Rock", library.askedGenre)
    }

    @Test
    fun followingTheArtistPicksFromTheirAlbums() = runTest {
        val library = Library(
            random = listOf(song("elsewhere")),
            artistAlbums = mapOf("a1" to listOf(album("al1"))),
            albumSongs = mapOf("al1" to listOf(song("mine1", artistId = "a1"), song("mine2", artistId = "a1"))),
        )
        val picked = pickAutoDj(library, config(follow = setOf(AutoDjFollow.ARTIST)), seed, emptySet(), emptySet())
        assertEquals(setOf("mine1", "mine2"), picked.map { it.id }.toSet())
    }

    @Test
    fun whenTheArtistHasNothingLeftItLetsGoOfTheArtist() = runTest {
        val library = Library(
            random = listOf(song("elsewhere")),
            artistAlbums = mapOf("a1" to listOf(album("al1"))),
            albumSongs = mapOf("al1" to listOf(song("mine", artistId = "a1"))),
        )
        val picked = pickAutoDj(
            library, config(follow = setOf(AutoDjFollow.ARTIST)), seed,
            exclude = setOf("mine"), excludeAlbums = emptySet(),
        )
        assertEquals(listOf("elsewhere"), picked.map { it.id })
    }

    @Test
    fun likedOnlyKeepsToLikedSongs() = runTest {
        val library = Library(liked = listOf(song("liked1", liked = true), song("liked2", liked = true)))
        val picked = pickAutoDj(library, config(filters = AutoDjFilters(likedOnly = true)), seed, emptySet(), emptySet())
        assertEquals(setOf("liked1", "liked2"), picked.map { it.id }.toSet())
    }

    @Test
    fun hiResOnlyDropsCdQualitySongs() = runTest {
        val library = Library(random = listOf(song("cd", samplingRate = 44_100), song("hires", samplingRate = 96_000)))
        val picked = pickAutoDj(library, config(filters = AutoDjFilters(hiResOnly = true)), seed, emptySet(), emptySet())
        assertEquals(listOf("hires"), picked.map { it.id })
    }

    @Test
    fun neverPlayedDropsSongsWithPlays() = runTest {
        val library = Library(random = listOf(song("played", playCount = 3), song("fresh")))
        val picked = pickAutoDj(library, config(filters = AutoDjFilters(neverPlayed = true)), seed, emptySet(), emptySet())
        assertEquals(listOf("fresh"), picked.map { it.id })
    }

    @Test
    fun aFilterThatRulesOutTheSeedsGenreStillGivesSongsOfTheFilter() = runTest {
        // Following the seed's Rock is impossible with a Jazz-only filter, so that set is skipped.
        val library = Library(random = listOf(song("jazz", genre = "Jazz")))
        val settings = config(follow = setOf(AutoDjFollow.GENRE), filters = AutoDjFilters(genres = setOf("Jazz")))
        val picked = pickAutoDj(library, settings, seed, emptySet(), emptySet())
        assertEquals(listOf("jazz"), picked.map { it.id })
        assertEquals("Jazz", library.askedGenre)
    }

    @Test
    fun theArtistFilterPicksFromThatArtist() = runTest {
        val library = Library(
            artistAlbums = mapOf("fav" to listOf(album("fa"))),
            albumSongs = mapOf("fa" to listOf(song("favSong", artistId = "fav"))),
        )
        val filters = AutoDjFilters(artists = listOf(AutoDjArtist("fav", "Favourite")))
        val picked = pickAutoDj(library, config(filters = filters), seed, emptySet(), emptySet())
        assertEquals(listOf("favSong"), picked.map { it.id })
    }

    @Test
    fun albumModeTakesAWholeAlbumNotAlreadyPicked() = runTest {
        val library = Library(
            randomAlbumList = listOf(album("done"), album("fresh")),
            albumSongs = mapOf("done" to listOf(song("d1")), "fresh" to listOf(song("f1"), song("f2"))),
        )
        val picked = pickAutoDj(library, config(AutoDjMode.ALBUMS), seed, emptySet(), excludeAlbums = setOf("done"))
        assertEquals(listOf("f1", "f2"), picked.map { it.id })
    }

    @Test
    fun aFailingServerPicksNothingRatherThanCrashing() = runTest {
        val library = FakeMusicLibrary() // every call throws
        assertEquals(emptyList<Song>(), pickAutoDj(library, config(), seed, emptySet(), emptySet()))
    }
}
