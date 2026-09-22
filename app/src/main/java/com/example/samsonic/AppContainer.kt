package com.example.samsonic

import android.content.Context
import androidx.compose.runtime.compositionLocalOf
import com.example.samsonic.data.SessionManager
import com.example.samsonic.data.SubsonicRepository
import com.example.samsonic.playback.PlayerState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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

    val repository = SubsonicRepository(sessionManager, okHttpClient)

    val playerState: PlayerState by lazy {
        PlayerState(appContext, repository, applicationScope).also { it.connect() }
    }
}

val LocalAppContainer = compositionLocalOf<AppContainer> {
    error("AppContainer not provided - wrap the app in CompositionLocalProvider(LocalAppContainer provides ...)")
}
