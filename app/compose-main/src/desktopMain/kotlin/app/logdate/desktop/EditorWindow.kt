package app.logdate.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.Window
import app.logdate.feature.editor.ui.EntryEditorScreen
import app.logdate.feature.editor.ui.editor.EntryEditorViewModel
import org.koin.compose.viewmodel.koinViewModel

/**
 * A window that allows the user to edit a journal entry.
 */
@Composable
internal fun EntryEditorWindow(
    appState: LogDateApplicationState,
    state: EntryEditorWindowState = rememberEntryEditorWindowState(appState),
    viewModel: EntryEditorViewModel = koinViewModel(),
) {
    // TODO: Support fullscreen, immersive journaling experience
    Window(
        onCloseRequest = state::exit,
        title = state.title,
    ) {
        // TODO: Implement logic to automatically update title
        EntryEditorScreen(
            viewModel = viewModel,
            onNavigateBack = {
                state.exit()
            },
            onEntrySaved = {
                state.exit()
            }
        )
    }
}

@Composable
internal fun rememberEntryEditorWindowState(appState: LogDateApplicationState): EntryEditorWindowState = remember {
    EntryEditorWindowState(appState)
}

class EntryEditorWindowState(
    appState: LogDateApplicationState,
    initialTitle: String = "New Entry",
) : LogDateWindowState {

    var title: String by mutableStateOf(initialTitle)
        private set

    fun updateTitle(newTitle: String) {
        title = newTitle
    }

    override fun exit(): Boolean {
        // TODO: Implement cleanup
        return true
    }
}