package app.logdate.navigation.window

/**
 * Represents the current state of an editor window.
 */
sealed interface EditorWindowState {
    /**
     * Window is in normal state (not minimized, maximized, or in another state).
     */
    object Normal : EditorWindowState

    /**
     * Window is currently in focus and active.
     */
    object Active : EditorWindowState

    /**
     * Window is in background (not currently focused).
     */
    object Background : EditorWindowState

    /**
     * Window is being resized.
     */
    data class Resizing(val width: Float, val height: Float) : EditorWindowState
}