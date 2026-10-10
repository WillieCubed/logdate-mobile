package app.logdate.feature.editor.ui.drop

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Adds photos, videos, and audio from the clipboard to the entry when the person pastes with a
 * keyboard (Ctrl+V). Android only; elsewhere the text field pastes text as before.
 *
 * The text field pastes text itself; this handles what it cannot, so copying an image in another
 * app and pasting it into LogDate attaches it. Media is copied into LogDate's private store before
 * [onMediaImported] receives it, as for drops.
 */
@Composable
expect fun Modifier.entryPasteTarget(onMediaImported: (List<String>) -> Unit): Modifier
