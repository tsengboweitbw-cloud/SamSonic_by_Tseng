package com.example.samsonic.playback

import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import java.lang.reflect.Proxy
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackErrorHandlerTest {
    /** A [Player] that records the calls the handler makes; [mediaItemCount] songs, on [index]. */
    private class Fake(val mediaItemCount: Int, var index: Int = 0) {
        val calls = mutableListOf<String>()
        val messages = mutableListOf<Boolean>()

        val player: Player = Proxy.newProxyInstance(
            Player::class.java.classLoader,
            arrayOf(Player::class.java),
        ) { _, method, _ ->
            when (method.name) {
                "getCurrentMediaItem" -> MediaItem.Builder().setMediaId("song$index").build()
                "getMediaItemCount" -> mediaItemCount
                "hasNextMediaItem" -> index < mediaItemCount - 1
                "seekToNextMediaItem" -> { index++; calls += "next"; null }
                "prepare" -> { calls += "prepare"; null }
                else -> error("unexpected ${method.name}")
            }
        } as Player

        val handler = PlaybackErrorHandler(player) { messages += it }
    }

    private fun error(code: Int) = PlaybackException("failed", null, code)
    private val network = PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED
    private val corrupt = PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED

    @Test fun networkErrorRetriesOnceThenSkips() {
        val f = Fake(mediaItemCount = 3)
        f.handler.onPlayerError(error(network))
        assertEquals(listOf("prepare"), f.calls)
        f.handler.onPlayerError(error(network))
        assertEquals(listOf("prepare", "next", "prepare"), f.calls)
        assertEquals(listOf(true), f.messages)
    }

    @Test fun corruptFileSkipsStraightAway() {
        val f = Fake(mediaItemCount = 3)
        f.handler.onPlayerError(error(corrupt))
        assertEquals(listOf("next", "prepare"), f.calls)
    }

    @Test fun lastSongStopsWithoutSkipping() {
        val f = Fake(mediaItemCount = 2, index = 1)
        f.handler.onPlayerError(error(corrupt))
        assertEquals(emptyList<String>(), f.calls)
        assertEquals(listOf(false), f.messages)
    }

    @Test fun stopsWhenEverySongHasFailed() {
        val f = Fake(mediaItemCount = 3)
        repeat(3) { f.handler.onPlayerError(error(corrupt)) }
        assertEquals(listOf(true, true, false), f.messages)
    }

    @Test fun playingAgainResetsTheCount() {
        val f = Fake(mediaItemCount = 2)
        f.handler.onPlayerError(error(corrupt))
        f.handler.onPlaybackStateChanged(Player.STATE_READY)
        f.index = 0
        f.handler.onPlayerError(error(corrupt))
        assertEquals(listOf(true, true), f.messages)
    }
}
