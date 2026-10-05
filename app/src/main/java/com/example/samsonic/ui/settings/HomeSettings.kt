package com.example.samsonic.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.zIndex
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.samsonic.LocalAppContainer
import com.example.samsonic.R
import com.example.samsonic.data.HomeSection
import com.example.samsonic.ui.home.HomeShelf
import com.example.samsonic.ui.home.ShelfKind
import com.example.samsonic.ui.player.PanelState
import com.example.samsonic.ui.theme.OneUiRow
import com.example.samsonic.ui.theme.OneUiSwitch
import com.example.samsonic.ui.theme.scrollEdgeFades
import dev.chrisbanes.haze.HazeState
import kotlin.math.roundToInt

/**
 * The Home group: a row opening the menu where its sections are shown, hidden, sized
 * and ordered, then what the like button, the mini player's swipes and the format
 * shown on songs do across the lists.
 */
@Composable
internal fun HomeSettings(panels: SettingsPanels) {
    val container = LocalAppContainer.current
    val sections by container.homeLayoutManager.sections.collectAsStateWithLifecycle()
    val likesEnabled by container.themeManager.likesEnabled.collectAsStateWithLifecycle()
    val audioFormatDisplay by container.themeManager.audioFormatDisplay.collectAsStateWithLifecycle()
    SettingsCard {
        NavRow(
            icon = Icons.Filled.Home,
            title = stringResource(R.string.settings_home_sections),
            value = stringResource(R.string.settings_home_sections_value, sections.count { it.visible }, sections.size),
            hint = stringResource(R.string.settings_home_sections_hint),
            onClick = { panels.homeLayoutMenu.open() },
            modifier = Modifier.menuOrigin(panels.homeLayoutMenu),
        )
        SwitchRow(
            icon = Icons.Filled.Favorite,
            title = stringResource(R.string.settings_like_button),
            checked = likesEnabled,
            onCheckedChange = container.themeManager::setLikesEnabled,
            hint = stringResource(R.string.settings_like_button_hint),
        )
        SwipeGestureRows(container.themeManager)
        // How every song list shows each song's format, or not at all.
        NavRow(
            icon = Icons.Filled.GraphicEq,
            title = stringResource(R.string.settings_audio_format),
            value = audioFormatDisplay.label,
            hint = stringResource(R.string.settings_audio_format_hint),
            onClick = { panels.audioFormatMenu.open() },
            modifier = Modifier.menuOrigin(panels.audioFormatMenu),
        )
    }
}

/**
 * The Home sections' menu ([SettingsMenu]): each section's switch, its length and
 * arrows to move it, in the order Home shows them. It takes all the height there
 * is and scrolls within, like Auto DJ's.
 */
@Composable
internal fun HomeLayoutMenu(panel: PanelState, haze: HazeState) {
    val manager = LocalAppContainer.current.homeLayoutManager
    val sections by manager.sections.collectAsStateWithLifecycle()
    val density = LocalDensity.current
    val listState = rememberLazyListState()
    val haptics = LocalHapticFeedback.current
    val currentSections by rememberUpdatedState(sections)
    // The section being dragged and how far it is from where the list lays it out.
    var dragging by remember { mutableStateOf<HomeShelf?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }

    // Moves the dragged section a place once its middle crosses into its neighbour, and
    // takes that place's length off the offset so it stays under the finger.
    fun drag(shelf: HomeShelf, delta: Float) {
        dragOffset += delta
        val items = listState.layoutInfo.visibleItemsInfo
        val current = items.firstOrNull { it.key == shelf.key } ?: return
        val middle = current.offset + current.size / 2f + dragOffset
        val over = items.firstOrNull { it.key != shelf.key && it.index < currentSections.size && middle >= it.offset && middle < it.offset + it.size }
            ?: return
        val step = if (over.index > current.index) 1 else -1
        manager.move(shelf, step)
        dragOffset -= step * (over.size + with(density) { SectionGap.toPx() })
        haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
    }

    SettingsMenu(panel, haze, title = stringResource(R.string.settings_home_sections)) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxWidth().fillMaxHeight().scrollEdgeFades(listState),
            verticalArrangement = Arrangement.spacedBy(SectionGap),
        ) {
            itemsIndexed(sections, key = { _, section -> section.shelf.key }) { index, section ->
                val lifted = dragging == section.shelf
                // The dragged one rides over the others, a touch larger; the rest slide aside.
                Box(
                    Modifier
                        // Held anywhere on the card, it lifts and follows the finger; the
                        // switch and slider still work as usual.
                        .pointerInput(section.shelf) {
                            val end = {
                                dragging = null
                                dragOffset = 0f
                            }
                            detectDragGesturesAfterLongPress(
                                onDragStart = {
                                    dragging = section.shelf
                                    dragOffset = 0f
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                },
                                onDragEnd = end,
                                onDragCancel = end,
                            ) { change, amount ->
                                change.consume()
                                drag(section.shelf, amount.y)
                            }
                        }
                        .zIndex(if (lifted) 1f else 0f)
                        .then(
                            if (lifted) {
                                Modifier.graphicsLayer {
                                    translationY = dragOffset
                                    scaleX = 1.02f
                                    scaleY = 1.02f
                                }
                            } else {
                                Modifier.animateItem()
                            },
                        ),
                ) {
                SettingsCard {
                    SectionRow(
                        section = section,
                        onMove = { manager.move(section.shelf, it) },
                        canMoveUp = index > 0,
                        canMoveDown = index < sections.lastIndex,
                        onVisibleChange = { manager.setVisible(section.shelf, it) },
                    )
                    if (section.visible) {
                        val songs = section.shelf.kind == ShelfKind.Songs
                        SliderRow(
                            icon = if (songs) Icons.Filled.MusicNote else Icons.Filled.Album,
                            title = stringResource(if (songs) R.string.settings_home_songs_shown else R.string.settings_home_albums_shown),
                            valueLabel = section.count.toString(),
                            value = section.count.toFloat(),
                            valueRange = HomeShelf.MIN_COUNT.toFloat()..section.shelf.previewSize.toFloat(),
                            steps = section.shelf.previewSize - HomeShelf.MIN_COUNT - 1,
                            onValueChange = { manager.setCount(section.shelf, it.roundToInt()) },
                        )
                    }
                }
                }
            }
            item(key = "reset") {
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.Center) {
                    TextButton(onClick = manager::reset) { Text(stringResource(R.string.settings_home_reset)) }
                }
            }
        }
    }
}

/**
 * A section's title and the switch that shows or
 * hides it. For TalkBack, which can't drag, the row also offers moving it a place up or down.
 */
@Composable
private fun SectionRow(
    section: HomeSection,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMove: (Int) -> Unit,
    onVisibleChange: (Boolean) -> Unit,
) {
    val moveUp = stringResource(R.string.settings_home_move_up)
    val moveDown = stringResource(R.string.settings_home_move_down)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                customActions = buildList {
                    if (canMoveUp) add(CustomAccessibilityAction(moveUp) { onMove(-1); true })
                    if (canMoveDown) add(CustomAccessibilityAction(moveDown) { onMove(1); true })
                }
            }
            .padding(OneUiRow.Inset)
            .padding(start = 8.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(section.shelf.title),
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 2,
            modifier = Modifier.weight(1f).alpha(if (section.visible) 1f else 0.6f),
        )
        OneUiSwitch(checked = section.visible, onCheckedChange = onVisibleChange)
        Spacer(Modifier.width(4.dp))
    }
}

private val SectionGap = 12.dp
