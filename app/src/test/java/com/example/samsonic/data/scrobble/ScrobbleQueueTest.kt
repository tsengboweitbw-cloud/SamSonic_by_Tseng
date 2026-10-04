package com.example.samsonic.data.scrobble

import com.example.samsonic.FakeMusicLibrary
import java.io.File
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ScrobbleQueueTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val file get() = File(folder.root, "scrobbles.json")

    /** Takes listens until [failAfter] have gone, then is out of reach. */
    private class Server(var failAfter: Int = Int.MAX_VALUE) : FakeMusicLibrary() {
        val received = mutableListOf<Pair<String, Long?>>()

        override suspend fun scrobble(id: String, submission: Boolean, timeMs: Long?) {
            if (received.size >= failAfter) throw IOException("unreachable")
            received += id to timeMs
        }
    }

    private fun listen(song: String, time: Long = 1L, server: String = "s1") = PendingScrobble(server, song, time)

    @Test
    fun listensSurviveTheAppClosing() = runTest {
        ScrobbleQueue(file).add(listen("a", 10))
        assertEquals(listOf(listen("a", 10)), ScrobbleQueue(file).pending())
    }

    @Test
    fun theOldestGoWhenTooManyWait() = runTest {
        val queue = ScrobbleQueue(file)
        repeat(MAX_PENDING_SCROBBLES + 5) { queue.add(listen("song$it", it.toLong())) }
        val kept = queue.pending()
        assertEquals(MAX_PENDING_SCROBBLES, kept.size)
        assertEquals("song5", kept.first().songId)
        assertEquals("song${MAX_PENDING_SCROBBLES + 4}", kept.last().songId)
    }

    @Test
    fun flushSendsOldestFirstWithTheirTimesAndEmptiesTheQueue() = runTest {
        val queue = ScrobbleQueue(file)
        queue.add(listen("a", 1))
        queue.add(listen("b", 2))
        val server = Server()
        assertEquals(2, queue.flush(server, "s1"))
        assertEquals(listOf("a" to 1L, "b" to 2L), server.received)
        assertEquals(emptyList<PendingScrobble>(), queue.pending())
    }

    @Test
    fun flushStopsAtTheFirstFailureAndKeepsTheRest() = runTest {
        val queue = ScrobbleQueue(file)
        listOf("a", "b", "c").forEachIndexed { i, id -> queue.add(listen(id, i.toLong())) }
        val server = Server(failAfter = 1)
        assertEquals(1, queue.flush(server, "s1"))
        assertEquals(listOf("b", "c"), queue.pending().map { it.songId })
    }

    @Test
    fun flushOnlySendsWhatBelongsToTheServerInUse() = runTest {
        val queue = ScrobbleQueue(file)
        queue.add(listen("mine", server = "s1"))
        queue.add(listen("theirs", server = "s2"))
        val server = Server()
        queue.flush(server, "s1")
        assertEquals(listOf("mine"), server.received.map { it.first })
        assertEquals(listOf("theirs"), queue.pending().map { it.songId })
    }

    @Test
    fun aRemovedServersListensAreForgotten() = runTest {
        val queue = ScrobbleQueue(file)
        queue.add(listen("a", server = "s1"))
        queue.add(listen("b", server = "s2"))
        queue.forget("s1")
        assertEquals(listOf("b"), queue.pending().map { it.songId })
    }

    @Test
    fun aFileThatCannotBeReadIsAnEmptyQueue() = runTest {
        file.writeText("{ not json")
        val queue = ScrobbleQueue(file)
        assertEquals(emptyList<PendingScrobble>(), queue.pending())
        queue.add(listen("a"))
        assertEquals(1, queue.pending().size)
    }
}
