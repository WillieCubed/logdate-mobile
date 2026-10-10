@file:Suppress("ktlint:standard:function-naming")

package app.logdate.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * iOS context menu — currently the same Compose long-press and Material dropdown used on Android
 * and desktop. A follow-up replaces this with a native `UIContextMenuInteraction` bridge so iOS
 * users get the system long-press preview with the system blur and rich actions. The
 * expect/actual split exists today so the bridge can land without touching callsites.
 */
@Composable
actual fun ContextMenuArea(
    items: List<ContextMenuItem>,
    modifier: Modifier,
    openOnLongPress: Boolean,
    content: @Composable () -> Unit,
) = MaterialContextMenuArea(items, modifier, openOnLongPress, content)
