package com.example.samsonic.ui.common

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.dp

private val FadeHeight = 32.dp

/**
 * A page title that scrolls away with the page, as if it were the page's first
 * row: scrolling up slides [header] out under the status bar before the content
 * moves, and it comes back once the content is scrolled back to its top.
 *
 * Everything below the status bar fades out into it, header included, so
 * nothing is sliced off at a hard edge. The fade only shows once the header has
 * started to move, so at rest the title is never dimmed.
 *
 * [content] gets the full height below the status bar, laid out once; only its
 * position moves as the header scrolls, so lists aren't remeasured every frame.
 */
@Composable
fun ScrollAwayHeader(
    header: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    // How far the header has scrolled away: 0 = fully shown, -headerHeight = gone.
    var offset by remember { mutableFloatStateOf(0f) }
    val headerHeight = remember { floatArrayOf(0f) }
    val connection = remember {
        object : NestedScrollConnection {
            // Scrolling up: the header leaves first, then the content scrolls.
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y >= 0f) return Offset.Zero
                return moveBy(available.y)
            }

            // Scrolling down: only what the content couldn't use (it's back at its
            // top) brings the header back, so the title stays attached to the page
            // instead of reappearing over scrolled content.
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (available.y <= 0f) return Offset.Zero
                return moveBy(available.y)
            }

            private fun moveBy(dy: Float): Offset {
                val next = (offset + dy).coerceIn(-headerHeight[0], 0f)
                val consumed = next - offset
                offset = next
                return Offset(0f, consumed)
            }
        }
    }

    Layout(
        contents = listOf(header, content),
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .nestedScroll(connection)
            .clipToBounds()
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                val fadePx = FadeHeight.toPx()
                // Eases in over the first stretch of the header's travel.
                val strength = (-offset / fadePx).coerceIn(0f, 1f)
                if (strength <= 0f) return@drawWithContent
                drawRect(
                    brush = Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 1f - strength),
                        1f to Color.Black,
                        startY = 0f,
                        endY = fadePx,
                    ),
                    topLeft = Offset.Zero,
                    size = Size(size.width, fadePx),
                    blendMode = BlendMode.DstIn,
                )
            },
    ) { (headerMeasurables, contentMeasurables), constraints ->
        val loose = constraints.copy(minHeight = 0)
        val headerPlaceable = headerMeasurables.first().measure(loose)
        headerHeight[0] = headerPlaceable.height.toFloat()
        val contentPlaceable = contentMeasurables.first().measure(
            constraints.copy(minHeight = constraints.maxHeight),
        )
        layout(constraints.maxWidth, constraints.maxHeight) {
            val y = offset.coerceIn(-headerHeight[0], 0f).toInt()
            headerPlaceable.place(0, y)
            contentPlaceable.place(0, headerPlaceable.height + y)
        }
    }
}
