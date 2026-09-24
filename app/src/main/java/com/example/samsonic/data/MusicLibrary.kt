package com.example.samsonic.data

import com.example.samsonic.model.Album
import com.example.samsonic.model.Artist
import com.example.samsonic.model.Genre
import com.example.samsonic.model.GenreContents
import com.example.samsonic.model.LyricLine
import com.example.samsonic.model.Playlist
import com.example.samsonic.model.SearchResults
import com.example.samsonic.model.Song

/** How many of a genre's songs a genre page lists at most. */
const val MAX_GENRE_SONGS = 5_000

/** Latest [year] first; items without one go last, and ties keep their order. */
internal fun <T> newestFirst(year: (T) -> Int?): Comparator<T> = compareByDescending { year(it) ?: Int.MIN_VALUE }

/**
 * Where the app's music comes from: a Subsonic server ([SubsonicRepository]) or
 * the music stored on this phone ([com.example.samsonic.data.device.DeviceLibrary]).
 * The UI only talks to this, through [MusicSources.library], so every screen works
 * the same whichever source is active.
 */
interface MusicLibrary {
    suspend fun getArtists(): List<Artist>

    /** The artists albums are filed under ("Various Artists" for a compilation), A to Z. */
    suspend fun getAlbumArtists(): List<Artist>

    /** A genre's albums (newest first), their album artists and up to [songCount] of its songs (newest first). */
    suspend fun getGenre(genre: String, songCount: Int = MAX_GENRE_SONGS): GenreContents

    /** Up to [count] of [genre]'s songs, newest first. */
    suspend fun getGenreSongs(genre: String, count: Int = MAX_GENRE_SONGS): List<Song>

    /** The artist and their own albums, newest first. */
    suspend fun getArtist(id: String): Pair<Artist, List<Album>>

    suspend fun getAlbum(id: String): Pair<Album, List<Song>>

    /** Every song of [albums], album after album in list order. */
    suspend fun getAlbumsSongs(albums: List<Album>): List<Song>

    /**
     * [artist]'s songs, newest first: every track of their [albums], then the tracks they
     * appear on elsewhere ([getSongsBy], or [songsBy] if already fetched). At most [limit].
     */
    suspend fun getArtistSongs(
        artist: Artist,
        albums: List<Album>,
        limit: Int? = null,
        songsBy: List<Song>? = null,
    ): List<Song>

    /** Songs whose artist is [artist]. */
    suspend fun getSongsBy(artist: Artist): List<Song>

    /** Other artists' albums that [artist] sings on, from [songsBy], newest first. */
    suspend fun getAppearsOn(artist: Artist, albums: List<Album>, songsBy: List<Song>): List<Album>

    /** Albums for a Home shelf or list, by Subsonic `getAlbumList2` [type] ("newest", "random", ...). */
    suspend fun getAlbumList(type: String = "newest", size: Int = 20): List<Album>

    suspend fun getPlaylists(): List<Playlist>

    suspend fun getPlaylist(id: String): Pair<Playlist, List<Song>>

    suspend fun getTopSongs(artistName: String, count: Int = 5): List<Song>

    /** Genres, most songs first. */
    suspend fun getGenres(): List<Genre>

    suspend fun search(query: String): SearchResults

    suspend fun star(id: String)

    suspend fun unstar(id: String)

    /**
     * Tells the server song [id] is playing: as "now playing" when [submission] is false,
     * or as a finished listen that counts towards its play count when true. The music
     * on this phone has no server to tell, so by default it does nothing.
     */
    suspend fun scrobble(id: String, submission: Boolean) {}

    suspend fun getLyrics(songId: String): List<LyricLine>

    /** What the player opens to play song [songId]. */
    fun streamUrl(songId: String): String

    /** The image to show for [coverArt] in the app, about [size] pixels across; null if there's none. */
    fun coverArtUrl(coverArt: String?, size: Int = 400): String?

    /** The image the notification and lock screen show for [coverArt]. */
    fun artworkUrl(coverArt: String?): String? = coverArtUrl(coverArt)
}
