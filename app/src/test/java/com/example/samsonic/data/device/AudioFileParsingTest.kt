package com.example.samsonic.data.device

import com.example.samsonic.model.LyricLine
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.RandomAccessFile

class AudioFileParsingTest {
    private fun <T> withFile(bytes: ByteArray, block: (AudioFileReader) -> T): T {
        val file = File.createTempFile("audio", ".bin")
        try {
            file.writeBytes(bytes)
            return RandomAccessFile(file, "r").use { block(AudioFileReader(it.channel)) }
        } finally {
            file.delete()
        }
    }

    private fun bytes(build: ByteArrayOutputStream.() -> Unit) = ByteArrayOutputStream().apply(build).toByteArray()
    private fun ByteArrayOutputStream.ascii(s: String) = write(s.toByteArray(Charsets.ISO_8859_1))
    private fun ByteArrayOutputStream.u8(v: Int) = write(v)
    private fun ByteArrayOutputStream.u16le(v: Int) { u8(v and 0xff); u8(v shr 8 and 0xff) }
    private fun ByteArrayOutputStream.u32le(v: Long) { u16le((v and 0xffff).toInt()); u16le((v shr 16 and 0xffff).toInt()) }
    private fun ByteArrayOutputStream.u64le(v: Long) { u32le(v and 0xffffffffL); u32le(v ushr 32) }
    private fun ByteArrayOutputStream.u16be(v: Int) { u8(v shr 8 and 0xff); u8(v and 0xff) }
    private fun ByteArrayOutputStream.u32be(v: Long) { u16be((v shr 16 and 0xffff).toInt()); u16be((v and 0xffff).toInt()) }
    private fun ByteArrayOutputStream.u24be(v: Int) { u8(v shr 16 and 0xff); u16be(v and 0xffff) }
    private fun ByteArrayOutputStream.syncsafe(v: Int) { u8(v shr 21 and 0x7f); u8(v shr 14 and 0x7f); u8(v shr 7 and 0x7f); u8(v and 0x7f) }

    /** A FLAC file with STREAMINFO for [rate]/[bits]/[channels] and a Vorbis comment block holding [comments]. */
    private fun flac(rate: Int, bits: Int, channels: Int, comments: List<String> = emptyList()) = bytes {
        ascii("fLaC")
        u8(0x00); u24be(34) // STREAMINFO, not last
        repeat(10) { u8(0) }
        u8(rate shr 12); u8(rate shr 4 and 0xff)
        u8((rate and 0xf) shl 4 or ((channels - 1) shl 1) or ((bits - 1) shr 4))
        u8(((bits - 1) and 0xf) shl 4)
        repeat(20) { u8(0) }
        val block = bytes {
            u32le(6); ascii("vendor"); u32le(comments.size.toLong())
            comments.forEach { val b = it.toByteArray(); u32le(b.size.toLong()); write(b) }
        }
        u8(0x84); u24be(block.size) // VORBIS_COMMENT, last
        write(block)
    }

    @Test
    fun flacStreamInfo() {
        assertEquals(AudioStreamFormat(192_000, 24, 2), withFile(flac(192_000, 24, 2)) { it.probeFormat() })
        assertEquals(AudioStreamFormat(44_100, 16, 1), withFile(flac(44_100, 16, 1)) { it.probeFormat() })
    }

    @Test
    fun wavExtensibleUsesValidBits() {
        val wav = bytes {
            ascii("RIFF"); u32le(60); ascii("WAVE")
            ascii("fmt "); u32le(40)
            u16le(0xFFFE); u16le(2); u32le(96_000); u32le(0); u16le(8); u16le(32); u16le(22); u16le(24)
            repeat(20) { u8(0) }
            ascii("data"); u32le(0)
        }
        assertEquals(AudioStreamFormat(96_000, 24, 2), withFile(wav) { it.probeFormat() })
    }

    @Test
    fun dsfIsOneBit() {
        val dsf = bytes {
            ascii("DSD "); u64le(28); u64le(0); u64le(0)
            ascii("fmt "); u64le(52); u32le(1); u32le(0); u32le(2); u32le(2); u32le(5_644_800); u32le(1)
            repeat(16) { u8(0) }
        }
        assertEquals(AudioStreamFormat(5_644_800, 1, 2), withFile(dsf) { it.probeFormat() })
    }

    @Test
    fun mp3FrameHeaderAfterId3() {
        val mp3 = bytes {
            ascii("ID3"); u8(3); u8(0); u8(0); syncsafe(0)
            u8(0xFF); u8(0xFB); u8(0x90); u8(0x44) // MPEG-1 layer III, 44.1kHz, joint stereo
            repeat(60) { u8(0) }
        }
        assertEquals(AudioStreamFormat(44_100, null, 2), withFile(mp3) { it.probeFormat() })
    }

    @Test
    fun alacConfigInMp4() {
        val alacConfig = bytes {
            u32be(4096); u8(0); u8(24); u8(40); u8(10); u8(14); u8(2); u16be(255); u32be(0); u32be(0); u32be(176_400)
        }
        fun box(type: String, body: ByteArray) = bytes { u32be(8L + body.size); ascii(type); write(body) }
        val entry = box("alac", bytes {
            repeat(6) { u8(0) }; u16be(1) // reserved, data reference
            u16be(0); u16be(0); u32be(0) // version, revision, vendor
            u16be(2); u16be(16); u16be(0); u16be(0); u32be(44_100L shl 16)
            write(box("alac", bytes { u32be(0); write(alacConfig) }))
        })
        val stsd = box("stsd", bytes { u32be(0); u32be(1); write(entry) })
        val hdlr = box("hdlr", bytes { u32be(0); u32be(0); ascii("soun"); repeat(12) { u8(0) } })
        val trak = box("trak", box("mdia", hdlr + box("minf", box("stbl", stsd))))
        val file = box("ftyp", bytes { ascii("M4A "); u32be(0) }) + box("moov", trak)
        assertEquals(AudioStreamFormat(176_400, 24, 2), withFile(file) { it.probeFormat() })
    }

    @Test
    fun flacLyricsPreferSynced() {
        val file = flac(44_100, 16, 2, listOf("UNSYNCEDLYRICS=Hello\nWorld", "LYRICS=[00:01.50]Hello\n[00:03.00]World"))
        assertEquals(listOf(LyricLine(1_500, "Hello"), LyricLine(3_000, "World")), withFile(file) { it.readLyrics() })
    }

    @Test
    fun id3UsltLyrics() {
        val text = "First line\nSecond line"
        val frame = bytes { u8(3); ascii("eng"); u8(0); write(text.toByteArray()) }
        val tag = bytes { ascii("USLT"); u32be(frame.size.toLong()); u16be(0); write(frame) }
        val mp3 = bytes {
            ascii("ID3"); u8(3); u8(0); u8(0); syncsafe(tag.size); write(tag)
            u8(0xFF); u8(0xFB); u8(0x90); u8(0x44); repeat(60) { u8(0) }
        }
        assertEquals(listOf(LyricLine(0, "First line"), LyricLine(0, "Second line")), withFile(mp3) { it.readLyrics() })
    }

    @Test
    fun lrcHandlesSeveralTimesOffsetAndWordTimes() {
        val lines = parseLrc("[ar:Someone]\n[offset:500]\n[00:10.00][00:20.00]Chorus\n[00:05.2]<00:05.20>Verse <00:06.00>line")
        assertEquals(
            listOf(LyricLine(4_700, "Verse line"), LyricLine(9_500, "Chorus"), LyricLine(19_500, "Chorus")),
            lines,
        )
    }
}
