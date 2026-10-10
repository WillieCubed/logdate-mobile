package app.logdate.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Makes this element a drag-and-drop source for plain text content.
 *
 * On Android, a long-press on the element initiates a system drag carrying [text]
 * as plain text, enabling the user to drop the content into another app window or
 * drop target within the same app.
 *
 * On iOS and Desktop this modifier is a no-op.
 */
expect fun Modifier.noteDragSource(text: String): Modifier

/**
 * Makes this element a drag-and-drop target that accepts plain text drops.
 *
 * On Android, [onDrop] is called when the user drops plain text content onto this element.
 * On iOS and Desktop this modifier is a no-op.
 *
 * @param onDrop Called with the dropped text when a valid drop occurs.
 */
expect fun Modifier.noteDropTarget(onDrop: (String) -> Unit): Modifier

/**
 * Makes this element a drag source for multiple items.
 *
 * On Android, initiates a system drag carrying all [items] as separate
 * plain text ClipData entries. On other platforms this is a no-op.
 */
expect fun Modifier.batchDragSource(items: List<String>): Modifier

/**
 * Makes a photo, video, or audio item draggable into other apps as the file itself.
 *
 * On Android the drag carries a read-only content URI for [uri] with the global drag flags, so the
 * app it is dropped into (a chat, a document, Files) receives the media rather than a path string.
 * The read grant lasts only for that drop. On iOS and Desktop it falls back to [noteDragSource].
 */
@Composable
expect fun Modifier.mediaDragSource(uri: String): Modifier
