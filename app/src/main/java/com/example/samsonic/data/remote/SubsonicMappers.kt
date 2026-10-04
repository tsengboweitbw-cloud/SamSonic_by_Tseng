package com.example.samsonic.data.remote

import com.example.samsonic.model.Album
import com.example.samsonic.model.Artist
import com.example.samsonic.model.ArtistCredit
import com.example.samsonic.model.Playlist
import com.example.samsonic.model.Song

// The wire format (SubsonicDtos.kt) onto the app's own models.

internal fun ArtistDto.toDomain() = Artist(id = id, name = name, albumCount = albumCount, coverArt = coverArt)

internal fun ArtistDetailDto.toDomain() = Artist(id = id, name = name, albumCount = albumCount, coverArt = coverArt)

internal fun AlbumDto.toDomain() = Album(
    id = id,
    title = name,
    artistId = artistId,
    artistName = artist ?: "Unknown Artist",
    year = year,
    genre = genre,
    trackCount = songCount,
    durationSeconds = duration,
    coverArt = coverArt,
    genres = genres.names(),
)

internal fun AlbumDetailDto.toDomain() = Album(
    id = id,
    title = name,
    artistId = artistId,
    artistName = artist ?: "Unknown Artist",
    year = year,
    genre = genre,
    trackCount = songCount,
    durationSeconds = duration,
    coverArt = coverArt,
    genres = genres.names(),
)

internal fun List<ItemGenreDto>.names() = map { it.name }.filter { it.isNotBlank() }

internal fun SongDto.toDomain() = Song(
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
    created = created,
    played = played,
    artists = artists.mapNotNull { ref -> ref.name?.takeIf { it.isNotBlank() }?.let { ArtistCredit(ref.id, it) } },
    albumArtistId = albumArtists.firstOrNull()?.id,
    albumArtistName = displayAlbumArtist?.takeIf { it.isNotBlank() } ?: albumArtists.firstOrNull()?.name,
    genres = genres.names(),
)

internal fun PlaylistDto.toDomain() = Playlist(
    id = id,
    name = name,
    description = comment ?: "",
    songCount = songCount,
    durationSeconds = duration,
    coverArt = coverArt,
    changed = changed,
)

internal fun PlaylistDetailDto.toDomain() = Playlist(
    id = id,
    name = name,
    description = comment ?: "",
    songCount = songCount,
    durationSeconds = duration,
    coverArt = coverArt,
    changed = changed,
)
