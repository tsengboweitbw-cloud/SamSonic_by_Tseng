package com.example.samsonic.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.res.stringResource
import com.example.samsonic.R
import com.example.samsonic.model.Song

/**
 * The codec with the sampling rate (kHz) and bit depth, short enough for a song row:
 * e.g. "FLAC 96/24". With only one of the two reported it keeps its unit, as a bare
 * number wouldn't say which it is: "MP3 44kHz". Omits whatever wasn't reported.
 */
fun formatCodecSampling(song: Song): String? {
    // DSD goes by its multiple of 44.1kHz ("DSF DSD64"), not the 2822/1 its numbers would read.
    dsdRateName(song)?.let { return listOfNotNull(song.suffix?.uppercase(), it).joinToString(" ") }
    val rate = song.samplingRate?.takeIf { it > 0 }?.let(::formatKilohertz)
    val depth = song.bitDepth?.takeIf { it > 0 }
    val sampling = when {
        rate != null && depth != null -> "$rate/$depth"
        rate != null -> "${rate}kHz"
        depth != null -> "${depth}bit"
        else -> null
    }
    val parts = listOfNotNull(song.suffix?.uppercase(), sampling)
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" ")
}

/**
 * Better than CD quality: sampled above 48kHz, or deeper than 16 bits. A song whose
 * library reports neither (the music on this phone) never counts.
 */
fun isHiRes(song: Song): Boolean =
    (song.samplingRate ?: 0) > 48_000 || (song.bitDepth ?: 0) > 16

/** "DSD64", "DSD128"... for a DSD song (1-bit, or a .dsf/.dff file) whose rate is known; null otherwise. */
private fun dsdRateName(song: Song): String? {
    val isDsd = song.bitDepth == 1 || song.suffix?.lowercase() in setOf("dsf", "dff")
    val rate = song.samplingRate?.takeIf { it >= 1_000_000 } ?: return null
    return if (isDsd) "DSD${(rate + 22_050) / 44_100}" else null
}

/** In whole kHz, the fraction dropped: 44100 -> "44", 88200 -> "88", 96000 -> "96". */
private fun formatKilohertz(hertz: Int): String = "${hertz / 1000}"

fun formatDuration(totalSeconds: Int): String {
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}

@Composable
@ReadOnlyComposable
fun formatAlbumDuration(totalSeconds: Int): String {
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    return if (hours > 0) {
        stringResource(R.string.app_duration_hours_minutes, hours, minutes)
    } else {
        stringResource(R.string.app_duration_minutes, minutes)
    }
}

/** e.g. "8.4 MB"; binary units, one decimal place from MB up. */
fun formatFileSize(bytes: Long): String = when {
    bytes >= 1L shl 30 -> "%.1f GB".format(bytes / (1L shl 30).toDouble())
    bytes >= 1L shl 20 -> "%.1f MB".format(bytes / (1L shl 20).toDouble())
    bytes >= 1L shl 10 -> "${bytes shr 10} KB"
    else -> "$bytes B"
}
