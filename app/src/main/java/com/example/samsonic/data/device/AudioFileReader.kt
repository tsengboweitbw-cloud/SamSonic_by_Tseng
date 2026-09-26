package com.example.samsonic.data.device

import android.content.ContentResolver
import android.net.Uri
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.channels.FileChannel

/**
 * Random access to an audio file's bytes, for reading its headers and tags without
 * decoding it. Reads past the end come back short rather than failing.
 */
internal class AudioFileReader(private val channel: FileChannel) {
    val size: Long = channel.size()

    /** Up to [count] bytes from [position]; fewer near the end, none past it. */
    fun read(position: Long, count: Int): ByteArray {
        if (position < 0 || position >= size || count <= 0) return ByteArray(0)
        val buffer = ByteBuffer.allocate(minOf(count.toLong(), size - position).toInt())
        var at = position
        while (buffer.hasRemaining()) {
            val read = channel.read(buffer, at)
            if (read <= 0) break
            at += read
        }
        return buffer.array().copyOf(buffer.position())
    }

    /** Where the audio starts: past an ID3v2 tag at the front (MP3, and some FLAC files), if any. */
    fun afterId3(): Long {
        val head = read(0, 10)
        if (head.size < 10 || head.ascii(0, 3) != "ID3") return 0
        val footer = if (head.u8(5) and 0x10 != 0) 10 else 0
        return 10L + head.syncsafe(6) + footer
    }
}

/** Opens [uri] for [block], or null if it can't be read (deleted, or no permission). */
internal fun <T> ContentResolver.readAudioFile(uri: Uri, block: (AudioFileReader) -> T?): T? = runCatching {
    openFileDescriptor(uri, "r")?.use { pfd ->
        FileInputStream(pfd.fileDescriptor).use { stream -> block(AudioFileReader(stream.channel)) }
    }
}.getOrNull()

// ---- Byte helpers: unsigned reads, big- (be) or little-endian (le) ----

internal fun ByteArray.u8(i: Int): Int = this[i].toInt() and 0xff

internal fun ByteArray.u16be(i: Int): Int = (u8(i) shl 8) or u8(i + 1)

internal fun ByteArray.u16le(i: Int): Int = u8(i) or (u8(i + 1) shl 8)

internal fun ByteArray.u24be(i: Int): Int = (u8(i) shl 16) or (u8(i + 1) shl 8) or u8(i + 2)

internal fun ByteArray.u32be(i: Int): Long = (u16be(i).toLong() shl 16) or u16be(i + 2).toLong()

internal fun ByteArray.u32le(i: Int): Long = u16le(i).toLong() or (u16le(i + 2).toLong() shl 16)

internal fun ByteArray.u64be(i: Int): Long = (u32be(i) shl 32) or u32be(i + 4)

internal fun ByteArray.u64le(i: Int): Long = u32le(i) or (u32le(i + 4) shl 32)

/** An ID3v2 size: 7 bits to a byte. */
internal fun ByteArray.syncsafe(i: Int): Int = (u8(i) shl 21) or (u8(i + 1) shl 14) or (u8(i + 2) shl 7) or u8(i + 3)

internal fun ByteArray.ascii(i: Int, count: Int): String =
    if (i + count > size) "" else String(this, i, count, Charsets.ISO_8859_1)
