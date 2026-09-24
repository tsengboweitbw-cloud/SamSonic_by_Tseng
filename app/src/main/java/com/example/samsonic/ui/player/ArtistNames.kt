package com.example.samsonic.ui.player

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import com.example.samsonic.model.ArtistCredit
import com.example.samsonic.model.Song
import com.example.samsonic.ui.components.pressClickable

/**
 * The artists to link [this] song's artist line to: each one the server credits
 * (OpenSubsonic), or else the whole line as the one artist.
 */
internal val Song.artistCredits: List<ArtistCredit>
    get() = artists.ifEmpty { listOf(ArtistCredit(artistId, artistName)) }

/**
 * A song's artist line where each artist opens their own page. With one artist the
 * whole line is the tap target (with a press squeeze, as the other lines have);
 * with several, only each name is, and it dims while pressed.
 */
@Composable
internal fun ArtistNames(
    song: Song,
    style: TextStyle,
    color: Color,
    onOpen: (artistId: String) -> Unit,
    modifier: Modifier = Modifier,
    maxLines: Int = 1,
) {
    val credits = song.artistCredits
    if (credits.size == 1) {
        val id = credits.single().id
        Text(
            text = song.artistName,
            style = style,
            color = color,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
            modifier = modifier.then(if (id != null) Modifier.pressClickable({ onOpen(id) }, pressedScale = 0.95f) else Modifier),
        )
        return
    }
    val pressed = TextLinkStyles(pressedStyle = SpanStyle(color = color.copy(alpha = color.alpha * 0.5f)))
    val text = remember(song.artistName, credits, pressed) { linkedNames(song.artistName, credits, pressed, onOpen) }
    Text(
        text = text,
        style = style,
        color = color,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

/**
 * [display] (the server's own artist string, keeping its "feat." and "&") with each
 * credited name linked where it appears in it, in order. If any name can't be found
 * there, the names are listed instead, comma separated.
 */
private fun linkedNames(
    display: String,
    credits: List<ArtistCredit>,
    styles: TextLinkStyles,
    onOpen: (String) -> Unit,
): AnnotatedString {
    val spans = spansIn(display, credits)
    val text = if (spans != null) display else credits.joinToString(", ") { it.name }
    val ranges = spans ?: spansIn(text, credits)!!
    return buildAnnotatedString {
        var at = 0
        credits.zip(ranges).forEach { (credit, range) ->
            append(text.substring(at, range.first))
            val name = text.substring(range)
            val id = credit.id
            if (id != null) {
                withLink(LinkAnnotation.Clickable(tag = id, styles = styles) { onOpen(id) }) { append(name) }
            } else {
                append(name)
            }
            at = range.last + 1
        }
        append(text.substring(at))
    }
}

/** Where each credit's name sits in [text], each after the one before; null if one is missing. */
private fun spansIn(text: String, credits: List<ArtistCredit>): List<IntRange>? {
    var from = 0
    return credits.map { credit ->
        val start = text.indexOf(credit.name, from)
        if (start < 0) return null
        from = start + credit.name.length
        start until from
    }
}
