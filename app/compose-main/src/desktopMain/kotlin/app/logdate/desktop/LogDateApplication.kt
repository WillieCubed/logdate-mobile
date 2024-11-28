package app.logdate.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.window.ApplicationScope

@Composable
fun ApplicationScope.LogDateApplication(state: LogDateApplicationState) {
    for (window in state.windows) {
        key(window) {
            when (window) {
                is MainWindowState -> MainWindow(
                    appState = state,
                    onOpenEditor = state::openNoteEditor,
                    state = window,
                )

                is EntryEditorWindowState -> EntryEditorWindow(
                    appState = state,
                    state = window,
                )
            }
        }
    }
}