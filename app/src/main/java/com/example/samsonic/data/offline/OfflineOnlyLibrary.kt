package com.example.samsonic.data.offline

import com.example.samsonic.data.MusicLibrary
import com.example.samsonic.model.Album
import com.example.samsonic.model.Artist
import com.example.samsonic.model.Genre
import com.example.samsonic.model.GenreContents
import com.example.samsonic.model.Playlist
import com.example.samsonic.model.SearchResults
import com.example.samsonic.model.Song
import com.example.samsonic.model.genreNames

/**
 * [inner] seen through the songs kept for offline: every list is made from those songs
 * alone ([OfflineStore] holds each with its details), so artists, albums, genres, search and
 * Home show only what plays without the server, and nothing needs the server to load. Playlists
 * are the server's, cut down to the kept songs; those with none are left out. Lyrics, likes,
 * plays and the stream itself still go to [inner].
 */
class OfflineOnlyLibrary(
    private val inner: MusicLibrary,
    private val store: OfflineStore,
    private val serverKey: () -> String?,
) : MusicLibrary by inner {
    private fun kept(): List<Song> = serverKey()?.let(store::songs).orEmpty()

    private fun albumOf(songs: List<Song>): Album {
        val first = songs.first()
        return Album(
            id = first.albumId.orEmpty(),
            title = first.albumTitle,
            artistId = first.albumArtistId ?: first.artistId,
            artistName = first.albumArtistName ?: first.artistName,
            year = songs.firstNotNullOfOrNull { it.year },
            genre = first.genre,
            trackCount = songs.size,
            durationSeconds = songs.sumOf { it.durationSeconds },
            coverArt = songs.firstNotNullOfOrNull { it.coverArt },
            genres = first.genres,
        )
    }

    private fun albumsOf(songs: List<Song>): List<Album> =
        songs.filter { it.albumId != null }.groupBy { it.albumId }.values.map(::albumOf)

    private fun artistsOf(songs: List<Song>, byAlbumArtist: Boolean): List<Artist> {
        val credited = songs.mapNotNull { song ->
            val id = if (byAlbumArtist) song.albumArtistId ?: song.artistId else song.artistId
            val name = if (byAlbumArtist) song.albumArtistName ?: song.artistName else song.artistName
            id?.let { Triple(it, name, song) }
        }
        return credited.groupBy { it.first }.map { (id, group) ->
            Artist(
                id = id,
                name = group.first().second,
                albumCount = group.mapNotNull { it.third.albumId }.distinct().size,
                coverArt = group.firstNotNullOfOrNull { it.third.coverArt },
            )
        }.sortedBy { it.name.lowercase() }
    }

    private fun List<Song>.inAlbumOrder() = sortedWith(compareBy({ it.discNumber ?: 1 }, { it.trackNumber }))

    private fun List<Album>.newestFirst() = sortedByDescending { it.year ?: Int.MIN_VALUE }

    override suspend fun getArtists(): List<Artist> = artistsOf(kept(), byAlbumArtist = false)

    override suspend fun getAlbumArtists(): List<Artist> = artistsOf(kept(), byAlbumArtist = true)

    override suspend fun getArtist(id: String): Pair<Artist, List<Album>> {
        val songs = kept()
        val artist = artistsOf(songs, byAlbumArtist = true).firstOrNull { it.id == id }
            ?: artistsOf(songs, byAlbumArtist = false).first { it.id == id }
        val owned = albumsOf(songs.filter { (it.albumArtistId ?: it.artistId) == id })
        return artist to owned.newestFirst()
    }

    override suspend fun getAlbum(id: String): Pair<Album, List<Song>> {
        val songs = kept().filter { it.albumId == id }.inAlbumOrder()
        return albumOf(songs) to songs
    }

    override suspend fun getAlbumsSongs(albums: List<Album>): List<Song> {
        val songs = kept()
        return albums.flatMap { album -> songs.filter { it.albumId == album.id }.inAlbumOrder() }
    }

    override suspend fun getSongsBy(artist: Artist): List<Song> = kept().filter { it.artistId == artist.id }

    override suspend fun getArtistSongs(artist: Artist, albums: List<Album>, limit: Int?, songsBy: List<Song>?): List<Song> {
        val own = getAlbumsSongs(albums)
        val ids = own.mapTo(HashSet()) { it.id }
        val all = own + (songsBy ?: getSongsBy(artist)).filter { it.id !in ids }
        return if (limit != null) all.take(limit) else all
    }

    override suspend fun getAppearsOn(artist: Artist, albums: List<Album>, songsBy: List<Song>): List<Album> {
        val own = albums.mapTo(HashSet()) { it.id }
        return albumsOf(songsBy.filter { it.albumId !in own }).newestFirst()
    }

    override suspend fun getAlbumList(type: String, size: Int): List<Album> {
        val all = albumsOf(kept())
        val ordered = when (type) {
            "random" -> all.shuffled()
            "alphabeticalByName" -> all.sortedBy { it.title.lowercase() }
            "alphabeticalByArtist" -> all.sortedBy { it.artistName.lowercase() }
            else -> all.newestFirst()
        }
        return ordered.take(size)
    }

    override suspend fun getSongList(type: String, size: Int): List<Song> {
        val songs = kept()
        val ordered = when (type) {
            "random" -> songs.shuffled()
            "recent" -> songs.filter { it.played != null }.sortedByDescending { it.played }
            "frequent" -> songs.filter { (it.playCount ?: 0) > 0 }.sortedByDescending { it.playCount }
            else -> songs.sortedByDescending { it.created.orEmpty() }
        }
        return ordered.take(size)
    }

    private fun List<Song>.within(genre: String?, fromYear: Int?, toYear: Int?) = filter { song ->
        (genre == null || song.genreNames.any { it.equals(genre, ignoreCase = true) }) &&
            (fromYear == null || (song.year ?: Int.MIN_VALUE) >= fromYear) &&
            (toYear == null || (song.year ?: Int.MAX_VALUE) <= toYear)
    }

    override suspend fun randomSongs(count: Int, genre: String?, fromYear: Int?, toYear: Int?): List<Song> =
        kept().within(genre, fromYear, toYear).shuffled().take(count)

    override suspend fun randomAlbums(count: Int, genre: String?, fromYear: Int?, toYear: Int?): List<Album> =
        albumsOf(kept().within(genre, fromYear, toYear)).shuffled().take(count)

    override suspend fun getGenres(): List<Genre> =
        kept().flatMap { it.genreNames }
            .groupBy { it.lowercase() }
            .map { (_, names) -> Genre(names.first(), names.size) }
            .sortedByDescending { it.songCount }

    override suspend fun getGenreSongs(genre: String, count: Int): List<Song> =
        kept().within(genre, null, null).sortedByDescending { it.year ?: Int.MIN_VALUE }.take(count)

    override suspend fun getGenre(genre: String, songCount: Int): GenreContents {
        val songs = getGenreSongs(genre, songCount)
        return GenreContents(
            albums = albumsOf(songs).newestFirst(),
            artists = artistsOf(songs, byAlbumArtist = true),
            songs = songs,
        )
    }

    override suspend fun getTopSongs(artistName: String, count: Int): List<Song> =
        kept().filter { it.artistName.equals(artistName, ignoreCase = true) }
            .sortedByDescending { it.playCount ?: 0 }.take(count)

    override suspend fun getLikedSongs(): List<Song> {
        val ids = kept().mapTo(HashSet()) { it.id }
        return inner.getLikedSongs().filter { it.id in ids }
    }

    override suspend fun search(query: String): SearchResults {
        val needle = query.trim().lowercase()
        if (needle.isEmpty()) return SearchResults(emptyList(), emptyList(), emptyList())
        val songs = kept()
        return SearchResults(
            artists = artistsOf(songs, byAlbumArtist = false).filter { needle in it.name.lowercase() },
            albums = albumsOf(songs).filter { needle in it.title.lowercase() || needle in it.artistName.lowercase() },
            songs = songs.filter {
                needle in it.title.lowercase() || needle in it.artistName.lowercase() || needle in it.albumTitle.lowercase()
            },
        )
    }

    override suspend fun getPlaylists(): List<Playlist> = inner.getPlaylists().mapNotNull { playlist ->
        runCatching { getPlaylist(playlist.id).first }.getOrNull()?.takeIf { it.songCount > 0 }
    }

    override suspend fun getPlaylist(id: String): Pair<Playlist, List<Song>> {
        val (playlist, songs) = inner.getPlaylist(id)
        val ids = kept().mapTo(HashSet()) { it.id }
        val left = songs.filter { it.id in ids }
        return playlist.copy(songCount = left.size, durationSeconds = left.sumOf { it.durationSeconds }) to left
    }
}
