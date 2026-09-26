package com.example.samsonic.data.device

import android.content.Context
import androidx.core.content.edit
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

/** A playlist of the music on this phone: its songs' MediaStore ids, in order. */
@Serializable
internal data class DevicePlaylist(
    val id: String,
    val name: String,
    val songIds: List<String>,
)

/** How often a song was listened to, and when last (epoch millis). */
@Serializable
internal data class DevicePlays(
    val count: Long,
    val lastPlayedMs: Long,
)

/**
 * What the app keeps itself for the music on this phone, which MediaStore has no room
 * for: its playlists and play history. A server keeps these for its own music; here
 * they live in the app's preferences, so they go when the app's data is cleared.
 */
internal class DeviceStore(context: Context) {
    private val prefs = context.getSharedPreferences("samsonic_device_library", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    fun playlists(): List<DevicePlaylist> = read(KEY_PLAYLISTS) ?: emptyList()

    fun playlist(id: String): DevicePlaylist? = playlists().firstOrNull { it.id == id }

    @Synchronized
    fun createPlaylist(name: String, songIds: List<String>) {
        val playlist = DevicePlaylist(id = PLAYLIST_ID_PREFIX + UUID.randomUUID(), name = name, songIds = songIds)
        write(KEY_PLAYLISTS, playlists() + playlist)
    }

    /** Adds [songIds] to the end of playlist [id]; false if there's no such playlist. */
    @Synchronized
    fun addToPlaylist(id: String, songIds: List<String>): Boolean {
        val all = playlists()
        if (all.none { it.id == id }) return false
        write(KEY_PLAYLISTS, all.map { if (it.id == id) it.copy(songIds = it.songIds + songIds) else it })
        return true
    }

    fun plays(): Map<String, DevicePlays> = read(KEY_PLAYS) ?: emptyMap()

    /** Counts a listen of song [id], now. */
    @Synchronized
    fun addPlay(id: String, nowMs: Long = System.currentTimeMillis()) {
        val all = plays()
        val count = (all[id]?.count ?: 0L) + 1
        write(KEY_PLAYS, all + (id to DevicePlays(count, nowMs)))
    }

    private inline fun <reified T> read(key: String): T? {
        val text = prefs.getString(key, null) ?: return null
        return runCatching { json.decodeFromString<T>(text) }.getOrNull()
    }

    private inline fun <reified T> write(key: String, value: T) {
        prefs.edit { putString(key, json.encodeToString(value)) }
    }

    private companion object {
        const val KEY_PLAYLISTS = "playlists"
        const val KEY_PLAYS = "plays"
        const val PLAYLIST_ID_PREFIX = "device:"
    }
}

/**
 * Each song's [AudioStreamFormat], read from its file once and kept (in the app's
 * files) until the file changes, since reading every header takes a while. A file
 * that couldn't be read is kept too, as no format, so it isn't read again and again.
 */
internal class DeviceFormatCache(context: Context) {
    @Serializable
    private data class Entry(val stamp: String, val format: AudioStreamFormat? = null)

    private val file = File(context.filesDir, "device_formats.json")
    private val json = Json { ignoreUnknownKeys = true }
    private val byId: MutableMap<String, Entry> by lazy {
        runCatching { json.decodeFromString<Map<String, Entry>>(file.readText()) }.getOrNull().orEmpty().toMutableMap()
    }

    /** The formats of [tracks] known and still current, by song id. */
    @Synchronized
    fun known(tracks: List<DeviceTrack>): Map<String, AudioStreamFormat> = buildMap {
        for (track in tracks) {
            val entry = byId[track.id.toString()] ?: continue
            if (entry.stamp == track.stamp) entry.format?.let { put(track.id.toString(), it) }
        }
    }

    /** Those of [tracks] whose file hasn't been read, or has changed since. */
    @Synchronized
    fun missing(tracks: List<DeviceTrack>): List<DeviceTrack> = tracks.filter { byId[it.id.toString()]?.stamp != it.stamp }

    @Synchronized
    fun put(track: DeviceTrack, format: AudioStreamFormat?) {
        byId[track.id.toString()] = Entry(track.stamp, format)
    }

    /** Writes the formats out, dropping songs no longer among [tracks]. */
    @Synchronized
    fun save(tracks: List<DeviceTrack>) {
        val ids = tracks.mapTo(HashSet()) { it.id.toString() }
        byId.keys.retainAll(ids)
        runCatching { file.writeText(json.encodeToString(byId.toMap())) }
    }

    /** What changes when the file does. */
    private val DeviceTrack.stamp: String get() = "$sizeBytes:$dateModified"
}