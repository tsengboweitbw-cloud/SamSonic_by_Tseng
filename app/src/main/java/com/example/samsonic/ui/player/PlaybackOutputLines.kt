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
import androidx.compose.material.icons.rounded.HighQuality
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
import com.example.samsonic.playback.BitPerfectTrack
import com.example.samsonic.playback.describeEncoding
import com.example.samsonic.playback.formatKilohertz

internal enum class PlaybackDetailKind { Output, Device, DeviceRate, BitPerfect }

/**
 * One playback detail: [label] and [value] for Song info's rows; [line] is the same
 * without the label, for Now Playing, where the tile says what it is. [active] is
 * false only for bit-perfect not in effect.
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
    val track by container.bitPerfect.track.collectAsStateWithLifecycle()
    val enabled by container.bitPerfect.enabled.collectAsStateWithLifecycle()
    val dac by container.bitPerfect.dac.collectAsStateWithLifecycle()
    val resources = LocalResources.current
    // The exclusive line always shows: when nothing's going to a DAC exclusively, it
    // says why not, dimmed, rather than disappearing.
    val bitPerfect = track ?: BitPerfectTrack(
        on = false,
        detail = resources.getString(
            when {
                !container.bitPerfect.available -> R.string.player_exclusive_unavailable
                !enabled -> R.string.player_exclusive_switched_off
                dac == null -> R.string.player_exclusive_no_dac
                else -> R.string.playback_not_to_dac
            },
        ),
    )
    // Output and bit-perfect held together, so a stale bit-perfect line never sits
    // under a live output (bit-perfect switched off clears it with a new track).
    return rememberLastNonNull(output?.let { resources.playbackDetails(it, bitPerfect) }).orEmpty()
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
 * Now Playing's five info lines, each behind a same-sized tile: the file's format
 * (its codec in the tile, the rest beside it), then the playback details. Always
 * takes room for all five, blank lines at the end standing in for any not there
 * (yet), so the page doesn't jump as a song loads.
 */
@Composable
internal fun NowPlayingInfoRows(song: Song) {
    val format = rememberLastNonNull(songFormat(song))
    if (format != null) {
        InfoTileRow(format.first, description = stringResource(R.string.player_format), text = format.second)
    } else {
        InfoTileRowPlaceholder()
    }
    val details = rememberPlaybackDetails()
    details.forEach { InfoTileRow(InfoTile.Glyph(iconOf(it)), description = it.label, text = it.line, active = it.active) }
    repeat(PlaybackDetailCount - details.size) { InfoTileRowPlaceholder() }
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
    PlaybackDetailKind.BitPerfect -> Icons.Rounded.HighQuality
    // Named by AudioOutputMonitor.describe: the kind of output comes first.
    PlaybackDetailKind.Device -> when {
        detail.value.startsWith("USB") -> Icons.Rounded.Usb
        detail.value.startsWith("Bluetooth") -> Icons.Rounded.Bluetooth
        detail.value.startsWith("Wired") -> Icons.Rounded.Headphones
        detail.value.startsWith("HDMI") -> Icons.Rounded.Tv
        else -> Icons.Rounded.Speaker
    }
}

// The most lines [playbackDetails] gives: Output, Device, Device rate, Exclusive.
private const val PlaybackDetailCount = 4

/**
 * What's actually being played out, which can differ from the file (DSD decoded to
 * PCM, high-res PCM cut to 16-bit): the PCM handed to Android, the device it plays on,
 * the rate Android's mixer runs at where it says (the phone's own outputs, not USB or
 * Bluetooth), and whether it goes out exclusive (bit-perfect or resampled).
 */
private fun Resources.playbackDetails(output: AudioOutput, bitPerfect: BitPerfectTrack?): List<PlaybackDetail> = listOfNotNull(
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
    ),
    output.device?.let { PlaybackDetail(PlaybackDetailKind.Device, getString(R.string.player_device), it) },
    output.mixerRate?.let {
        PlaybackDetail(
            PlaybackDetailKind.DeviceRate,
            getString(R.string.player_device_rate),
            formatKilohertz(it),
            line = getString(R.string.player_device_at, formatKilohertz(it)),
        )
    },
    // Exclusive mode's line: "Bit-perfect · 32-bit · 96 kHz" or "Resampled 32 → 64 kHz · 32-bit" when on,
    // and why not ("Not exclusive · No USB DAC") when not.
    bitPerfect?.let {
        PlaybackDetail(
            PlaybackDetailKind.BitPerfect,
            getString(R.string.player_exclusive),
            if (it.on) it.detail else getString(R.string.player_exclusive_off, it.detail),
            line = if (it.on) it.detail else getString(R.string.player_not_exclusive, it.detail),
            active = it.on,
        )
    },
)

/** A channel count as shown: mono, stereo, or the number of channels. */
internal fun Resources.channelsOf(channels: Int): String = when (channels) {
    1 -> getString(R.string.player_mono)
    2 -> getString(R.string.player_stereo)
    else -> getString(R.string.player_channel_count, channels)
}
