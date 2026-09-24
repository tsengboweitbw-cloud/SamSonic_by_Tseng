package com.example.samsonic.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Wire format for the Subsonic/OpenSubsonic REST API (f=json). Every endpoint returns the
 * same envelope with one populated field depending on which method was called; everything
 * is nullable/defaulted so unknown or missing fields never crash parsing.
 */
@Serializable
data class SubsonicEnvelope(
    @SerialName("subsonic-response") val response: SubsonicResponseBody,
)

@Serializable
data class SubsonicResponseBody(
    val status: String,
    val version: String? = null,
    val error: SubsonicErrorDto? = null,
    val artists: ArtistsIndexDto? = null,
    val artist: ArtistDetailDto? = null,
    val album: AlbumDetailDto? = null,
    val albumList2: AlbumListDto? = null,
    val playlists: PlaylistsDto? = null,
    val playlist: PlaylistDetailDto? = null,
    val genres: GenresDto? = null,
    val searchResult3: SearchResult3Dto? = null,
    val randomSongs: SongsDto? = null,
    val topSongs: SongsDto? = null,
    val songsByGenre: SongsDto? = null,
    val starred2: Starred2Dto? = null,
    val lyricsList: LyricsListDto? = null,
)

@Serializable
data class SubsonicErrorDto(val code: Int, val message: String? = null)

@Serializable
data class ArtistsIndexDto(val index: List<ArtistIndexGroupDto> = emptyList())

@Serializable
data class ArtistIndexGroupDto(val name: String, val artist: List<ArtistDto> = emptyList())

@Serializable
data class ArtistDto(
    val id: String,
    val name: String,
    val coverArt: String? = null,
    val albumCount: Int = 0,
    val starred: String? = null,
)

@Serializable
data class ArtistDetailDto(
    val id: String,
    val name: String,
    val coverArt: String? = null,
    val albumCount: Int = 0,
    val starred: String? = null,
    val album: List<AlbumDto> = emptyList(),
)

@Serializable
data class AlbumDto(
    val id: String,
    val name: String,
    val artist: String? = null,
    val artistId: String? = null,
    val coverArt: String? = null,
    val songCount: Int = 0,
    val duration: Int = 0,
    val year: Int? = null,
    val genre: String? = null,
    val starred: String? = null,
)

@Serializable
data class AlbumDetailDto(
    val id: String,
    val name: String,
    val artist: String? = null,
    val artistId: String? = null,
    val coverArt: String? = null,
    val songCount: Int = 0,
    val duration: Int = 0,
    val year: Int? = null,
    val genre: String? = null,
    val starred: String? = null,
    val song: List<SongDto> = emptyList(),
)

@Serializable
data class AlbumListDto(val album: List<AlbumDto> = emptyList())

@Serializable
data class SongDto(
    val id: String,
    val title: String,
    val album: String? = null,
    val albumId: String? = null,
    val artist: String? = null,
    val artistId: String? = null,
    val track: Int? = null,
    val year: Int? = null,
    val genre: String? = null,
    val coverArt: String? = null,
    val duration: Int = 0,
    val starred: String? = null,
    // Audio quality info, shown in Now Playing. suffix/bitRate are core
    // Subsonic; samplingRate/bitDepth are OpenSubsonic extensions, so
    // older servers just omit them - hence all nullable.
    val suffix: String? = null,
    val bitRate: Int? = null,
    val samplingRate: Int? = null,
    val bitDepth: Int? = null,
    val discNumber: Int? = null,
    val size: Long? = null,
    val contentType: String? = null,
    val path: String? = null,
    val playCount: Long? = null,
    // OpenSubsonic extension.
    val channelCount: Int? = null,
    // OpenSubsonic extension: every artist of the song, where [artist] has them in one string.
    val artists: List<ArtistRefDto> = emptyList(),
    // OpenSubsonic extensions: the album's artist, which core Subsonic leaves
    // to the album itself (getAlbum).
    val albumArtists: List<ArtistRefDto> = emptyList(),
    val displayAlbumArtist: String? = null,
)

@Serializable
data class ArtistRefDto(val id: String? = null, val name: String? = null)

@Serializable
data class SongsDto(val song: List<SongDto> = emptyList())

@Serializable
data class PlaylistsDto(val playlist: List<PlaylistDto> = emptyList())

@Serializable
data class PlaylistDto(
    val id: String,
    val name: String,
    val comment: String? = null,
    val songCount: Int = 0,
    val duration: Int = 0,
    val coverArt: String? = null,
)

@Serializable
data class PlaylistDetailDto(
    val id: String,
    val name: String,
    val comment: String? = null,
    val songCount: Int = 0,
    val duration: Int = 0,
    val coverArt: String? = null,
    val entry: List<SongDto> = emptyList(),
)

@Serializable
data class GenresDto(val genre: List<GenreDto> = emptyList())

@Serializable
data class GenreDto(val value: String, val songCount: Int = 0, val albumCount: Int = 0)

@Serializable
data class SearchResult3Dto(
    val artist: List<ArtistDto> = emptyList(),
    val album: List<AlbumDto> = emptyList(),
    val song: List<SongDto> = emptyList(),
)

@Serializable
data class Starred2Dto(val song: List<SongDto> = emptyList())

@Serializable
data class LyricsListDto(val structuredLyrics: List<StructuredLyricsDto> = emptyList())

@Serializable
data class StructuredLyricsDto(
    val lang: String? = null,
    val synced: Boolean = false,
    val line: List<LyricLineDto> = emptyList(),
)

@Serializable
data class LyricLineDto(val start: Long? = null, val value: String = "")
