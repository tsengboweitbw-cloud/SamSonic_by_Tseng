package com.example.samsonic

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import com.example.samsonic.data.CoverArtCacheKeys
import okio.Path.Companion.toOkioPath

class SamSonicApplication : Application(), SingletonImageLoader.Factory {

    val container: AppContainer by lazy { AppContainer(this) }

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
