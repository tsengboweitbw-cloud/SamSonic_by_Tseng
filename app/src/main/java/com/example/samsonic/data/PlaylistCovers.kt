package com.example.samsonic.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.compose.ui.unit.IntRect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

/**
 * The covers picked for playlists, kept on the phone: a server has no place for one. Each is a
 * square JPEG in its own file, named for its playlist (by [key]) and the moment it was set, so
 * a new cover is a new image to the loader and the old one is simply deleted.
 */
class PlaylistCovers(private val context: Context, private val dir: File = File(context.filesDir, "playlist_covers")) {
    private val _covers = MutableStateFlow(scan())

    /** The file of each playlist's cover, by [key]; a playlist with none is absent. */
    val covers: StateFlow<Map<String, File>> = _covers

    /** Sets the cover of the playlist [key] to the image at [uri], cropped to a square; false if it can't be read. */
    suspend fun set(key: String, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val bitmap = decode(uri, MAX_SIDE)
            val side = minOf(bitmap.width, bitmap.height)
            val square = Bitmap.createBitmap(bitmap, (bitmap.width - side) / 2, (bitmap.height - side) / 2, side, side)
            dir.mkdirs()
            val file = File(dir, "$key-${System.currentTimeMillis()}.jpg")
            file.outputStream().use { square.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            delete(key, except = file)
            _covers.value = scan()
        }.isSuccess
    }

    /** The image at [uri] as the cropping screen shows it, its short side no more than [CROP_SIDE] px; null if it can't be read. */
    suspend fun loadForCrop(uri: Uri): Bitmap? = withContext(Dispatchers.IO) { runCatching { decode(uri, CROP_SIDE) }.getOrNull() }

    /**
     * The part of the image at [uri] inside [region] (in the pixels of [loadForCrop]'s bitmap) as a
     * file of its own, to be picked as the cover; null if it can't be made.
     */
    suspend fun cropToFile(uri: Uri, region: IntRect): Uri? = withContext(Dispatchers.IO) {
        runCatching {
            val bitmap = decode(uri, CROP_SIDE)
            val left = region.left.coerceIn(0, bitmap.width - 1)
            val top = region.top.coerceIn(0, bitmap.height - 1)
            val width = region.width.coerceIn(1, bitmap.width - left)
            val height = region.height.coerceIn(1, bitmap.height - top)
            val cropped = Bitmap.createBitmap(bitmap, left, top, width, height)
            context.cacheDir.listFiles()?.filter { it.name.startsWith(PENDING_PREFIX) }?.forEach { it.delete() }
            val file = File(context.cacheDir, "$PENDING_PREFIX${System.currentTimeMillis()}.jpg")
            file.outputStream().use { cropped.compress(Bitmap.CompressFormat.JPEG, 92, it) }
            Uri.fromFile(file)
        }.getOrNull()
    }

    // Decoded no larger than needed: the biggest cover is shown at a few hundred dp.
    private fun decode(uri: Uri, maxShortSide: Int): Bitmap =
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
            val shortSide = minOf(info.size.width, info.size.height)
            if (shortSide > maxShortSide) {
                val scale = maxShortSide.toFloat() / shortSide
                decoder.setTargetSize((info.size.width * scale).toInt().coerceAtLeast(1), (info.size.height * scale).toInt().coerceAtLeast(1))
            }
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }

    /** Lets the cover of the playlist [key] go, so it has its own again. */
    suspend fun remove(key: String) {
        withContext(Dispatchers.IO) {
            delete(key)
            _covers.value = scan()
        }
    }

    private fun delete(key: String, except: File? = null) {
        dir.listFiles()?.filter { it.keyOf() == key && it != except }?.forEach { it.delete() }
    }

    private fun scan(): Map<String, File> =
        dir.listFiles()?.filter { it.extension == "jpg" }?.groupBy { it.keyOf() }
            ?.mapValues { (_, files) -> files.maxBy { it.name } }
            ?: emptyMap()

    private fun File.keyOf() = nameWithoutExtension.substringBeforeLast('-')

    companion object {
        private const val MAX_SIDE = 1024
        private const val CROP_SIDE = 1600
        private const val PENDING_PREFIX = "cover-pending-"

        /** The key of playlist [playlistId] of the server [serverKey] (null for the music on this phone); a name safe for a file. */
        fun key(serverKey: String?, playlistId: String): String =
            MessageDigest.getInstance("SHA-1").digest("${serverKey.orEmpty()}|$playlistId".toByteArray())
                .joinToString("") { "%02x".format(it) }
    }
}
