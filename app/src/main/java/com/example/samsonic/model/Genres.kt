package com.example.samsonic.model

// What a genre tag holding several genres runs them together with ("Rock; Pop", "Jazz/Funk").
private val GenreSeparators = Regex("""\s*[;/|,]\s*""")

/** Each genre in [raw], a tag that may hold several, without blanks or repeats. */
fun splitGenres(raw: String?): List<String> =
    raw.orEmpty().split(GenreSeparators).map { it.trim() }.filter { it.isNotEmpty() }.distinctBy { it.lowercase() }

/** Each of the song's genres: as the source lists them, else split out of its one tag. */
val Song.genreNames: List<String> get() = genres.ifEmpty { splitGenres(genre) }

/** Each of the album's genres: as the source lists them, else split out of its one tag. */
val Album.genreNames: List<String> get() = genres.ifEmpty { splitGenres(genre) }
