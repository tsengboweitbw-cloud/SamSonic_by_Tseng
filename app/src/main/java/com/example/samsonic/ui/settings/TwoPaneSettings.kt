package com.example.samsonic.ui.settings

import androidx.compose.runtime.getValue
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import com.example.samsonic.ui.components.TabIndicatorSpring
import com.example.samsonic.ui.components.tabProximity
import com.example.samsonic.ui.theme.OneUiRadius
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.semantics.selected
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.samsonic.ui.theme.OneUiRow
import com.example.samsonic.ui.theme.oneUiRowClickable

// In one column, each group is two items: its label, then its card.
internal const val ItemsPerGroup = 2

/** One group of settings: its name and icon (for the list of groups beside it), and its card. */
internal class SettingsGroup(
    @param:StringRes val label: Int,
    val icon: ImageVector,
    val content: @Composable () -> Unit,
)

/**
 * Settings on a wide screen: the groups listed in a card at the side, and the one picked
 * shown beside them, from its top each time another is picked. Picking another, the
 * highlight glides over to it in the list, as the rail's indicator glides between tabs,
 * and its settings rise into place as the last group's fade: up from below for a group
 * further down the list, down from above for one higher up. The pick ([selected],
 * changed by [onSelect]) is kept by the caller, through any change of layout.
 */
@Composable
internal fun TwoPaneSettings(
    groups: List<SettingsGroup>,
    selected: Int,
    onSelect: (Int) -> Unit,
    topPadding: Dp,
    bottomPadding: Dp,
) {
    val picked = selected.coerceIn(groups.indices)
    // Where the highlight is, in rows; read only as the list is drawn.
    val highlight = remember { Animatable(picked.toFloat()) }
    LaunchedEffect(picked) { highlight.animateTo(picked.toFloat(), TabIndicatorSpring) }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val listWidth = (maxWidth * 0.38f).coerceIn(GroupListMinWidth, GroupListMaxWidth)
        val padding = PaddingValues(top = topPadding + 12.dp, bottom = 24.dp + bottomPadding)
        Row(Modifier.fillMaxSize()) {
            LazyColumn(modifier = Modifier.width(listWidth).fillMaxHeight(), contentPadding = padding) {
                item {
                    SettingsCard {
                        GroupList(
                            groups = groups,
                            picked = picked,
                            highlight = { highlight.value.coerceIn(0f, groups.lastIndex.toFloat()) },
                            onPick = onSelect,
                        )
                    }
                }
            }
            val shiftPx = with(LocalDensity.current) { GroupShift.roundToPx() }
            AnimatedContent(
                targetState = picked,
                modifier = Modifier.weight(1f).fillMaxHeight(),
                transitionSpec = {
                    // Further down the list: up from below; higher up: down from above.
                    val down = targetState > initialState
                    val enter = slideInVertically(tween(GroupInMillis, easing = GroupEasing)) { if (down) shiftPx else -shiftPx } +
                        fadeIn(tween(GroupInMillis - GroupOutMillis, delayMillis = GroupOutMillis / 2, easing = GroupEasing))
                    val exit = slideOutVertically(tween(GroupOutMillis, easing = GroupEasing)) { if (down) -shiftPx / 2 else shiftPx / 2 } +
                        fadeOut(tween(GroupOutMillis, easing = GroupEasing))
                    (enter togetherWith exit).using(SizeTransform(clip = false))
                },
                label = "settingsGroup",
            ) { group ->
                // Each group its own list, so the one coming in starts at its top.
                LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = padding) {
                    item { groups[group].content() }
                }
            }
        }
    }
}

// The list of groups takes about a third of the width, within these.
private val GroupListMinWidth = 240.dp
private val GroupListMaxWidth = 320.dp

// A group's settings coming in travel this far; the last group's, half as far out.
private val GroupShift = 32.dp
private const val GroupInMillis = 340
private const val GroupOutMillis = 140
private val GroupEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

/**
 * The groups, one row each, over one highlight at [highlight] (in rows, gliding between
 * them), tinting each row's label as it passes. As one row of the card, so its edges are
 * the card's first and last row's.
 */
@Composable
private fun GroupList(groups: List<SettingsGroup>, picked: Int, highlight: () -> Float, onPick: (Int) -> Unit) {
    val wash = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
    Column(
        Modifier
            .fillMaxWidth()
            .cardEdgeInset(top = 6.dp, bottom = 6.dp)
            .drawBehind {
                // The rows are all one height; the highlight is a row's press shape, inset as it is.
                val rowHeight = size.height / groups.size
                val inset = OneUiRow.Inset
                val left = inset.calculateLeftPadding(layoutDirection).toPx()
                val right = inset.calculateRightPadding(layoutDirection).toPx()
                val vertical = inset.calculateTopPadding().toPx()
                val radius = OneUiRadius.Art.toPx()
                drawRoundRect(
                    color = wash,
                    topLeft = Offset(left, highlight() * rowHeight + vertical),
                    size = Size(size.width - left - right, rowHeight - vertical * 2),
                    cornerRadius = CornerRadius(radius),
                )
            },
    ) {
        groups.forEachIndexed { index, group ->
            GroupRow(
                group = group,
                selected = index == picked,
                emphasis = { tabProximity(index, highlight()) },
                onClick = { onPick(index) },
            )
        }
    }
}

/**
 * A group in the list beside the settings: tapped, its settings show. Its label takes the
 * accent as the highlight comes over it ([emphasis], 1 under it, 0 a row away).
 */
@Composable
private fun GroupRow(group: SettingsGroup, selected: Boolean, emphasis: () -> Float, onClick: () -> Unit) {
    val onColor = MaterialTheme.colorScheme.primary
    val restColor = MaterialTheme.colorScheme.onSurface
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .oneUiRowClickable(onClick)
            .semantics { this.selected = selected }
            .padding(horizontal = 8.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(group.icon, contentDescription = null, tint = rowIconTint(), modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        // Coloured as it's drawn, following the highlight without recomposing.
        BasicText(
            text = stringResource(group.label),
            style = MaterialTheme.typography.bodyLarge,
            color = { lerp(restColor, onColor, emphasis()) },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
