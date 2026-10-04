package com.example.samsonic.playback

import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import com.example.samsonic.FakeMusicLibrary
import com.example.samsonic.data.scrobble.PendingScrobble
import com.example.samsonic.data.scrobble.ScrobbleQueue
import java.io.File
import java.lang.reflect.Proxy
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ScrobblerTest {
    /** A [Player] with the little state [Scrobbler] reads, and the listeners it adds. */
    private class FakePlayer(var songId: String?, var durationMs: Long = 100_000, var speed: Float = 1f) {
        var playing = false
        val listeners = mutableListOf<Player.Listener>()

        val player: Player = Proxy.newProxyInstance(Player::class.java.classLoader, arrayOf(Player::class.java)) { _, method, args ->
            when (method.name) {
                "addListener" -> { listeners += args[0] as Player.Listener; null }
                "removeListener" -> { listeners -= args[0] as Player.Listener; null }
                "getCurrentMediaItem" -> songId?.let { MediaItem.Builder().setMediaId(it).build() }
                "isPlaying" -> playing
                "getDuration" -> durationMs
                "getPlaybackParameters" -> PlaybackParameters(speed)
                else -> error("unexpected ${method.name}")
            }
        } as Player

        fun playing(value: Boolean) {
            playing = value
            listeners.toList().forEach { it.onIsPlayingChanged(value) }
        }

        fun play(id: String) {
            songId = id
            listeners.toList().forEach {
                it.onMediaItemTransition(MediaItem.Builder().setMediaId(id).build(), Player.MEDIA_ITEM_TRANSITION_REASON_AUTO)
            }
        }
    }

    private class Recorder : FakeMusicLibrary() {
        val sent = mutableListOf<Pair<String, Boolean>>()
        var fail = false
        val times = mutableListOf<Long?>()
        override suspend fun scrobble(id: String, submission: Boolean, timeMs: Long?) {
            if (fail) throw java.io.IOException("offline")
            sent += id to submission
            times += timeMs
        }
    }

    private fun TestScope.scrobbler(
        player: FakePlayer,
        library: Recorder,
        queue: ScrobbleQueue? = null,
        server: String? = null,
    ) =
        Scrobbler(
            player.player, { library }, backgroundScope, clock = { currentTime },
            queue = queue, serverKey = { server }, wallClock = { 1_700_000_000_000 + currentTime },
        )

    @Test
    fun startingASongSendsNowPlayingOnce() = runTest {
        val library = Recorder()
        val player = FakePlayer("a")
        scrobbler(player, library)
        player.playing(true)
        advanceTimeBy(500)
        player.playing(false)
        player.playing(true)
        advanceTimeBy(500)
        assertEquals(listOf("a" to false), library.sent)
    }

    @Test
    fun aPlayIsSubmittedOnceFortyPercentHasBeenListenedTo() = runTest {
        val library = Recorder()
        val player = FakePlayer("a", durationMs = 100_000)
        scrobbler(player, library)
        player.playing(true)
        advanceTimeBy(39_000)
        assertEquals(listOf("a" to false), library.sent)
        advanceTimeBy(3_000)
        assertEquals(listOf("a" to false, "a" to true), library.sent)
        // Not again as it plays on.
        advanceTimeBy(60_000)
        assertEquals(2, library.sent.size)
    }

    @Test
    fun timeSpentPausedDoesNotCount() = runTest {
        val library = Recorder()
        val player = FakePlayer("a", durationMs = 100_000)
        scrobbler(player, library)
        player.playing(true)
        advanceTimeBy(5_000)
        player.playing(false)
        advanceTimeBy(60_000)
        assertEquals(listOf("a" to false), library.sent)
        player.playing(true)
        advanceTimeBy(38_000)
        assertEquals(listOf("a" to false, "a" to true), library.sent)
    }

    @Test
    fun fasterPlaybackCountsFaster() = runTest {
        val library = Recorder()
        val player = FakePlayer("a", durationMs = 100_000, speed = 2f)
        scrobbler(player, library)
        player.playing(true)
        advanceTimeBy(22_000)
        assertEquals(listOf("a" to false, "a" to true), library.sent)
    }

    @Test
    fun skippingBeforeFortyPercentSubmitsNothing() = runTest {
        val library = Recorder()
        val player = FakePlayer("a")
        scrobbler(player, library)
        player.playing(true)
        advanceTimeBy(3_000)
        player.play("b")
        advanceTimeBy(3_000)
        assertEquals(listOf("a" to false, "b" to false), library.sent)
    }

    @Test
    fun theSameSongPlayedAgainIsReportedAgain() = runTest {
        val library = Recorder()
        val player = FakePlayer("a", durationMs = 10_000)
        scrobbler(player, library)
        player.playing(true)
        advanceTimeBy(5_000)
        player.play("a")
        advanceTimeBy(5_000)
        assertEquals(listOf("a" to false, "a" to true, "a" to false, "a" to true), library.sent)
    }

    @Test
    fun anUnknownDurationSubmitsNothing() = runTest {
        val library = Recorder()
        val player = FakePlayer("a", durationMs = C.TIME_UNSET)
        scrobbler(player, library)
        player.playing(true)
        advanceTimeBy(120_000)
        assertEquals(listOf("a" to false), library.sent)
    }

    @Test
    fun aFailedScrobbleIsDroppedWithoutStoppingTheRest() = runTest {
        val library = Recorder().apply { fail = true }
        val player = FakePlayer("a", durationMs = 10_000)
        scrobbler(player, library)
        player.playing(true)
        advanceTimeBy(2_000)
        library.fail = false
        player.play("b")
        advanceTimeBy(5_000)
        assertEquals(listOf("b" to false, "b" to true), library.sent)
    }

    @Test
    fun releasedItStopsListening() = runTest {
        val library = Recorder()
        val player = FakePlayer("a")
        val scrobbler = scrobbler(player, library)
        scrobbler.release()
        player.playing(true)
        advanceTimeBy(20_000)
        assertEquals(emptyList<Pair<String, Boolean>>(), library.sent)
    }

    private fun queueIn(folder: File) = ScrobbleQueue(File(folder, "scrobbles.json"))

    /** The queue works on a real IO thread, which virtual time does not wait for: give it a moment. */
    private fun TestScope.settle() = repeat(20) {
        Thread.sleep(10)
        runCurrent()
    }

    @Test
    fun aListenThatFailsWaitsInTheQueueWithTheTimeItHappened() = runTest {
        val dir = java.nio.file.Files.createTempDirectory("queue").toFile()
        val queue = queueIn(dir)
        val library = Recorder().apply { fail = true }
        val player = FakePlayer("a", durationMs = 10_000)
        scrobbler(player, library, queue, server = "s1")
        player.playing(true)
        advanceTimeBy(5_000)
        settle()
        // Only the listen, not the "now playing" that failed first.
        assertEquals(1, queue.pending().size)
        val waiting = queue.pending().single()
        assertEquals("s1", waiting.serverKey)
        assertEquals("a", waiting.songId)
        assertEquals(1_700_000_000_000 + 4_000, waiting.timeMs)
    }

    @Test
    fun whatWaitsGoesOutOnceTheServerAnswers() = runTest {
        val dir = java.nio.file.Files.createTempDirectory("queue").toFile()
        val queue = queueIn(dir)
        queue.add(PendingScrobble("s1", "old", 1_600_000_000_000))
        val library = Recorder()
        val player = FakePlayer("a", durationMs = 10_000)
        scrobbler(player, library, queue, server = "s1")
        advanceTimeBy(1)
        settle()
        // On starting, the old listen goes out with the time it happened.
        assertEquals(listOf("old" to true), library.sent)
        assertEquals(listOf<Long?>(1_600_000_000_000), library.times)
        assertEquals(emptyList<PendingScrobble>(), queue.pending())
    }

    @Test
    fun whatWaitsStaysWhileTheServerIsOutOfReach() = runTest {
        val dir = java.nio.file.Files.createTempDirectory("queue").toFile()
        val queue = queueIn(dir)
        queue.add(PendingScrobble("s1", "old", 1))
        val library = Recorder().apply { fail = true }
        val player = FakePlayer("a", durationMs = 10_000)
        scrobbler(player, library, queue, server = "s1")
        advanceTimeBy(1)
        settle()
        assertEquals(1, queue.pending().size)
    }

    @Test
    fun withNoServerNothingIsQueued() = runTest {
        val dir = java.nio.file.Files.createTempDirectory("queue").toFile()
        val queue = queueIn(dir)
        val library = Recorder().apply { fail = true }
        val player = FakePlayer("a", durationMs = 10_000)
        scrobbler(player, library, queue, server = null)
        player.playing(true)
        advanceTimeBy(5_000)
        settle()
        assertEquals(emptyList<PendingScrobble>(), queue.pending())
    }
}
