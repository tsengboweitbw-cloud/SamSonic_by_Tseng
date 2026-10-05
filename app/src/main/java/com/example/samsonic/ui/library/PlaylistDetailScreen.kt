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
import androidx.compose.animation.core.snap
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
                        pickedCover?.let { if (!covers.set(coverKey, it)) throw IllegalStateException(coverFailure) }
                        if (removeCover) covers.remove(coverKey)
                        refresh.refresh()
                    }.onSuccess {
                        // A reload only ends editing when the list changed; a cover alone leaves it as it was.
                        pickedCover = null
                        removeCover = false
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
                PlaylistDragState(listState, draft, edgePx = with(density) { 64.dp.toPx() }, maxStepPx = with(density) { 14.dp.toPx() })
            }
            LaunchedEffect(drag, isEditing) { if (isEditing) drag.scrollNearEdges() }
            // Entering editing moves nothing above the songs: the header and the room for Play and Shuffle keep
            // their height (the buttons just fade), and the rows dip out and the other kind dips in, with no
            // placement animation until they have settled.
            val actionsFade by animateFloatAsState(if (isEditing) 0f else 1f, tween(200), label = "playlistActions")
            val rowsAlpha = remember { Animatable(1f) }
            var rowsEditing by remember { mutableStateOf(false) }
            var rowsSettled by remember { mutableStateOf(true) }
            LaunchedEffect(isEditing) {
                if (isEditing != rowsEditing) {
                    rowsSettled = false
                    rowsAlpha.animateTo(0f, tween(110))
                    rowsEditing = isEditing
                }
                rowsAlpha.animateTo(1f, tween(180))
                rowsSettled = true
            }
            // A new sort starts over from the first song, if the list was past it.
            OnSortChange(sort) { if (listState.firstVisibleItemIndex > 2) listState.scrollToItem(2) }
            CompositionLocalProvider(LocalSongRemoval provides removal) {
            Box(Modifier.fillMaxSize()) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().scrollTopFade(listState).backButtonHazeSource(backHaze)
                        .onGloballyPositioned { drag.listBounds = it.boundsInRoot() },
                    state = listState,
                    overscrollEffect = overscroll,
                    contentPadding = PaddingValues(top = BackButtonClearance, bottom = contentPaddingBottom),
                ) {
                    item(key = "header") {
                        PlaylistHeader(
                            playlist, songs.size, cornerRadius, actions = null,
                            editing = if (isEditing) ({
                                PlaylistNameField(name = draftName, onNameChange = { draftName = it }, enabled = !saving)
                            }) else null,
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
                    floatingActionsSlot(bottomSpacing = 8.dp)
                    if (rowsEditing) {
                        items(draft, key = { drag.key(it) }) { item ->
                            // The one being dragged follows the finger; the others slide out of its way.
                            EditSongRow(
                                item = item,
                                cornerRadius = cornerRadius,
                                drag = drag,
                                enabled = !saving,
                                modifier = Modifier
                                    .graphicsLayer { alpha = rowsAlpha.value }
                                    .then(if (!rowsSettled || drag.draggedUid == item.uid) Modifier else Modifier.animateItem()),
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
                        enter = fadeIn() + scaleIn(initialScale = 0.8f),
                        exit = fadeOut() + scaleOut(targetScale = 0.8f),
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
                        enter = fadeIn() + scaleIn(initialScale = 0.8f),
                        exit = fadeOut() + scaleOut(targetScale = 0.8f),
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
    editing: (@Composable () -> Unit)? = null,
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
            if (coverEdit != null) CoverEditOverlay(coverEdit, cornerRadius)
        }
        Spacer(Modifier.height(16.dp))
        // The name becomes a field and back in a place as tall as the field, so the page below doesn't move.
        val lastEditing = remember { arrayOfNulls<@Composable () -> Unit>(1) }
        if (editing != null) lastEditing[0] = editing
        AnimatedContent(
            targetState = editing != null,
            modifier = Modifier.fillMaxWidth().heightIn(min = NameFieldHeight),
            transitionSpec = {
                (fadeIn(tween(200, delayMillis = 60)) togetherWith fadeOut(tween(120)))
                    .using(SizeTransform(clip = false) { _, _ -> snap() })
            },
            contentAlignment = Alignment.Center,
            label = "playlistName",
        ) { isEditing ->
            if (isEditing) {
                lastEditing[0]?.invoke()
            } else {
                Text(text = playlist.name, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
            }
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
)

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
