package app.logdate.ui.common

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
