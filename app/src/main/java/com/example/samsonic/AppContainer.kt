package com.example.samsonic

import android.content.Context
import androidx.compose.runtime.compositionLocalOf
import com.example.samsonic.data.AutoDjSettings
import com.example.samsonic.data.CoverArtPrefetcher
import com.example.samsonic.data.ImageCacheSettings
import com.example.samsonic.data.MusicCache
import com.example.samsonic.data.MusicLibrary
import com.example.samsonic.data.MusicSources
import com.example.samsonic.data.SessionManager
import com.example.samsonic.data.SubsonicRepository
import com.example.samsonic.data.LibraryLayoutManager
import com.example.samsonic.data.ThemeManager
import com.example.samsonic.data.device.DeviceLibrary
import com.example.samsonic.playback.AudioOutputMonitor
import com.example.samsonic.playback.BitPerfectOutput
import com.example.samsonic.playback.PlayerState
import com.example.samsonic.playback.autodj.AutoDj
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
        .addInterceptor(
            HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC }
        )
        .build()

    val sessionManager = SessionManager(appContext)

    val themeManager = ThemeManager(appContext)

    val libraryLayoutManager = LibraryLayoutManager(appContext)

    val imageCacheSettings = ImageCacheSettings(appContext)

    /** Streamed songs kept on the phone, so a poor connection doesn't stop the music. */
    val musicCache = MusicCache(appContext, imageCacheSettings)

    /** What the player sends out and where; the playback service feeds it, Song info shows it. */
    val audioOutput = AudioOutputMonitor(appContext)

    /** Bit-perfect output to a USB DAC (Android 14+); the playback service's tracks go through it. */
    val bitPerfect = BitPerfectOutput(appContext)

    private val subsonic = SubsonicRepository(okHttpClient, appContext)

    val sources = MusicSources(sessionManager, subsonic, DeviceLibrary(appContext))

    /** Caches every cover of the signed-in server; the music on this phone has its art on hand. */
    val coverArtPrefetcher = CoverArtPrefetcher(appContext, subsonic, applicationScope)

    /** The active source's library. Read it where it's used rather than holding on to it: it changes with the source. */
    val repository: MusicLibrary get() = sources.library

    /** What Auto DJ adds as the queue runs out, and from what. */
    val autoDjSettings = AutoDjSettings(appContext)

    private var startedAutoDj: AutoDj? = null

    val playerState: PlayerState by lazy {
        PlayerState(appContext, { repository }, applicationScope).also { player ->
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
