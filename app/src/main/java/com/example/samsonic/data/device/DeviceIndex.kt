package com.example.samsonic.data.device

import com.example.samsonic.data.newestFirst
import com.example.samsonic.model.Album
import com.example.samsonic.model.Artist
import com.example.samsonic.model.Genre
import com.example.samsonic.model.Song

private const val VARIOUS_ARTISTS = "Various Artists"

/**
 * An artist's id, from their name, since MediaStore has no id for an album artist.
 * The same name always gets the same id, so links keep working after a rescan.
 */
internal fun deviceArtistId(name: String): String {
    // 64-bit FNV-1a: collisions are vanishingly unlikely for a phone's worth of artists.
    var hash = -0x340d631b7bdddcdbL
    for (ch in name.lowercase()) {
        hash = (hash xor ch.code.toLong()) * 0x100000001b3L
    }
    return "a" + hash.toULong().toString(16)
}

/**
 * The phone's music as the app's models: [DeviceTrack]s grouped into albums and artists
 * the way a Subsonic server would. Built once per scan; every lookup is in memory.
 *
 * Ids are MediaStore's for songs and albums (a song's cover art id is its album's id),
 * and [deviceArtistId] for artists.
 */
internal class DeviceIndex(tracks: List<DeviceTrack>, likedIds: Set<String>) {
    /** Each album's songs, by disc and track. */
    private val songsByAlbum: Map<String, List<Song>>
    /** Newest file first, for "Recently Added". */
    private val albumsByDateAdded: List<Album>
    private val genresBySong: Map<String, String>

    /** A to Z by album artist, then title. */
    val albums: List<Album>
    val albumsById: Map<String, Album>
    val songs: List<Song>

    init {
        val groups = tracks.groupBy { it.albumId.toString() }
        albums = groups.map { (id, group) -> buildAlbum(id, group) }
            .sortedWith(compareBy({ it.artistName.lowercase() }, { it.title.lowercase() }))
        albumsById = albums.associateBy { it.id }
        songsByAlbum = albums.associate { album ->
            album.id to groups.getValue(album.id)
                .sortedWith(compareBy({ it.disc ?: 0 }, { it.track }, { it.title.lowercase() }))
                .map { it.toSong(album, liked = it.id.toString() in likedIds) }
        }
        songs = albums.flatMap { songsByAlbum.getValue(it.id) }
        val added = groups.mapValues { (_, group) -> group.maxOf { it.dateAdded } }
        albumsByDateAdded = albums.sortedByDescending { added[it.id] ?: 0L }
        genresBySong = tracks.mapNotNull { t -> t.genre?.let { t.id.toString() to it } }.toMap()
    }

    /**
     * The album its [tracks] make up. Its artist is the album artist the tags agree on,
     * else the one artist on every track, else "Various Artists".
     */
    private fun buildAlbum(id: String, tracks: List<DeviceTrack>): Album {
        val artist = tracks.mapNotNull { it.albumArtist }.mostCommon()
            ?: tracks.map { it.artist }.distinct().singleOrNull()
            ?: VARIOUS_ARTISTS
        return Album(
            id = id,
            title = tracks.first().album,
            artistId = deviceArtistId(artist),
            artistName = artist,
            year = tracks.mapNotNull { it.year }.maxOrNull(),
            genre = tracks.mapNotNull { it.genre }.mostCommon(),
            trackCount = tracks.size,
            durationSeconds = (tracks.sumOf { it.durationMs } / 1000).toInt(),
            coverArt = id,
        )
    }

    private fun DeviceTrack.toSong(album: Album, liked: Boolean) = Song(
        id = id.toString(),
        title = title,
        artistId = deviceArtistId(artist),
        artistName = artist,
        albumId = album.id,
        albumTitle = album.title,
        trackNumber = track,
        durationSeconds = (durationMs / 1000).toInt(),
        coverArt = album.coverArt,
        liked = liked,
        suffix = path?.substringAfterLast('.', "")?.lowercase()?.takeIf { it.isNotEmpty() },
        bitRate = bitRate?.let { it / 1000 },
        year = year,
        genre = genre,
        discNumber = disc,
        sizeBytes = sizeBytes,
        contentType = mimeType,
        path = path,
        albumArtistId = album.artistId,
        albumArtistName = album.artistName,
    )

    fun songsOf(albumId: String): List<Song> = songsByAlbum[albumId].orEmpty()

    /** Artists albums are filed under, A to Z; a picture is their first album's cover. */
    val albumArtists: List<Artist> by lazy { artistsOf(albums) }

    /** Everyone credited on a track, A to Z, with how many albums are filed under them. */
    val trackArtists: List<Artist> by lazy {
        val own = albums.groupBy { it.artistName }
        songs.groupBy { it.artistName }
            .map { (name, songs) ->
                Artist(
                    id = deviceArtistId(name),
                    name = name,
                    albumCount = own[name]?.size ?: 0,
                    coverArt = own[name]?.first()?.coverArt ?: songs.first().coverArt,
                )
            }
            .sortedBy { it.name.lowercase() }
    }

    /** The artist with this id, whether they have albums of their own or only appear on tracks. */
    fun artist(id: String): Artist? =
        albumArtists.firstOrNull { it.id == id } ?: trackArtists.firstOrNull { it.id == id }

    fun albumsBy(artistId: String): List<Album> = albums.filter { it.artistId == artistId }

    fun songsBy(artistId: String): List<Song> = songs.filter { it.artistId == artistId }

    fun albumList(type: String): List<Album> = when (type) {
        "newest" -> albumsByDateAdded
        "random" -> albums.shuffled()
        "alphabeticalByName" -> albums.sortedBy { it.title.lowercase() }
        "alphabeticalByArtist" -> albums
        // "recent", "frequent" and the like need play history, which the phone doesn't keep.
        else -> emptyList()
    }

    val genres: List<Genre> by lazy {
        genresBySong.values.groupingBy { it }.eachCount()
            .map { (name, count) -> Genre(name, count) }
            .sortedByDescending { it.songCount }
    }

    fun genreSongs(genre: String): List<Song> =
        songs.filter { genresBySong[it.id] == genre }.sortedWith(newestFirst { it.year })

    /** Albums with at least one track in [genre], newest first. */
    fun genreAlbums(genre: String): List<Album> {
        val ids = genreSongs(genre).mapNotNullTo(HashSet()) { it.albumId }
        return albums.filter { it.id in ids }.sortedWith(newestFirst { it.year })
    }

    /** The artists [albums] are filed under, A to Z, each counting only their albums in the list. */
    fun artistsOf(albums: List<Album>): List<Artist> =
        albums.groupBy { it.artistName }
            .map { (name, group) ->
                Artist(id = deviceArtistId(name), name = name, albumCount = group.size, coverArt = group.first().coverArt)
            }
            .sortedBy { it.name.lowercase() }
}

/** The value that occurs most often, or null for an empty list; ties go to the first seen. */
private fun List<String>.mostCommon(): String? =
    groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
