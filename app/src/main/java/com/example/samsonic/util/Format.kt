package com.example.samsonic.util

import com.example.samsonic.model.Song

/** e.g. "FLAC • 24bit • 96kHz • 3587kbps"; omits whatever the server didn't report. */
fun formatAudioInfo(song: Song): String? {
    val parts = listOfNotNull(
        song.suffix?.uppercase(),
        song.bitDepth?.let { "${it}bit" },
        song.samplingRate?.let { "${it / 1000}kHz" },
        song.bitRate?.let { "${it}kbps" },
    )
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" • ")
}

fun formatDuration(totalSeconds: Int): String {
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}

fun formatAlbumDuration(totalSeconds: Int): String {
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    return if (hours > 0) "$hours hr $minutes min" else "$minutes min"
}
