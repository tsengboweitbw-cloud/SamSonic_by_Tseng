package com.example.samsonic.data

import android.content.Context
import android.content.ContextWrapper
import java.security.MessageDigest
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** The repository against a local server speaking Subsonic's JSON, as Navidrome does. */
class SubsonicRepositoryTest {
    private lateinit var server: MockWebServer
    private lateinit var repository: SubsonicRepository

    // Only the error paths read strings from it, and none of these tests take one.
    private val context: Context = ContextWrapper(null)

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        repository = SubsonicRepository(OkHttpClient(), context)
        repository.configure(ServerCredentials(server.url("/").toString(), "amber", "secret"))
    }

    @After
    fun tearDown() = server.shutdown()

    private fun reply(json: String) = server.enqueue(
        MockResponse().setHeader("Content-Type", "application/json").setBody(json),
    )

    private fun ok(body: String = "") = reply("""{"subsonic-response":{"status":"ok","version":"1.16.1"$body}}""")

    private fun RecordedRequest.param(name: String): String? = requestUrl?.queryParameter(name)

    @Test
    fun everyCallCarriesATokenAndNeverThePassword() = runTest {
        ok()
        repository.star("song1")
        val request = server.takeRequest()
        assertEquals("/rest/star.view", request.requestUrl?.encodedPath)
        assertEquals("amber", request.param("u"))
        assertEquals("song1", request.param("id"))
        assertEquals("json", request.param("f"))
        assertEquals(null, request.param("p"))
        val salt = request.param("s")
        assertNotNull(salt)
        assertEquals(md5("secret$salt"), request.param("t"))
        assertFalse(request.path.orEmpty().contains("secret"))
    }

    @Test
    fun pingAcceptsALoginAndAddsTheSchemeAndSlash() = runTest {
        ok()
        val host = "${server.hostName}:${server.port}"
        val result = repository.ping("  $host/ ", "amber", "secret")
        assertEquals("http://$host/", result.getOrThrow().serverUrl)
    }

    @Test
    fun pingReportsWhyTheServerRefusedALogin() = runTest {
        reply("""{"subsonic-response":{"status":"failed","error":{"code":40,"message":"Wrong username or password"}}}""")
        val result = repository.ping(server.url("/").toString(), "amber", "wrong")
        assertEquals("Wrong username or password", result.exceptionOrNull()?.message)
    }

    @Test
    fun anAlbumMapsToItsSongsWithDefaultsForWhatTheServerOmits() = runTest {
        ok(
            """
            ,"album":{"id":"al1","name":"Kind of Blue","artist":"Miles Davis","artistId":"ar1","songCount":2,
              "duration":2700,"year":1959,"genre":"Jazz",
              "song":[
                {"id":"s1","title":"So What","album":"Kind of Blue","albumId":"al1","artist":"Miles Davis",
                 "track":1,"duration":562,"suffix":"flac","bitRate":900,"samplingRate":96000,"bitDepth":24,
                 "starred":"2024-01-01T00:00:00Z"},
                {"id":"s2","title":"Blue in Green"}
              ]}
            """,
        )
        val (album, songs) = repository.getAlbum("al1")
        assertEquals("Kind of Blue", album.title)
        assertEquals(1959, album.year)
        assertEquals(listOf("s1", "s2"), songs.map { it.id })
        val first = songs[0]
        assertTrue(first.liked)
        assertEquals(96_000, first.samplingRate)
        assertEquals(24, first.bitDepth)
        assertEquals("flac", first.suffix)
        val bare = songs[1]
        assertFalse(bare.liked)
        assertEquals("Unknown Artist", bare.artistName)
        assertEquals(0, bare.trackNumber)
        assertEquals("al1", server.takeRequest().param("id"))
    }

    @Test
    fun searchMapsArtistsAlbumsAndSongs() = runTest {
        ok(
            """
            ,"searchResult3":{
              "artist":[{"id":"ar1","name":"Miles Davis","albumCount":30}],
              "album":[{"id":"al1","name":"Kind of Blue","artist":"Miles Davis","songCount":5,"duration":2700}],
              "song":[{"id":"s1","title":"So What"}]}
            """,
        )
        val results = repository.search("miles")
        assertEquals(listOf("Miles Davis"), results.artists.map { it.name })
        assertEquals(listOf("Kind of Blue"), results.albums.map { it.title })
        assertEquals(listOf("s1"), results.songs.map { it.id })
        assertEquals("miles", server.takeRequest().param("query"))
    }

    @Test
    fun anEmptySearchIsEmptyNotAnError() = runTest {
        ok()
        val results = repository.search("zzz")
        assertTrue(results.artists.isEmpty() && results.albums.isEmpty() && results.songs.isEmpty())
    }

    @Test
    fun randomSongsPassTheFiltersAlong() = runTest {
        ok(""","randomSongs":{"song":[{"id":"s1","title":"One"}]}""")
        val songs = repository.randomSongs(count = 7, genre = "Jazz", fromYear = 1950, toYear = 1959)
        assertEquals(listOf("s1"), songs.map { it.id })
        val request = server.takeRequest()
        assertEquals("7", request.param("size"))
        assertEquals("Jazz", request.param("genre"))
        assertEquals("1950", request.param("fromYear"))
        assertEquals("1959", request.param("toYear"))
    }

    @Test
    fun likedSongsComeFromTheStarredList() = runTest {
        ok(""","starred2":{"song":[{"id":"s1","title":"One","starred":"2024-01-01T00:00:00Z"}]}""")
        val songs = repository.getLikedSongs()
        assertEquals(listOf("s1"), songs.map { it.id })
        assertTrue(songs.single().liked)
    }

    @Test
    fun aScrobbleSaysWhetherItIsASubmission() = runTest {
        ok()
        ok()
        repository.scrobble("s1", submission = false)
        repository.scrobble("s1", submission = true)
        val nowPlaying = server.takeRequest()
        val played = server.takeRequest()
        assertEquals("/rest/scrobble.view", nowPlaying.requestUrl?.encodedPath)
        assertEquals("false", nowPlaying.param("submission"))
        assertEquals("true", played.param("submission"))
        assertEquals("s1", played.param("id"))
    }


    @Test
    fun aListenToldLateCarriesTheTimeItHappened() = runTest {
        ok()
        repository.scrobble("s1", submission = true, timeMs = 1_700_000_000_000)
        assertEquals("1700000000000", server.takeRequest().param("time"))
    }

    @Test
    fun aListenToldAtOnceCarriesNoTime() = runTest {
        ok()
        repository.scrobble("s1", submission = true)
        assertEquals(null, server.takeRequest().param("time"))
    }
    @Test
    fun aPlaylistCreatedWithSeveralSongsRepeatsTheSongIdParameter() = runTest {
        ok()
        repository.createPlaylist("Evening", listOf("s1", "s2", "s3"))
        val request = server.takeRequest()
        assertEquals("Evening", request.param("name"))
        assertEquals(listOf("s1", "s2", "s3"), request.requestUrl?.queryParameterValues("songId"))
    }

    @Test
    fun renamingAPlaylistSendsItsIdAndTheNewName() = runTest {
        ok()
        repository.renamePlaylist("p1", "Late night")
        val request = server.takeRequest()
        assertEquals("/rest/updatePlaylist.view", request.requestUrl?.encodedPath)
        assertEquals("p1", request.param("playlistId"))
        assertEquals("Late night", request.param("name"))
        assertEquals(emptyList<String>(), request.requestUrl?.queryParameterValues("songIdToAdd"))
    }

    @Test
    fun reorderingAPlaylistSendsEverySongInTheNewOrder() = runTest {
        ok()
        repository.reorderPlaylist("p1", listOf("s3", "s1", "s2", "s1"))
        val request = server.takeRequest()
        assertEquals("/rest/createPlaylist.view", request.requestUrl?.encodedPath)
        assertEquals("p1", request.param("playlistId"))
        assertEquals(null, request.param("name"))
        assertEquals(listOf("s3", "s1", "s2", "s1"), request.requestUrl?.queryParameterValues("songId"))
    }

    @Test
    fun anEmptyOrderIsNeverSentSoThePlaylistIsNotEmptied() = runTest {
        val failed = runCatching { repository.reorderPlaylist("p1", emptyList()) }.isFailure
        assertTrue(failed)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun streamUrlsPointAtTheServerWithTokenAuth() {
        val url = repository.streamUrl("song 1")
        assertTrue(url, url.startsWith(server.url("/rest/stream.view").toString()))
        assertTrue(url, url.contains("id=song+1"))
        assertTrue(url, url.contains("&t=") || url.contains("?t="))
        assertFalse(url, url.contains("secret"))
    }

    @Test
    fun streamsAreCachedByServerUserAndSongNotByTheChangingToken() {
        val key = repository.streamCacheKey("s1")
        assertEquals("${server.url("/")}|amber|s1", key)
        assertEquals(key, repository.streamCacheKey("s1"))
    }

    @Test
    fun coverUrlsShareOneSaltAndAreAbsentWithoutArt() {
        assertEquals(null, repository.coverArtUrl(null))
        assertEquals(null, repository.coverArtUrl("  "))
        val a = repository.coverArtUrl("al1", 300)!!
        val b = repository.coverArtUrl("al2", 300)!!
        fun salt(url: String) = Regex("[?&]s=([^&]*)").find(url)?.groupValues?.get(1)
        assertNotNull(salt(a))
        assertEquals(salt(a), salt(b))
    }

    private fun md5(text: String) =
        MessageDigest.getInstance("MD5").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }
}
