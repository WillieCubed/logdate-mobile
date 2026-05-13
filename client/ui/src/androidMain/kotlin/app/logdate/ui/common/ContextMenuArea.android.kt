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
import androidx.compose.ui.input.pointer.pointerInput

@Composable
actual fun ContextMenuArea(
    items: List<ContextMenuItem>,
    modifier: Modifier,
    content: @Composable () -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }

    Box(
        modifier =
            modifier.pointerInput(Unit) {
                detectTapGestures(
                    onLongPress = { showMenu = true },
                )
            },
    ) {
        content()

        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false },
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
