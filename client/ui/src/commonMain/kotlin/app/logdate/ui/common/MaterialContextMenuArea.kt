@file:Suppress("ktlint:standard:function-naming")

package app.logdate.ui.common

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.DpOffset

/**
 * The Material dropdown version of [ContextMenuArea]: the menu opens below the content on a
 * long-press, and at the pointer on a secondary (right) click. With no [items], every press is left
 * to the content.
 */
@Composable
internal fun MaterialContextMenuArea(
    items: List<ContextMenuItem>,
    modifier: Modifier,
    openOnLongPress: Boolean,
    content: @Composable () -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }
    var menuOffset by remember { mutableStateOf(DpOffset.Zero) }
    val currentItems by rememberUpdatedState(items)
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current

    Box(
        modifier =
            modifier.contextMenuGestures(
                isEnabled = { currentItems.isNotEmpty() },
                openOnLongPress = openOnLongPress,
                onLongPress = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    menuOffset = DpOffset.Zero
                    showMenu = true
                },
                onSecondaryClick = { offset ->
                    menuOffset = with(density) { DpOffset(offset.x.toDp(), offset.y.toDp()) }
                    showMenu = true
                },
                onDragAfterLongPress = { showMenu = false },
            ),
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

/**
 * Detects the gestures that open a context menu, over content that may itself be clickable.
 *
 * A clickable child consumes a press before its parent's usual gesture detectors see it, so a parent
 * listening in the normal pass never hears about a long-press. The long-press detector here listens
 * in the [PointerEventPass.Initial] pass, which reaches the parent first, and consumes nothing until
 * the press has lasted the long-press timeout. After that, lifting the finger is consumed so the
 * same press does not also count as a tap, and moving past the touch slop calls
 * [onDragAfterLongPress] so a drag can take over from the menu.
 *
 * [onSecondaryClick] receives the dropdown offset that places the menu at the pointer.
 */
private fun Modifier.contextMenuGestures(
    isEnabled: () -> Boolean,
    openOnLongPress: Boolean,
    onLongPress: () -> Unit,
    onSecondaryClick: (Offset) -> Unit,
    onDragAfterLongPress: () -> Unit,
): Modifier =
    pointerInput(openOnLongPress) {
        if (!openOnLongPress) return@pointerInput
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            if (!isEnabled() || endsBeforeLongPress(down)) return@awaitEachGesture
            onLongPress()
            var dragged = false
            while (true) {
                val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) {
                    change.consume()
                    break
                }
                if (!dragged && movedPastSlop(change, down)) {
                    dragged = true
                    onDragAfterLongPress()
                }
            }
        }
    }.pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent()
                if (event.type == PointerEventType.Press && event.buttons.isSecondaryPressed && isEnabled()) {
                    onSecondaryClick(contextMenuOffset(event.changes.first().position, size.height))
                    event.changes.forEach { it.consume() }
                }
            }
        }
    }

/** True when the press lifts, or moves past the touch slop, before the long-press timeout. */
private suspend fun AwaitPointerEventScope.endsBeforeLongPress(down: PointerInputChange): Boolean =
    withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
        var ended = false
        while (!ended) {
            val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id }
            ended = change == null || !change.pressed || movedPastSlop(change, down)
        }
        true
    } ?: false

private fun AwaitPointerEventScope.movedPastSlop(
    change: PointerInputChange,
    down: PointerInputChange,
): Boolean = (change.position - down.position).getDistance() > viewConfiguration.touchSlop
