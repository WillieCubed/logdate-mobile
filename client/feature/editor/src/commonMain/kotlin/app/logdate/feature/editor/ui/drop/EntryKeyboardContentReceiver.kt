@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.editor.ui.drop

import androidx.compose.runtime.Composable

/**
 * Lets the on-screen keyboard insert GIFs, stickers, and other images into the entry being written
 * in [content]. Android only; elsewhere [content] is shown unchanged.
 *
 * A keyboard inserts a file only into a field that says it accepts that type, and otherwise falls
 * back to sharing a link or refusing. The editor's text fields accept [keyboardContentMimeTypes];
 * each file the keyboard sends is copied into LogDate's private store and attached to the entry
 * through [onMediaImported], as for pasted and dropped media.
 */
@Composable
expect fun EntryKeyboardContentReceiver(
    onMediaImported: (EntryMediaImport) -> Unit,
    content: @Composable () -> Unit,
)
