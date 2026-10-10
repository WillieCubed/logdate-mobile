@file:Suppress("ktlint:standard:function-naming")

package app.logdate.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Opens the menu on long-press, and on a secondary (right) click from a mouse or trackpad. A
 * right-click menu opens at the pointer, the way desktop context menus do.
 */
@Composable
actual fun ContextMenuArea(
    items: List<ContextMenuItem>,
    modifier: Modifier,
    openOnLongPress: Boolean,
    content: @Composable () -> Unit,
) = MaterialContextMenuArea(items, modifier, openOnLongPress, content)
