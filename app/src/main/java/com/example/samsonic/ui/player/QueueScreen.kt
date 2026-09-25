package com.example.samsonic.ui.player

import com.example.samsonic.playback.LocalPlayerState

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.samsonic.LocalAppContainer
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.samsonic.model.Song
import com.example.samsonic.model.artSeed
import com.example.samsonic.ui.components.ListItemFade
import com.example.samsonic.ui.components.ListItemMove
import com.example.samsonic.ui.components.MediaArt
import com.example.samsonic.ui.components.SongRowEnd
import com.example.samsonic.ui.components.audioFormatDisplay
import com.example.samsonic.ui.components.songSubtitle
import com.example.samsonic.ui.components.SwipeAction
import com.example.samsonic.ui.components.SwipeActions
import com.example.samsonic.ui.theme.OneUiRadius
import com.example.samsonic.ui.theme.OneUiRow
import com.example.samsonic.ui.theme.accentWash
import com.example.samsonic.ui.theme.scrollTopFade

/** The queue's rows in play order, swipeable to play next or remove; the body of the Up Next card. */
@Composable
fun QueueScreen(modifier: Modifier = Modifier) {
    val player = LocalPlayerState.current
    val likesEnabled by LocalAppContainer.current.themeManager.likesEnabled.collectAsStateWithLifecycle()
    val display = audioFormatDisplay()

    Column(modifier = modifier.fillMaxSize()) {
        // Queue indices in play order (the shuffle order when shuffle is on). playOrder catches
        // up a moment after an insert, so until then fall back to the queue's own order.
        val order = player.playOrder.takeIf { it.size == player.queue.size } ?: player.queue.indices.toList()
        val listState = rememberLazyListState()
        val rowKeys = player.queue.occurrenceKeys()
        LazyColumn(
            modifier = Modifier.fillMaxSize().scrollTopFade(listState),
            state = listState,
            // Lines the rows' text up with the card's title.
            contentPadding = PaddingValues(horizontal = 4.dp),
        ) {
            // Keyed by song plus which copy of it this is, since the same song can be queued
            // more than once; stable across moves, so a moved row animates to its new place.
            items(order, key = { rowKeys.getOrElse(it) { "$it" } }) { index ->
                val song = player.queue.getOrNull(index) ?: return@items
                val isCurrent = index == player.currentIndex
                SwipeActions(
                    swipeRight = SwipeAction("Play next", Icons.Filled.SkipNext) { player.moveToNext(index) },
                    swipeLeft = SwipeAction("Remove", Icons.Filled.Delete, destructive = true) { player.removeFromQueue(index) },
                    modifier = Modifier.animateItem(ListItemFade, ListItemMove, ListItemFade),
                ) {
                    Row(
                        // Same rounded, inset highlight and press shape as SongRow (see OneUiRow).
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(OneUiRow.Shape)
                            .then(
                                if (isCurrent) {
                                    // No hazeState: nested inside the NavHost's own hazeSource
                                    // subtree (see MediaLists.SongRow comment) - flat fill only.
                                    Modifier.accentWash(OneUiRow.Shape)
                                } else {
                                    Modifier
                                }
                            )
                            .clickable {
                                player.playQueueIndex(index)
                            }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (isCurrent) {
                            Icon(
                                Icons.Filled.Equalizer,
                                contentDescription = "Now playing",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .padding(end = 10.dp)
                                    .width(20.dp),
                            )
                        } else {
                            MediaArt(
                                coverArt = song.coverArt,
                                colorSeed = song.id.artSeed(),
                                size = 40.dp,
                                cornerRadius = OneUiRadius.Chip,
                                shadowElevation = 0.dp,
                            )
                            Spacer(Modifier.width(10.dp))
                        }
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = song.title,
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = songSubtitle(song.artistName, song, display),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        SongRowEnd(song, display, liked = player.isLiked(song), likesEnabled = likesEnabled, heartButton = null)
                    }
                }
            }
        }
    }
}

/** One unique key per queue row: the song id, plus a copy number for repeats of the same song. */
private fun List<Song>.occurrenceKeys(): List<String> {
    val seen = HashMap<String, Int>()
    return map { song ->
        val copy = seen.merge(song.id, 1, Int::plus)!!
        "${song.id}#$copy"
    }
}
