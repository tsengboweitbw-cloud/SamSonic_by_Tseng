package com.example.samsonic.playback

import android.content.Context
import androidx.core.content.edit
import com.example.samsonic.model.Song
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Where playback stood: the queue's place, how far into the song, and the modes. */
data class PlaybackCursor(
    val index: Int,
    val positionMs: Long,
    val shuffle: Boolean,
    val repeatMode: Int,
)

/**
 * Remembers the queue and where playback stood, so they come back after the app is closed,
 * and the media controls on the lock screen and in One UI's panel can resume. The app writes
 * the songs (it knows them); the playback service writes the [PlaybackCursor] (it knows the
 * position, and runs when the app does not).
 */
class PlaybackStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("playback_state", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private class Saved(val songs: List<Song>)

    fun saveSongs(songs: List<Song>) {
        val text = json.encodeToString(Saved.serializer(), Saved(songs))
        prefs.edit { putString(KeySongs, text) }
    }

    fun loadSongs(): List<Song> = prefs.getString(KeySongs, null)
        ?.let { runCatching { json.decodeFromString(Saved.serializer(), it).songs }.getOrNull() }
        .orEmpty()

    fun saveCursor(cursor: PlaybackCursor) = prefs.edit {
        putInt(KeyIndex, cursor.index)
        putLong(KeyPosition, cursor.positionMs)
        putBoolean(KeyShuffle, cursor.shuffle)
        putInt(KeyRepeat, cursor.repeatMode)
    }

    fun loadCursor(): PlaybackCursor = PlaybackCursor(
        index = prefs.getInt(KeyIndex, 0),
        positionMs = prefs.getLong(KeyPosition, 0L),
        shuffle = prefs.getBoolean(KeyShuffle, false),
        repeatMode = prefs.getInt(KeyRepeat, 0),
    )

    private companion object {
        const val KeySongs = "songs"
        const val KeyIndex = "index"
        const val KeyPosition = "position"
        const val KeyShuffle = "shuffle"
        const val KeyRepeat = "repeat"
    }
}
