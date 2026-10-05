package com.example.samsonic.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.example.samsonic.R

/** Some of the songs are in the playlist already: add them again, or skip them. */
@Composable
internal fun DuplicatesPrompt(
    step: Step.Duplicates,
    itemTitle: String,
    saving: Boolean,
    onAddAnyway: () -> Unit,
    onSkip: () -> Unit,
) {
    val total = step.songIds.size
    val count = step.duplicates.size
    val playlist = step.playlist.name
    val message = when {
        total == 1 -> stringResource(R.string.library_duplicate_one, itemTitle, playlist)
        count == total -> stringResource(R.string.library_duplicate_all, total, playlist)
        else -> pluralStringResource(R.plurals.library_duplicate_some, count, count, total, playlist)
    }
    Column(Modifier.padding(horizontal = 24.dp)) {
        Text(text = message, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onAddAnyway, enabled = !saving) { Text(stringResource(R.string.library_add_anyway)) }
            Spacer(Modifier.width(8.dp))
            Button(onClick = onSkip, enabled = !saving) {
                Text(stringResource(if (total == 1 || count == total) R.string.library_skip else R.string.library_skip_duplicates))
            }
        }
    }
}

@Composable
internal fun NewPlaylistForm(saving: Boolean, onCancel: () -> Unit, onCreate: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val canCreate = name.isNotBlank() && !saving
    Column(Modifier.padding(horizontal = 24.dp)) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text(stringResource(R.string.library_playlist_name)) },
            singleLine = true,
            enabled = !saving,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (canCreate) onCreate(name.trim()) }),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            ),
            modifier = Modifier.fillMaxWidth().focusRequester(focus),
        )
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onCancel, enabled = !saving) { Text(stringResource(R.string.library_cancel)) }
            Spacer(Modifier.width(8.dp))
            Button(onClick = { onCreate(name.trim()) }, enabled = canCreate) { Text(stringResource(if (saving) R.string.library_creating else R.string.library_create)) }
        }
    }
}

@Composable
internal fun MenuMessage(text: String, isError: Boolean = false) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 12.dp),
    )
}

/** The heading of a menu's second step, over its choices. */
@Composable
internal fun StepHeading(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 4.dp),
    )
}
