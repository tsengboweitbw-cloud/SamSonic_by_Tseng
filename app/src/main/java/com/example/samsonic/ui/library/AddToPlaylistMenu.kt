package com.example.samsonic.ui.library

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.LibraryAdd
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.OfflinePin
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.samsonic.LocalAppContainer
import com.example.samsonic.playback.LocalPlayerState
import com.example.samsonic.R
import com.example.samsonic.model.ArtistCredit
import com.example.samsonic.model.Playlist
import com.example.samsonic.model.Song
import com.example.samsonic.ui.common.UiState
import com.example.samsonic.ui.player.PlayerLinks
import com.example.samsonic.ui.settings.MenuOption
import com.example.samsonic.ui.settings.SettingsMenu
import com.example.samsonic.ui.theme.scrollEdgeFades
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.launch


private const val PlaylistListMaxHeight = 360
private const val StepFadeMillis = 220

/**
 * Where the menu is: the actions, choosing a playlist, naming a new one, asking about songs
 * already there, or choosing which of several artists to go to.
 */
internal sealed interface Step {
    data object Pick : Step
    data object Playlists : Step
    class Artists(val credits: List<ArtistCredit>) : Step
    data object Naming : Step
    class Duplicates(val playlist: Playlist, val songIds: List<String>, val duplicates: Set<String>) : Step
}

/**
 * What can be done with [state]'s song or album: add it to the queue or a playlist (one
 * of the user's, or a new one named here), go to its album, album artist or artist, and
 * keep it for offline. A card like the Settings menus, grown out of what was long-pressed
 * over the dimmed app; [haze] is the content it blurs. Where there is a choice to make (which
 * playlist, which of several artists) it's a second step in the same card. Songs already in
 * the chosen playlist are asked about first: skipped, or added again. It closes once the
 * server takes the songs, and keeps any error in the card so it can be tried again. [links]
 * open the pages of the Go to rows.
 */
@Composable
fun AddToPlaylistMenu(state: AddToPlaylistState, haze: HazeState, links: PlayerLinks) {
    SettingsMenu(state.panel, haze, title = stringResource(R.string.library_more_options), originRadius = state.originRadius, resizable = true) {
        val items = state.items ?: return@SettingsMenu
        val repository = LocalAppContainer.current.repository
        val player = LocalPlayerState.current
        val context = LocalContext.current
        val resources = LocalResources.current
        val scope = rememberCoroutineScope()
        // All composed afresh at every open, so the list has any playlist made since.
        var playlists by remember { mutableStateOf<UiState<List<Playlist>>>(UiState.Loading) }
        var step by remember { mutableStateOf<Step>(Step.Pick) }
        // What's being worked on (a playlist's id, or "" for the rest) while the server answers.
        var saving by remember { mutableStateOf<String?>(null) }
        var error by remember { mutableStateOf<String?>(null) }
        // An album's songs, fetched at the first playlist picked.
        val songIds = remember { arrayOfNulls<List<String>>(1) }
        val loadedSongs = remember { arrayOfNulls<List<Song>>(1) }
        // The songs once fetched, to tell whether Offline would keep them or let them go.
        var songs by remember { mutableStateOf<List<Song>?>(null) }
        val offline = LocalAppContainer.current.offlineMusic
        val kept by offline.keptIds.collectAsStateWithLifecycle()
        val downloader = LocalAppContainer.current.offlineDownloader
        val refused by downloader.refusedIds.collectAsStateWithLifecycle()

        suspend fun loadSongIds(): List<String> =
            songIds[0] ?: items.songs(repository).also { loadedSongs[0] = it }.map { it.id }.also {
                if (it.isEmpty()) throw IllegalStateException(resources.getString(R.string.library_no_songs_to_add))
                songIds[0] = it
            }

        LaunchedEffect(Unit) {
            playlists = runCatching { repository.getOwnPlaylists() }
                .fold({ UiState.Success(it) }, { UiState.Error(resources.getString(R.string.library_playlists_load_error)) })
        }
        LaunchedEffect(Unit) {
            if (offline.available) {
                runCatching { loadSongIds() }
                songs = loadedSongs[0]
            }
        }

        fun work(key: String, block: suspend () -> Unit) {
            if (saving != null) return
            saving = key
            error = null
            scope.launch {
                runCatching { block() }.onFailure { error = it.message ?: resources.getString(R.string.library_add_to_playlist_error) }
                saving = null
            }
        }

        fun done(message: String) {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            state.panel.close()
        }

        suspend fun add(playlist: Playlist, ids: List<String>, skipped: Int = 0) {
            if (ids.isEmpty()) return done(resources.getString(R.string.library_already_in_playlist, playlist.name))
            repository.addToPlaylist(playlist.id, ids)
            done(addedMessage(context, ids.size, skipped, playlist.name))
        }

        // The step before this one, for Back and the heading's tap.
        BackHandler(enabled = step != Step.Pick) {
            if (saving == null) {
                step = if (step is Step.Naming || step is Step.Duplicates) Step.Playlists else Step.Pick
                error = null
            }
        }

        fun goToArtist(artistId: String) {
            state.panel.close()
            links.openArtist(artistId)
        }

        fun goToAlbumArtist() = work("albumArtist") {
            val artist = items.albumArtist
                ?: items.albumId?.let { repository.getAlbum(it).first }?.let { ArtistCredit(it.artistId, it.artistName) }
            val id = artist?.id ?: throw IllegalStateException(resources.getString(R.string.library_no_album_artist))
            goToArtist(id)
        }

        fun pick(playlist: Playlist) = work(playlist.id) {
            val ids = loadSongIds()
            val existing = repository.getPlaylist(playlist.id).second.mapTo(HashSet()) { it.id }
            val duplicates = ids.filterTo(HashSet()) { it in existing }
            if (duplicates.isEmpty()) add(playlist, ids) else step = Step.Duplicates(playlist, ids, duplicates)
        }

        Text(
            text = items.title,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
        Spacer(Modifier.height(8.dp))

        // Steps cross-fade while the card eases to the new one's height.
        AnimatedContent(
            targetState = step,
            transitionSpec = {
                (fadeIn(tween(StepFadeMillis, delayMillis = StepFadeMillis / 2)) togetherWith fadeOut(tween(StepFadeMillis / 2)))
                    .using(SizeTransform(clip = false) { _, _ -> spring(dampingRatio = 0.85f, stiffness = 380f) })
            },
            contentAlignment = Alignment.TopCenter,
            label = "addToPlaylistStep",
        ) { current ->
            // Within a step too, as when the playlists come in over the spinner.
            Column(Modifier.animateContentSize(spring(dampingRatio = 0.85f, stiffness = 380f))) {
                when (current) {
                    Step.Naming -> NewPlaylistForm(
                        saving = saving != null,
                        onCancel = { step = Step.Playlists; error = null },
                        onCreate = { name ->
                            work("") {
                                val ids = loadSongIds()
                                repository.createPlaylist(name, ids)
                                done(addedMessage(context, ids.size, 0, name))
                            }
                        },
                    )
                    is Step.Duplicates -> DuplicatesPrompt(
                        step = current,
                        itemTitle = items.title,
                        saving = saving != null,
                        onAddAnyway = { work("") { add(current.playlist, current.songIds) } },
                        onSkip = {
                            work("") {
                                add(current.playlist, current.songIds.filterNot { it in current.duplicates }, current.duplicates.size)
                            }
                        },
                    )
                    Step.Pick -> {
                        state.removal?.let { removal ->
                            MenuOption(
                                icon = Icons.Filled.RemoveCircleOutline,
                                label = stringResource(R.string.library_remove_from_playlist),
                                selected = false,
                                onClick = {
                                    if (saving == null) work("remove") {
                                        removal.remove()
                                        done(resources.getString(R.string.library_removed_from_playlist, items.title))
                                    }
                                },
                            )
                        }
                        if (state.offersQueue) {
                            MenuOption(
                                icon = Icons.AutoMirrored.Filled.PlaylistAdd,
                                label = stringResource(R.string.components_add_to_queue),
                                selected = false,
                                onClick = {
                                    if (saving == null) {
                                        // Fetched and queued by the player, so it carries on after the menu closes.
                                        player.queueLater(next = false) { items.songs(repository) }
                                        done(resources.getString(R.string.library_added_to_queue, items.title))
                                    }
                                },
                            )
                        }
                        MenuOption(
                            icon = Icons.Filled.LibraryAdd,
                            label = stringResource(R.string.library_add_to_playlist),
                            selected = false,
                            onClick = { if (saving == null) step = Step.Playlists },
                        )
                        if (items.albumId != null) {
                            MenuOption(
                                icon = Icons.Filled.Album,
                                label = stringResource(R.string.library_go_to_album),
                                selected = false,
                                onClick = {
                                    state.panel.close()
                                    links.openAlbum(items.albumId)
                                },
                            )
                            MenuOption(
                                icon = Icons.Filled.AccountCircle,
                                label = stringResource(R.string.library_go_to_album_artist),
                                selected = saving == "albumArtist",
                                onClick = { goToAlbumArtist() },
                            )
                        }
                        if (items.artists.isNotEmpty()) {
                            MenuOption(
                                icon = Icons.Filled.Person,
                                label = stringResource(R.string.library_go_to_artist),
                                selected = false,
                                onClick = {
                                    // One artist goes straight there; several are chosen from.
                                    val only = items.artists.singleOrNull()
                                    if (only?.id != null) goToArtist(only.id) else step = Step.Artists(items.artists)
                                },
                            )
                        }
                        // Only with a server in use: the music on this phone is on it already.
                        if (offline.available) {
                            val loaded = songs
                            val keeping = loaded != null && loaded.isNotEmpty() && loaded.all { it.id in kept }
                            MenuOption(
                                icon = if (keeping) Icons.Filled.CloudOff else Icons.Filled.OfflinePin,
                                label = stringResource(if (keeping) R.string.offline_remove_option else R.string.offline_keep_option),
                                selected = keeping,
                                onClick = {
                                    if (saving == null) work("offline") {
                                        loadSongIds()
                                        val all = loadedSongs[0].orEmpty()
                                        if (keeping) offline.remove(all) else offline.keep(all)
                                        done(resources.getString(if (keeping) R.string.offline_removed else R.string.offline_kept, items.title))
                                    }
                                },
                            )
                            // Some of these songs refused by the server: ask it for them again.
                            val refusedHere = songs.orEmpty().filter { it.id in kept && it.id in refused }
                            if (refusedHere.isNotEmpty()) {
                                MenuOption(
                                    icon = Icons.Filled.Refresh,
                                    label = stringResource(R.string.offline_retry_option),
                                    selected = false,
                                    onClick = {
                                        if (saving == null) {
                                            downloader.retry(refusedHere.mapTo(HashSet()) { it.id })
                                            done(resources.getString(R.string.offline_retrying, items.title))
                                        }
                                    },
                                )
                            }
                        }
                    }
                    is Step.Artists -> {
                        StepHeading(stringResource(R.string.library_choose_artist))
                        current.credits.forEach { credit ->
                            MenuOption(
                                icon = Icons.Filled.Person,
                                label = credit.name,
                                selected = false,
                                onClick = { credit.id?.let(::goToArtist) },
                            )
                        }
                    }
                    Step.Playlists -> {
                        StepHeading(stringResource(R.string.library_add_to_playlist))
                        MenuOption(
                            icon = Icons.Filled.Add,
                            label = stringResource(R.string.library_new_playlist),
                            selected = false,
                            onClick = { if (saving == null) step = Step.Naming },
                        )
                        when (val list = playlists) {
                            UiState.Loading -> Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                            }
                            is UiState.Error -> MenuMessage(list.message)
                            is UiState.Success -> {
                                val scroll = rememberScrollState()
                                Column(
                                    Modifier
                                        .heightIn(max = PlaylistListMaxHeight.dp)
                                        .scrollEdgeFades(scroll)
                                        .verticalScroll(scroll),
                                ) {
                                    list.data.forEach { playlist ->
                                        MenuOption(
                                            icon = Icons.AutoMirrored.Filled.QueueMusic,
                                            label = playlist.name,
                                            supporting = if (saving == playlist.id) stringResource(R.string.library_adding) else songCount(playlist.songCount),
                                            selected = saving == playlist.id,
                                            onClick = { pick(playlist) },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        AnimatedVisibility(
            visible = error != null,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            // Kept through the exit, so the text doesn't vanish before the space closes.
            val shown = remember { arrayOf("") }
            error?.let { shown[0] = it }
            MenuMessage(shown[0], isError = true)
        }
    }
}

private fun addedMessage(context: Context, added: Int, skipped: Int, playlistName: String): String {
    fun songs(count: Int) = context.resources.getQuantityString(R.plurals.library_song_count, count, count)
    return when {
        skipped > 0 -> context.getString(R.string.library_added_songs_skipped, songs(added), playlistName, songs(skipped))
        added == 1 -> context.getString(R.string.library_added_to_playlist, playlistName)
        else -> context.getString(R.string.library_added_songs, songs(added), playlistName)
    }
}
