package com.example.samsonic.ui.library

import android.widget.Toast
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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.samsonic.LocalAppContainer
import com.example.samsonic.data.MusicLibrary
import com.example.samsonic.model.Album
import com.example.samsonic.model.Playlist
import com.example.samsonic.model.Song
import com.example.samsonic.ui.common.UiState
import com.example.samsonic.ui.player.PanelState
import com.example.samsonic.ui.settings.MenuOption
import com.example.samsonic.ui.settings.SettingsMenu
import com.example.samsonic.ui.theme.OneUiRadius
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** What the Add to playlist menu adds: one song, or every song of an album. */
class PlaylistItems internal constructor(
    val title: String,
    internal val songIds: suspend (MusicLibrary) -> List<String>,
)

internal fun Song.toPlaylistItems() = PlaylistItems(title) { listOf(id) }

internal fun Album.toPlaylistItems() = PlaylistItems(title) { library -> library.getAlbum(id).second.map { it.id } }

/** What the Add to playlist menu is open for, and the menu's panel. */
class AddToPlaylistState internal constructor(scope: CoroutineScope) {
    internal val panel = PanelState(scope)

    internal var items by mutableStateOf<PlaylistItems?>(null)
        private set

    /** The corner radius of what the menu grows out of, for it to fold back into. */
    internal var originRadius by mutableStateOf(OneUiRadius.Art)
        private set

    /** Opens the menu for [items], growing out of [from] (bounds in the root, with [originRadius] corners). */
    fun open(items: PlaylistItems, from: Rect, originRadius: Dp) {
        this.items = items
        this.originRadius = originRadius
        panel.origin = from
        panel.open()
    }
}

/** Null where nothing can be added to playlists, such as the music on this phone. */
val LocalAddToPlaylist = staticCompositionLocalOf<AddToPlaylistState?> { null }

@Composable
fun rememberAddToPlaylistState(): AddToPlaylistState {
    val scope = rememberCoroutineScope()
    return remember { AddToPlaylistState(scope) }
}

/**
 * A row's or card's way into the Add to playlist menu: [origin] goes on what the
 * menu should grow out of, and [onLongClick] opens it. Both do nothing where
 * nothing can be added to playlists.
 */
class AddToPlaylistLongPress(val origin: Modifier, val onLongClick: (() -> Unit)?, val label: String?)

/** For a song row, which the menu grows out of. */
@Composable
fun rememberAddToPlaylistLongPress(song: Song): AddToPlaylistLongPress =
    rememberAddToPlaylistLongPress(OneUiRadius.Art) { song.toPlaylistItems() }

/** For an album, whose [origin][AddToPlaylistLongPress.origin] has [originRadius] corners (its cover, or its row). */
@Composable
fun rememberAddToPlaylistLongPress(album: Album, originRadius: Dp): AddToPlaylistLongPress =
    rememberAddToPlaylistLongPress(originRadius) { album.toPlaylistItems() }

@Composable
private fun rememberAddToPlaylistLongPress(originRadius: Dp, items: () -> PlaylistItems): AddToPlaylistLongPress {
    val state = LocalAddToPlaylist.current
    val haptics = LocalHapticFeedback.current
    val currentItems by rememberUpdatedState(items)
    val currentRadius by rememberUpdatedState(originRadius)
    // A plain holder, so scrolling doesn't recompose.
    val bounds = remember { arrayOf(Rect.Zero) }
    return remember(state, haptics) {
        if (state == null) return@remember AddToPlaylistLongPress(Modifier, null, null)
        AddToPlaylistLongPress(
            origin = Modifier.onGloballyPositioned { bounds[0] = it.boundsInRoot() },
            onLongClick = {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                state.open(currentItems(), bounds[0], currentRadius)
            },
            label = "Add to playlist",
        )
    }
}

private const val PlaylistListMaxHeight = 360
private const val StepFadeMillis = 220

/** Where the menu is: choosing a playlist, naming a new one, or asking about songs already there. */
private sealed interface Step {
    data object Pick : Step
    data object Naming : Step
    class Duplicates(val playlist: Playlist, val songIds: List<String>, val duplicates: Set<String>) : Step
}

/**
 * Adds [state]'s song or album to one of the user's playlists, or to a new one
 * named here. A card like the Settings menus, grown out of what was long-pressed
 * over the dimmed app; [haze] is the content it blurs. Songs already in the chosen
 * playlist are asked about first: skipped, or added again. It closes once the
 * server takes the songs, and keeps any error in the card so it can be tried again.
 */
@Composable
fun AddToPlaylistMenu(state: AddToPlaylistState, haze: HazeState) {
    SettingsMenu(state.panel, haze, title = "Add to playlist", originRadius = state.originRadius, resizable = true) {
        val items = state.items ?: return@SettingsMenu
        val repository = LocalAppContainer.current.repository
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        // All composed afresh at every open, so the list has any playlist made since.
        var playlists by remember { mutableStateOf<UiState<List<Playlist>>>(UiState.Loading) }
        var step by remember { mutableStateOf<Step>(Step.Pick) }
        // What's being worked on (a playlist's id, or "" for the rest) while the server answers.
        var saving by remember { mutableStateOf<String?>(null) }
        var error by remember { mutableStateOf<String?>(null) }
        // An album's songs, fetched at the first playlist picked.
        val songIds = remember { arrayOfNulls<List<String>>(1) }
        LaunchedEffect(Unit) {
            playlists = runCatching { repository.getOwnPlaylists() }
                .fold({ UiState.Success(it) }, { UiState.Error("Couldn't load playlists") })
        }

        suspend fun loadSongIds(): List<String> =
            songIds[0] ?: items.songIds(repository).also {
                if (it.isEmpty()) throw IllegalStateException("There are no songs to add")
                songIds[0] = it
            }

        fun work(key: String, block: suspend () -> Unit) {
            if (saving != null) return
            saving = key
            error = null
            scope.launch {
                runCatching { block() }.onFailure { error = it.message ?: "Couldn't add to the playlist" }
                saving = null
            }
        }

        fun done(message: String) {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            state.panel.close()
        }

        suspend fun add(playlist: Playlist, ids: List<String>, skipped: Int = 0) {
            if (ids.isEmpty()) return done("Already in ${playlist.name}")
            repository.addToPlaylist(playlist.id, ids)
            done(addedMessage(ids.size, skipped, playlist.name))
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
                        onCancel = { step = Step.Pick; error = null },
                        onCreate = { name ->
                            work("") {
                                val ids = loadSongIds()
                                repository.createPlaylist(name, ids)
                                done(addedMessage(ids.size, 0, name))
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
                        MenuOption(
                            icon = Icons.Filled.Add,
                            label = "New playlist",
                            selected = false,
                            onClick = { if (saving == null) step = Step.Naming },
                        )
                        when (val list = playlists) {
                            UiState.Loading -> Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                            }
                            is UiState.Error -> MenuMessage(list.message)
                            is UiState.Success -> Column(
                                Modifier.heightIn(max = PlaylistListMaxHeight.dp).verticalScroll(rememberScrollState()),
                            ) {
                                list.data.forEach { playlist ->
                                    MenuOption(
                                        icon = Icons.AutoMirrored.Filled.QueueMusic,
                                        label = playlist.name,
                                        supporting = if (saving == playlist.id) "Adding…" else "${playlist.songCount} songs",
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

private fun songs(count: Int) = if (count == 1) "1 song" else "$count songs"

private fun addedMessage(added: Int, skipped: Int, playlistName: String): String {
    val what = if (added == 1 && skipped == 0) "Added" else "Added ${songs(added)}"
    val rest = if (skipped > 0) ", skipped ${songs(skipped)} already there" else ""
    return "$what to $playlistName$rest"
}

/** Some of the songs are in the playlist already: add them again, or skip them. */
@Composable
private fun DuplicatesPrompt(
    step: Step.Duplicates,
    itemTitle: String,
    saving: Boolean,
    onAddAnyway: () -> Unit,
    onSkip: () -> Unit,
) {
    val total = step.songIds.size
    val count = step.duplicates.size
    val playlist = step.playlist.name
    val message = when {
        total == 1 -> "“$itemTitle” is already in “$playlist”."
        count == total -> "All $total songs are already in “$playlist”."
        else -> "${songs(count).replaceFirstChar { it.uppercase() }} of $total ${if (count == 1) "is" else "are"} already in “$playlist”."
    }
    Column(Modifier.padding(horizontal = 24.dp)) {
        Text(text = message, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onAddAnyway, enabled = !saving) { Text("Add anyway") }
            Spacer(Modifier.width(8.dp))
            Button(onClick = onSkip, enabled = !saving) {
                Text(if (total == 1 || count == total) "Skip" else "Skip duplicates")
            }
        }
    }
}

@Composable
private fun NewPlaylistForm(saving: Boolean, onCancel: () -> Unit, onCreate: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val canCreate = name.isNotBlank() && !saving
    Column(Modifier.padding(horizontal = 24.dp)) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Name") },
            singleLine = true,
            enabled = !saving,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (canCreate) onCreate(name.trim()) }),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            ),
            modifier = Modifier.fillMaxWidth().focusRequester(focus),
        )
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onCancel, enabled = !saving) { Text("Cancel") }
            Spacer(Modifier.width(8.dp))
            Button(onClick = { onCreate(name.trim()) }, enabled = canCreate) { Text(if (saving) "Creating…" else "Create") }
        }
    }
}

@Composable
private fun MenuMessage(text: String, isError: Boolean = false) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 12.dp),
    )
}
