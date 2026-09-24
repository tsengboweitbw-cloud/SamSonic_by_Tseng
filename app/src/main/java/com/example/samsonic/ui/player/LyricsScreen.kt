package com.example.samsonic.ui.player

import com.example.samsonic.playback.LocalPlayerState

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.samsonic.LocalAppContainer
import com.example.samsonic.model.LyricLine
import com.example.samsonic.ui.theme.scrollTopFade

/** Synced lyrics, the current line highlighted from the live playback position; the body of the Lyrics card. */
@Composable
fun LyricsScreen(
    onCollapse: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val player = LocalPlayerState.current
    val repository = LocalAppContainer.current.repository
    val song = player.currentSong ?: run {
        onCollapse()
        return
    }
    val lines by produceState<List<LyricLine>?>(initialValue = null, key1 = song.id) {
        value = runCatching { repository.getLyrics(song.id) }.getOrDefault(emptyList())
    }
    val positionMs = (player.positionSeconds * 1000).toLong()
    val currentIndex = lines.orEmpty().indexOfLast { it.timeMs <= positionMs }.coerceAtLeast(0)

    Column(modifier = modifier.fillMaxSize()) {
        when {
            lines == null -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
            lines.isNullOrEmpty() -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "No lyrics found for this track",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            else -> {
                val listState = rememberLazyListState()
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp)
                        .scrollTopFade(listState),
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    items(lines!!) { line ->
                        val isCurrent = lines!!.indexOf(line) == currentIndex
                        Text(
                            text = line.text,
                            style = if (isCurrent) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.titleMedium,
                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                            color = if (isCurrent) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                            },
                        )
                    }
                }
            }
        }
    }
}
