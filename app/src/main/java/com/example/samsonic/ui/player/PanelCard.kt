package com.example.samsonic.ui.player

import android.util.TypedValue
import android.view.ContextThemeWrapper
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.samsonic.ui.components.pressClickable
import com.example.samsonic.ui.theme.GlassAlpha
import com.example.samsonic.ui.theme.LocalGlassSettings
import com.example.samsonic.ui.theme.OneUiRadius
import com.example.samsonic.ui.theme.glassSurface
import dev.chrisbanes.haze.HazeState

// Every card is this share of the height, tall enough for a list to show a good stretch of itself.
private const val CardHeight = 0.88f

/**
 * How much a platform dialog dims the screen behind it: Compose's Dialog sets
 * no amount of its own, so it is the platform dialog theme's.
 */
@Composable
private fun dialogDimAmount(): Float {
    val context = LocalContext.current
    return remember(context) {
        val value = TypedValue()
        val dialogTheme = if (context.theme.resolveAttribute(android.R.attr.dialogTheme, value, true)) value.resourceId else 0
        val attrs = ContextThemeWrapper(context, dialogTheme).obtainStyledAttributes(intArrayOf(android.R.attr.backgroundDimAmount))
        try {
            attrs.getFloat(0, 0.6f)
        } finally {
            attrs.recycle()
        }
    }
}

/**
 * A panel as a floating glass card over a dimmed Now Playing, styled like the
 * Settings menus: [title] on top, [content] below, and Close at the bottom.
 * It grows out of its button ([MorphPanel]); tapping outside it, back, or Close
 * folds it back, as does pulling it down from the top of its content. Every card
 * has the same fixed height, which [content] fills.
 * [haze] is Now Playing's haze source, which the card's glass blurs.
 */
@Composable
internal fun PanelCard(
    panel: PanelState,
    icon: ImageVector,
    title: String,
    haze: HazeState,
    content: @Composable ColumnScope.() -> Unit,
) {
    val showing by remember(panel) { derivedStateOf { panel.progress > 0f } }
    if (!showing) return
    val shape = RoundedCornerShape(OneUiRadius.Card)
    val dim = dialogDimAmount()
    val glass = LocalGlassSettings.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .drawBehind { drawRect(Color.Black.copy(alpha = dim * panel.progress)) }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = panel.isOpen,
                onClick = { panel.close() },
            )
            .systemBarsPadding()
            .padding(horizontal = 28.dp, vertical = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        MorphPanel(
            panel = panel,
            icon = icon,
            surface = Modifier.glassSurface(
                shape = shape,
                hazeState = haze,
                tint = MaterialTheme.colorScheme.surfaceContainerHigh,
                // Starts thin, like its button's glass; the wash below thickens it.
                alpha = MorphGlassBase,
                blurRadius = glass.panelBlur,
                scaleOpacity = false,
                rim = false,
            ),
            wash = MaterialTheme.colorScheme.surfaceContainerHigh,
            // The menu settings, apart from the chrome's: a modal panel keeps
            // its own look whatever the chrome's glass is set to.
            washAlpha = washToReach(MorphGlassBase, 2f * GlassAlpha.Sheet * glass.panelOpacity),
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(CardHeight)
                // Swallows taps so only the space around the card dismisses.
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
            radius = OneUiRadius.Card,
        ) {
            Column(Modifier.padding(vertical = 24.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
                Column(
                    modifier = Modifier
                        .padding(top = 16.dp, bottom = 8.dp)
                        .weight(1f),
                    content = content,
                )
                Text(
                    text = "Close",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .pressClickable({ panel.close() }, pressedScale = 0.95f)
                        .padding(vertical = 12.dp),
                )
            }
        }
    }
}
