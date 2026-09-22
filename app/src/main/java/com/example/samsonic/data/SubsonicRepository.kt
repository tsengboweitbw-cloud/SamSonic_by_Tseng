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
import com.example.samsonic.model.LyricLine
import com.example.samsonic.model.Playlist
import com.example.samsonic.model.SearchResults
import com.example.samsonic.model.Song
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.net.URLEncoder

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

    suspend fun getArtist(id: String): Pair<Artist, List<Album>> {
        val detail = requireApi().getArtist(authParams() + ("id" to id)).response.artist
            ?: error("Artist not found")
        val albums = detail.album.map { it.toDomain() }
        return detail.toDomain() to albums
    }

    suspend fun getAlbum(id: String): Pair<Album, List<Song>> {
        val detail = requireApi().getAlbum(authParams() + ("id" to id)).response.album
            ?: error("Album not found")
        val songs = detail.song.map { it.toDomain() }
        return detail.toDomain() to songs
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
