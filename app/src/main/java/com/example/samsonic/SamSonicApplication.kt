package com.example.samsonic

import android.app.Application
import android.content.Context
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import com.example.samsonic.data.CoverArtCacheKeys
import com.example.samsonic.locale.AppLanguages
import okio.Path.Companion.toOkioPath
import kotlin.concurrent.thread

class SamSonicApplication : Application(), SingletonImageLoader.Factory {
    // The language picked in Settings, before Android 13, for text made outside a screen.
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(AppLanguages.wrap(base))
    }

    val container: AppContainer by lazy { AppContainer(this) }

    override fun onCreate() {
        super.onCreate()
        // Opens the disk cache now, alongside Home loading its lists. Coil otherwise
        // opens it on the first image asked for, and every cover waits while it reads
        // its journal: about a second with a fully cached library (tens of thousands
        // of covers), several in a debug build, so Home first showed only placeholders.
        thread(name = "DiskCacheWarmUp", isDaemon = true) { SingletonImageLoader.get(this).diskCache?.size }
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader {
        return ImageLoader.Builder(context)
            .components {
                add(CoverArtCacheKeys.interceptor)
                add(CoverArtCacheKeys.keyer)
                add(OkHttpNetworkFetcherFactory(callFactory = { container.okHttpClient }))
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(container.imageCacheSettings.activeLocation.cacheDir.toOkioPath())
                    .maxSizeBytes(container.imageCacheSettings.activeMaxSizeBytes)
                    .build()
            }
            .crossfade(true)
            .build()
    }
}
