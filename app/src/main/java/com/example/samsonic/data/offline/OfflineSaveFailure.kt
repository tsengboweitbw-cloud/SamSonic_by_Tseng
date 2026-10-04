package com.example.samsonic.data.offline

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.HttpDataSource

/** What a song that couldn't be saved means for the ones after it. */
internal enum class SaveFailure {
    /** The server refuses this song (it's gone, or not allowed): carry on with the next, and leave this one be. */
    SkipSong,

    /** The connection or the server failed: the rest would fail the same way, so stop and try again later. */
    Stop,

    /** The phone has no room left: stop, and say so. */
    OutOfSpace,
}

/** Tells which of [SaveFailure] [error] is. */
@OptIn(UnstableApi::class)
internal fun saveFailureOf(error: Throwable): SaveFailure {
    val chain = generateSequence(error) { it.cause }.take(8).toList()
    val code = chain.filterIsInstance<HttpDataSource.InvalidResponseCodeException>().firstOrNull()?.responseCode
    return saveFailureFor(code, chain.mapNotNull { it.message })
}

/** [saveFailureOf] for the server's answer [responseCode] (null if it never answered) and the [messages] of the error. */
internal fun saveFailureFor(responseCode: Int?, messages: List<String>): SaveFailure {
    if (messages.any { it.contains("ENOSPC") || it.contains("No space left", ignoreCase = true) }) return SaveFailure.OutOfSpace
    // Another 4xx than "too many requests" or "timed out": the song itself is the trouble.
    if (responseCode != null && responseCode in 400..499 && responseCode != 408 && responseCode != 429) return SaveFailure.SkipSong
    return SaveFailure.Stop
}
