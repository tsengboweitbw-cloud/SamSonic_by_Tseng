package com.example.samsonic.playback

import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.example.samsonic.data.SubsonicRepository
import com.example.samsonic.model.Song

/** The Media3 item that streams this song, with the metadata the notification and lock screen show. */
fun Song.toMediaItem(repository: SubsonicRepository): MediaItem {
    val metadata = MediaMetadata.Builder()
        .setTitle(title)
        .setArtist(artistName)
        .setAlbumTitle(albumTitle)
        .apply { repository.coverArtUrl(coverArt)?.let { setArtworkUri(it.toUri()) } }
        .build()
    return MediaItem.Builder()
        .setMediaId(id)
        .setUri(repository.streamUrl(id))
        .setMediaMetadata(metadata)
        .build()
}
