package com.example.samsonic.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.samsonic.LocalAppContainer
import com.example.samsonic.model.Album
import com.example.samsonic.ui.common.StateContent
import com.example.samsonic.ui.common.UiState
import com.example.samsonic.ui.components.AlbumCard
import com.example.samsonic.ui.components.HorizontalCarousel
import com.example.samsonic.ui.components.SectionHeader

private data class HomeSections(
    val recentlyAdded: List<Album>,
    val randomPicks: List<Album>,
    val recentlyPlayed: List<Album>,
    val mostPlayed: List<Album>,
)

@Composable
fun HomeScreen(
    onAlbumClick: (Album) -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentPaddingBottom: Dp = 0.dp,
) {
    val repository = LocalAppContainer.current.repository

    val state by produceState<UiState<HomeSections>>(initialValue = UiState.Loading, key1 = Unit) {
        value = UiState.Loading
        value = runCatching {
            HomeSections(
                recentlyAdded = repository.getAlbumList("newest", 20),
                randomPicks = repository.getAlbumList("random", 12),
                recentlyPlayed = repository.getAlbumList("recent", 12),
                mostPlayed = repository.getAlbumList("frequent", 12),
            )
        }.fold(
            onSuccess = { UiState.Success(it) },
            onFailure = { UiState.Error(it.message ?: "Couldn't load your library") },
        )
    }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(text = "Welcome back", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(text = "Your library", style = MaterialTheme.typography.displaySmall)
            }
            IconButton(onClick = onSettingsClick) {
                Icon(Icons.Filled.Settings, contentDescription = "Settings")
            }
        }

        StateContent(state = state, modifier = Modifier.fillMaxSize()) { sections ->
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 4.dp, bottom = contentPaddingBottom),
            ) {
                item {
                    SectionHeader(title = "Recently Added")
                    HorizontalCarousel(items = sections.recentlyAdded) { album ->
                        AlbumCard(album = album, onClick = { onAlbumClick(album) })
                    }
                    Spacer(Modifier.height(20.dp))
                }
                item {
                    SectionHeader(title = "Picked For You")
                    HorizontalCarousel(items = sections.randomPicks) { album ->
                        AlbumCard(album = album, onClick = { onAlbumClick(album) })
                    }
                    Spacer(Modifier.height(20.dp))
                }
                if (sections.recentlyPlayed.isNotEmpty()) {
                    item {
                        SectionHeader(title = "Recently Played")
                        HorizontalCarousel(items = sections.recentlyPlayed) { album ->
                            AlbumCard(album = album, onClick = { onAlbumClick(album) })
                        }
                        Spacer(Modifier.height(20.dp))
                    }
                }
                if (sections.mostPlayed.isNotEmpty()) {
                    item {
                        SectionHeader(title = "Most Played")
                        HorizontalCarousel(items = sections.mostPlayed) { album ->
                            AlbumCard(album = album, onClick = { onAlbumClick(album) })
                        }
                        Spacer(Modifier.height(24.dp))
                    }
                }
            }
        }
    }
}
