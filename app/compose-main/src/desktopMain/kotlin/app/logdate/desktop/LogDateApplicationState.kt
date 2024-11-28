package app.logdate.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import app.logdate.desktop.notification.DesktopNotification

/**
 * Global state for the LogDate application.
 *
 * This manages state for the entire application, including all windows, dialogs, and notifications.
 */
class LogDateApplicationState {

    val windows: List<LogDateWindowState>
        get() = _windows

    private val _windows = mutableStateListOf<LogDateWindowState>()

    init {
        // Open the main window by default
        _windows += MainWindowState(this)
    }

    fun openJournal(journalId: String) {

    }

    fun openNoteEditor() {
        _windows.add(EntryEditorWindowState(this))
    }

    fun sendNotification(notification: DesktopNotification) {
    }

    /**
     * Closes all windows, performing cleanup as needed, and exits the application.
     */
    fun exit() {
        // Close in reverse order to avoid issues with nested windows.
        for (window in windows.reversed()) {
            if (window.exit()) {
                _windows.remove(window)
            }
        }
    }
}

/**
 * Utility function to remember the [LogDateApplicationState] in a composable.
 */
@Composable
fun rememberApplicationState(): LogDateApplicationState = remember {
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