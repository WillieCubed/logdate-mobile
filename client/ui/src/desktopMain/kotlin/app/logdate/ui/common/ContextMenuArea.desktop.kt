@file:Suppress("ktlint:standard:function-naming")

package app.logdate.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Opens the menu on right-click at the pointer, and on long-press from a touchscreen. */
@Composable
actual fun ContextMenuArea(
    items: List<ContextMenuItem>,
    modifier: Modifier,
    openOnLongPress: Boolean,
    content: @Composable () -> Unit,
) = MaterialContextMenuArea(items, modifier, openOnLongPress, content)
