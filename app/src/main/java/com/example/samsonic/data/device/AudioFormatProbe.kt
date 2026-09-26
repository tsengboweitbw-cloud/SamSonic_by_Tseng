package com.example.samsonic.data.device

import kotlinx.serialization.Serializable

/**
 * An audio file's stream format, read from its header: what MediaStore doesn't report
 * and the app needs to tell hi-res (and DSD) apart. [bitDepth] is null for lossy
 * codecs, which have none, and 1 for DSD, as OpenSubsonic servers report it.
 */
@Serializable
internal data class AudioStreamFormat(
    val sampleRate: Int? = null,
    val bitDepth: Int? = null,
    val channels: Int? = null,
)

/** The file's format from its header, or null for a container this doesn't know. */
internal fun AudioFileReader.probeFormat(): AudioStreamFormat? {
    val start = afterId3()
    val head = read(start, 64)
    if (head.size < 12) return null
    return when {
        head.ascii(0, 4) == "fLaC" -> flacStreamInfo(read(start + 8, 18))
        head.ascii(0, 4) == "RIFF" && head.ascii(8, 4) == "WAVE" -> wavFormat()
        head.ascii(0, 4) == "FORM" && head.ascii(8, 3) == "AIF" -> aiffFormat()
        head.ascii(0, 4) == "DSD " -> dsfFormat()
        head.ascii(0, 4) == "FRM8" -> dffFormat()
        head.ascii(0, 4) == "OggS" -> oggFormat(head)
        head.ascii(0, 4) == "wvpk" -> wavPackFormat(head)
        head.ascii(4, 4) == "ftyp" -> mp4Format()
        else -> mp3Format(start)
    }
}

/** FLAC's STREAMINFO block: 20 bits of sample rate, 3 of channels - 1, 5 of bits - 1. */
private fun flacStreamInfo(info: ByteArray): AudioStreamFormat? {
    if (info.size < 14) return null
    val rate = (info.u8(10) shl 12) or (info.u8(11) shl 4) or (info.u8(12) shr 4)
    val channels = ((info.u8(12) shr 1) and 0x7) + 1
    val bits = (((info.u8(12) and 0x1) shl 4) or (info.u8(13) shr 4)) + 1
    return AudioStreamFormat(rate.takeIf { it > 0 }, bits, channels)
}

/** Each chunk of a RIFF (little-endian) or IFF (big-endian) file after its 12-byte header: id, data start, size. */
internal fun AudioFileReader.chunks(littleEndian: Boolean, from: Long = 12): Sequence<Triple<String, Long, Long>> = sequence {
    var at = from
    while (at + 8 <= size) {
        val header = read(at, 8)
        if (header.size < 8) break
        val chunkSize = if (littleEndian) header.u32le(4) else header.u32be(4)
        yield(Triple(header.ascii(0, 4), at + 8, chunkSize))
        at += 8 + chunkSize + (chunkSize and 1)
    }
}

private fun AudioFileReader.wavFormat(): AudioStreamFormat? {
    val (_, at, _) = chunks(littleEndian = true).firstOrNull { it.first == "fmt " } ?: return null
    val fmt = read(at, 24)
    if (fmt.size < 16) return null
    val bits = fmt.u16le(14)
    // WAVE_FORMAT_EXTENSIBLE says how many of the container's bits are real.
    val valid = if (fmt.u16le(0) == 0xFFFE && fmt.size >= 20) fmt.u16le(18).takeIf { it > 0 } else null
    return AudioStreamFormat(fmt.u32le(4).toInt(), valid ?: bits, fmt.u16le(2))
}

private fun AudioFileReader.aiffFormat(): AudioStreamFormat? {
    val (_, at, _) = chunks(littleEndian = false).firstOrNull { it.first == "COMM" } ?: return null
    val comm = read(at, 18)
    if (comm.size < 18) return null
    // The rate is an 80-bit extended float: 15 bits of exponent, then a 64-bit mantissa.
    val exponent = (((comm.u8(8) and 0x7f) shl 8) or comm.u8(9)) - 16383
    val rate = if (exponent in 0..62) (comm.u64be(10) ushr (63 - exponent)).toInt() else null
    return AudioStreamFormat(rate, comm.u16be(6), comm.u16be(0))
}

/** DSF: a 28-byte "DSD " chunk, then "fmt " with the channels and rate. */
private fun AudioFileReader.dsfFormat(): AudioStreamFormat? {
    val fmt = read(28, 40)
    if (fmt.size < 40 || fmt.ascii(0, 4) != "fmt ") return null
    return AudioStreamFormat(fmt.u32le(28).toInt(), 1, fmt.u32le(24).toInt())
}

/** DSDIFF: big-endian chunks with 64-bit sizes; the format is in PROP's "FS  " and "CHNL". */
private fun AudioFileReader.dffFormat(): AudioStreamFormat? {
    var at = 16L
    while (at + 12 <= size) {
        val header = read(at, 12)
        val chunkSize = header.u64be(4)
        if (header.ascii(0, 4) == "PROP") {
            var sub = at + 16 // past "PROP", its size and "SND "
            val end = at + 12 + chunkSize
            var rate: Int? = null
            var channels: Int? = null
            while (sub + 12 <= end) {
                val h = read(sub, 16)
                if (h.size < 16) break
                when (h.ascii(0, 4)) {
                    "FS  " -> rate = h.u32be(12).toInt()
                    "CHNL" -> channels = h.u16be(12)
                }
                val len = h.u64be(4)
                sub += 12 + len + (len and 1)
            }
            return AudioStreamFormat(rate, 1, channels)
        }
        at += 12 + chunkSize + (chunkSize and 1)
    }
    return null
}

/** Ogg: the first packet names the codec. Opus always decodes at 48kHz. */
private fun oggFormat(page: ByteArray): AudioStreamFormat? {
    val packet = 27 + page.u8(26)
    if (page.size < packet + 20) return null
    return when {
        page.ascii(packet, 8) == "OpusHead" -> AudioStreamFormat(48_000, null, page.u8(packet + 9))
        page.ascii(packet + 1, 6) == "vorbis" -> AudioStreamFormat(page.u32le(packet + 12).toInt(), null, page.u8(packet + 11))
        // Ogg FLAC: "\x7fFLAC", version, header count, "fLaC", a block header, then STREAMINFO.
        page.ascii(packet + 1, 4) == "FLAC" && page.size >= packet + 31 -> flacStreamInfo(page.copyOfRange(packet + 17, page.size))
        else -> null
    }
}

private val WAVPACK_RATES = intArrayOf(6000, 8000, 9600, 11025, 12000, 16000, 22050, 24000, 32000, 44100, 48000, 64000, 88200, 96000, 192000)

/** WavPack's first block header: the flags hold bytes per sample, mono and a rate index. */
private fun wavPackFormat(head: ByteArray): AudioStreamFormat? {
    if (head.size < 28) return null
    val flags = head.u32le(24)
    val bits = ((flags and 0x3) + 1).toInt() * 8
    val channels = if (flags and 0x4 != 0L) 1 else 2
    val rate = WAVPACK_RATES.getOrNull(((flags shr 23) and 0xf).toInt())
    return AudioStreamFormat(rate, bits, channels)
}

/** MPEG audio: the first frame header's version and rate index. */
private fun AudioFileReader.mp3Format(start: Long): AudioStreamFormat? {
    val bytes = read(start, 64 * 1024)
    for (i in 0 until bytes.size - 3) {
        if (bytes.u8(i) != 0xff || bytes.u8(i + 1) and 0xe0 != 0xe0) continue
        val version = (bytes.u8(i + 1) shr 3) and 0x3 // 3 MPEG-1, 2 MPEG-2, 0 MPEG-2.5
        val layer = (bytes.u8(i + 1) shr 1) and 0x3
        val rateIndex = (bytes.u8(i + 2) shr 2) and 0x3
        if (version == 1 || layer == 0 || rateIndex == 3) continue
        val base = intArrayOf(44_100, 48_000, 32_000)[rateIndex]
        val rate = when (version) {
            3 -> base
            2 -> base / 2
            else -> base / 4
        }
        val channels = if (bytes.u8(i + 3) shr 6 == 3) 1 else 2
        return AudioStreamFormat(rate, null, channels)
    }
    return null
}

/** An MP4 box: where its contents start and end. */
internal data class Mp4Box(val type: String, val start: Long, val end: Long)

/** The boxes directly inside [from]..[to]. */
internal fun AudioFileReader.mp4Boxes(from: Long, to: Long): Sequence<Mp4Box> = sequence {
    var at = from
    while (at + 8 <= to) {
        val header = read(at, 16)
        if (header.size < 8) break
        var boxSize = header.u32be(0)
        var headerSize = 8
        when (boxSize) {
            1L -> { boxSize = header.u64be(8); headerSize = 16 }
            0L -> boxSize = to - at
        }
        if (boxSize < headerSize) break
        yield(Mp4Box(header.ascii(4, 4), at + headerSize, at + boxSize))
        at += boxSize
    }
}

/** The box at [path] (such as moov/trak/mdia), each step the first of its type; null if missing. */
internal fun AudioFileReader.mp4Find(vararg path: String, from: Mp4Box? = null): Mp4Box? {
    var box = from ?: Mp4Box("", 0, size)
    for (type in path) {
        // "meta" is a full box in MP4 (4 bytes of version and flags first), but not in QuickTime.
        val start = if (box.type == "meta" && read(box.start + 4, 4).ascii(0, 4) != "hdlr") box.start + 4 else box.start
        box = mp4Boxes(start, box.end).firstOrNull { it.type == type } ?: return null
    }
    return box
}

/** The first audio track's sample entry: its rate and channels, and the bit depth for ALAC and FLAC. */
private fun AudioFileReader.mp4Format(): AudioStreamFormat? {
    val moov = mp4Find("moov") ?: return null
    for (trak in mp4Boxes(moov.start, moov.end).filter { it.type == "trak" }) {
        val hdlr = mp4Find("mdia", "hdlr", from = trak) ?: continue
        if (read(hdlr.start + 8, 4).ascii(0, 4) != "soun") continue
        val stsd = mp4Find("mdia", "minf", "stbl", "stsd", from = trak) ?: continue
        // The sample entry, after stsd's version, flags and entry count.
        val entry = read(stsd.start + 8, 128)
        if (entry.size < 36) continue
        val channels = entry.u16be(24)
        // A 16.16 fixed-point rate, which overflows above 65535Hz; ALAC's and FLAC's own config is exact.
        val fieldRate = entry.u16be(32)
        // QuickTime's version 1 and 2 sound entries add fields before the child boxes.
        val child = 36 + when (entry.u16be(16)) {
            1 -> 16
            2 -> 36
            else -> 0
        }
        val codec = entry.ascii(4, 4)
        val childType = entry.ascii(child + 4, 4)
        // Past the child box's size, type, version and flags.
        val config = child + 12
        return when {
            // ALACSpecificConfig: frame length (4), version (1), bit depth (1), 3 tuning bytes, channels (1), ..., rate at 20.
            codec == "alac" && childType == "alac" && entry.size >= config + 24 ->
                AudioStreamFormat(entry.u32be(config + 20).toInt(), entry.u8(config + 5), entry.u8(config + 9))
            // dfLa: FLAC metadata blocks, STREAMINFO first, past its 4-byte block header.
            codec == "fLaC" && childType == "dfLa" && entry.size >= config + 4 + 14 ->
                flacStreamInfo(entry.copyOfRange(config + 4, entry.size))
            else -> AudioStreamFormat(fieldRate.takeIf { it > 0 }, null, channels)
        }
    }
    return null
}
