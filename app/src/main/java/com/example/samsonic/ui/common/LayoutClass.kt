package com.example.samsonic.ui.common

import android.content.res.Configuration
import androidx.compose.material3.adaptive.Posture
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.samsonic.data.GridForm

/**
 * Which layout the app's window gets, from its size alone: never from the device, nor
 * from portrait or landscape (a foldable's inner screen is near square either way round).
 */
enum class LayoutClass {
    /** Phones, a foldable's cover screen, a narrow split screen, and any short window (a phone on its side). */
    Phone,

    /** Wide enough for a rail and wider content, but for one pane: small tablets, a narrow split screen on a tablet. */
    Medium,

    /** Wide and tall enough for two panes: tablets either way round, a foldable's inner screen, DeX. */
    Wide,
}

// Narrower than this, or shorter than PhoneMinHeight, it's laid out as a phone.
private val PhoneMaxWidth = 600.dp
private val PhoneMinHeight = 480.dp

// Two panes from here: a Z Fold's inner screen is about 704 wide the narrow way round, so
// it keeps two panes as it's turned rather than switching layouts. (Material's 840 would
// never give it two.)
private val WideMinWidth = 700.dp
private val WideMinHeight = 600.dp

// Text is only scaled up on a tablet whose shorter side is at least this: a Z Fold's inner
// screen is about 704 the short way round and stays at a phone's size; a Tab S9 is about 800.
// Check against real devices, the gap between the two is small.
private val TabletTextScaleMinShortSide = 750.dp

/**
 * The window's [layoutClass], its size, how a foldable is folded ([posture]: hinges,
 * tabletop).
 */
@Immutable
data class WindowLayout(
    val layoutClass: LayoutClass,
    val width: Dp,
    val height: Dp,
    val posture: Posture,
    // In Samsung DeX: the app in a window on a monitor, worked with a mouse and keyboard.
    val desktop: Boolean = false,
) {
    /** Anything larger than a phone: a rail in place of the bottom nav bar, wider content. */
    val isTablet: Boolean get() = layoutClass != LayoutClass.Phone

    /**
     * A phone on its side (a foldable's cover screen too): too short for the nav bar and
     * mini player along the foot, so a short rail at the side holds both, and Now Playing
     * sets its cover beside the controls; its pages keep a phone's one column.
     */
    val phoneLandscape: Boolean get() = layoutClass == LayoutClass.Phone && width > height && width >= PhoneMaxWidth

    /** The nav bar as a rail at the side, with the mini player under it: wider screens, and a phone on its side. */
    val usesRail: Boolean get() = isTablet || phoneLandscape

    /**
     * Whether pages go in two columns side by side (a list and what's opened from it, and
     * Settings' groups beside the one picked): wide enough, and held the wide way round.
     * Held tall, a tablet or an open foldable keeps one column, with the rail.
     */
    val twoPane: Boolean get() = layoutClass == LayoutClass.Wide //&& width > height

    /**
     * How much larger text is than on a phone: a size up in DeX (a monitor, further off
     * still) and on a real tablet, where it read small; none on a phone or a foldable's
     * inner screen (judged by its short side, not by the device).
     */
    val textScale: Float
        get() = when {
            desktop -> DesktopTextScale
            isTablet && minOf(width, height) >= TabletTextScaleMinShortSide -> TabletTextScale
            else -> 1f
        }

    /** The kind of screen for the grids' column counts. */
    val gridForm: GridForm get() = gridFormFor(width, height)
}

/**
 * The kind of screen a window [width] by [height] is, for the grids' column counts: a
 * phone's layout (a foldable's cover screen included), or a tablet the wide or the tall
 * way round (a foldable's inner screen included).
 */
fun gridFormFor(width: Dp, height: Dp): GridForm {
    val landscape = width > height
    return when {
        layoutClassFor(width, height) == LayoutClass.Phone ->
            if (landscape) GridForm.PHONE_LANDSCAPE else GridForm.PHONE
        else ->
            if (landscape) GridForm.TABLET_LANDSCAPE else GridForm.TABLET_PORTRAIT
    }
}

/**
 * Whether the app is on a desktop, as in Samsung DeX: worked with a mouse and keyboard
 * on a screen without touch (a monitor, a TV, a PC's DeX window). That's what DeX's
 * configuration says on One UI 8, which no longer carries the desktop mode field Samsung
 * once documented; older One UI's field counts too. A pop-up window on the phone's own
 * screen is still a touch screen, and doesn't count.
 */
fun Configuration.isSamsungDex(): Boolean {
    if (touchscreen == Configuration.TOUCHSCREEN_NOTOUCH) return true
    return try {
        val type = javaClass
        val enabled = type.getField("SEM_DESKTOP_MODE_ENABLED").getInt(type)
        type.getField("semDesktopModeEnabled").getInt(this) == enabled
    } catch (_: ReflectiveOperationException) {
        false
    }
}

internal fun layoutClassFor(width: Dp, height: Dp): LayoutClass = when {
    width < PhoneMaxWidth || height < PhoneMinHeight -> LayoutClass.Phone
    width >= WideMinWidth && height >= WideMinHeight -> LayoutClass.Wide
    else -> LayoutClass.Medium
}

/** The window's layout now, following it live as it's turned, split, folded or resized. */
@Composable
fun currentWindowLayout(): WindowLayout {
    val size = LocalWindowInfo.current.containerSize
    val (width, height) = with(LocalDensity.current) { size.width.toDp() to size.height.toDp() }
    val posture = currentWindowAdaptiveInfo().windowPosture
    val configuration = LocalConfiguration.current
    val desktop = remember(configuration) { configuration.isSamsungDex() }
    return WindowLayout(layoutClassFor(width, height), width, height, posture, desktop)
}

/** The app window's layout; provided in MainActivity. The phone's until then. */
val LocalWindowLayout = compositionLocalOf {
    WindowLayout(LayoutClass.Phone, 0.dp, 0.dp, Posture())
}

// Text on a tablet and in DeX, against a phone's.
private const val TabletTextScale = 1.15f
private const val DesktopTextScale = 1.2f