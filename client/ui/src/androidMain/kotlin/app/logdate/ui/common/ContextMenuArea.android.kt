@file:Suppress("ktlint:standard:function-naming")

package app.logdate.ui.common

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.DpOffset

/**
 * Opens the menu on long-press, and on a secondary (right) click from a mouse or trackpad. A
 * right-click menu opens at the pointer, the way desktop context menus do.
 */
@Composable
actual fun ContextMenuArea(
    items: List<ContextMenuItem>,
    modifier: Modifier,
    content: @Composable () -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }
    var menuOffset by remember { mutableStateOf(DpOffset.Zero) }

    Box(
        modifier =
            modifier
                .pointerInput(Unit) {
                    detectTapGestures(
                        onLongPress = {
                            menuOffset = DpOffset.Zero
                            showMenu = true
                        },
                    )
                }.pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            if (event.type == PointerEventType.Press && event.buttons.isSecondaryPressed) {
                                val offset = contextMenuOffset(event.changes.first().position, size.height)
                                menuOffset = DpOffset(offset.x.toDp(), offset.y.toDp())
                                showMenu = true
                                event.changes.forEach { it.consume() }
                            }
                        }
                    }
                },
    ) {
        content()

        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false },
            offset = menuOffset,
        ) {
            items.forEach { item ->
                DropdownMenuItem(
                    text = { Text(item.label) },
                    onClick = {
                        showMenu = false
                        item.onClick()
                    },
                )
            }
        }
    }
}
