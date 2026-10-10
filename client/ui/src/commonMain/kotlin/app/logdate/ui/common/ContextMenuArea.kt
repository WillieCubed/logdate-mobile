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
 * Wraps [content] with a context menu that appears on long-press (all platforms) or
 * right-click (Android with a mouse or trackpad, and desktop). On iOS the menu is
 * presented through a native `UIContextMenuInteraction`, so users get the system long-press
 * preview with rich actions; on Android and desktop the menu renders as a Material dropdown.
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
