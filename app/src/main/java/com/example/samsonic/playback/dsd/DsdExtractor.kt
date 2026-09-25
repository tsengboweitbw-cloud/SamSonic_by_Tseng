package com.example.samsonic.playback.dsd

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.ParserException
import androidx.media3.common.util.ParsableByteArray
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.Extractor
import androidx.media3.extractor.ExtractorInput
import androidx.media3.extractor.ExtractorOutput
import androidx.media3.extractor.PositionHolder
import androidx.media3.extractor.SeekMap
import androidx.media3.extractor.SeekPoint
import androidx.media3.extractor.TrackOutput
import java.nio.ByteBuffer
import java.nio.ByteOrder

// DSDIFF frames read per step: 4096 bytes of each channel, like one DSF block.
private const val DFF_READ_FRAMES = 4096

/**
 * Plays DSD files, which no Android decoder handles: DSF (Sony) and DSDIFF (.dff, Philips;
 * uncompressed only, not DST). The DSD is filtered down to float PCM here as it's read
 * ([DsdToPcm]), so the player takes it as plain PCM, with seeking and a duration.
 *
 * Or, when [streamFor] says the DAC takes it (bit-perfect, as DoP or native DSD), it's
 * packed untouched instead ([DsdPacker]), with the [DsdStream] on the track's Format.
 */
@UnstableApi
class DsdExtractor(
    private val streamFor: (dsdRate: Int, channels: Int) -> DsdStream? = { _, _ -> null },
) : Extractor {
    private enum class Layout { DSF, DFF }

    private lateinit var output: ExtractorOutput
    private lateinit var track: TrackOutput

    private var headerRead = false
    private var layout = Layout.DSF
    private var channels = 0
    private var dsdRate = 0
    private var dataStart = 0L
    // Bytes of each channel's DSD in the file, and how many of them have been read.
    private var bytesPerChannel = 0L
    private var bytesDone = 0L
    // DSF only: the size of a channel's block (the file interleaves whole blocks).
    private var blockSize = 0

    private var converter: DsdEncoder? = null
    private var framesOut = 0L

    // Reused from read to read.
    private var raw = ByteArray(0)
    private var interleaved = ByteArray(0)
    private var pcm = FloatArray(0)
    private var pcmBytes = ByteArray(0)

    override fun sniff(input: ExtractorInput): Boolean {
        val head = ByteArray(16)
        if (!input.peekFully(head, 0, head.size, /* allowEndOfInput = */ true)) return false
        val first = String(head, 0, 4, Charsets.US_ASCII)
        return first == "DSD " || (first == "FRM8" && String(head, 12, 4, Charsets.US_ASCII) == "DSD ")
    }

    override fun init(output: ExtractorOutput) {
        this.output = output
        track = output.track(0, C.TRACK_TYPE_AUDIO)
    }

    override fun read(input: ExtractorInput, seekPosition: PositionHolder): Int {
        if (!headerRead) {
            readHeader(input)
            headerRead = true
            val lsbFirst = layout == Layout.DSF
            val stream = streamFor(dsdRate, channels)
            val converter = when (stream) {
                null -> DsdToPcm(channels, dsdRate, lsbFirst)
                else -> DsdPacker(channels, dsdRate, lsbFirst, dop = stream is DsdStream.Dop)
            }.also { this.converter = it }
            track.format(
                Format.Builder()
                    .setSampleMimeType(MimeTypes.AUDIO_RAW)
                    .setPcmEncoding(C.ENCODING_PCM_FLOAT)
                    .setChannelCount(channels)
                    .setSampleRate(converter.outputRate)
                    .setCustomData(stream)
                    .build(),
            )
            output.seekMap(DsdSeekMap())
            output.endTracks()
            return Extractor.RESULT_CONTINUE
        }
        if (bytesDone >= bytesPerChannel) return Extractor.RESULT_END_OF_INPUT
        val frames = when (layout) {
            Layout.DSF -> readDsfBlock(input)
            Layout.DFF -> readDffFrames(input)
        }
        if (frames <= 0) return Extractor.RESULT_END_OF_INPUT
        bytesDone += frames
        writePcm(frames)
        return Extractor.RESULT_CONTINUE
    }

    override fun seek(position: Long, timeUs: Long) {
        if (position == 0L || !headerRead) {
            headerRead = false
            bytesDone = 0
            framesOut = 0
            return
        }
        val offset = position - dataStart
        bytesDone = when (layout) {
            Layout.DSF -> offset / (blockSize.toLong() * channels) * blockSize
            Layout.DFF -> offset / channels
        }
        val converter = converter ?: return
        converter.reset()
        framesOut = bytesDone / converter.bytesPerOutput
    }

    override fun release() {}

    // ---- Headers ----

    private fun readHeader(input: ExtractorInput) {
        val id = readId(input)
        when (id) {
            "DSD " -> readDsfHeader(input)
            "FRM8" -> readDffHeader(input)
            else -> throw ParserException.createForMalformedContainer("Not a DSD file", null)
        }
        if (channels <= 0 || dsdRate <= 0 || bytesPerChannel <= 0) {
            throw ParserException.createForMalformedContainer("Incomplete DSD header", null)
        }
    }

    /** DSF: a "DSD " chunk, a "fmt " chunk, then "data" holding each channel's blocks in turn. */
    private fun readDsfHeader(input: ExtractorInput) {
        val dsdChunkSize = readLongLe(input)
        input.skipFully((dsdChunkSize - 12).toInt())
        if (readId(input) != "fmt ") throw ParserException.createForMalformedContainer("DSF without fmt", null)
        val fmtSize = readLongLe(input)
        val fmt = ByteArray((fmtSize - 12).toInt())
        input.readFully(fmt, 0, fmt.size)
        val f = ByteBuffer.wrap(fmt).order(ByteOrder.LITTLE_ENDIAN)
        f.int // format version
        if (f.int != 0) throw ParserException.createForUnsupportedContainerFeature("DSF format other than raw DSD")
        f.int // channel type
        channels = f.int
        dsdRate = f.int
        val bitsPerSample = f.int
        val sampleCount = f.long
        blockSize = f.int
        // 1 is DSF's usual LSB-first order; 8 would be MSB-first, which it never uses in practice.
        if (bitsPerSample != 1) throw ParserException.createForUnsupportedContainerFeature("DSF with $bitsPerSample bits per sample")
        if (readId(input) != "data") throw ParserException.createForMalformedContainer("DSF without data", null)
        readLongLe(input) // the data chunk's size; the sample count says how much is audio
        dataStart = input.position
        bytesPerChannel = (sampleCount + 7) / 8
        layout = Layout.DSF
    }

    /** DSDIFF: IFF chunks, big-endian; the format is in "PROP", the samples in "DSD ", one byte per channel in turn. */
    private fun readDffHeader(input: ExtractorInput) {
        readLongBe(input) // the FRM8 size
        if (readId(input) != "DSD ") throw ParserException.createForMalformedContainer("Not a DSDIFF file", null)
        while (true) {
            val id = readId(input)
            val size = readLongBe(input)
            when (id) {
                "PROP" -> readDffProperties(input, size)
                "DSD " -> {
                    dataStart = input.position
                    bytesPerChannel = size / channels.coerceAtLeast(1)
                    layout = Layout.DFF
                    return
                }
                "DST " -> throw ParserException.createForUnsupportedContainerFeature("DST-compressed DSDIFF")
                else -> input.skipFully(padded(size).toInt())
            }
        }
    }

    private fun readDffProperties(input: ExtractorInput, size: Long) {
        val end = input.position + padded(size)
        if (readId(input) != "SND ") {
            input.skipFully((end - input.position).toInt())
            return
        }
        while (input.position < end) {
            val id = readId(input)
            val chunk = readLongBe(input)
            val chunkEnd = input.position + padded(chunk)
            when (id) {
                "FS  " -> dsdRate = readIntBe(input)
                "CHNL" -> channels = readShortBe(input)
                "CMPR" -> if (readId(input) != "DSD ") {
                    throw ParserException.createForUnsupportedContainerFeature("Compressed DSDIFF")
                }
            }
            input.skipFully((chunkEnd - input.position).toInt())
        }
    }

    private fun padded(size: Long) = size + (size and 1)

    // ---- Samples ----

    /** One DSF block group (a block of each channel), turned frame-interleaved; returns its frames. */
    private fun readDsfBlock(input: ExtractorInput): Int {
        val groupBytes = blockSize * channels
        if (raw.size < groupBytes) raw = ByteArray(groupBytes)
        if (!input.readFully(raw, 0, groupBytes, /* allowEndOfInput = */ true)) return 0
        // The last block is padded out; only the samples the header counts are audio.
        val frames = minOf(blockSize.toLong(), bytesPerChannel - bytesDone).toInt()
        ensureInterleaved(frames)
        for (channel in 0 until channels) {
            val from = channel * blockSize
            var to = channel
            for (i in 0 until frames) {
                interleaved[to] = raw[from + i]
                to += channels
            }
        }
        return frames
    }

    /** Up to [DFF_READ_FRAMES] DSDIFF frames, already interleaved as the converter takes them. */
    private fun readDffFrames(input: ExtractorInput): Int {
        val frames = minOf(DFF_READ_FRAMES.toLong(), bytesPerChannel - bytesDone).toInt()
        ensureInterleaved(frames)
        if (!input.readFully(interleaved, 0, frames * channels, /* allowEndOfInput = */ true)) return 0
        return frames
    }

    private fun ensureInterleaved(frames: Int) {
        if (interleaved.size < frames * channels) interleaved = ByteArray(frames * channels)
    }

    /** Filters the [frames] in [interleaved] and hands the PCM to the track as one sample. */
    private fun writePcm(frames: Int) {
        val converter = converter ?: return
        val maxOut = frames / converter.bytesPerOutput + 1
        if (pcm.size < maxOut * channels) pcm = FloatArray(maxOut * channels)
        val written = converter.process(interleaved, frames, pcm)
        if (written == 0) return
        val size = written * channels * 4
        if (pcmBytes.size < size) pcmBytes = ByteArray(size)
        ByteBuffer.wrap(pcmBytes).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer().put(pcm, 0, written * channels)
        track.sampleData(ParsableByteArray(pcmBytes, size), size)
        val timeUs = framesOut * C.MICROS_PER_SECOND / converter.outputRate
        track.sampleMetadata(timeUs, C.BUFFER_FLAG_KEY_FRAME, size, 0, null)
        framesOut += written
    }

    // ---- Seeking ----

    /**
     * Seeks by arithmetic, as DSD is a constant stream: to the start of a DSF block group,
     * or of a DSDIFF frame that begins an output sample.
     */
    private inner class DsdSeekMap : SeekMap {
        override fun isSeekable() = true

        override fun getDurationUs(): Long = bytesPerChannel * 8 * C.MICROS_PER_SECOND / dsdRate

        override fun getSeekPoints(timeUs: Long): SeekMap.SeekPoints {
            val bytesPerOutput = converter?.bytesPerOutput ?: 1
            val step = if (layout == Layout.DSF) blockSize.toLong() else bytesPerOutput.toLong()
            val wanted = timeUs.coerceAtLeast(0) * dsdRate / 8 / C.MICROS_PER_SECOND
            val byte = (wanted / step * step).coerceIn(0, ((bytesPerChannel - 1) / step * step).coerceAtLeast(0))
            val position = dataStart + byte * channels
            val time = byte * 8 * C.MICROS_PER_SECOND / dsdRate
            return SeekMap.SeekPoints(SeekPoint(time, position))
        }
    }

    // ---- Reading numbers ----

    private val scratch = ByteArray(8)

    private fun readId(input: ExtractorInput): String {
        input.readFully(scratch, 0, 4)
        return String(scratch, 0, 4, Charsets.US_ASCII)
    }

    private fun readLongLe(input: ExtractorInput): Long {
        input.readFully(scratch, 0, 8)
        return ByteBuffer.wrap(scratch, 0, 8).order(ByteOrder.LITTLE_ENDIAN).long
    }

    private fun readLongBe(input: ExtractorInput): Long {
        input.readFully(scratch, 0, 8)
        return ByteBuffer.wrap(scratch, 0, 8).order(ByteOrder.BIG_ENDIAN).long
    }

    private fun readIntBe(input: ExtractorInput): Int {
        input.readFully(scratch, 0, 4)
        return ByteBuffer.wrap(scratch, 0, 4).order(ByteOrder.BIG_ENDIAN).int
    }

    private fun readShortBe(input: ExtractorInput): Int {
        input.readFully(scratch, 0, 2)
        return ByteBuffer.wrap(scratch, 0, 2).order(ByteOrder.BIG_ENDIAN).short.toInt() and 0xFFFF
    }
}
