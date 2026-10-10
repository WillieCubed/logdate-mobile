package app.logdate.feature.editor.ui.drop

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Lets people drop text, photos, videos, and audio from other apps onto the editor.
 *
 * Media is copied into LogDate's private store before [onMediaImported] receives the copies, because
 * the permission to read another app's file ends with the drag. Files that could not be copied are
 * reported in the same result, so the editor can say what went wrong. [onActiveChanged] reports whether an
 * acceptable drag is over the editor, so the editor can highlight where it will land.
 */
@Composable
expect fun Modifier.entryDropTarget(
    onText: (String) -> Unit,
    onMediaImported: (EntryMediaImport) -> Unit,
    onActiveChanged: (Boolean) -> Unit,
): Modifier
