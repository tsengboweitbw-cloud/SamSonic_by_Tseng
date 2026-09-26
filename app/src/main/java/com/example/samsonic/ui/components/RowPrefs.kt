package com.example.samsonic.ui.components

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.Dp
import com.example.samsonic.data.AudioFormatDisplay
import com.example.samsonic.data.ThemeManager

/**
 * The settings every song, album and playlist row draws with, collected once for the
 * app ([LocalRowPrefs]) rather than by each row: a row collecting them itself sets up
 * a lifecycle-aware collector per row, a cost paid by each new row as a list flings.
 */
@Immutable
data class RowPrefs(
    val artCornerRadius: Dp = ThemeManager.DefaultAlbumArtCornerRadius,
    val likesEnabled: Boolean = true,
    val audioFormat: AudioFormatDisplay = AudioFormatDisplay.OFF,
)

val LocalRowPrefs = compositionLocalOf { RowPrefs() }