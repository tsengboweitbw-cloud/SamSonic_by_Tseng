package com.example.samsonic.ui.library

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.samsonic.LocalAppContainer
import com.example.samsonic.model.Album
import com.example.samsonic.model.Artist
import com.example.samsonic.model.Song
import com.example.samsonic.model.artSeed
import com.example.samsonic.playback.LocalPlayerState
import com.example.samsonic.ui.common.StateContent
import com.example.samsonic.ui.common.rememberScreenLoad
import com.example.samsonic.ui.components.BackButtonClearance
import com.example.samsonic.ui.components.GlassBackButton
import com.example.samsonic.ui.components.MediaArt
import com.example.samsonic.ui.components.PlayShuffleButtons
import com.example.samsonic.ui.components.backButtonHazeSource
import com.example.samsonic.ui.theme.scrollTopFade
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/** How many of the artist's songs their page lists; the section's title opens them all. */
private const val SONGS_PREVIEW = 10

private data class ArtistDetail(
    val artist: Artist,
    val albums: List<Album>,
    val topSongs: List<Song>,
    val songs: List<Song>,
    val appearsOn: List<Album>,
)

@Composable
fun ArtistDetailScreen(
    artistId: String,
    onBack: () -> Unit,
    onAlbumClick: (Album) -> Unit,
    onAllAlbumsClick: () -> Unit,
    onAllSongsClick: () -> Unit,
    onAppearsOnClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentPaddingBottom: Dp = 0.dp,
) {
    val player = LocalPlayerState.current
    val repository = LocalAppContainer.current.repository

    val state = rememberScreenLoad(artistId, errorMessage = "Couldn't load artist") {
        val (artist, albums) = repository.getArtist(artistId)
        coroutineScope {
            val topSongs = async { runCatching { repository.getTopSongs(artist.name) }.getOrDefault(emptyList()) }
            // One search for their songs feeds both the Songs and the Appears on sections.
            val songsBy = runCatching { repository.getSongsBy(artist) }.getOrDefault(emptyList())
            val songs = async {
                runCatching { repository.getArtistSongs(artist, albums, SONGS_PREVIEW, songsBy) }.getOrDefault(emptyList())
            }
            val appearsOn = async { runCatching { repository.getAppearsOn(artist, albums, songsBy) }.getOrDefault(emptyList()) }
            ArtistDetail(artist, albums, topSongs.await(), songs.await(), appearsOn.await())
        }
    }

    val listState = rememberLazyListState()
    val backHaze = rememberHazeState()
    Box(modifier = modifier.fillMaxSize().statusBarsPadding()) {
        StateContent(state = state, modifier = Modifier.fillMaxSize()) { detail ->
            val (artist, albums, topSongs, songs, appearsOn) = detail
            LazyColumn(
                modifier = Modifier.fillMaxSize().scrollTopFade(listState).backButtonHazeSource(backHaze),
                state = listState,
                contentPadding = PaddingValues(top = BackButtonClearance, bottom = contentPaddingBottom),
            ) {
                item(key = "header") {
                    DetailHeader(
                        title = artist.name,
                        subtitle = "${artist.albumCount} albums",
                        art = {
                            MediaArt(
                                coverArt = artist.coverArt,
                                colorSeed = artist.id.artSeed(),
                                size = 140.dp,
                                cornerRadius = 70.dp,
                                icon = false,
                                fit = false,
                                modifier = Modifier.clip(CircleShape),
                            )
                        },
                    ) {
                        // Plays everything, not just the songs listed here.
                        PlayShuffleButtons(key = artist.id, loadSongs = { repository.getArtistSongs(artist, albums) })
                    }
                }
                songSection("popular", "Popular", topSongs, player, onTitleClick = null)
                albumShelf("albums", "Albums", albums, onAllAlbumsClick, onAlbumClick, rows = 2)
                songSection("songs", "Songs", songs, player, onTitleClick = onAllSongsClick)
                albumShelf("appearsOn", "Appears on", appearsOn, onAppearsOnClick, onAlbumClick)
                item(key = "end") { Spacer(Modifier.height(24.dp)) }
            }
        }
        // Floats over the list: rows scroll up under it and fade out at the status bar.
        GlassBackButton(onClick = onBack, hazeState = backHaze, modifier = Modifier.padding(start = 16.dp, top = 8.dp))
    }
}
