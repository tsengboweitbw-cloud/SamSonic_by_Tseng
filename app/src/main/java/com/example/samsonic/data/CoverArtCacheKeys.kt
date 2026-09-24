package com.example.samsonic.data

import coil3.Uri
import coil3.intercept.Interceptor
import coil3.key.Keyer
import coil3.request.ImageResult
import coil3.request.Options
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * The few sizes cover art is fetched at. Each size is its own image in the cache, so
 * rounding every request up to one of these lets a list thumbnail and a grid tile
 * share one download, and lets [CoverArtPrefetcher] cache everything the app shows.
 */
object CoverArtSizes {
    /** List rows, the mini player and small tiles. */
    const val Small = 300

    /** Grids, detail headers and Now Playing. */
    const val Large = 1000

    val All = listOf(Small, Large)

    fun bucket(px: Int): Int = All.firstOrNull { px <= it } ?: Large
}

/**
 * Subsonic cover art URLs carry a fresh salt and token (`s`, `t`) every time one is
 * built, so as far as Coil can tell the same cover never has the same URL twice and
 * neither cache is ever hit. These key covers by the URL without its auth params:
 * the same cover at the same size is then found in memory and on disk, across app
 * starts too, and the request still goes out authenticated.
 */
object CoverArtCacheKeys {
    private val AuthParams = listOf("t", "s", "p")

    /** [url] without its auth params, or null if it isn't a Subsonic cover art URL. */
    fun stableKey(url: String): String? {
        val parsed = url.toHttpUrlOrNull() ?: return null
        if (!parsed.encodedPath.endsWith("getCoverArt.view")) return null
        return parsed.newBuilder()
            .apply { AuthParams.forEach { removeAllQueryParameters(it) } }
            .build()
            .toString()
    }

    /** The memory cache key (Coil adds the size and other extras on top). */
    val keyer = object : Keyer<Uri> {
        override fun key(data: Uri, options: Options): String? = stableKey(data.toString())
    }

    /** The disk cache key, which Coil otherwise takes from the full URL. */
    val interceptor = object : Interceptor {
        override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
            val request = chain.request
            val url = (request.data as? String) ?: (request.data as? Uri)?.toString()
            val key = url?.takeIf { request.diskCacheKey == null }?.let(::stableKey)
                ?: return chain.proceed()
            return chain.withRequest(request.newBuilder().diskCacheKey(key).build()).proceed()
        }
    }
}
