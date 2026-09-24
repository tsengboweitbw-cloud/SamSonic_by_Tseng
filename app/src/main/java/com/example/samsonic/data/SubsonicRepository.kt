package com.example.samsonic.data

import com.example.samsonic.data.remote.AlbumDetailDto
import com.example.samsonic.data.remote.AlbumDto
import com.example.samsonic.data.remote.ArtistDetailDto
import com.example.samsonic.data.remote.ArtistDto
import com.example.samsonic.data.remote.PlaylistDetailDto
import com.example.samsonic.data.remote.PlaylistDto
import com.example.samsonic.data.remote.SongDto
import com.example.samsonic.data.remote.SubsonicApi
import com.example.samsonic.data.remote.SubsonicAuth
import com.example.samsonic.model.Album
import com.example.samsonic.model.Artist
import com.example.samsonic.model.Genre
import com.example.samsonic.model.GenreContents
import com.example.samsonic.model.LyricLine
import com.example.samsonic.model.Playlist
import com.example.samsonic.model.SearchResults
import com.example.samsonic.model.Song
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.net.URLEncoder

private const val ALBUM_FETCH_CONCURRENCY = 6
// getAlbumList2 returns at most 500 albums per call; page through up to this many.
private const val ALBUM_PAGE_SIZE = 500
private const val MAX_ALBUMS = 20_000
// How many songs a search for an artist's name looks through for their guest appearances.
private const val ARTIST_SEARCH_SONGS = 500
// getSongsByGenre returns at most 500 songs per call; a genre page lists up to this many.
private const val SONG_PAGE_SIZE = 500
private const val MAX_GENRE_SONGS = 5_000

/** Latest [year] first; items without one go last, and ties keep their order. */
private fun <T> newestFirst(year: (T) -> Int?): Comparator<T> = compareByDescending { year(it) ?: Int.MIN_VALUE }

/**
 * Talks to a Subsonic/OpenSubsonic server (Navidrome, etc.) and maps the wire DTOs onto the
 * app's domain models. There's no server-side pagination UI yet, so list calls just ask for
 * a generous page size in one shot - fine for typical home-library sizes.
 */
class SubsonicRepository(
    private val sessionManager: SessionManager,
    private val okHttpClient: OkHttpClient,
) {
    private val json = Json { ignoreUnknownKeys = true }

    private var api: SubsonicApi? = null
    private var credentials: ServerCredentials? = null

    init {
        sessionManager.credentials.value?.let { configure(it) }
    }

    private fun configure(creds: ServerCredentials) {
        credentials = creds
        val baseUrl = if (creds.serverUrl.endsWith("/")) creds.serverUrl else "${creds.serverUrl}/"
        val retrofit = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
        api = retrofit.create(SubsonicApi::class.java)
    }

    val isConnected: Boolean get() = api != null

    suspend fun connect(serverUrl: String, username: String, password: String): Result<Unit> = runCatching {
        val normalizedUrl = normalizeUrl(serverUrl)
        configure(ServerCredentials(normalizedUrl, username, password))
        val body = requireApi().ping(SubsonicAuth.params(username, password)).response
        if (body.status != "ok") {
            error(body.error?.message ?: "Could not connect to server")
        }
        sessionManager.save(ServerCredentials(normalizedUrl, username, password))
    }.onFailure {
        api = null
        credentials = null
    }

    fun signOut() {
        sessionManager.clear()
        api = null
        credentials = null
    }

    private fun normalizeUrl(rawUrl: String): String {
        val trimmed = rawUrl.trim().trimEnd('/')
        val withScheme = if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            trimmed
        } else {
            "http://$trimmed"
        }
        return "$withScheme/"
    }

    private fun requireApi(): SubsonicApi = api ?: error("Not connected to a server")
    private fun requireCreds(): ServerCredentials = credentials ?: error("Not connected to a server")
    private fun authParams(): Map<String, String> {
        val creds = requireCreds()
        return SubsonicAuth.params(creds.username, creds.password)
    }

    suspend fun getArtists(): List<Artist> {
        val body = requireApi().getArtists(authParams()).response
        return body.artists?.index.orEmpty()
            .flatMap { it.artist }
            .map { it.toDomain() }
            .sortedBy { it.name.lowercase() }
    }

    /**
     * Album artists (the artist an album is filed under, e.g. "Various Artists" for
     * a compilation) rather than every track artist, which is what getArtists lists
     * on servers that index by track. Built from the albums themselves - an album's
     * `artist` is its album artist - paging through all of them, with each artist's
     * picture taken from getArtists where it has one.
     */
    suspend fun getAlbumArtists(): List<Artist> = coroutineScope {
        val covers = async { artistCovers() }
        albumArtistsOf(allAlbums(mapOf("type" to "alphabeticalByArtist")), covers.await())
    }

    /**
     * A genre's albums (newest first), their album artists (as [getAlbumArtists]
     * builds them) and up to [songCount] of its songs (newest first).
     */
    suspend fun getGenre(genre: String, songCount: Int = MAX_GENRE_SONGS): GenreContents = coroutineScope {
        val songs = async { if (songCount > 0) getGenreSongs(genre, songCount) else emptyList() }
        val covers = async { artistCovers() }
        val albums = allAlbums(mapOf("type" to "byGenre", "genre" to genre))
        GenreContents(
            albums = albums.map { it.toDomain() }.sortedWith(newestFirst { it.year }),
            artists = albumArtistsOf(albums, covers.await()),
            songs = songs.await(),
        )
    }

    /** Up to [count] of [genre]'s songs, newest first; getSongsByGenre pages at 500. */
    suspend fun getGenreSongs(genre: String, count: Int = MAX_GENRE_SONGS): List<Song> = buildList {
        do {
            val size = minOf(SONG_PAGE_SIZE, count - this.size)
            val params = authParams() + mapOf("genre" to genre, "count" to "$size", "offset" to "${this.size}")
            val page = requireApi().getSongsByGenre(params).response.songsByGenre?.song.orEmpty()
            addAll(page.map { it.toDomain() })
        } while (page.size == size && this.size < count)
    }.distinctBy { it.id }.sortedWith(newestFirst { it.year })

    /** Every album getAlbumList2 lists for [query] (its type and filters), page after page. */
    private suspend fun allAlbums(query: Map<String, String>): List<AlbumDto> = buildList {
        var offset = 0
        do {
            val params = authParams() + query + mapOf("size" to "$ALBUM_PAGE_SIZE", "offset" to "$offset")
            val page = requireApi().getAlbumList2(params).response.albumList2?.album.orEmpty()
            addAll(page)
            offset += page.size
        } while (page.size == ALBUM_PAGE_SIZE && offset < MAX_ALBUMS)
    }

    /** Each artist's picture by id, from getArtists; empty if that fails. */
    private suspend fun artistCovers(): Map<String, String?> =
        runCatching { getArtists() }.getOrDefault(emptyList()).associate { it.id to it.coverArt }

    /** The artists [albums] are filed under, A to Z, with pictures from [coverById] where it has one. */
    private fun albumArtistsOf(albums: List<AlbumDto>, coverById: Map<String, String?>): List<Artist> {
        return albums
            .filter { !it.artist.isNullOrBlank() }
            // By id where the server gives one, else by name.
            .groupBy { it.artistId ?: "name:${it.artist}" }
            .map { (key, group) ->
                val id = group.first().artistId
                Artist(
                    id = id ?: key,
                    name = group.first().artist.orEmpty(),
                    albumCount = group.size,
                    coverArt = id?.let { coverById[it] },
                )
            }
            .sortedBy { it.name.lowercase() }
    }

    suspend fun getArtist(id: String): Pair<Artist, List<Album>> {
        val detail = requireApi().getArtist(authParams() + ("id" to id)).response.artist
            ?: error("Artist not found")
        // Newest first, as every list on an artist's pages is.
        val albums = detail.album.map { it.toDomain() }.sortedWith(newestFirst { it.year })
        return detail.toDomain() to albums
    }

    suspend fun getAlbum(id: String): Pair<Album, List<Song>> {
        val detail = requireApi().getAlbum(authParams() + ("id" to id)).response.album
            ?: error("Album not found")
        val songs = detail.song.map { it.toDomain() }
        return detail.toDomain() to songs
    }

    /**
     * Every song of [albums], album after album in list order. Albums are fetched a few at
     * a time; one that fails to load is skipped rather than failing the whole list.
     */
    suspend fun getAlbumsSongs(albums: List<Album>): List<Song> = coroutineScope {
        val permits = Semaphore(ALBUM_FETCH_CONCURRENCY)
        albums.map { album ->
            async { permits.withPermit { runCatching { getAlbum(album.id).second }.getOrDefault(emptyList()) } }
        }.awaitAll().flatten()
    }

    /**
     * [artist]'s songs: every track of their [albums], album after album, then the
     * tracks they appear on elsewhere ([getSongsBy], or [songsBy] if already fetched).
     * With [limit], albums are fetched only until that many songs are in hand.
     */
    suspend fun getArtistSongs(
        artist: Artist,
        albums: List<Album>,
        limit: Int? = null,
        songsBy: List<Song>? = null,
    ): List<Song> = coroutineScope {
        val elsewhere = async { songsBy ?: runCatching { getSongsBy(artist) }.getOrDefault(emptyList()) }
        val onAlbums = if (limit == null) {
            getAlbumsSongs(albums)
        } else buildList {
            for (chunk in albums.chunked(ALBUM_FETCH_CONCURRENCY)) {
                addAll(getAlbumsSongs(chunk))
                if (size >= limit) break
            }
        }
        // Stable: within a year, songs keep their album and track order.
        val songs = (onAlbums + elsewhere.await()).distinctBy { it.id }.sortedWith(newestFirst { it.year })
        if (limit == null) songs else songs.take(limit)
    }

    /** Songs whose artist is [artist], from a search for their name (which also matches titles). */
    suspend fun getSongsBy(artist: Artist): List<Song> {
        val params = authParams() + mapOf(
            "query" to artist.name,
            "artistCount" to "0",
            "albumCount" to "0",
            "songCount" to ARTIST_SEARCH_SONGS.toString(),
        )
        return requireApi().search3(params).response.searchResult3?.song.orEmpty()
            .filter { it.artistId == artist.id }
            .map { it.toDomain() }
    }

    /**
     * Other artists' albums that [artist] sings on: the albums of [songsBy] (their
     * songs, from [getSongsBy]) that aren't among their own [albums], in first-seen order.
     * An album that fails to load is left out.
     */
    suspend fun getAppearsOn(artist: Artist, albums: List<Album>, songsBy: List<Song>): List<Album> = coroutineScope {
        val own = albums.mapTo(HashSet()) { it.id }
        val ids = songsBy.mapNotNull { it.albumId }.distinct().filter { it !in own }
        val permits = Semaphore(ALBUM_FETCH_CONCURRENCY)
        ids.map { id -> async { permits.withPermit { runCatching { getAlbum(id).first }.getOrNull() } } }
            .awaitAll()
            .filterNotNull()
            .filter { it.artistId != artist.id }
            .sortedWith(newestFirst { it.year })
    }

    suspend fun getAlbumList(type: String = "newest", size: Int = 20): List<Album> {
        val params = authParams() + mapOf("type" to type, "size" to size.toString())
        return requireApi().getAlbumList2(params).response.albumList2?.album.orEmpty().map { it.toDomain() }
    }

    suspend fun getPlaylists(): List<Playlist> {
        return requireApi().getPlaylists(authParams()).response.playlists?.playlist.orEmpty().map { it.toDomain() }
    }

    suspend fun getPlaylist(id: String): Pair<Playlist, List<Song>> {
        val detail = requireApi().getPlaylist(authParams() + ("id" to id)).response.playlist
            ?: error("Playlist not found")
        val songs = detail.entry.map { it.toDomain() }
        return detail.toDomain() to songs
    }

    suspend fun getTopSongs(artistName: String, count: Int = 5): List<Song> {
        val params = authParams() + mapOf("artist" to artistName, "count" to count.toString())
        return requireApi().getTopSongs(params).response.topSongs?.song.orEmpty().map { it.toDomain() }
    }

    suspend fun getGenres(): List<Genre> {
        return requireApi().getGenres(authParams()).response.genres?.genre.orEmpty()
            .map { Genre(it.value, it.songCount) }
            .sortedByDescending { it.songCount }
    }

    suspend fun search(query: String): SearchResults {
        val params = authParams() + mapOf(
            "query" to query,
            "artistCount" to "20",
            "albumCount" to "20",
            "songCount" to "30",
        )
        val result = requireApi().search3(params).response.searchResult3
        return SearchResults(
            artists = result?.artist.orEmpty().map { it.toDomain() },
            albums = result?.album.orEmpty().map { it.toDomain() },
            songs = result?.song.orEmpty().map { it.toDomain() },
        )
    }

    suspend fun getRandomSongs(count: Int = 20): List<Song> {
        val params = authParams() + ("size" to count.toString())
        return requireApi().getRandomSongs(params).response.randomSongs?.song.orEmpty().map { it.toDomain() }
    }

    suspend fun getStarredSongs(): List<Song> {
        return requireApi().getStarred2(authParams()).response.starred2?.song.orEmpty().map { it.toDomain() }
    }

    suspend fun star(id: String) {
        requireApi().star(authParams() + ("id" to id))
    }

    suspend fun unstar(id: String) {
        requireApi().unstar(authParams() + ("id" to id))
    }

    suspend fun scrobble(id: String, submission: Boolean) {
        requireApi().scrobble(authParams() + mapOf("id" to id, "submission" to submission.toString()))
    }

    suspend fun getLyrics(songId: String): List<LyricLine> {
        return runCatching {
            val lists = requireApi().getLyricsBySongId(authParams() + ("id" to songId)).response.lyricsList
            val synced = lists?.structuredLyrics.orEmpty().firstOrNull { it.synced } ?: lists?.structuredLyrics.orEmpty().firstOrNull()
            synced?.line.orEmpty().map { LyricLine(it.start ?: 0L, it.value) }
        }.getOrDefault(emptyList())
    }

    fun streamUrl(songId: String): String = buildUrl("rest/stream.view", mapOf("id" to songId))

    fun coverArtUrl(coverArt: String?, size: Int = 400): String? {
        if (coverArt.isNullOrBlank()) return null
        return buildUrl("rest/getCoverArt.view", mapOf("id" to coverArt, "size" to size.toString()))
    }

    private fun buildUrl(path: String, extra: Map<String, String>): String {
        val creds = requireCreds()
        val params = SubsonicAuth.params(creds.username, creds.password) + extra
        val query = params.entries.joinToString("&") { (k, v) -> "$k=${URLEncoder.encode(v, "UTF-8")}" }
        return "${creds.serverUrl}rest/${path.removePrefix("rest/")}?$query"
    }

    private fun ArtistDto.toDomain() = Artist(id = id, name = name, albumCount = albumCount, coverArt = coverArt)

    private fun ArtistDetailDto.toDomain() = Artist(id = id, name = name, albumCount = albumCount, coverArt = coverArt)

    private fun AlbumDto.toDomain() = Album(
        id = id,
        title = name,
        artistId = artistId,
        artistName = artist ?: "Unknown Artist",
        year = year,
        genre = genre,
        trackCount = songCount,
        durationSeconds = duration,
        coverArt = coverArt,
    )

    private fun AlbumDetailDto.toDomain() = Album(
        id = id,
        title = name,
        artistId = artistId,
        artistName = artist ?: "Unknown Artist",
        year = year,
        genre = genre,
        trackCount = songCount,
        durationSeconds = duration,
        coverArt = coverArt,
    )

    private fun SongDto.toDomain() = Song(
        id = id,
        title = title,
        artistId = artistId,
        artistName = artist ?: "Unknown Artist",
        albumId = albumId,
        albumTitle = album ?: "",
        trackNumber = track ?: 0,
        durationSeconds = duration,
        coverArt = coverArt,
        liked = starred != null,
        suffix = suffix,
        bitRate = bitRate,
        samplingRate = samplingRate,
        bitDepth = bitDepth,
        year = year,
        genre = genre,
        discNumber = discNumber,
        sizeBytes = size,
        contentType = contentType,
        path = path,
        playCount = playCount,
        channelCount = channelCount,
        albumArtistId = albumArtists.firstOrNull()?.id,
        albumArtistName = displayAlbumArtist?.takeIf { it.isNotBlank() } ?: albumArtists.firstOrNull()?.name,
    )

    private fun PlaylistDto.toDomain() = Playlist(
        id = id,
        name = name,
        description = comment ?: "",
        songCount = songCount,
        durationSeconds = duration,
        coverArt = coverArt,
    )

    private fun PlaylistDetailDto.toDomain() = Playlist(
        id = id,
        name = name,
        description = comment ?: "",
        songCount = songCount,
        durationSeconds = duration,
        coverArt = coverArt,
    )
}
