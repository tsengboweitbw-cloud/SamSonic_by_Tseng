package com.example.samsonic.ui.library

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.ui.draw.clip
import com.example.samsonic.data.PlaylistCovers
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import com.example.samsonic.ui.components.rememberPullOverscroll
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.offset
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.ui.unit.IntOffset
import com.example.samsonic.ui.common.LocalChromeGuard
import com.example.samsonic.ui.components.GlassIconButton
import kotlin.math.roundToInt
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.ui.graphics.graphicsLayer
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.draw.alpha
import com.example.samsonic.ui.player.PanelState
import com.example.samsonic.ui.settings.menuOrigin
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.example.samsonic.data.SortedList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.samsonic.R
import com.example.samsonic.ui.common.rememberScreenLoad
import com.example.samsonic.LocalAppContainer
import com.example.samsonic.model.FAVOURITES_PLAYLIST_ID
import com.example.samsonic.model.Playlist
import com.example.samsonic.model.favouritesPlaylist
import com.example.samsonic.data.placeToRemove
import com.example.samsonic.model.Song
import com.example.samsonic.ui.common.ScreenRefresh
import androidx.compose.runtime.CompositionLocalProvider
import com.example.samsonic.model.artSeed
import com.example.samsonic.playback.LocalPlayerState
import com.example.samsonic.ui.common.StateContent
import com.example.samsonic.ui.common.UiState
import com.example.samsonic.ui.common.ArtKeys
import com.example.samsonic.ui.common.LocalArtTransitions
import com.example.samsonic.ui.common.sharedArt
import androidx.compose.material3.CircularProgressIndicator
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.samsonic.ui.components.BackButtonClearance
import com.example.samsonic.ui.components.backButtonHazeSource
import com.example.samsonic.ui.components.GlassBackButton
import com.example.samsonic.ui.components.ChromeButtonSize
import com.example.samsonic.ui.components.FloatingListActions
import com.example.samsonic.ui.components.floatingActionsSlot
import com.example.samsonic.ui.components.floatingActionsEnd
import com.example.samsonic.ui.components.PlaylistArt
import com.example.samsonic.ui.components.PlayShuffleButtons
import com.example.samsonic.ui.components.SongRow
import com.example.samsonic.ui.theme.scrollTopFade
import dev.chrisbanes.haze.rememberHazeState

@Composable
fun PlaylistDetailScreen(
    playlistId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    contentPaddingBottom: Dp = 0.dp,
) {
    val player = LocalPlayerState.current
    val repository = LocalAppContainer.current.repository
    val cornerRadius by LocalAppContainer.current.themeManager.albumArtCornerRadius.collectAsStateWithLifecycle()

    val favouritesName = stringResource(R.string.data_favourites)
    // Loads the playlist again once a song has been taken out of it.
    val refresh = remember { ScreenRefresh() }
    val offlineOnly by LocalAppContainer.current.offlineOnly.enabled.collectAsStateWithLifecycle()
    val state = rememberScreenLoad(playlistId, errorMessage = stringResource(R.string.library_playlist_load_error), refresh = refresh) {
        if (playlistId == FAVOURITES_PLAYLIST_ID) {
            val liked = repository.getLikedSongs()
            favouritesPlaylist(liked, favouritesName) to liked
        } else {
            repository.getPlaylist(playlistId)
        }
    }

    // The playlist as the card tapped to open it knew it: its header shows at once, for
    // the cover to grow into while the songs load.
    val preview = LocalArtTransitions.current.preview<Playlist>(ArtKeys.playlist(playlistId))

    val backHaze = rememberHazeState()
    // What the back button does while a playlist is being edited; null when it is not.
    var editBack by remember { mutableStateOf<(() -> Unit)?>(null) }
    Box(modifier = modifier.fillMaxSize().statusBarsPadding()) {
        if (state is UiState.Loading && preview != null) {
            // Just where the loaded list puts its header, so the two swap unseen.
            Column(Modifier.fillMaxSize().padding(top = BackButtonClearance)) {
                PlaylistHeader(preview, preview.songCount, cornerRadius, actions = null)
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
        ) { (playlist, loaded) ->
            // A song's place in the playlist as the server lists it, which its removal goes by; not in
            // Favourites (its hearts do that) or with Offline only on (the list is cut down, so places differ).
            val gone = stringResource(R.string.library_song_not_in_playlist)
            val removal: ((Song) -> SongRemoval?)? = if (!repository.canEditPlaylists || offlineOnly || playlistId == FAVOURITES_PLAYLIST_ID) null else { song ->
                val place = loaded.indexOfFirst { it === song }
                if (place < 0) null else SongRemoval {
                    // The playlist as it is now, in case it was edited elsewhere since this page loaded.
                    val current = repository.getPlaylist(playlistId).second.map { it.id }
                    val at = placeToRemove(current, song.id, place)
                    if (at == null) {
                        refresh.refresh()
                        throw IllegalStateException(gone)
                    }
                    repository.removeFromPlaylist(playlistId, at)
                    refresh.refresh()
                }
            }
            // Editing renames the playlist and moves its songs about, in the order the server lists them
            // (not the sort); a reload, such as after saving, ends it.
            val editable = repository.canEditPlaylists && !offlineOnly && playlistId != FAVOURITES_PLAYLIST_ID
            var isEditing by remember(loaded) { mutableStateOf(false) }
            var draftName by remember(loaded) { mutableStateOf(playlist.name) }
            val draft = remember(loaded) { mutableStateListOf<DraftSong>() }
            var saving by remember(loaded) { mutableStateOf(false) }
            val scope = rememberCoroutineScope()
            val context = LocalContext.current
            val changedElsewhere = stringResource(R.string.library_playlist_changed_elsewhere)
            val saveFailure = stringResource(R.string.library_save_playlist_error)
            // The cover is picked on this phone: kept here until Save, then handed to [PlaylistCovers].
            val covers = LocalAppContainer.current.playlistCovers
            val coverKey = PlaylistCovers.key(LocalAppContainer.current.sources.serverKey, playlistId)
            val savedCover = covers.covers.collectAsStateWithLifecycle().value[coverKey]
            var pickedCover by remember(loaded) { mutableStateOf<Uri?>(null) }
            var removeCover by remember(loaded) { mutableStateOf(false) }
            // The photo just chosen, being cropped; the crop is what is kept as [pickedCover].
            var cropSource by remember(loaded) { mutableStateOf<Uri?>(null) }
            val coverPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
                if (uri != null) cropSource = uri
            }
            cropSource?.let { source ->
                CoverCropDialog(
                    uri = source,
                    onCropped = { cropped ->
                        pickedCover = cropped
                        removeCover = false
                        cropSource = null
                    },
                    onDismiss = { cropSource = null },
                )
            }
            val coverChanged = pickedCover != null || removeCover
            val coverFailure = stringResource(R.string.library_cover_error)
            val newName = draftName.trim()
            val order = draft.map { it.song.id }
            val canSave = newName.isNotEmpty() && (newName != playlist.name || order != loaded.map { it.id } || coverChanged)
            fun save() {
                val reordered = order != loaded.map { it.id }
                saving = true
                scope.launch {
                    runCatching {
                        // As it is now: if it was edited elsewhere since this page loaded, its order isn't ours to overwrite.
                        val current = repository.getPlaylist(playlistId).second.map { it.id }
                        if (current != loaded.map { it.id }) {
                            refresh.refresh()
                            throw IllegalStateException(changedElsewhere)
                        }
                        if (order != current) repository.reorderPlaylist(playlistId, order)
                        if (newName != playlist.name) repository.renamePlaylist(playlistId, newName)
                        // On the server, so every device shows it; kept on this phone only if the server won't take it.
                        pickedCover?.let { picked ->
                            val bytes = covers.readBytes(picked)
                            val onServer = repository.canSetPlaylistCover && bytes != null &&
                                runCatching { repository.setPlaylistCover(playlistId, bytes) }.isSuccess
                            if (onServer) covers.remove(coverKey)
                            else if (!covers.set(coverKey, picked)) throw IllegalStateException(coverFailure)
                        }
                        if (removeCover) {
                            if (repository.canSetPlaylistCover) runCatching { repository.removePlaylistCover(playlistId) }
                            covers.remove(coverKey)
                        }
                        refresh.refresh()
                    }.onSuccess {
                        // A reload only ends editing when the list changed; a cover alone leaves it as it was.
                        pickedCover = null
                        removeCover = false
                        // A new order is left through the reload that follows (the rows swap in place, unseen);
                        // leaving first would show the old order for a moment. If it never comes, leave anyway.
                        if (reordered) delay(5_000)
                        isEditing = false
                    }.onFailure { Toast.makeText(context, it.message ?: saveFailure, Toast.LENGTH_LONG).show() }
                    saving = false
                }
            }
            // Going back while editing leaves editing, not the page: at once if nothing was changed, else after
            // asking whether to save. The back button above the list does the same, so it is told how.
            val leavePanel = remember { PanelState(scope) }
            val hasChanges = newName != playlist.name || order != loaded.map { it.id } || coverChanged
            fun leaveEditing() {
                if (saving) return
                if (hasChanges) leavePanel.open() else isEditing = false
            }
            val currentLeave by rememberUpdatedState(::leaveEditing)
            BackHandler(enabled = isEditing) { currentLeave() }
            DisposableEffect(isEditing) {
                if (isEditing) editBack = { currentLeave() }
                onDispose { editBack = null }
            }
            val sort = rememberListSort(SortedList.PLAYLIST_SONGS)
            // Played in the order shown.
            val songs = remember(loaded, sort) { loaded.sortedFor(sort) }
            val listState = rememberLazyListState()
            val overscroll = rememberPullOverscroll()
            val density = LocalDensity.current
            val drag = remember(loaded, density) {
                // The finger scrolls the list from 64dp inside its visible edge, at up to 14dp a frame.
                PlaylistDragState(listState, draft, edgePx = with(density) { 120.dp.toPx() }, maxStepPx = with(density) { 30.dp.toPx() })
            }
            LaunchedEffect(drag, isEditing) { if (isEditing) drag.scrollNearEdges() }
            // Entering editing moves nothing above the songs: the header and the room for Play and Shuffle keep
            // their height (the buttons just fade), and the rows dip out and the other kind dips in, with no
            // placement animation until they have settled.
            val actionsFade by animateFloatAsState(if (isEditing) 0f else 1f, tween(200), label = "playlistActions")
            val rowsAlpha = remember { Animatable(1f) }
            var rowsEditing by remember { mutableStateOf(false) }
            var rowsSettled by remember { mutableStateOf(true) }
            val editSeen = remember { EditSeen() }
            // The effects below outlive the draft and songs they started with (saving reloads both), so they read these.
            val songsNow by rememberUpdatedState(songs)
            val draftNow by rememberUpdatedState(draft)
            LaunchedEffect(listState, draft) {
                snapshotFlow { Triple(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset, draft.size) }
                    .collect { (index, offset, size) ->
                        if (size > 0 && rowsEditing) {
                            editSeen.songs = draft.map { it.song }
                            editSeen.index = index
                            editSeen.offset = offset
                        }
                    }
            }
            // The saved playlist arriving while still editing (the draft starts empty again): its rows take the edit
            // rows' place in this very composition, and the list is asked to stay on the song it was on, so nothing
            // dips, jumps or flashes.
            val swapRows = rowsEditing && draft.isEmpty() && editSeen.songs.isNotEmpty()
            remember(loaded) {
                if (swapRows) {
                    val seen = editSeen.songs
                    val at = (editSeen.index - ROWS_START).coerceIn(0, seen.size - 1)
                    val song = seen[at]
                    val nth = seen.take(at).count { it.id == song.id }
                    val to = songs.withIndex().filter { it.value.id == song.id }.getOrNull(nth)?.index
                    if (editSeen.index >= ROWS_START && to != null) listState.requestScrollToItem(ROWS_START + to, editSeen.offset)
                    editSeen.swapped = true
                }
            }
            // Saving reloads the playlist, maybe after editing has ended: the list is put back on the song it
            // was on, found in the songs as saved.
            LaunchedEffect(loaded) {
                val id = editSeen.exitId
                // Only a reload soon after leaving editing is the save's; a later one leaves the list be.
                if (id != null && !rowsEditing && System.currentTimeMillis() - editSeen.exitAt < 10_000) {
                    val at = songs.withIndex().filter { it.value.id == id }.getOrNull(editSeen.exitNth)?.index
                    if (at != null) listState.scrollToItem(ROWS_START + at, editSeen.exitOffset)
                    editSeen.exitId = null
                }
            }
            LaunchedEffect(isEditing) {
                if (editSeen.swapped) {
                    // Already swapped and placed while composing; only the flag is left to catch up.
                    editSeen.swapped = false
                    editSeen.songs = emptyList()
                    rowsEditing = isEditing
                } else if (isEditing != rowsEditing) {
                    rowsSettled = false
                    rowsAlpha.animateTo(0.1f, tween(140, easing = FastOutLinearInEasing))
                    // The two kinds of row list the songs in different orders (the sort's, and the server's), so the
                    // song at the top of the screen is found in the other list and put back where it was, not by index.
                    // Saving reloads the playlist, which empties the draft and with it the rows, so the list
                    // has already fallen back to the top; the place it had is the one last seen while editing.
                    val reloaded = rowsEditing && editSeen.songs.isNotEmpty()
                    val first = if (reloaded) editSeen.index else listState.firstVisibleItemIndex
                    val fromSongs = if (reloaded) editSeen.songs else if (rowsEditing) draftNow.map { it.song } else songsNow
                    val rowCount = fromSongs.size
                    editSeen.exitId = null
                    val anchor = if (first >= ROWS_START && rowCount > 0) {
                        val at = (first - ROWS_START).coerceAtMost(rowCount - 1)
                        val to = if (rowsEditing) songsNow else draftNow.map { it.song }
                        val song = fromSongs[at]
                        val nth = fromSongs.take(at).count { it.id == song.id }
                        if (rowsEditing) {
                            editSeen.exitId = song.id
                            editSeen.exitNth = nth
                            editSeen.exitOffset = if (reloaded) editSeen.offset else listState.firstVisibleItemScrollOffset
                            editSeen.exitAt = System.currentTimeMillis()
                        }
                        to.withIndex().filter { it.value.id == song.id }.getOrNull(nth)?.index
                    } else null
                    val scrollOffset = if (reloaded) editSeen.offset else listState.firstVisibleItemScrollOffset
                    rowsEditing = isEditing
                    if (anchor != null) listState.scrollToItem(ROWS_START + anchor, scrollOffset)
                }
                rowsAlpha.animateTo(1f, tween(280, easing = LinearOutSlowInEasing))
                rowsSettled = true
            }
            // A new sort starts over from the first song, if the list was past it.
            OnSortChange(sort) { if (listState.firstVisibleItemIndex > 2) listState.scrollToItem(2) }
            CompositionLocalProvider(LocalSongRemoval provides removal) {
            Box(Modifier.fillMaxSize()) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().scrollTopFade(listState).backButtonHazeSource(backHaze)
                        .onGloballyPositioned { drag.listBounds = it.boundsInRoot() }
                        .scrollWithSecondFinger(drag),
                    state = listState,
                    overscrollEffect = overscroll,
                    contentPadding = PaddingValues(top = BackButtonClearance, bottom = contentPaddingBottom),
                ) {
                    item(key = "header") {
                        PlaylistHeader(
                            playlist, songs.size, cornerRadius, actions = null,
                            // The title follows the field below it as it is typed.
                            name = if (isEditing) draftName else playlist.name,
                            coverEdit = if (isEditing) {
                                CoverEdit(
                                    pending = pickedCover?.toString(),
                                    showPicked = !removeCover,
                                    removable = pickedCover != null || (!removeCover && savedCover != null),
                                    enabled = !saving,
                                    onPick = { coverPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                                    onRemove = {
                                        // A cover picked just now is dropped; one saved earlier is let go on Save.
                                        if (pickedCover != null) pickedCover = null else removeCover = true
                                    },
                                )
                            } else null,
                        )
                    }
                    // Play and shuffle float over the list ([FloatingListActions]); this keeps their place.
                    // Editing has no use for them, so the songs start right under the header.
                    // The name field takes their room while editing, fading in as they fade out.
                    floatingActionsSlot(bottomSpacing = 8.dp) {
                        if (isEditing || actionsFade < 1f) {
                            Box(Modifier.padding(horizontal = 24.dp).graphicsLayer { alpha = 1f - actionsFade }) {
                                PlaylistNameField(name = draftName, onNameChange = { draftName = it }, enabled = !saving && isEditing)
                            }
                        }
                    }
                    if (rowsEditing && !swapRows) {
                        items(draft, key = { drag.key(it) }) { item ->
                            // The one being dragged follows the finger; the others slide out of its way.
                            EditSongRow(
                                item = item,
                                cornerRadius = cornerRadius,
                                drag = drag,
                                enabled = !saving,
                                modifier = Modifier.graphicsLayer { alpha = rowsAlpha.value }.then(if (!rowsSettled || drag.draggedUid == item.uid) Modifier else Modifier.animateItem(fadeInSpec = null, fadeOutSpec = null, placementSpec = if (drag.scrolling) null else spring(stiffness = Spring.StiffnessMedium, visibilityThreshold = IntOffset.VisibilityThreshold))),
                            )
                        }
                    } else {
                        // By place as well as song: a playlist can hold the same song more than once.
                        itemsIndexed(songs, key = { i, s -> "${s.id}#$i" }) { _, song ->
                            SongRow(
                                song = song,
                                isCurrent = player.currentSong?.id == song.id,
                                liked = player.isLiked(song),
                                onToggleLike = { player.toggleLike(song) },
                                onClick = { player.play(song, songs) },
                                modifier = Modifier.graphicsLayer { alpha = rowsAlpha.value },
                            )
                        }
                        floatingActionsEnd()
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                }
                // Its corner button goes beside the sort button.
                if (actionsFade > 0f) {
                    Box(Modifier.fillMaxSize().graphicsLayer { alpha = actionsFade }) {
                    FloatingListActions(listState, overscroll = overscroll, cornerEndOffset = ChromeButtonSize + 8.dp, haze = backHaze) { PlayShuffleButtons(songs = songs, playlistTitle = playlist.name) }
                    }
                }
                // Edit, where the Library's new playlist button is: a glass button over the nav bar's corner.
                if (editable) {
                    val chromeTop = LocalChromeGuard.current?.top ?: { 0f }
                    AnimatedVisibility(
                        visible = !isEditing,
                        enter = fadeIn(tween(180, delayMillis = 90)) + scaleIn(tween(180, delayMillis = 90), initialScale = 0.8f),
                        exit = fadeOut(tween(90)) + scaleOut(tween(90), targetScale = 0.8f),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 16.dp)
                            .offset { IntOffset(0, -(chromeTop() + 12.dp.toPx()).roundToInt()) },
                    ) {
                        GlassIconButton(
                            icon = Icons.Filled.Edit,
                            tint = MaterialTheme.colorScheme.primary,
                            contentDescription = stringResource(R.string.library_edit_playlist),
                            onClick = {
                                draftName = playlist.name
                                pickedCover = null
                                removeCover = false
                                draft.clear()
                                draft.addAll(loaded.mapIndexed { i, s -> DraftSong(i.toLong(), s) })
                                isEditing = true
                            },
                            hazeState = backHaze,
                            iconSize = 24.dp,
                        )
                    }
                    // While editing, Cancel and Save take its place.
                    AnimatedVisibility(
                        visible = isEditing,
                        enter = fadeIn(tween(180, delayMillis = 90)) + scaleIn(tween(180, delayMillis = 90), initialScale = 0.8f),
                        exit = fadeOut(tween(90)) + scaleOut(tween(90), targetScale = 0.8f),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 16.dp)
                            .offset { IntOffset(0, -(chromeTop() + 12.dp.toPx()).roundToInt()) },
                    ) {
                        val canSaveNow = canSave && !saving
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            GlassIconButton(
                                icon = Icons.Filled.Close,
                                tint = MaterialTheme.colorScheme.error,
                                contentDescription = stringResource(R.string.library_cancel),
                                onClick = { if (!saving) isEditing = false },
                                hazeState = backHaze,
                                modifier = Modifier.alpha(if (saving) 0.4f else 1f),
                                iconSize = 24.dp,
                            )
                            GlassIconButton(
                                icon = Icons.Filled.Check,
                                tint = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) Color(0xFF5FD38D) else Color(0xFF1E8E4E),
                                contentDescription = stringResource(R.string.library_save),
                                onClick = { if (canSaveNow) save() },
                                hazeState = backHaze,
                                modifier = Modifier.menuOrigin(leavePanel).alpha(if (canSaveNow) 1f else 0.4f),
                                iconSize = 24.dp,
                            )
                        }
                    }
                }
                SaveChangesMenu(
                    panel = leavePanel,
                    haze = backHaze,
                    playlistName = playlist.name,
                    onSave = {
                        leavePanel.close()
                        // A blank name can not be saved: stay in editing, to give it one.
                        if (canSave) save()
                    },
                    onDiscard = {
                        leavePanel.close()
                        isEditing = false
                    },
                )
            }
            }
        }
        // Floats over the list: rows scroll up under it and fade out at the status bar.
        GlassBackButton(onClick = { editBack?.invoke() ?: onBack() }, hazeState = backHaze, modifier = Modifier.padding(start = 16.dp, top = 8.dp))
        ListSortMenu(SortedList.PLAYLIST_SONGS, backHaze)
    }
}

/**
 * The playlist's cover (which the tapped card's cover grows into, rounded like every
 * album cover), name, description and [songCount], over its [actions] (play and
 * shuffle), or none while it's loading.
 */
@Composable
private fun PlaylistHeader(
    playlist: Playlist,
    songCount: Int,
    cornerRadius: Dp,
    actions: (@Composable () -> Unit)?,
    name: String = playlist.name,
    coverEdit: CoverEdit? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(contentAlignment = Alignment.Center) {
            PlaylistArt(
                playlist = playlist,
                size = 180.dp,
                cornerRadius = cornerRadius,
                smallFirst = true,
                pendingCover = coverEdit?.pending,
                showPicked = coverEdit?.showPicked ?: true,
                modifier = Modifier.sharedArt(ArtKeys.playlist(playlist.id)),
            )
            // The overlay fades with the mode instead of popping, and keeps its last content while it fades out.
            val lastCoverEdit = remember { arrayOfNulls<CoverEdit>(1) }
            if (coverEdit != null) lastCoverEdit[0] = coverEdit
            val overlayAlpha by animateFloatAsState(if (coverEdit != null) 1f else 0f, tween(200), label = "coverEditOverlay")
            val shown = coverEdit ?: lastCoverEdit[0]
            if (shown != null && overlayAlpha > 0f) {
                Box(Modifier.graphicsLayer { alpha = overlayAlpha }) {
                    CoverEditOverlay(if (coverEdit != null) shown else shown.disabled(), cornerRadius)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Box(Modifier.fillMaxWidth().heightIn(min = NameFieldHeight), contentAlignment = Alignment.Center) {
            Text(text = name, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
        }
        if (playlist.description.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = playlist.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = pluralStringResource(R.plurals.library_song_count, songCount, songCount),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(20.dp))
        if (actions != null) {
            actions()
            Spacer(Modifier.height(8.dp))
        }
    }
}

/** The cover while editing: [pending] is an image picked but not saved, [showPicked] false when the saved pick is to go. */
private class CoverEdit(
    val pending: String?,
    val showPicked: Boolean,
    val removable: Boolean,
    val enabled: Boolean,
    val onPick: () -> Unit,
    val onRemove: () -> Unit,
) {
    /** The same overlay, not tappable: for while it fades out. */
    fun disabled() = CoverEdit(pending, showPicked, removable, enabled = false, onPick, onRemove)
}

/** Over the cover, 180dp square: tapping it picks another image, and a cross lets a picked one go. */
@Composable
private fun CoverEditOverlay(edit: CoverEdit, cornerRadius: Dp) {
    val shape = RoundedCornerShape(cornerRadius)
    Box(Modifier.size(180.dp)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(shape)
                .background(Color.Black.copy(alpha = 0.3f))
                .clickable(enabled = edit.enabled, onClickLabel = stringResource(R.string.library_change_cover), onClick = edit.onPick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Edit,
                contentDescription = stringResource(R.string.library_change_cover),
                tint = Color.White,
                modifier = Modifier.size(32.dp),
            )
        }
        if (edit.removable) {
            IconButton(
                onClick = edit.onRemove,
                enabled = edit.enabled,
                modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(36.dp).background(Color.Black.copy(alpha = 0.55f), CircleShape),
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = stringResource(R.string.library_remove_cover),
                    tint = Color.White,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

/** The height of the playlist's name field, which its name's place in the header keeps in both modes. */
private val NameFieldHeight = 56.dp

/** Where the list was, and the songs it showed, while editing; and the song it was on when editing ended. */
private class EditSeen {
    var songs: List<Song> = emptyList()
    var index = 0
    var offset = 0
    var exitId: String? = null
    var exitNth = 0
    var exitOffset = 0
    var exitAt = 0L
    /** The saved playlist arrived while editing, and its rows have already replaced the edit rows. */
    var swapped = false
}

/** Where the song rows start in the list: after the header and the room for Play and Shuffle. */
private const val ROWS_START = 2
