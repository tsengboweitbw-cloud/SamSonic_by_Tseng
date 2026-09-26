package com.example.samsonic.ui.common

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import java.util.concurrent.atomic.AtomicLong

/** Shared-element keys for the art a page grows out of: an album's, artist's or playlist's. */
object ArtKeys {
    fun album(id: String) = "album:$id"
    fun artist(id: String) = "artist:$id"
    fun playlist(id: String) = "playlist:$id"
}

/**
 * Which cover or picture each album and artist page grows out of: the one the user
 * tapped, by its [ArtKeys] key. Only that one takes part, so an album shown twice
 * on a page (two Home shelves) grows from the copy tapped, and shrinks back into it.
 * Also keeps the tapped item, for the page to show its header before its data loads.
 */
@Stable
class ArtTransitions {
    // Snapshot state: the tapped art and the page's art both switch on in the same frame.
    private val sources = mutableStateMapOf<String, Any>()
    private val previews = HashMap<String, Any>()

    /** Called on tap, before navigating: [token] names the tapped copy, [item] its album or artist. */
    fun open(key: String, token: Any, item: Any) {
        previews[key] = item
        sources[key] = token
    }

    internal fun isSource(key: String, token: Any) = sources[key] == token

    internal fun hasSource(key: String) = key in sources

    /** The item tapped to open [key]'s page, if it was opened from its art. */
    @Suppress("UNCHECKED_CAST")
    fun <T> preview(key: String): T? = previews[key] as? T
}

val LocalArtTransitions = staticCompositionLocalOf { ArtTransitions() }

/** The nav host's shared-transition scope; null outside it (previews), where art doesn't travel. */
@OptIn(ExperimentalSharedTransitionApi::class)
val LocalSharedTransitionScope = staticCompositionLocalOf<SharedTransitionScope?> { null }

/** The current nav destination's enter/exit scope, which drives its shared elements. */
val LocalNavAnimatedScope = staticCompositionLocalOf<AnimatedVisibilityScope?> { null }

// The page transitions' curve and a little longer than their slide, so the art lands last.
private val ArtBounds = BoundsTransform { _, _ -> tween(420, easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)) }

/**
 * Makes this art travel between a card and the page it opens, under [key]. On a card,
 * pass the card's [token] (see [rememberArtToken]): it takes part only if it's the
 * copy tapped. On the page itself, leave it null: it takes part whenever its page
 * was opened from art.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.sharedArt(key: String, token: Any? = null): Modifier {
    val shared = LocalSharedTransitionScope.current ?: return this
    val scope = LocalNavAnimatedScope.current ?: return this
    val transitions = LocalArtTransitions.current
    // Derived, so a tap (which writes the map every card reads) recomposes only the cards
    // it switches on or off, not every card on the page in the frame the page opens.
    val active by remember(transitions, key, token) {
        derivedStateOf { if (token != null) transitions.isSource(key, token) else transitions.hasSource(key) }
    }
    if (!active) return this
    return with(shared) {
        this@sharedArt.sharedElement(
            sharedContentState = rememberSharedContentState(key),
            animatedVisibilityScope = scope,
            boundsTransform = ArtBounds,
        )
    }
}

/**
 * A card's own identity for [sharedArt] and [ArtTransitions.open]. Saved with the
 * card's state: the page it sits on leaves composition while the opened page shows,
 * and comes back rebuilt on back, so a plain remembered one wouldn't match any more
 * and the art would have nowhere to shrink back into.
 */
@Composable
fun rememberArtToken(): Any = rememberSaveable { "$TokenRun-${nextToken.incrementAndGet()}" }

// Unique within this run of the app by the counter, and across runs (tokens restored after
// the process was killed) by the run's own start. A random UUID per row cost a secure
// random for every row composed.
private val TokenRun = System.nanoTime().toString(36)
private val nextToken = AtomicLong()

/** A card's travelling art: [modifier] for its art, [onClick] for the card. */
class SharedArtSource(val modifier: Modifier, val onClick: () -> Unit)

/**
 * For a card or row that opens [item]'s page ([key]): its art grows into the page's
 * when this copy is the one tapped, and [onClick] records that before opening it.
 */
@Composable
fun rememberSharedArt(key: String, item: Any, onClick: () -> Unit): SharedArtSource {
    val transitions = LocalArtTransitions.current
    val token = rememberArtToken()
    return SharedArtSource(
        modifier = Modifier.sharedArt(key, token),
        onClick = {
            transitions.open(key, token, item)
            onClick()
        },
    )
}
