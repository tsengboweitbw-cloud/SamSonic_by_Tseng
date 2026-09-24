package com.example.samsonic.playback.dsd

import android.net.Uri
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.Extractor
import androidx.media3.extractor.ExtractorsFactory
import androidx.media3.extractor.text.SubtitleParser

/**
 * The player's usual extractors with [DsdExtractor] in front: it only has to peek at a
 * file's first bytes to pass on everything that isn't DSD, and it goes first so nothing
 * else mistakes a DSD file for its own format.
 */
@UnstableApi
class DsdExtractorsFactory(private val base: ExtractorsFactory = DefaultExtractorsFactory()) : ExtractorsFactory {
    override fun createExtractors(): Array<Extractor> = arrayOf<Extractor>(DsdExtractor()) + base.createExtractors()

    override fun createExtractors(uri: Uri, responseHeaders: Map<String, List<String>>): Array<Extractor> =
        arrayOf<Extractor>(DsdExtractor()) + base.createExtractors(uri, responseHeaders)

    override fun experimentalSetTextTrackTranscodingEnabled(textTrackTranscodingEnabled: Boolean): ExtractorsFactory {
        base.experimentalSetTextTrackTranscodingEnabled(textTrackTranscodingEnabled)
        return this
    }

    override fun setSubtitleParserFactory(subtitleParserFactory: SubtitleParser.Factory): ExtractorsFactory {
        base.setSubtitleParserFactory(subtitleParserFactory)
        return this
    }
}
