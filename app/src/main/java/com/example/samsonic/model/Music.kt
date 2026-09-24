package com.example.samsonic.model

/**
 * Domain models for the UI layer, populated from a Subsonic/OpenSubsonic server
 * (see data/remote for the wire format and data/SubsonicRepository for the mapping).
 */

data class Artist(
    val id: String,
    val name: String,
    val albumCount: Int,
    val coverArt: String?,
    val bio: String = "",
)

data class Album(
    val id: String,
    val title: String,
    val artistId: String?,
    val artistName: String,
    val year: Int?,
    val genre: String?,
    val trackCount: Int,
    val durationSeconds: Int,
    val coverArt: String?,
)

data class Song(
    val id: String,
    val title: String,
    val artistId: String?,
    val artistName: String,
    val albumId: String?,
    val albumTitle: String,
    val trackNumber: Int,
    val durationSeconds: Int,
    val coverArt: String?,
    val liked: Boolean = false,
    val suffix: String? = null,
    val bitRate: Int? = null,
    val samplingRate: Int? = null,
    val bitDepth: Int? = null,
    // Shown in the song info sheet; each is whatever the server reported, else null.
    val year: Int? = null,
    val genre: String? = null,
    val discNumber: Int? = null,
    val sizeBytes: Long? = null,
    val contentType: String? = null,
    val path: String? = null,
    val playCount: Long? = null,
    val channelCount: Int? = null,
    // OpenSubsonic only; on other servers the album artist comes from the album.
    val albumArtistId: String? = null,
    val albumArtistName: String? = null,
)

data class Playlist(
    val id: String,
    val name: String,
    val description: String,
    val songCount: Int,
    val durationSeconds: Int,
    val coverArt: String?,
)

data class Genre(
    val name: String,
    val songCount: Int,
)

data class LyricLine(
    val timeMs: Long,
    val text: String,
)

data class SearchResults(
    val artists: List<Artist>,
    val albums: List<Album>,
    val songs: List<Song>,
)

/** Deterministic fallback color used for the gradient placeholder when there's no cover art. */
fun String.artSeed(): Int = hashCode()
