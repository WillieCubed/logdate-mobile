@file:Suppress("ktlint:standard:function-naming")

package app.logdate.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset

/**
 * A single item in a context menu.
 */
data class ContextMenuItem(
    val label: String,
    val onClick: () -> Unit,
)

/**
 * Wraps [content] with a context menu that appears on long-press, or on right-click with a mouse or
 * trackpad. The menu renders as a Material dropdown on every platform today; on iOS a native
 * `UIContextMenuInteraction` bridge is a planned follow-up.
 *
 * [content] may be clickable: a long-press opens the menu without also counting as a tap. Pass
 * `openOnLongPress = false` when the content uses long-press for something else, such as starting a
 * drag; right-click still opens the menu. With no [items], every press is left to the content.
 *
 * Usage:
 * ```
 * ContextMenuArea(
 *     items = listOf(
 *         ContextMenuItem("Edit") { onEdit() },
 *         ContextMenuItem("Delete") { onDelete() },
 *     ),
 * ) {
 *     Card { ... }
 * }
 * ```
 */
@Composable
expect fun ContextMenuArea(
    items: List<ContextMenuItem>,
    modifier: Modifier = Modifier,
    openOnLongPress: Boolean = true,
    content: @Composable () -> Unit,
)

/**
 * Where a dropdown anchored to an item should open so it appears at [pointer], given that dropdown
 * offsets are measured from the anchor's bottom-left corner.
 */
fun contextMenuOffset(
    pointer: Offset,
    anchorHeight: Int,
): Offset = Offset(pointer.x, pointer.y - anchorHeight)
