package app.logdate.ui.common

import androidx.compose.ui.Modifier

/**
 * Standard cursor types for desktop interaction.
 */
enum class CursorType {
    DEFAULT,
    TEXT,
    CROSSHAIR,
    MOVE,
    RESIZE_HORIZONTAL,
    RESIZE_VERTICAL,
    RESIZE_DIAGONAL,
    POINTER,
}

/**
 * Sets the cursor icon when hovering over this element.
 *
 * On Android, maps to [android.view.PointerIcon] types.
 * On other platforms, this is a no-op.
 */
expect fun Modifier.cursorIcon(type: CursorType): Modifier
