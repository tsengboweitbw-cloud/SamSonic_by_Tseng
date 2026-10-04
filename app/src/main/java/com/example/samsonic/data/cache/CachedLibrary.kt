package com.example.samsonic.data.cache

import com.example.samsonic.data.MusicLibrary
import com.example.samsonic.model.Album
import com.example.samsonic.model.Artist
import com.example.samsonic.model.Genre
import com.example.samsonic.model.Playlist
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.KSerializer

/** How long a saved list is shown without asking the server again. */
const val LIBRARY_CACHE_TTL_MS = 10 * 60 * 1000L

/** The albums listed per call from this size up are the Library's own list; smaller ones are Home's shelves, which stay fresh. */
private const val LIBRARY_ALBUM_LIST_MIN = 100

/**
 * [inner] with the Library's big listings (artists, albums, playlists, genres) kept on disk
 * in [store]. A listing saved less than [ttlMs] ago is returned without asking the server;
 * an older one is fetched again, and the saved one stands in if the server can't be reached.
 * [invalidate] (a pull-to-refresh) makes everything saved so far count as old.
 *
 * Saved per server, under what [serverKey] says is the one in use; with none, nothing is kept.
 * Everything else, such as what a song's like or a search turns up, goes straight through.
 */
class CachedLibrary(
    private val inner: MusicLibrary,
    private val store: LibraryCacheStore,
    private val serverKey: () -> String?,
    private val ttlMs: Long = LIBRARY_CACHE_TTL_MS,
    private val now: () -> Long = System::currentTimeMillis,
) : MusicLibrary by inner {
    @Volatile
    private var refreshedAfter = 0L

    /** Has what's saved count as old, so the next load asks the server (still falling back on it if that fails). */
    fun invalidate() {
        refreshedAfter = now()
    }

    /** Forgets what's saved for the server [key]: it has been removed. */
    suspend fun forget(key: String) = store.clear("$key$SEPARATOR")

    private suspend fun <T> cached(name: String, item: KSerializer<T>, fetch: suspend () -> List<T>): List<T> {
        val key = serverKey() ?: return fetch()
        val file = "$key$SEPARATOR$name"
        val saved = store.read(file, item)
        if (saved != null && saved.savedAt >= refreshedAfter && now() - saved.savedAt < ttlMs) return saved.items
        return try {
            fetch().also { store.write(file, item, now(), it) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            saved?.items ?: throw e
        }
    }

    override suspend fun getArtists(): List<Artist> =
        cached("artists", Artist.serializer()) { inner.getArtists() }

    override suspend fun getAlbumArtists(): List<Artist> =
        cached("albumArtists", Artist.serializer()) { inner.getAlbumArtists() }

    override suspend fun getGenres(): List<Genre> =
        cached("genres", Genre.serializer()) { inner.getGenres() }

    override suspend fun getAlbumList(type: String, size: Int): List<Album> =
        if (size < LIBRARY_ALBUM_LIST_MIN) inner.getAlbumList(type, size)
        else cached("albums.$type.$size", Album.serializer()) { inner.getAlbumList(type, size) }

    override suspend fun getPlaylists(): List<Playlist> =
        cached("playlists", Playlist.serializer()) { inner.getPlaylists() }

    // What the library's playlists are changes here, so what's saved no longer is.
    override suspend fun addToPlaylist(playlistId: String, songIds: List<String>) {
        inner.addToPlaylist(playlistId, songIds)
        invalidate()
    }

    override suspend fun createPlaylist(name: String, songIds: List<String>) {
        inner.createPlaylist(name, songIds)
        invalidate()
    }

    private companion object {
        const val SEPARATOR = "__"
    }
}
