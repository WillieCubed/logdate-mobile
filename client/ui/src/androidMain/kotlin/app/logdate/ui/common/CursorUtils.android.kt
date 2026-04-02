package app.logdate.ui.common

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon

actual fun Modifier.cursorIcon(type: CursorType): Modifier =
    pointerHoverIcon(
        when (type) {
            CursorType.DEFAULT -> PointerIcon.Default
            CursorType.TEXT -> PointerIcon.Text
            CursorType.CROSSHAIR -> PointerIcon.Crosshair
            CursorType.MOVE -> PointerIcon.Hand
            CursorType.RESIZE_HORIZONTAL -> PointerIcon.Hand
            CursorType.RESIZE_VERTICAL -> PointerIcon.Hand
            CursorType.RESIZE_DIAGONAL -> PointerIcon.Hand
            CursorType.POINTER -> PointerIcon.Hand
        },
    )
