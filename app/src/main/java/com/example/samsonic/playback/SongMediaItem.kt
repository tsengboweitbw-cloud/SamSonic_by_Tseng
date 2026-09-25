package com.example.samsonic.playback

import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.example.samsonic.data.MusicLibrary
import com.example.samsonic.model.Song

/** The Media3 item that plays this song (streamed, or a file on the phone), with the metadata the notification and lock screen show. */
fun Song.toMediaItem(repository: MusicLibrary): MediaItem {
    val metadata = MediaMetadata.Builder()
        .setTitle(title)
        .setArtist(artistName)
        .setAlbumTitle(albumTitle)
        .apply { repository.artworkUrl(coverArt)?.let { setArtworkUri(it.toUri()) } }
        .build()
    return MediaItem.Builder()
        .setMediaId(id)
        .setUri(repository.streamUrl(id))
        .setCustomCacheKey(repository.streamCacheKey(id))
        .setMediaMetadata(metadata)
        .build()
}
