package com.example.samsonic.data

import kotlin.math.abs

/**
 * Where song [songId] is in a playlist that now lists [currentIds], for taking it out by place:
 * [expected] if it's still there, else the nearest place holding it (the playlist was edited
 * elsewhere meanwhile); null if it's gone.
 */
fun placeToRemove(currentIds: List<String>, songId: String, expected: Int): Int? {
    if (currentIds.getOrNull(expected) == songId) return expected
    return currentIds.indices.filter { currentIds[it] == songId }.minByOrNull { abs(it - expected) }
}
