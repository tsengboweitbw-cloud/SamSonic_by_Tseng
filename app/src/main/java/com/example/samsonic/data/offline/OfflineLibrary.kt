package com.example.samsonic.data.offline

import com.example.samsonic.data.MusicLibrary
import com.example.samsonic.model.Playlist
import com.example.samsonic.model.Song

/** The id of the Offline playlist: the songs kept on the phone, listed with a server's own playlists. */
const val OFFLINE_PLAYLIST_ID = "samsonic:offline"

val Playlist.isOffline: Boolean get() = id == OFFLINE_PLAYLIST_ID

/**
 * [inner] with a playlist of its own, Offline, among the server's: adding songs to it keeps
 * them on the phone ([OfflineStore], saved whole by [OfflineDownloader]) so they play without
 * the server. It's kept by the app, as Favourites is, not by the server; each server has its
 * own, and it's there only while a server is in use ([serverKey]), named [playlistName] in the
 * UI's language. [onAdded] runs when songs were added, to start saving them.
 */
class OfflineLibrary(
    private val inner: MusicLibrary,
    private val store: OfflineStore,
    private val serverKey: () -> String?,
    private val playlistName: () -> String,
    private val onAdded: () -> Unit,
) : MusicLibrary by inner {
    private fun offlinePlaylist(songs: List<Song>) = Playlist(
        id = OFFLINE_PLAYLIST_ID,
        name = playlistName(),
        description = "",
        songCount = songs.size,
        durationSeconds = songs.sumOf { it.durationSeconds },
        coverArt = songs.firstNotNullOfOrNull { it.coverArt },
    )

    private fun offline(): Playlist? = serverKey()?.let { offlinePlaylist(store.songs(it)) }

    override suspend fun getPlaylists(): List<Playlist> = inner.getPlaylists() + listOfNotNull(offline())

    override suspend fun getOwnPlaylists(): List<Playlist> = inner.getOwnPlaylists() + listOfNotNull(offline())

    override suspend fun getPlaylist(id: String): Pair<Playlist, List<Song>> {
        if (id != OFFLINE_PLAYLIST_ID) return inner.getPlaylist(id)
        val songs = serverKey()?.let(store::songs).orEmpty()
        return offlinePlaylist(songs) to songs
    }

    override suspend fun addSongsToPlaylist(playlistId: String, songs: List<Song>) {
        if (playlistId != OFFLINE_PLAYLIST_ID) return addToPlaylist(playlistId, songs.map { it.id })
        val server = serverKey() ?: return
        val keyed = songs.mapNotNull { song -> inner.streamCacheKey(song.id)?.let { song to it } }
        if (store.add(server, keyed) > 0) onAdded()
    }

    override suspend fun addToPlaylist(playlistId: String, songIds: List<String>) {
        // Only the songs themselves can be kept; see addSongsToPlaylist.
        if (playlistId == OFFLINE_PLAYLIST_ID) throw UnsupportedOperationException("Offline takes songs, not ids")
        inner.addToPlaylist(playlistId, songIds)
    }
}
