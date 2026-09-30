package com.example.samsonic.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.samsonic.ui.common.LocalWindowLayout

/** How Now Playing is laid out: the cover above the controls, or beside them. */
enum class NowPlayingColumns { One, Two }

/** The widest the one column gets on a tablet: a phone's, centred. */
internal val OneColumnMaxWidth = 560.dp

// The column's side padding.
private val ColumnPadding = 24.dp

// The height one column gives everything but the cover: the collapse button, the song's
// title and format, the seek bar, the transport, the actions, and the system bars.
private val OneColumnControlsHeight = 600.dp

// Two columns, the height the cover's side gives up: the collapse button, the room above
// and below the cover, and the system bars.
private val TwoColumnMargins = 150.dp

// Switching only once the other layout's cover is this much larger, so a window being
// resized across the point doesn't flicker between the two.
private const val SwitchMargin = 1.08f

/** How large the cover can be in one column of a window [width] by [height]. */
internal fun oneColumnCover(width: Dp, height: Dp): Dp =
    minOf(minOf(width, OneColumnMaxWidth + ColumnPadding * 2) - ColumnPadding * 2, height - OneColumnControlsHeight)

/** How large the cover can be in the left of two columns of a window [width] by [height]. */
internal fun twoColumnCover(width: Dp, height: Dp): Dp =
    minOf(width / 2 - ColumnPadding * 2, height - TwoColumnMargins)

/**
 * Whichever layout gives the larger cover in a window [width] by [height]: a tall window
 * gets one column, a wide one two, a near square one whichever looks better. From the
 * layout [current]ly shown, the other only wins by [SwitchMargin]; with none yet, the
 * larger cover wins outright.
 */
internal fun nowPlayingColumnsFor(width: Dp, height: Dp, current: NowPlayingColumns?): NowPlayingColumns {
    val one = oneColumnCover(width, height)
    val two = twoColumnCover(width, height)
    return when (current) {
        NowPlayingColumns.One -> if (two > one * SwitchMargin) NowPlayingColumns.Two else NowPlayingColumns.One
        NowPlayingColumns.Two -> if (one > two * SwitchMargin) NowPlayingColumns.One else NowPlayingColumns.Two
        null -> if (two > one) NowPlayingColumns.Two else NowPlayingColumns.One
    }
}

/**
 * Now Playing's layout for the window as it is now, following it as it's turned, split,
 * folded or resized: always one column on a phone (its layout, as ever), and on a larger
 * screen the one giving the larger cover.
 */
@Composable
internal fun rememberNowPlayingColumns(): NowPlayingColumns {
    val window = LocalWindowLayout.current
    // The last layout chosen, for the switching margin; not state, as it only ever
    // follows from the window's size, which recomposes this as it changes.
    val last = remember { arrayOfNulls<NowPlayingColumns>(1) }
    val columns = when {
        // On its side, a phone's too short for the cover above the controls.
        window.phoneLandscape -> NowPlayingColumns.Two
        window.isTablet -> nowPlayingColumnsFor(window.width, window.height, last[0])
        else -> NowPlayingColumns.One
    }
    last[0] = columns
    return columns
}
