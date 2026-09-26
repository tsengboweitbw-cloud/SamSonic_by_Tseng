package com.example.samsonic.data

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** What Auto DJ adds as the queue runs out: nothing, a few songs, or a whole album. */
enum class AutoDjMode { OFF, SONGS, ALBUMS }

/** What of the song playing Auto DJ keeps to in what it adds. */
enum class AutoDjFollow { ARTIST, GENRE, ERA }

/** The decade standing for everything before 1950, which is too little each to list apart. */
const val EARLIER_DECADES = 0

/** The decades Auto DJ can keep to, by their first year: before 1950, then the 1950s on. */
val AutoDjDecades: List<Int> = listOf(EARLIER_DECADES) + (1950..2020 step 10)

/** The years in decade [decade] (see [AutoDjDecades]). */
fun decadeYears(decade: Int): IntRange = if (decade == EARLIER_DECADES) 0..1949 else decade..decade + 9

/** The decade (see [AutoDjDecades]) [year] falls in. */
fun decadeOf(year: Int): Int = if (year < 1950) EARLIER_DECADES else year / 10 * 10

/** An artist Auto DJ may pick from, kept with their name to show without a lookup. */
data class AutoDjArtist(val id: String, val name: String)

/**
 * The limits on everything Auto DJ adds, whatever it follows: only these [genres],
 * [decades] (see [decadeYears]), these [artists], and liked, hi-res or never played
 * songs. Empty or null is no limit.
 */
data class AutoDjFilters(
    val genres: Set<String> = emptySet(),
    val decades: Set<Int> = emptySet(),
    val artists: List<AutoDjArtist> = emptyList(),
    val likedOnly: Boolean = false,
    val hiResOnly: Boolean = false,
    val neverPlayed: Boolean = false,
) {
    val count: Int get() = listOf(genres.isNotEmpty(), decades.isNotEmpty(), artists.isNotEmpty(), likedOnly, hiResOnly, neverPlayed).count { it }
}

/**
 * All of Auto DJ's settings: its [mode], what of the playing song it [follow]s (none:
 * anything at random), the genres of it not to follow ([ignoredGenres], for a song of
 * several), and the [filters] on it all.
 */
data class AutoDjConfig(
    val mode: AutoDjMode = AutoDjMode.OFF,
    val follow: Set<AutoDjFollow> = emptySet(),
    val ignoredGenres: Set<String> = emptySet(),
    val filters: AutoDjFilters = AutoDjFilters(),
)

/** Keeps [AutoDjConfig] across launches, in its own preferences file. */
class AutoDjSettings(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("samsonic_auto_dj", Context.MODE_PRIVATE)
    private val _config = MutableStateFlow(load())
    val config: StateFlow<AutoDjConfig> = _config.asStateFlow()

    fun update(transform: (AutoDjConfig) -> AutoDjConfig) {
        val next = transform(_config.value)
        if (next == _config.value) return
        _config.value = next
        save(next)
    }

    fun setMode(mode: AutoDjMode) = update { it.copy(mode = mode) }

    fun setFollowing(follow: AutoDjFollow, on: Boolean) =
        update { it.copy(follow = if (on) it.follow + follow else it.follow - follow) }

    /** Follows the genre [genre] of a song of several, or not. */
    fun setGenreFollowed(genre: String, followed: Boolean) =
        update { it.copy(ignoredGenres = if (followed) it.ignoredGenres - genre else it.ignoredGenres + genre) }

    fun updateFilters(transform: (AutoDjFilters) -> AutoDjFilters) = update { it.copy(filters = transform(it.filters)) }

    private fun load(): AutoDjConfig = AutoDjConfig(
        mode = AutoDjMode.entries.find { it.name == prefs.getString(KEY_MODE, null) } ?: AutoDjMode.OFF,
        follow = prefs.getStringSet(KEY_FOLLOW, null).orEmpty().mapNotNullTo(HashSet()) { name -> AutoDjFollow.entries.find { it.name == name } },
        ignoredGenres = prefs.getStringSet(KEY_IGNORED_GENRES, null).orEmpty().toSet(),
        filters = AutoDjFilters(
            genres = prefs.getStringSet(KEY_GENRES, null).orEmpty().toSet(),
            decades = prefs.getStringSet(KEY_DECADES, null).orEmpty().mapNotNullTo(HashSet()) { it.toIntOrNull() },
            artists = prefs.getStringSet(KEY_ARTISTS, null).orEmpty().mapNotNull(::decodeArtist).sortedBy { it.name.lowercase() },
            likedOnly = prefs.getBoolean(KEY_LIKED_ONLY, false),
            hiResOnly = prefs.getBoolean(KEY_HI_RES_ONLY, false),
            neverPlayed = prefs.getBoolean(KEY_NEVER_PLAYED, false),
        ),
    )

    private fun save(config: AutoDjConfig) = prefs.edit {
        val filters = config.filters
        putString(KEY_MODE, config.mode.name)
        putStringSet(KEY_FOLLOW, config.follow.mapTo(HashSet()) { it.name })
        putStringSet(KEY_IGNORED_GENRES, config.ignoredGenres)
        putStringSet(KEY_GENRES, filters.genres)
        putStringSet(KEY_DECADES, filters.decades.mapTo(HashSet()) { it.toString() })
        putStringSet(KEY_ARTISTS, filters.artists.mapTo(HashSet()) { "${it.id}$SEPARATOR${it.name}" })
        putBoolean(KEY_LIKED_ONLY, filters.likedOnly)
        putBoolean(KEY_HI_RES_ONLY, filters.hiResOnly)
        putBoolean(KEY_NEVER_PLAYED, filters.neverPlayed)
    }

    private fun decodeArtist(value: String): AutoDjArtist? {
        val id = value.substringBefore(SEPARATOR, "").takeIf { it.isNotEmpty() } ?: return null
        return AutoDjArtist(id, value.substringAfter(SEPARATOR))
    }

    private companion object {
        const val KEY_MODE = "mode"
        const val KEY_FOLLOW = "follow"
        const val KEY_IGNORED_GENRES = "ignored_genres"
        const val KEY_GENRES = "filter_genres"
        const val KEY_DECADES = "filter_decades"
        const val KEY_ARTISTS = "filter_artists"
        const val KEY_LIKED_ONLY = "filter_liked_only"
        const val KEY_HI_RES_ONLY = "filter_hi_res_only"
        const val KEY_NEVER_PLAYED = "filter_never_played"
        const val SEPARATOR = '\t'
    }
}
