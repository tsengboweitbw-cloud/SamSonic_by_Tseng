package com.example.samsonic.data.device

import android.content.ContentUris
import android.content.Context
import android.database.ContentObserver
import android.provider.MediaStore
import androidx.core.content.edit
import com.example.samsonic.R
import com.example.samsonic.data.MusicLibrary
import com.example.samsonic.data.newestFirst
import com.example.samsonic.model.Album
import com.example.samsonic.model.Artist
import com.example.samsonic.model.GenreContents
import com.example.samsonic.model.LyricLine
import com.example.samsonic.model.Playlist
import com.example.samsonic.model.SearchResults
import com.example.samsonic.model.Song
import com.example.samsonic.model.genreNames
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * The music stored on this phone, read from MediaStore. The scan is kept in memory
 * and redone the next time it's needed after MediaStore reports a change (a song
 * downloaded or deleted).
 *
 * The phone keeps no playlists, play counts or lyrics for the app to read, so those
 * come back empty; likes are kept by the app itself.
 */
class DeviceLibrary(context: Context) : MusicLibrary {
    private val appContext = context.applicationContext
    private val likes = appContext.getSharedPreferences("samsonic_device_likes", Context.MODE_PRIVATE)
    private val mutex = Mutex()

    @Volatile
    private var cached: DeviceIndex? = null
    private var observing = false

    private val observer = object : ContentObserver(null) {
        override fun onChange(selfChange: Boolean) {
            cached = null
        }
    }

    private suspend fun index(): DeviceIndex = cached ?: mutex.withLock {
        cached ?: withContext(Dispatchers.IO) { scan() }.also { cached = it }
    }

    private fun scan(): DeviceIndex {
        check(appContext.hasAudioPermission()) { appContext.getString(R.string.data_allow_device_music) }
        val resolver = appContext.contentResolver
        if (!observing) {
            resolver.registerContentObserver(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, true, observer)
            observing = true
        }
        return DeviceIndex(resolver.scanDeviceTracks(), likes.getStringSet(KEY_LIKED, null).orEmpty())
    }

    override suspend fun getArtists(): List<Artist> = index().trackArtists

    override suspend fun getAlbumArtists(): List<Artist> = index().albumArtists

    override suspend fun getGenre(genre: String, songCount: Int): GenreContents {
        val index = index()
        val albums = index.genreAlbums(genre)
        return GenreContents(
            albums = albums,
            artists = index.artistsOf(albums),
            songs = index.genreSongs(genre).take(songCount),
        )
    }

    override suspend fun getGenreSongs(genre: String, count: Int): List<Song> = index().genreSongs(genre).take(count)

    override suspend fun getArtist(id: String): Pair<Artist, List<Album>> {
        val index = index()
        val artist = index.artist(id) ?: error(appContext.getString(R.string.data_artist_not_found))
        return artist to index.albumsBy(id).sortedWith(newestFirst { it.year })
    }

    override suspend fun getAlbum(id: String): Pair<Album, List<Song>> {
        val index = index()
        val album = index.albumsById[id] ?: error(appContext.getString(R.string.data_album_not_found))
        return album to index.songsOf(id)
    }

    override suspend fun getAlbumsSongs(albums: List<Album>): List<Song> {
        val index = index()
        return albums.flatMap { index.songsOf(it.id) }
    }

    override suspend fun getArtistSongs(artist: Artist, albums: List<Album>, limit: Int?, songsBy: List<Song>?): List<Song> {
        val elsewhere = songsBy ?: getSongsBy(artist)
        // Stable: within a year, songs keep their album and track order.
        val songs = (getAlbumsSongs(albums) + elsewhere).distinctBy { it.id }.sortedWith(newestFirst { it.year })
        return if (limit == null) songs else songs.take(limit)
    }

    override suspend fun getSongsBy(artist: Artist): List<Song> = index().songsBy(artist.id)

    override suspend fun getAppearsOn(artist: Artist, albums: List<Album>, songsBy: List<Song>): List<Album> {
        val index = index()
        val own = albums.mapTo(HashSet()) { it.id }
        return songsBy.mapNotNull { it.albumId }.distinct()
            .filter { it !in own }
            .mapNotNull { index.albumsById[it] }
            .filter { it.artistId != artist.id }
            .sortedWith(newestFirst { it.year })
    }

    override suspend fun getAlbumList(type: String, size: Int): List<Album> = index().albumList(type).take(size)

    override suspend fun getSongList(type: String, size: Int): List<Song> = index().songList(type).take(size)

    override suspend fun randomSongs(count: Int, genre: String?, fromYear: Int?, toYear: Int?): List<Song> =
        index().songs.filter { matches(it.genreNames, it.year, genre, fromYear, toYear) }.shuffled().take(count)

    override suspend fun randomAlbums(count: Int, genre: String?, fromYear: Int?, toYear: Int?): List<Album> =
        index().albums.filter { matches(it.genreNames, it.year, genre, fromYear, toYear) }.shuffled().take(count)

    /** Whether something of [genres] and [year] is of [genre] and between [fromYear] and [toYear], where given. */
    private fun matches(genres: List<String>, year: Int?, genre: String?, fromYear: Int?, toYear: Int?): Boolean {
        if (genre != null && genres.none { it.equals(genre, ignoreCase = true) }) return false
        if (fromYear == null && toYear == null) return true
        val y = year ?: return false
        return (fromYear == null || y >= fromYear) && (toYear == null || y <= toYear)
    }

    override suspend fun getPlaylists(): List<Playlist> = emptyList()

    override suspend fun getPlaylist(id: String): Pair<Playlist, List<Song>> = error(appContext.getString(R.string.data_playlist_not_found))

    override suspend fun getTopSongs(artistName: String, count: Int): List<Song> = emptyList()

    override suspend fun getGenres() = index().genres

    override suspend fun search(query: String): SearchResults {
        val index = index()
        val q = query.trim()
        return SearchResults(
            artists = index.trackArtists.filter { it.name.contains(q, ignoreCase = true) }.take(20),
            albums = index.albums.filter { it.title.contains(q, ignoreCase = true) }.take(20),
            songs = index.songs.filter { it.title.contains(q, ignoreCase = true) }.take(30),
        )
    }

    // From the saved likes rather than the index's, which only knew those it was built with.
    override suspend fun getLikedSongs(): List<Song> {
        val liked = likes.getStringSet(KEY_LIKED, null).orEmpty()
        return index().songs.filter { it.id in liked }.map { it.copy(liked = true) }
    }

    override suspend fun star(id: String) = setLiked(id, true)

    override suspend fun unstar(id: String) = setLiked(id, false)

    private fun setLiked(id: String, liked: Boolean) {
        val current = likes.getStringSet(KEY_LIKED, null).orEmpty()
        likes.edit { putStringSet(KEY_LIKED, if (liked) current + id else current - id) }
    }

    override suspend fun getLyrics(songId: String): List<LyricLine> = emptyList()

    override fun streamUrl(songId: String): String =
        ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, songId.toLong()).toString()

    /** The album's thumbnail. Coil loads MediaStore album URIs through the system's thumbnail API. */
    override fun coverArtUrl(coverArt: String?, size: Int): String? {
        val albumId = coverArt?.toLongOrNull() ?: return null
        return ContentUris.withAppendedId(MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI, albumId).toString()
    }

    /** The notification reads the image as a plain file, which the album art URI serves on every version. */
    override fun artworkUrl(coverArt: String?): String? {
        val albumId = coverArt?.toLongOrNull() ?: return null
        return "content://media/external/audio/albumart/$albumId"
    }

    private companion object {
        const val KEY_LIKED = "liked"
    }
}
