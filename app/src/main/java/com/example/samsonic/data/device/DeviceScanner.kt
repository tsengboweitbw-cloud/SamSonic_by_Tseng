package com.example.samsonic.data.device

import android.content.ContentResolver
import android.database.Cursor
import android.os.Build
import android.provider.MediaStore.Audio.Media

/** One audio file as MediaStore describes it. */
internal data class DeviceTrack(
    val id: Long,
    val title: String,
    val artist: String,
    /** From the file's tags; MediaStore only reports it on Android 11+. */
    val albumArtist: String?,
    val album: String,
    val albumId: Long,
    val track: Int,
    val disc: Int?,
    val durationMs: Long,
    val year: Int?,
    /** Android 11+ only. */
    val genre: String?,
    /** Seconds since the epoch. */
    val dateAdded: Long,
    val sizeBytes: Long?,
    val mimeType: String?,
    val path: String?,
    /** Bits per second; Android 11+ only. */
    val bitRate: Int?,
)

// MediaStore's placeholder for a missing artist or album tag.
private const val UNKNOWN_TAG = "<unknown>"

// The file path, only shown in the song info sheet; deprecated for opening files, not for display.
@Suppress("DEPRECATION")
private const val DATA_COLUMN = Media.DATA

/** Every music file MediaStore knows of. Needs the audio read permission ([audioPermission]). */
internal fun ContentResolver.scanDeviceTracks(): List<DeviceTrack> {
    val newColumns = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
    val projection = buildList {
        add(Media._ID)
        add(Media.TITLE)
        add(Media.ARTIST)
        add(Media.ALBUM)
        add(Media.ALBUM_ID)
        add(Media.TRACK)
        add(Media.DURATION)
        add(Media.YEAR)
        add(Media.DATE_ADDED)
        add(Media.SIZE)
        add(Media.MIME_TYPE)
        add(DATA_COLUMN)
        if (newColumns) {
            add(Media.ALBUM_ARTIST)
            add(Media.GENRE)
            add(Media.DISC_NUMBER)
            add(Media.BITRATE)
        }
    }.toTypedArray()

    val cursor = query(Media.EXTERNAL_CONTENT_URI, projection, "${Media.IS_MUSIC} != 0", null, null)
        ?: return emptyList()
    return cursor.use { c ->
        buildList(c.count) {
            while (c.moveToNext()) {
                // TRACK packs the disc in the thousands on older Android versions: 2003 is disc 2, track 3.
                val packedTrack = c.int(Media.TRACK) ?: 0
                add(
                    DeviceTrack(
                        id = c.getLong(c.getColumnIndexOrThrow(Media._ID)),
                        title = c.string(Media.TITLE) ?: "",
                        artist = c.tag(Media.ARTIST) ?: "Unknown Artist",
                        albumArtist = if (newColumns) c.tag(Media.ALBUM_ARTIST) else null,
                        album = c.tag(Media.ALBUM) ?: "",
                        albumId = c.getLong(c.getColumnIndexOrThrow(Media.ALBUM_ID)),
                        track = packedTrack % 1000,
                        disc = (packedTrack / 1000).takeIf { it > 0 }
                            ?: if (newColumns) c.string(Media.DISC_NUMBER)?.substringBefore('/')?.trim()?.toIntOrNull() else null,
                        durationMs = c.long(Media.DURATION) ?: 0L,
                        year = c.int(Media.YEAR)?.takeIf { it > 0 },
                        genre = if (newColumns) c.tag(Media.GENRE) else null,
                        dateAdded = c.long(Media.DATE_ADDED) ?: 0L,
                        sizeBytes = c.long(Media.SIZE),
                        mimeType = c.string(Media.MIME_TYPE),
                        path = c.string(DATA_COLUMN),
                        bitRate = if (newColumns) c.int(Media.BITRATE) else null,
                    ),
                )
            }
        }
    }
}

private fun Cursor.index(column: String): Int? = getColumnIndex(column).takeIf { it >= 0 && !isNull(it) }

private fun Cursor.string(column: String): String? = index(column)?.let(::getString)

private fun Cursor.int(column: String): Int? = index(column)?.let(::getInt)

private fun Cursor.long(column: String): Long? = index(column)?.let(::getLong)

/** A text tag, or null when it is blank or MediaStore's "<unknown>". */
private fun Cursor.tag(column: String): String? = string(column)?.trim()?.takeIf { it.isNotEmpty() && it != UNKNOWN_TAG }
