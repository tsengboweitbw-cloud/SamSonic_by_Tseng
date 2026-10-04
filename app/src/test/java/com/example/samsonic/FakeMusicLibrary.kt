package com.example.samsonic

import com.example.samsonic.data.MusicLibrary
import com.example.samsonic.model.Album
import com.example.samsonic.model.Artist
import com.example.samsonic.model.Genre
import com.example.samsonic.model.GenreContents
import com.example.samsonic.model.LyricLine
import com.example.samsonic.model.Playlist
import com.example.samsonic.model.SearchResults
import com.example.samsonic.model.Song

/** A [MusicLibrary] for tests: override what a test uses, and anything else fails loudly. */
open class FakeMusicLibrary : MusicLibrary {
    override suspend fun getArtists(): List<Artist> = unused()
    override suspend fun getAlbumArtists(): List<Artist> = unused()
    override suspend fun getGenre(genre: String, songCount: Int): GenreContents = unused()
    override suspend fun getGenreSongs(genre: String, count: Int): List<Song> = unused()
    override suspend fun getArtist(id: String): Pair<Artist, List<Album>> = unused()
    override suspend fun getAlbum(id: String): Pair<Album, List<Song>> = unused()
    override suspend fun getAlbumsSongs(albums: List<Album>): List<Song> = unused()
    override suspend fun getArtistSongs(artist: Artist, albums: List<Album>, limit: Int?, songsBy: List<Song>?): List<Song> = unused()
    override suspend fun getSongsBy(artist: Artist): List<Song> = unused()
    override suspend fun getAppearsOn(artist: Artist, albums: List<Album>, songsBy: List<Song>): List<Album> = unused()
    override suspend fun getAlbumList(type: String, size: Int): List<Album> = unused()
    override suspend fun getSongList(type: String, size: Int): List<Song> = unused()
    override suspend fun randomSongs(count: Int, genre: String?, fromYear: Int?, toYear: Int?): List<Song> = unused()
    override suspend fun randomAlbums(count: Int, genre: String?, fromYear: Int?, toYear: Int?): List<Album> = unused()
    override suspend fun getPlaylists(): List<Playlist> = unused()
    override suspend fun getPlaylist(id: String): Pair<Playlist, List<Song>> = unused()
    override suspend fun getTopSongs(artistName: String, count: Int): List<Song> = unused()
    override suspend fun getGenres(): List<Genre> = unused()
    override suspend fun search(query: String): SearchResults = unused()
    override suspend fun getLikedSongs(): List<Song> = unused()
    override suspend fun star(id: String) = unused()
    override suspend fun unstar(id: String) = unused()
    override suspend fun getLyrics(songId: String): List<LyricLine> = unused()
    override fun streamUrl(songId: String): String = unused()
    override fun coverArtUrl(coverArt: String?, size: Int): String? = unused()

    private fun unused(): Nothing = throw UnsupportedOperationException("not faked")
}

/** A song with just what a test cares about. */
fun song(
    id: String,
    year: Int? = null,
    genre: String? = null,
    artistId: String? = "artist",
    albumId: String? = "album",
    liked: Boolean = false,
    samplingRate: Int? = null,
    playCount: Long? = null,
) = Song(
    id = id,
    title = id,
    artistId = artistId,
    artistName = "Artist",
    albumId = albumId,
    albumTitle = "Album",
    trackNumber = 1,
    durationSeconds = 200,
    coverArt = null,
    liked = liked,
    year = year,
    genre = genre,
    samplingRate = samplingRate,
    playCount = playCount,
)

fun album(id: String, year: Int? = null, genre: String? = null) = Album(
    id = id,
    title = id,
    artistId = "artist",
    artistName = "Artist",
    year = year,
    genre = genre,
    trackCount = 10,
    durationSeconds = 2400,
    coverArt = null,
)
