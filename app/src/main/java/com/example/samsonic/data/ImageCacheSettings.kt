package com.example.samsonic.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.roundToInt

/**
 * The on-device cover art cache: how much room it may take (one of [Steps], doubling
 * from 128 MB up to 1 TB, stored as its index) and where it lives ([CacheLocation]).
 *
 * Coil's disk cache is built once, with its folder and size, so a new limit or
 * location applies from the next app start; [activeStep] and [activeLocation] are
 * the ones in use until then.
 */
class ImageCacheSettings(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("samsonic_cache", Context.MODE_PRIVATE)

    private val _maxSizeStep = MutableStateFlow(
        prefs.getInt(KEY_MAX_SIZE_STEP, DefaultStep).coerceIn(Steps.indices),
    )
    val maxSizeStep: StateFlow<Int> = _maxSizeStep.asStateFlow()

    val activeStep: Int = _maxSizeStep.value

    /** The limit the disk cache is built with. */
    val activeMaxSizeBytes: Long get() = Steps[activeStep]

    private val _locationId = MutableStateFlow(prefs.getString(KEY_LOCATION, null) ?: CacheLocations.InternalId)
    /** The chosen location, which may be an SD card that isn't in right now. */
    val locationId: StateFlow<String> = _locationId.asStateFlow()

    private val startLocationId = _locationId.value

    /** Where the cache is this run: the chosen location, or the phone's storage if that SD card is missing. */
    val activeLocation: CacheLocation = locations().let { all ->
        all.firstOrNull { it.id == startLocationId } ?: all.first()
    }

    /** Whether the chosen SD card was missing at start, so the phone's storage stands in. */
    val usingFallback: Boolean get() = activeLocation.id != startLocationId

    /** Whether something set since the start only applies after a restart. */
    fun changedSinceStart(step: Int, locationId: String): Boolean =
        step != activeStep || locationId != startLocationId

    fun locations(): List<CacheLocation> = CacheLocations.available(appContext)

    fun setMaxSizeStep(step: Int) {
        val clamped = step.coerceIn(Steps.indices)
        if (clamped == _maxSizeStep.value) return
        prefs.edit().putInt(KEY_MAX_SIZE_STEP, clamped).apply()
        _maxSizeStep.value = clamped
    }

    fun setLocation(id: String) {
        prefs.edit().putString(KEY_LOCATION, id).apply()
        _locationId.value = id
    }

    /**
     * Deletes the caches left at locations no longer in use (after a move). Not while
     * standing in for a missing SD card: the phone's cache is then the one in use, and
     * the card's comes back with the card. Does disk work, so call it off the main thread.
     */
    fun deleteUnusedCaches() {
        if (usingFallback) return
        locations().filter { it.id != activeLocation.id }.forEach { it.cacheDir.deleteRecursively() }
    }

    companion object {
        private const val KEY_MAX_SIZE_STEP = "cover_cache_max_size_step"
        private const val KEY_LOCATION = "cover_cache_location"
        private const val MB = 1024L * 1024L

        /** 128 MB, 256 MB, ... 1 TB. */
        val Steps: List<Long> = List(14) { (128 * MB) shl it }

        /** 2 GB. */
        const val DefaultStep = 4

        fun label(bytes: Long): String {
            val mb = bytes / MB
            return when {
                mb < 1024 -> "$mb MB"
                mb < 1024 * 1024 -> "${mb / 1024} GB"
                else -> "${mb / (1024 * 1024)} TB"
            }
        }

        /** Like [label], for any amount: one decimal from 1 GB up (e.g. "1.4 GB"). */
        fun usageLabel(bytes: Long): String {
            val mb = bytes.toDouble() / MB
            return when {
                mb < 1024 -> "${mb.roundToInt()} MB"
                mb < 1024 * 1024 -> "%.1f GB".format(mb / 1024)
                else -> "%.1f TB".format(mb / (1024 * 1024))
            }
        }
    }
}
