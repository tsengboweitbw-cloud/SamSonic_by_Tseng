package com.example.samsonic.ui.player

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.samsonic.LocalAppContainer
import com.example.samsonic.playback.AudioOutput
import com.example.samsonic.playback.BitPerfectTrack
import com.example.samsonic.playback.describeEncoding
import com.example.samsonic.playback.formatKilohertz

/**
 * The playback details as (label, value) pairs. The player drops its audio track
 * for a moment on every seek and track change, so between tracks this keeps
 * showing the last ones until the next track's arrive; none before the first.
 */
@Composable
internal fun rememberPlaybackDetails(): List<Pair<String, String>> {
    val container = LocalAppContainer.current
    val output by container.audioOutput.output.collectAsStateWithLifecycle()
    val bitPerfect by container.bitPerfect.track.collectAsStateWithLifecycle()
    // Output and bit-perfect held together, so a stale bit-perfect line never sits
    // under a live output (bit-perfect switched off clears it with a new track).
    return rememberLastNonNull(output?.let { playbackDetails(it, bitPerfect) }).orEmpty()
}

/** [value], or while it's null the last non-null value it had (null if never). */
@Composable
internal fun <T : Any> rememberLastNonNull(value: T?): T? {
    // A plain holder, not state: [value] changing already recomposes the caller.
    val last = remember { arrayOfNulls<Any>(1) }
    if (value != null) last[0] = value
    @Suppress("UNCHECKED_CAST")
    return value ?: last[0] as T?
}

/**
 * The playback details as one small line each, under Now Playing's audio info.
 * Always takes room for all of them, blank lines at the end filling in for any
 * not there (yet), so the page doesn't jump as a song loads.
 */
@Composable
internal fun PlaybackOutputLines() {
    val lines = rememberPlaybackDetails().map { (label, value) -> "$label: $value" }
    (lines + List(PlaybackDetailCount - lines.size) { "" }).forEach {
        Text(
            text = it,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// The most lines [playbackDetails] gives: Output, Device, Device rate, Bit-perfect.
private const val PlaybackDetailCount = 4

/**
 * What's actually being played out, which can differ from the file (DSD decoded to
 * PCM, high-res PCM cut to 16-bit): the PCM handed to Android, the device it plays on,
 * the rate Android's mixer runs at where it says (the phone's own outputs, not USB or
 * Bluetooth), and whether it's bit-perfect.
 */
private fun playbackDetails(output: AudioOutput, bitPerfect: BitPerfectTrack?): List<Pair<String, String>> = listOfNotNull(
    "Output" to if (output.offload) {
        "Hardware decoding"
    } else {
        listOf(
            formatKilohertz(output.sampleRate),
            describeEncoding(output.encoding),
            if (output.channels == 1) "Mono" else if (output.channels == 2) "Stereo" else "${output.channels} channels",
        ).joinToString(" · ")
    },
    output.device?.let { "Device" to it },
    output.mixerRate?.let { "Device rate" to formatKilohertz(it) },
    bitPerfect?.let { "Bit-perfect" to (if (it.on) "On · " else "Off · ") + it.detail },
)
