package com.example.samsonic

import android.content.Context
import java.io.File
import android.util.Log
import androidx.compose.runtime.compositionLocalOf
import com.example.samsonic.data.AutoDjSettings
import com.example.samsonic.data.CoverArtPrefetcher
import com.example.samsonic.data.ImageCacheSettings
import com.example.samsonic.data.MusicCache
import com.example.samsonic.data.MusicLibrary
import com.example.samsonic.data.MusicSources
import com.example.samsonic.data.PlaylistCovers
import com.example.samsonic.data.SessionManager
import com.example.samsonic.data.SubsonicRepository
import com.example.samsonic.data.cache.LibraryCacheStore
import com.example.samsonic.data.offline.OfflineDownloader
import com.example.samsonic.data.offline.OfflineMusic
import com.example.samsonic.data.offline.OfflineOnlySetting
import com.example.samsonic.data.offline.OfflineStore
import com.example.samsonic.data.scrobble.ScrobbleQueue
import androidx.media3.datasource.okhttp.OkHttpDataSource
import com.example.samsonic.data.HomeLayoutManager
import com.example.samsonic.data.LibraryLayoutManager
import com.example.samsonic.data.ThemeManager
import com.example.samsonic.data.device.DeviceLibrary
import com.example.samsonic.playback.AudioOutputMonitor
import com.example.samsonic.playback.PlaybackStore
import com.example.samsonic.playback.PlayerState
import com.example.samsonic.playback.autodj.AutoDj
import com.example.samsonic.playback.usb.UsbDacManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit

/**
 * Small hand-rolled DI container (no Hilt/Koin) since the app is a single module with a
 * handful of singletons. Lives on [SamSonicApplication] so it (and playback) survives
 * Activity recreation.
 */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val applicationScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .apply {
            // Debug builds only, and with the auth params blanked: the URLs carry them.
            if (BuildConfig.DEBUG) {
                addInterceptor(
                    HttpLoggingInterceptor { Log.d("OkHttp", it.replace(AuthParams, "$1=…")) }
                        .apply { level = HttpLoggingInterceptor.Level.BASIC },
                )
            }
        }
        .build()

    val sessionManager = SessionManager(appContext)

    val themeManager = ThemeManager(appContext)

    /** The queue and playback position, kept for the next time the app or its media controls start. */
    val playbackStore = PlaybackStore(appContext)

    val libraryLayoutManager = LibraryLayoutManager(appContext)

    /** Which Home sections show, in what order and how long. */
    val homeLayoutManager = HomeLayoutManager(appContext)

    /** The covers picked for playlists, which the server has no place for. */
    val playlistCovers = PlaylistCovers(appContext)

    val imageCacheSettings = ImageCacheSettings(appContext)

    /** The songs kept on the phone for offline; the music cache leaves them alone. */
    val offlineStore = OfflineStore(File(appContext.filesDir, "offline.json"))

    /** Whether only the songs kept for offline are shown. */
    val offlineOnly = OfflineOnlySetting(appContext)

    /** Streamed songs kept on the phone, so a poor connection doesn't stop the music. */
    val musicCache = MusicCache(appContext, imageCacheSettings, pinned = offlineStore::cacheKeys)

    /** What the player sends out and where; the playback service feeds it, Song info shows it. */
    val audioOutput = AudioOutputMonitor(appContext)

    /** USB audio devices plugged in, and the way to open one for the native driver. */
    val usbDacs = UsbDacManager(appContext)

    /** Listens the server couldn't be told of, sent once it can be. */
    val scrobbleQueue = ScrobbleQueue(File(appContext.filesDir, "scrobbles.json"))

    private val subsonic = SubsonicRepository(okHttpClient, appContext)

    val sources: MusicSources = MusicSources(
        sessionManager, subsonic, DeviceLibrary(appContext),
        LibraryCacheStore(File(appContext.cacheDir, "library")), scrobbleQueue,
        offlineStore, offlineOnly.enabled, applicationScope,
    )

    /** Caches every cover of the signed-in server; the music on this phone has its art on hand. */
    val coverArtPrefetcher = CoverArtPrefetcher(appContext, subsonic, applicationScope)

    /** The active source's library. Read it where it's used rather than holding on to it: it changes with the source. */
    val repository: MusicLibrary get() = sources.library

    /** Saves the songs kept for offline whole, from the server in use. */
    val offlineDownloader: OfflineDownloader = OfflineDownloader(
        context = appContext,
        cache = { musicCache.cache },
        upstream = OkHttpDataSource.Factory(okHttpClient),
        store = offlineStore,
        library = { repository },
        serverKey = { sources.serverKey },
        wifiOnly = musicCache.prefetchWifiOnly,
        scope = applicationScope,
    )

    /** Keeping songs on the phone for offline, and which are kept. */
    val offlineMusic = OfflineMusic(
        store = offlineStore,
        library = { repository },
        serverKey = { sources.serverKey },
        serverKeys = sources.serverKeys,
        scope = applicationScope,
        onKept = { offlineDownloader.start() },
    )

    /** What Auto DJ adds as the queue runs out, and from what. */
    val autoDjSettings = AutoDjSettings(appContext)

    private var startedAutoDj: AutoDj? = null

    val playerState: PlayerState by lazy {
        PlayerState(appContext, { repository }, applicationScope, playbackStore).also { player ->
            player.connect()
            // Watches the queue from the start, whether or not anything shows it.
            startedAutoDj = AutoDj(player, { repository }, autoDjSettings, applicationScope).also { it.start() }
        }
    }

    /** Keeps the music going when the queue runs out; starts with [playerState]. */
    val autoDj: AutoDj get() = playerState.let { startedAutoDj!! }

    init {
        // What's playing belongs to the library just left, as does what played before.
        applicationScope.launch {
            sources.active.drop(1).collect {
                playerState.stopAndClearQueue()
                autoDj.reset()
            }
        }
        // So are the covers being cached.
        applicationScope.launch { sources.active.drop(1).collect { coverArtPrefetcher.cancel() } }
        // The songs kept for offline are saved for the server in use, from the start and after each switch.
        applicationScope.launch {
            sources.active.collect {
                offlineDownloader.cancel()
                offlineDownloader.start()
            }
        }
        // Caches left behind by moving them to or from an SD card.
        applicationScope.launch(Dispatchers.IO) {
            imageCacheSettings.deleteUnusedCaches()
            musicCache.deleteUnusedCaches()
        }
    }
}

val LocalAppContainer = compositionLocalOf<AppContainer> {
    error("AppContainer not provided - wrap the app in CompositionLocalProvider(LocalAppContainer provides ...)")
}

/** The token, salt and password query params of a Subsonic URL. */
private val AuthParams = Regex("([?&][tsp])=[^&\\s]*")
