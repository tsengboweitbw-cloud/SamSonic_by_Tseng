package com.example.samsonic.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned

/** Pieces that morph between the mini player and Now Playing. */
enum class PlayerElement { Art, Progress }

/** Which player surface an anchor sits on. */
enum class PlayerSurface { Mini, Full }

/**
 * Moves the cover and progress line between the mini player and Now Playing
 * in step with the player sheet ([progress] 0 = mini, 1 = Now Playing).
 *
 * Each surface marks its root ([playerMorphRoot]) and its elements
 * ([playerMorphAnchor]). An element's spot is its position within its surface
 * plus where that surface currently sits, so it stays right however the sheet
 * is being moved. While the sheet is between the ends, [PlayerMorphOverlay]
 * draws each element on a line between its two spots and the real ones hide.
 */
@Stable
class PlayerMorphState(
    private val progress: () -> Float,
    private val active: () -> Boolean,
) {
    private class Anchor(val owner: Any, val coordinates: LayoutCoordinates)

    private val anchors = mutableStateMapOf<Pair<PlayerElement, PlayerSurface>, Anchor>()
    private val roots = mutableStateMapOf<PlayerSurface, LayoutCoordinates>()

    /**
     * Where each surface's root sits in the sheet host right now; set by the sheet.
     * Both ride the sheet, so the in-flight elements line up with the surfaces
     * around them instead of with where those surfaces will come to rest.
     */
    var surfaceOrigin: (PlayerSurface) -> Offset by mutableStateOf({ _: PlayerSurface -> Offset.Zero })

    /**
     * How the sheet is scaled right now, on top of [surfaceOrigin]: about which point (in
     * sheet host coordinates) and by how much. 1 but for a card lifted in the pile.
     */
    var surfaceScale: () -> Pair<Offset, Float> by mutableStateOf({ Offset.Zero to 1f })

    val fraction: Float get() = progress()

    /** Whether the overlay is currently drawing [element] (so the real ones should hide). */
    fun covers(element: PlayerElement): Boolean =
        active() && PlayerSurface.entries.all { surfaceBounds(element, it) != null }

    /** Where [element] is drawn right now, in sheet host coordinates; null when not morphing. */
    fun boundsOf(element: PlayerElement): Rect? {
        if (!active()) return null
        val mini = surfaceBounds(element, PlayerSurface.Mini) ?: return null
        val full = surfaceBounds(element, PlayerSurface.Full) ?: return null
        return lerp(mini, full, fraction)
    }

    private fun surfaceBounds(element: PlayerElement, surface: PlayerSurface): Rect? {
        val anchor = anchors[element to surface]?.coordinates ?: return null
        val root = roots[surface] ?: return null
        if (!anchor.isAttached || !root.isAttached) return null
        val bounds = root.localBoundingBoxOf(anchor, clipBounds = false).translate(surfaceOrigin(surface))
        val (pivot, scale) = surfaceScale()
        if (scale == 1f) return bounds
        return Rect(pivot + (bounds.topLeft - pivot) * scale, pivot + (bounds.bottomRight - pivot) * scale)
    }

    internal fun setRoot(surface: PlayerSurface, coordinates: LayoutCoordinates) {
        if (roots[surface] !== coordinates) roots[surface] = coordinates
    }

    internal fun report(element: PlayerElement, surface: PlayerSurface, owner: Any, coordinates: LayoutCoordinates) {
        val current = anchors[element to surface]
        if (current?.owner !== owner || current.coordinates !== coordinates) {
            anchors[element to surface] = Anchor(owner, coordinates)
        }
    }

    // Only the current owner may clear a slot: when the carousel moves the anchor to
    // another page, the new page can report before the old one is disposed.
    internal fun release(element: PlayerElement, surface: PlayerSurface, owner: Any) {
        if (anchors[element to surface]?.owner === owner) anchors.remove(element to surface)
    }
}

val LocalPlayerMorph = staticCompositionLocalOf<PlayerMorphState?> { null }

/**
 * Marks the root of a player surface: anchors inside it are measured relative to
 * this. Put it inside any offset that moves the surface with the sheet.
 */
@Composable
fun Modifier.playerMorphRoot(surface: PlayerSurface): Modifier {
    val morph = LocalPlayerMorph.current ?: return this
    return onGloballyPositioned { morph.setRoot(surface, it) }
}

/**
 * Marks this as [element] on [surface]: its position feeds the morph, and it
 * hides while the overlay draws it in flight. A no-op without a [LocalPlayerMorph].
 */
@Composable
fun Modifier.playerMorphAnchor(element: PlayerElement, surface: PlayerSurface): Modifier {
    val morph = LocalPlayerMorph.current ?: return this
    val owner = remember { Any() }
    DisposableEffect(morph, element, surface) {
        onDispose { morph.release(element, surface, owner) }
    }
    return this
        .onGloballyPositioned { morph.report(element, surface, owner, it) }
        .graphicsLayer { alpha = if (morph.covers(element)) 0f else 1f }
}
