package com.example.samsonic.data

import android.content.Context
import android.os.Environment
import android.os.storage.StorageManager
import com.example.samsonic.R
import java.io.File

/** Somewhere the caches (cover art and music) can live: the phone's own storage, or a removable SD card. */
data class CacheLocation(
    /** [CacheLocations.InternalId], or the SD card's volume UUID (stable across remounts). */
    val id: String,
    val label: String,
    /** The app's own folder on that storage; the caches sit in [coverArtDir] and [musicDir] inside it. */
    val root: File,
) {
    val isInternal: Boolean get() = id == CacheLocations.InternalId
    val coverArtDir: File get() = root.resolve(CacheLocations.CoverArtDirName)
    val musicDir: File get() = root.resolve(CacheLocations.MusicDirName)
    val freeBytes: Long get() = root.usableSpace
}

object CacheLocations {
    const val InternalId = "internal"
    const val CoverArtDirName = "cover_art_cache"
    const val MusicDirName = "music_cache"

    /**
     * The phone's storage, then each mounted SD card. On an SD card the caches go in the
     * app's own folder there, which needs no storage permission and is removed with the app.
     */
    fun available(context: Context): List<CacheLocation> {
        // Outside cacheDir, which the system clears when storage runs low: the user sets how big this gets.
        val internal = CacheLocation(InternalId, context.getString(R.string.data_phone_storage), context.noBackupFilesDir)
        val storage = context.getSystemService(StorageManager::class.java)
        val cards = context.getExternalFilesDirs(null).filterNotNull().mapNotNull { dir ->
            val removable = runCatching { Environment.isExternalStorageRemovable(dir) }.getOrDefault(false)
            val mounted = Environment.getExternalStorageState(dir) == Environment.MEDIA_MOUNTED
            if (!removable || !mounted) return@mapNotNull null
            val volume = storage.getStorageVolume(dir) ?: return@mapNotNull null
            CacheLocation(volume.uuid ?: dir.path, volume.getDescription(context), dir)
        }
        return listOf(internal) + cards
    }
}
