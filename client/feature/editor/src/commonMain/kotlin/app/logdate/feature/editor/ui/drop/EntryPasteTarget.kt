package app.logdate.feature.editor.ui.drop

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Adds photos, videos, and audio from the clipboard to the entry when the person pastes, with a
 * keyboard (Ctrl+V) or from the text menu that a long press or right-click opens. Android only;
 * elsewhere the text field pastes text as before.
 *
 * The text field pastes text itself; this handles what it cannot, so copying an image in another
 * app and pasting it into LogDate attaches it. When the clipboard holds media, the text menu gains
 * Paste, or Paste as attachment when there is also text for the field's own Paste. Media is copied
 * into LogDate's private store before [onMediaImported] receives it, as for drops.
 */
@Composable
expect fun Modifier.entryPasteTarget(onMediaImported: (List<String>) -> Unit): Modifier
