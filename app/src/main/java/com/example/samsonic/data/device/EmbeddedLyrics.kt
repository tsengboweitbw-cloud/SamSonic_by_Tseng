package com.example.samsonic.data.device

import com.example.samsonic.model.LyricLine
import java.io.ByteArrayOutputStream

/** The most of a tag or comment block read for lyrics; beyond it is cover art, not text. */
private const val MAX_TAG_BYTES = 16 * 1024 * 1024

/**
 * The lyrics tagged into the file, synced where they carry times: ID3v2 SYLT or
 * USLT (MP3, and ID3 inside WAV, AIFF and DSF), Vorbis comments (FLAC, Ogg) and
 * MP4's ©lyr. Plain lyrics come back one line each with time 0. Empty if there are none.
 */
internal fun AudioFileReader.readLyrics(): List<LyricLine> {
    val start = afterId3()
    val head = read(start, 64)
    if (head.size < 12) return emptyList()
    return when {
        head.ascii(0, 4) == "fLaC" -> flacComments(start + 4)?.let(::lyricsFromComments)
        head.ascii(0, 4) == "OggS" -> oggComments()?.let(::lyricsFromComments)
        head.ascii(4, 4) == "ftyp" -> mp4Lyrics()?.let(::parseLrc)
        head.ascii(0, 4) == "RIFF" -> chunks(littleEndian = true)
            .firstOrNull { it.first.equals("id3 ", ignoreCase = true) }?.let { id3Lyrics(it.second) }
        head.ascii(0, 4) == "FORM" -> chunks(littleEndian = false)
            .firstOrNull { it.first == "ID3 " }?.let { id3Lyrics(it.second) }
        // DSF keeps its ID3 tag at the end, where the "DSD " chunk points.
        head.ascii(0, 4) == "DSD " -> head.u64le(20).takeIf { it > 0 }?.let { id3Lyrics(it) }
        start > 0 -> id3Lyrics(0)
        else -> null
    }.orEmpty()
}

// ---- Vorbis comments (FLAC and Ogg) ----

/** The first comment of [comments] that holds lyrics, synced ones preferred. */
private fun lyricsFromComments(comments: List<Pair<String, String>>): List<LyricLine>? {
    val found = comments.filter { (key, _) -> key.uppercase() in LYRICS_KEYS }.map { parseLrc(it.second) }.filter { it.isNotEmpty() }
    return found.firstOrNull { lines -> lines.any { it.timeMs > 0 } } ?: found.firstOrNull()
}

private val LYRICS_KEYS = setOf("LYRICS", "UNSYNCEDLYRICS", "SYNCEDLYRICS", "UNSYNCED LYRICS")

/** FLAC's VORBIS_COMMENT metadata block, walking the blocks from [from]. */
private fun AudioFileReader.flacComments(from: Long): List<Pair<String, String>>? {
    var at = from
    while (at + 4 <= size) {
        val header = read(at, 4)
        if (header.size < 4) return null
        val type = header.u8(0) and 0x7f
        val length = header.u24be(1)
        if (type == 4) return parseVorbisComments(read(at + 4, minOf(length, MAX_TAG_BYTES)), 0)
        if (header.u8(0) and 0x80 != 0) return null // the last block
        at += 4 + length
    }
    return null
}

/** A comment packet from [from]: vendor string, then count and "KEY=value" entries, each length-prefixed little-endian. */
private fun parseVorbisComments(bytes: ByteArray, from: Int): List<Pair<String, String>>? {
    if (bytes.size < from + 8) return null
    var at = from + 4 + bytes.u32le(from).toInt()
    if (at + 4 > bytes.size || at < 0) return null
    val count = bytes.u32le(at)
    at += 4
    val comments = mutableListOf<Pair<String, String>>()
    for (i in 0 until count) {
        if (at + 4 > bytes.size) break
        val length = bytes.u32le(at).toInt()
        at += 4
        if (length < 0 || at + length > bytes.size) break
        val text = String(bytes, at, length, Charsets.UTF_8)
        at += length
        val eq = text.indexOf('=')
        if (eq > 0) comments += text.substring(0, eq) to text.substring(eq + 1)
    }
    return comments
}

/** Ogg's second packet, the comment header, pieced together from its pages. */
private fun AudioFileReader.oggComments(): List<Pair<String, String>>? {
    val packets = mutableListOf<ByteArray>()
    val current = ByteArrayOutputStream()
    var at = 0L
    while (packets.size < 2 && at + 27 <= size && current.size() <= MAX_TAG_BYTES) {
        val header = read(at, 27)
        if (header.ascii(0, 4) != "OggS") return null
        val segments = read(at + 27, header.u8(26))
        var data = at + 27 + segments.size
        for (i in segments.indices) {
            val lacing = segments.u8(i)
            current.write(read(data, lacing))
            data += lacing
            // A lacing value under 255 ends the packet.
            if (lacing < 255) {
                packets += current.toByteArray()
                current.reset()
                if (packets.size == 2) break
            }
        }
        at = data
    }
    val packet = packets.getOrNull(1) ?: return null
    return when {
        packet.ascii(0, 8) == "OpusTags" -> parseVorbisComments(packet, 8)
        packet.ascii(1, 6) == "vorbis" -> parseVorbisComments(packet, 7)
        // Ogg FLAC: the second packet is a FLAC metadata block, VORBIS_COMMENT first.
        packet.isNotEmpty() && packet.u8(0) and 0x7f == 4 -> parseVorbisComments(packet, 4)
        else -> null
    }
}

// ---- MP4 ----

/** moov/udta/meta/ilst/©lyr: a data box holding type, locale, then UTF-8 text. */
private fun AudioFileReader.mp4Lyrics(): String? {
    val lyr = mp4Find("moov", "udta", "meta", "ilst", "©lyr") ?: return null
    val data = mp4Boxes(lyr.start, lyr.end).firstOrNull { it.type == "data" } ?: return null
    val bytes = read(data.start + 8, (data.end - data.start - 8).coerceIn(0, MAX_TAG_BYTES.toLong()).toInt())
    return String(bytes, Charsets.UTF_8)
}

// ---- ID3v2 ----

/** The lyrics of the ID3v2 tag at [at]: SYLT (in milliseconds) if it has them, else USLT. */
private fun AudioFileReader.id3Lyrics(at: Long): List<LyricLine>? {
    val header = read(at, 10)
    if (header.size < 10 || header.ascii(0, 3) != "ID3") return null
    val version = header.u8(3)
    val flags = header.u8(5)
    var tag = read(at + 10, minOf(header.syncsafe(6), MAX_TAG_BYTES))
    if (version < 4 && flags and 0x80 != 0) tag = tag.withoutUnsync()
    var pos = 0
    if (version >= 3 && flags and 0x40 != 0 && tag.size >= 4) {
        pos = if (version == 4) tag.syncsafe(0) else 4 + tag.u32be(0).toInt()
    }
    val idLength = if (version == 2) 3 else 4
    val headerLength = if (version == 2) 6 else 10
    var synced: List<LyricLine>? = null
    var plain: List<LyricLine>? = null
    while (pos + headerLength <= tag.size) {
        val id = tag.ascii(pos, idLength)
        if (id.isBlank() || id[0] == '\u0000') break
        val length = when (version) {
            2 -> tag.u24be(pos + 3)
            3 -> tag.u32be(pos + 4).toInt()
            else -> tag.syncsafe(pos + 4)
        }
        val start = pos + headerLength
        if (length <= 0 || start + length > tag.size) break
        var body = tag.copyOfRange(start, start + length)
        if (version == 4) {
            val frameFlags = tag.u8(pos + 9)
            if (frameFlags and 0x01 != 0 && body.size >= 4) body = body.copyOfRange(4, body.size) // data length
            if (frameFlags and 0x02 != 0 || flags and 0x80 != 0) body = body.withoutUnsync()
        }
        when (id) {
            "SYLT", "SLT" -> if (synced == null) synced = parseSylt(body)?.takeIf { it.isNotEmpty() }
            "USLT", "ULT" -> if (plain == null) plain = parseUslt(body)?.let(::parseLrc)?.takeIf { it.isNotEmpty() }
        }
        pos = start + length
    }
    return synced ?: plain
}

/** Undoes ID3 unsynchronisation: a 0x00 put after each 0xFF. */
private fun ByteArray.withoutUnsync(): ByteArray {
    val out = ByteArrayOutputStream(size)
    var i = 0
    while (i < size) {
        out.write(this[i].toInt())
        if (u8(i) == 0xff && i + 1 < size && this[i + 1].toInt() == 0) i++
        i++
    }
    return out.toByteArray()
}

/** USLT: encoding, 3-letter language, a description, then the text. */
private fun parseUslt(body: ByteArray): String? {
    if (body.size < 5) return null
    val encoding = body.u8(0)
    val (_, textStart) = id3String(body, 4, encoding)
    return id3String(body, textStart, encoding).first
}

/** SYLT with millisecond times: encoding, language, time format, content type, description, then text and time pairs. */
private fun parseSylt(body: ByteArray): List<LyricLine>? {
    if (body.size < 7 || body.u8(4) != 2) return null
    val encoding = body.u8(0)
    var pos = id3String(body, 6, encoding).second
    val lines = mutableListOf<LyricLine>()
    while (pos < body.size) {
        val (text, next) = id3String(body, pos, encoding)
        if (next + 4 > body.size) break
        lines += LyricLine(body.u32be(next), text.trimStart('\n', '\r'))
        pos = next + 4
    }
    return lines.sortedBy { it.timeMs }
}

/** An ID3 string at [from] in [encoding], ended by its terminator or the frame: the text, and where the next field starts. */
private fun id3String(bytes: ByteArray, from: Int, encoding: Int): Pair<String, Int> {
    if (from >= bytes.size) return "" to bytes.size
    val wide = encoding == 1 || encoding == 2
    var end = from
    if (wide) {
        while (end + 1 < bytes.size && !(bytes[end].toInt() == 0 && bytes[end + 1].toInt() == 0)) end += 2
    } else {
        while (end < bytes.size && bytes[end].toInt() != 0) end++
    }
    val charset = when (encoding) {
        1 -> Charsets.UTF_16
        2 -> Charsets.UTF_16BE
        3 -> Charsets.UTF_8
        else -> Charsets.ISO_8859_1
    }
    val text = String(bytes, from, (minOf(end, bytes.size) - from).coerceAtLeast(0), charset)
    return text to minOf(end + if (wide) 2 else 1, bytes.size)
}

// ---- LRC ----

private val LRC_TIME = Regex("""\[(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?]""")
private val LRC_WORD_TIME = Regex("""<\d{1,3}:\d{1,2}(?:[.:]\d{1,3})?>""")
private val LRC_OFFSET = Regex("""\[offset:\s*([+-]?\d+)\s*]""", RegexOption.IGNORE_CASE)

/**
 * Lyrics text as lines: LRC's "[mm:ss.xx]" times where it has them (a line may carry
 * several, and "[offset:]" shifts them all), else each line with time 0.
 */
internal fun parseLrc(text: String): List<LyricLine> {
    val rows = text.replace("\r\n", "\n").replace('\r', '\n').split('\n')
    if (rows.none { LRC_TIME.containsMatchIn(it) }) {
        return rows.map { it.trimEnd() }.dropWhile { it.isBlank() }.dropLastWhile { it.isBlank() }.map { LyricLine(0, it) }
    }
    // A positive offset shows lines sooner.
    val offset = rows.firstNotNullOfOrNull { LRC_OFFSET.find(it)?.groupValues?.get(1)?.toLongOrNull() } ?: 0L
    val lines = mutableListOf<LyricLine>()
    for (row in rows) {
        var rest = row.trim()
        val times = mutableListOf<Long>()
        while (true) {
            val match = LRC_TIME.find(rest)?.takeIf { it.range.first == 0 } ?: break
            val (min, sec, frac) = match.destructured
            val fraction = when (frac.length) {
                0 -> 0L
                1 -> frac.toLong() * 100
                2 -> frac.toLong() * 10
                else -> frac.toLong()
            }
            times += min.toLong() * 60_000 + sec.toLong() * 1000 + fraction
            rest = rest.substring(match.value.length)
        }
        if (times.isEmpty()) continue // a tag such as [ar:...], or a stray line
        val lyric = LRC_WORD_TIME.replace(rest, "").trim()
        times.forEach { lines += LyricLine((it - offset).coerceAtLeast(0), lyric) }
    }
    return lines.sortedBy { it.timeMs }
}
