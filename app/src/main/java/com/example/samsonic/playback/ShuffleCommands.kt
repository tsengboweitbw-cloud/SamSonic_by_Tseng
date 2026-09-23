package com.example.samsonic.playback

import android.os.Bundle
import androidx.annotation.OptIn
import androidx.core.os.BundleCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ShuffleOrder.DefaultShuffleOrder
import androidx.media3.session.SessionCommand
import kotlin.random.Random

/*
 * Playlist edits that also have to control where items land in the shuffle order. ExoPlayer's
 * default shuffle order drops inserted or moved items at random positions, so "play next" and
 * "add to queue" would land anywhere; these commands make the edit and place the items in the
 * shuffle order in one step. (Doing the edit as a regular controller call wouldn't work: the
 * session defers player commands until the controller flushes them, while custom commands run
 * immediately, so the reorder could run before the edit.)
 */

/** Inserts items at a queue index and places them next or last in the shuffle order. */
val ShuffleInsertCommand = SessionCommand("com.example.samsonic.SHUFFLE_INSERT", Bundle.EMPTY)

/** Moves a queue item so it plays right after the current one, in queue and shuffle order. */
val ShuffleMoveNextCommand = SessionCommand("com.example.samsonic.SHUFFLE_MOVE_NEXT", Bundle.EMPTY)

private const val ArgIndex = "index"
private const val ArgItems = "items"
private const val ArgPlayNext = "playNext"

/** Where the inserted items go in the shuffle order. */
enum class ShufflePlacement { Next, End }

@OptIn(UnstableApi::class)
fun shuffleInsertArgs(index: Int, items: List<MediaItem>, placement: ShufflePlacement): Bundle = Bundle().apply {
    putInt(ArgIndex, index)
    putParcelableArrayList(ArgItems, ArrayList(items.map { it.toBundleIncludeLocalConfiguration() }))
    putBoolean(ArgPlayNext, placement == ShufflePlacement.Next)
}

fun shuffleMoveNextArgs(from: Int): Bundle = Bundle().apply { putInt(ArgIndex, from) }

/** The queue index [PlayerState.moveToNext] moves [from] to: right after [current], counted after the removal. */
fun moveNextTarget(from: Int, current: Int): Int = if (from < current) current else current + 1

/** Handles [ShuffleInsertCommand]: inserts the items at their queue index, then places them in the shuffle order. */
@OptIn(UnstableApi::class)
fun ExoPlayer.shuffleInsert(args: Bundle) {
    val items = BundleCompat.getParcelableArrayList(args, ArgItems, Bundle::class.java)
        ?.map(MediaItem::fromBundle)
    if (items.isNullOrEmpty()) return
    val index = args.getInt(ArgIndex, mediaItemCount).coerceIn(0, mediaItemCount)
    addMediaItems(index, items)

    val inserted = index until index + items.size
    val order = shuffledIndices().filterNot { it in inserted }.toMutableList()
    val at = if (args.getBoolean(ArgPlayNext)) order.indexOf(currentMediaItemIndex) + 1 else order.size
    order.addAll(at, inserted.toList())
    setShuffleOrder(order)
}

/** Handles [ShuffleMoveNextCommand], keeping every other item's place in the shuffle order. */
fun ExoPlayer.shuffleMoveNext(args: Bundle) {
    val from = args.getInt(ArgIndex, C.INDEX_UNSET)
    val current = currentMediaItemIndex
    if (from !in 0 until mediaItemCount || from == current) return
    val to = moveNextTarget(from, current)
    val before = shuffledIndices()
    moveMediaItem(from, to)

    // Where each old queue index ends up once [from] has moved to [to].
    fun moved(i: Int) = when {
        i == from -> to
        from < to && i in from + 1..to -> i - 1
        from > to && i in to until from -> i + 1
        else -> i
    }
    val order = before.map(::moved).filter { it != to }.toMutableList()
    order.add(order.indexOf(currentMediaItemIndex) + 1, to)
    setShuffleOrder(order)
}

/**
 * A fresh shuffle order with the current item first and everything else in random order after
 * it. ExoPlayer only shuffles when the playlist is set, so turning shuffle off and on again
 * would replay the same order (including items placed by the commands above); the service
 * calls this every time shuffle is turned on instead.
 */
fun ExoPlayer.reshuffleFromCurrent() {
    val count = mediaItemCount
    if (count == 0) return
    val current = currentMediaItemIndex.coerceIn(0, count - 1)
    setShuffleOrder(listOf(current) + (0 until count).filter { it != current }.shuffled())
}

/** Queue indices in the current shuffle order. */
private fun ExoPlayer.shuffledIndices(): List<Int> {
    val timeline = currentTimeline
    val order = ArrayList<Int>(timeline.windowCount)
    var i = timeline.getFirstWindowIndex(/* shuffleModeEnabled = */ true)
    while (i != C.INDEX_UNSET && order.size < timeline.windowCount) {
        order += i
        i = timeline.getNextWindowIndex(i, Player.REPEAT_MODE_OFF, /* shuffleModeEnabled = */ true)
    }
    return order
}

@OptIn(UnstableApi::class)
private fun ExoPlayer.setShuffleOrder(order: List<Int>) {
    setShuffleOrder(DefaultShuffleOrder(order.toIntArray(), Random.nextLong()))
}
