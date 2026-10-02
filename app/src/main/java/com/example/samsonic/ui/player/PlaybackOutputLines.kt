package com.example.samsonic.ui.player

import android.content.res.Resources
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Speaker
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.Usb
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.samsonic.LocalAppContainer
import com.example.samsonic.R
import com.example.samsonic.model.Song
import com.example.samsonic.playback.AudioOutput
import com.example.samsonic.playback.describeEncoding
import com.example.samsonic.playback.formatKilohertz

internal enum class PlaybackDetailKind { Output, Device, DeviceRate }

/**
 * One playback detail: [label] and [value] for Song info's rows; [line] is the same
 * without the label, for Now Playing, where the tile says what it is. [active]
 * is false for a detail that isn't in effect.
 */
internal data class PlaybackDetail(
    val kind: PlaybackDetailKind,
    val label: String,
    val value: String,
    val line: String = value,
    val active: Boolean = true,
)

/**
 * The playback details. The player drops its audio track for a moment on every
 * seek and track change, so between tracks this keeps showing the last ones until
 * the next track's arrive; none before the first.
 */
@Composable
internal fun rememberPlaybackDetails(): List<PlaybackDetail> {
    val container = LocalAppContainer.current
    val output by container.audioOutput.output.collectAsStateWithLifecycle()
    val resources = LocalResources.current
    return rememberLastNonNull(output?.let { resources.playbackDetails(it) }).orEmpty()
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
 * Now Playing's info line: a capsule each for the file's format (its codec leading, the
 * quality beside it), then the playback details. Keeps the last ones while the next
 * song's arrive, so the line doesn't jump as a song loads.
 */
@Composable
internal fun NowPlayingInfoRows(song: Song) {
    InfoCapsuleRow(rememberInfoCapsules(song))
}

/** [song]'s format and the playback details as info capsules; also what the capsule's window lists. */
@Composable
internal fun rememberInfoCapsules(song: Song): List<InfoCapsule> {
    val format = rememberLastNonNull(songFormat(song))
    val formatDescription = stringResource(R.string.player_format)
    val details = rememberPlaybackDetails()
    return listOfNotNull(format?.let { InfoCapsule(it.first, formatDescription, it.second) }) +
        details.map { InfoCapsule(InfoTile.Glyph(iconOf(it)), it.label, it.line, it.active) }
}

/** The format tile (the codec, or a file icon when unknown) and the quality beside it; null if nothing's known. */
private fun songFormat(song: Song): Pair<InfoTile, String>? {
    val codec = song.suffix?.uppercase()?.takeIf { it.isNotBlank() }
    val quality = listOfNotNull(
        song.bitDepth?.let { "${it}bit" },
        song.samplingRate?.let { "${it / 1000}kHz" },
        song.bitRate?.let { "${it}kbps" },
    ).joinToString(" • ")
    if (codec == null && quality.isEmpty()) return null
    return (codec?.let { InfoTile.Label(it) } ?: InfoTile.Glyph(Icons.Rounded.AudioFile)) to quality
}

private fun iconOf(detail: PlaybackDetail): ImageVector = when (detail.kind) {
    PlaybackDetailKind.Output -> Icons.Filled.GraphicEq
    PlaybackDetailKind.DeviceRate -> Icons.Rounded.Memory
    // Named by AudioOutputMonitor.describe: the kind of output comes first.
    PlaybackDetailKind.Device -> when {
        detail.value.startsWith("USB") -> Icons.Rounded.Usb
        detail.value.startsWith("Bluetooth") -> Icons.Rounded.Bluetooth
        detail.value.startsWith("Wired") -> Icons.Rounded.Headphones
        detail.value.startsWith("HDMI") -> Icons.Rounded.Tv
        else -> Icons.Rounded.Speaker
    }
}

/**
 * What's actually being played out, which can differ from the file (DSD decoded to
 * PCM, high-res PCM cut to 16-bit): the PCM handed to Android, the device it plays on,
 * the rate Android's mixer runs at where it says (the phone's own outputs, not USB or
 * Bluetooth).
 */
private fun Resources.playbackDetails(output: AudioOutput): List<PlaybackDetail> = listOfNotNull(
    PlaybackDetail(
        PlaybackDetailKind.Output,
        getString(R.string.player_output),
        if (output.offload) {
            getString(R.string.player_hardware_decoding)
        } else {
            listOf(
                formatKilohertz(output.sampleRate),
                describeEncoding(this, output.encoding),
                channelsOf(output.channels),
            ).joinToString(" · ")
        },
        // Now Playing's capsule is short: no spaces in the units, and stereo (the usual) goes unsaid.
        line = if (output.offload) {
            getString(R.string.player_hardware_decoding)
        } else {
            listOfNotNull(
                formatKilohertz(output.sampleRate).replace(" kHz", "kHz"),
                describeEncoding(this, output.encoding).replace("-bit", "bit"),
                output.channels.takeIf { it != 2 }?.let { channelsOf(it) },
            ).joinToString(" · ")
        },
    ),
    output.device?.let { PlaybackDetail(PlaybackDetailKind.Device, getString(R.string.player_device), it) },
    output.mixerRate?.let {
        PlaybackDetail(
            PlaybackDetailKind.DeviceRate,
            getString(R.string.player_device_rate),
            formatKilohertz(it),
            line = formatKilohertz(it).replace(" kHz", "kHz"),
        )
    },
)

/** A channel count as shown: mono, stereo, or the number of channels. */
internal fun Resources.channelsOf(channels: Int): String = when (channels) {
    1 -> getString(R.string.player_mono)
    2 -> getString(R.string.player_stereo)
    else -> getString(R.string.player_channel_count, channels)
}
