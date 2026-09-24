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
import com.example.samsonic.ui.common.UiState
import com.example.samsonic.ui.common.ArtKeys
import com.example.samsonic.ui.common.LocalArtTransitions
import com.example.samsonic.ui.common.sharedArt
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Alignment
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
// How many albums the Albums shelf needs before it stacks them in two rows.
private const val TWO_ROW_MIN_ALBUMS = 6

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

    // The artist as the card tapped to open it knew them: the header shows at once,
    // for the picture to grow into while the rest loads.
    val preview = LocalArtTransitions.current.preview<Artist>(ArtKeys.artist(artistId))

    val listState = rememberLazyListState()
    val backHaze = rememberHazeState()
    Box(modifier = modifier.fillMaxSize().statusBarsPadding()) {
        if (state is UiState.Loading && preview != null) {
            // Just where the loaded list puts its header, so the two swap unseen.
            Column(Modifier.fillMaxSize().padding(top = BackButtonClearance)) {
                DetailHeader(
                    title = preview.name,
                    subtitle = "${preview.albumCount} albums",
                    art = { ArtistPicture(preview) },
                    actions = {},
                )
                Box(Modifier.fillMaxWidth().padding(top = 24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }
        }
        StateContent(
            state = state,
            modifier = Modifier.fillMaxSize(),
            // The preview above stands in for the spinner.
            loading = if (preview != null) ({}) else null,
        ) { detail ->
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
                        art = { ArtistPicture(artist) },
                    ) {
                        // Plays everything, not just the songs listed here.
                        PlayShuffleButtons(key = artist.id, loadSongs = { repository.getArtistSongs(artist, albums) })
                    }
                }
                songSection("popular", "Popular", topSongs, player, onTitleClick = null)
                // Two rows only once there are enough albums to fill them; fewer sit in one.
                val albumRows = if (albums.size < TWO_ROW_MIN_ALBUMS) 1 else 2
                albumShelf("albums", "Albums", albums, onAllAlbumsClick, onAlbumClick, rows = albumRows)
                songSection("songs", "Songs", songs, player, onTitleClick = onAllSongsClick)
                albumShelf("appearsOn", "Appears on", appearsOn, onAppearsOnClick, onAlbumClick)
                item(key = "end") { Spacer(Modifier.height(24.dp)) }
            }
        }
        // Floats over the list: rows scroll up under it and fade out at the status bar.
        GlassBackButton(onClick = onBack, hazeState = backHaze, modifier = Modifier.padding(start = 16.dp, top = 8.dp))
    }
}

/** The artist's round picture, which the tapped card's picture grows into. */
@Composable
private fun ArtistPicture(artist: Artist) {
    MediaArt(
        coverArt = artist.coverArt,
        colorSeed = artist.id.artSeed(),
        size = 140.dp,
        cornerRadius = 70.dp,
        icon = false,
        fit = false,
        smallFirst = true,
        modifier = Modifier.sharedArt(ArtKeys.artist(artist.id)).clip(CircleShape),
    )
}
