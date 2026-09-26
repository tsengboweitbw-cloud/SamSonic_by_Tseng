package com.example.samsonic.playback.autodj

import com.example.samsonic.data.AutoDjConfig
import com.example.samsonic.data.AutoDjFollow
import com.example.samsonic.data.AutoDjMode
import com.example.samsonic.data.MusicLibrary
import com.example.samsonic.data.decadeOf
import com.example.samsonic.data.decadeYears
import com.example.samsonic.model.Album
import com.example.samsonic.model.Song
import com.example.samsonic.model.genreNames
import com.example.samsonic.util.isHiRes

// Songs added at a time in song mode.
private const val SONG_BATCH = 5
// Songs asked for per random pick, to have enough left after the filters.
private const val SONG_POOL = 100
// Albums asked for per random pick.
private const val ALBUM_POOL = 40
// Tries at a pick before relaxing what's followed.
private const val TRIES = 3
// Albums of an artist fetched per try, for their songs.
private const val ARTIST_ALBUMS = 3

/**
 * What one pick must be: of [genres], [decades] and [artistIds] where given (any of
 * them), and liked, hi-res or never played if asked.
 */
private data class Criteria(
    val genres: Set<String>?,
    val decades: Set<Int>?,
    val artistIds: Set<String>?,
    val likedOnly: Boolean,
    val hiResOnly: Boolean,
    val neverPlayed: Boolean,
)

/**
 * Picks what Auto DJ adds after [seed], the queue's last song, from [library]. It keeps
 * to everything the settings follow of the seed first; finding nothing, it lets go of
 * them one at a time, the artist first, then the era, then the genre, but never of the
 * filters. Nothing in [exclude] (songs queued or lately played) or [excludeAlbums] is
 * picked again.
 */
internal suspend fun pickAutoDj(
    library: MusicLibrary,
    config: AutoDjConfig,
    seed: Song,
    exclude: Set<String>,
    excludeAlbums: Set<String>,
): List<Song> {
    for (criteria in criteriaFor(config, seed)) {
        val picked = runCatching {
            when (config.mode) {
                AutoDjMode.SONGS -> pickSongs(library, criteria, exclude)
                AutoDjMode.ALBUMS -> pickAlbum(library, criteria, excludeAlbums)
                AutoDjMode.OFF -> emptyList()
            }
        }.getOrDefault(emptyList())
        if (picked.isNotEmpty()) return picked
    }
    return emptyList()
}

/** Every set of [Criteria] to try in turn, from following all that's asked to following nothing. */
private fun criteriaFor(config: AutoDjConfig, seed: Song): List<Criteria> {
    val filters = config.filters
    val filterGenres = filters.genres.takeIf { it.isNotEmpty() }
    val filterDecades = filters.decades.takeIf { it.isNotEmpty() }
    val filterArtists = filters.artists.map { it.id }.toSet().takeIf { it.isNotEmpty() }

    val seedGenres = seed.genreNames.filter { it !in config.ignoredGenres }.toSet().takeIf { it.isNotEmpty() }
    val seedDecades = seed.year?.let { setOf(decadeOf(it)) }
    val seedArtists = setOfNotNull(seed.artistId, seed.albumArtistId).takeIf { it.isNotEmpty() }

    // Let go of the artist first, then the era, then the genre.
    val order = listOf(AutoDjFollow.ARTIST, AutoDjFollow.ERA, AutoDjFollow.GENRE).filter { it in config.follow }
    return (0..order.size).mapNotNull { dropped ->
        val kept = order.drop(dropped).toSet()
        // A followed trait the filters rule out makes this set impossible: skip it.
        val genres = narrow(filterGenres, seedGenres.takeIf { AutoDjFollow.GENRE in kept }) { a, b -> a.equals(b, ignoreCase = true) } ?: return@mapNotNull null
        val decades = narrow(filterDecades, seedDecades.takeIf { AutoDjFollow.ERA in kept }) ?: return@mapNotNull null
        val artists = narrow(filterArtists, seedArtists.takeIf { AutoDjFollow.ARTIST in kept }) ?: return@mapNotNull null
        Criteria(
            genres = genres.value,
            decades = decades.value,
            artistIds = artists.value,
            likedOnly = filters.likedOnly,
            hiResOnly = filters.hiResOnly,
            neverPlayed = filters.neverPlayed,
        )
    }.distinct()
}

/**
 * A limit, whose [value] may itself be null for no limit; a function giving a null
 * [Limit] means no pick could keep to both of what it was given.
 */
private class Limit<T>(val value: T)

/** What keeps to both [filter] and [follow] (null for either: no limit from it), telling items [same]. */
private fun <T> narrow(filter: Set<T>?, follow: Set<T>?, same: (T, T) -> Boolean = { a, b -> a == b }): Limit<Set<T>?>? = when {
    filter == null -> Limit(follow)
    follow == null -> Limit(filter)
    else -> filter.filterTo(HashSet()) { f -> follow.any { same(it, f) } }.takeIf { it.isNotEmpty() }?.let { Limit(it) }
}

private suspend fun pickSongs(library: MusicLibrary, criteria: Criteria, exclude: Set<String>): List<Song> {
    repeat(TRIES) {
        val pool = when {
            criteria.artistIds != null -> artistSongs(library, criteria)
            criteria.likedOnly -> library.getLikedSongs()
            else -> criteria.decades?.random()?.let(::decadeYears).let { years ->
                library.randomSongs(SONG_POOL, genre = criteria.genres?.random(), fromYear = years?.first, toYear = years?.last)
            }
        }
        val fits = pool.filter { it.id !in exclude && criteria.admits(it) }.distinctBy { it.id }
        if (fits.isNotEmpty()) return fits.shuffled().take(SONG_BATCH)
    }
    return emptyList()
}

/** Songs of a few of one of [Criteria.artistIds]'s albums that fit the decades and genres. */
private suspend fun artistSongs(library: MusicLibrary, criteria: Criteria): List<Song> {
    val artistId = criteria.artistIds?.random() ?: return emptyList()
    val albums = library.getArtist(artistId).second.filter { criteria.admitsAlbum(it) }
    return library.getAlbumsSongs(albums.shuffled().take(ARTIST_ALBUMS))
}

private suspend fun pickAlbum(library: MusicLibrary, criteria: Criteria, excludeAlbums: Set<String>): List<Song> {
    repeat(TRIES) {
        val candidates: List<String> = when {
            criteria.artistIds != null -> criteria.artistIds.random().let { id ->
                library.getArtist(id).second.filter { criteria.admitsAlbum(it) }.map { it.id }
            }
            criteria.likedOnly -> library.getLikedSongs().filter { criteria.admits(it) }.mapNotNull { it.albumId }.distinct()
            else -> criteria.decades?.random()?.let(::decadeYears).let { years ->
                library.randomAlbums(ALBUM_POOL, genre = criteria.genres?.random(), fromYear = years?.first, toYear = years?.last)
            }.filter { criteria.admitsAlbum(it) }.map { it.id }
        }
        for (id in candidates.filter { it !in excludeAlbums }.shuffled().take(TRIES)) {
            val songs = library.getAlbum(id).second
            if (songs.isNotEmpty() && criteria.admitsAlbumSongs(songs)) return songs
        }
    }
    return emptyList()
}

private fun Criteria.admits(song: Song): Boolean {
    if (genres != null && song.genreNames.none { g -> genres.any { it.equals(g, ignoreCase = true) } }) return false
    if (decades != null && decadeOf(song.year ?: return false) !in decades) return false
    if (artistIds != null && song.artistId !in artistIds && song.albumArtistId !in artistIds &&
        song.artists.none { it.id in artistIds }
    ) return false
    if (likedOnly && !song.liked) return false
    if (hiResOnly && !isHiRes(song)) return false
    if (neverPlayed && ((song.playCount ?: 0L) > 0L || song.played != null)) return false
    return true
}

/** Whether [album] may fit, from what's known of it before its songs are fetched. */
private fun Criteria.admitsAlbum(album: Album): Boolean {
    val albumGenres = album.genreNames
    if (genres != null && albumGenres.isNotEmpty() && albumGenres.none { g -> genres.any { it.equals(g, ignoreCase = true) } }) return false
    if (decades != null && decadeOf(album.year ?: return false) !in decades) return false
    return true
}

/** Whether an album of [songs] fits as a whole: most of it hi-res, none of it played, if asked. */
private fun Criteria.admitsAlbumSongs(songs: List<Song>): Boolean {
    if (hiResOnly && songs.count { isHiRes(it) } * 2 < songs.size) return false
    if (neverPlayed && songs.any { (it.playCount ?: 0L) > 0L || it.played != null }) return false
    return true
}
