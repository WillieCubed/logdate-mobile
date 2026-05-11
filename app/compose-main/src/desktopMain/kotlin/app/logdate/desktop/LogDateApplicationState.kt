package app.logdate.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import app.logdate.desktop.notification.DesktopNotification

/**
 * Global state for the LogDate application.
 *
 * This manages state for the entire application, including all windows, dialogs, and notifications.
 *
 * The main window is a single, always-present `val`; editor windows live in a separate list and
 * can be opened and closed independently. Combining them in [windows] preserves the legacy
 * iteration order while making the "exactly one main" invariant structural.
 */
class LogDateApplicationState {
    val mainWindow: MainWindowState = MainWindowState(this)

    private val _editorWindows = mutableStateListOf<EntryEditorWindowState>()
    val editorWindows: List<EntryEditorWindowState>
        get() = _editorWindows

    val windows: List<LogDateWindowState>
        get() =
            buildList(_editorWindows.size + 1) {
                add(mainWindow)
                addAll(_editorWindows)
            }

    /**
     * Set to true when the application should terminate. [LogDateApplication] observes this and
     * invokes `ApplicationScope.exitApplication()` so the process can shut down cleanly.
     */
    var shouldExitApplication: Boolean by mutableStateOf(false)
        private set

    fun openJournal(journalId: String) {
    }

    fun openNoteEditor() {
        _editorWindows.add(EntryEditorWindowState(this))
    }

    fun sendNotification(notification: DesktopNotification) {
    }

    /**
     * Closes a single window. Returns true when the window actually closed; false when
     * [LogDateWindowState.exit] vetoed the close.
     *
     * Closing the main window also closes every editor window and signals app exit. Editors are
     * removed programmatically, so an unsaved editor's pending content is not auto-saved by this
     * path — users get save-on-close semantics only when they dismiss the editor window directly.
     */
    fun closeWindow(window: LogDateWindowState): Boolean =
        when (window) {
            mainWindow -> closeApplication()
            is EntryEditorWindowState -> closeEditor(window)
            else -> false
        }

    /**
     * Closes all windows, performing cleanup as needed, and exits the application.
     */
    fun exit() {
        closeApplication()
    }

    private fun closeEditor(editor: EntryEditorWindowState): Boolean {
        if (!editor.exit()) return false
        _editorWindows.remove(editor)
        return true
    }

    private fun closeApplication(): Boolean {
        if (!mainWindow.exit()) return false
        _editorWindows.clear()
        shouldExitApplication = true
        return true
    }
}

/**
 * Utility function to remember the [LogDateApplicationState] in a composable.
 */
@Composable
fun rememberApplicationState(): LogDateApplicationState =
    remember {
        LogDateApplicationState()
    }

/**
 * A generic interface for a window in the LogDate application.
 */
sealed interface LogDateWindowState {
    /**
     * Attempts to close this window.
     *
     * This method should perform any necessary cleanup and check if the window can be closed using
     * domain-specific logic. Once the method returns, it is assumed that the window has already
     * performed all necessary cleanup and can be safely removed from the application state.
     *
     * @return `true` if the window can be closed, `false` if the window should remain open.
     */
    fun exit(): Boolean
}
