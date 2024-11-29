package app.logdate.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.window.Window
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.logdate.client.ui.LogDateAppRoot
import app.logdate.feature.core.AppViewModel
import app.logdate.feature.core.GlobalAppUiLoadedState
import app.logdate.feature.core.GlobalAppUiLoadingState
import org.koin.compose.viewmodel.koinViewModel

@Composable
internal fun MainWindow(
    appState: LogDateApplicationState,
    onOpenEditor: () -> Unit,
    state: MainWindowState = rememberMainWindowState(appState),
) {
    Window(
        onCloseRequest = state::exit,
        title = "LogDate",
    ) {
        MainWindowContent(
            onOpenEditor = onOpenEditor,
        )
    }
}

/**
 * Must be called from inside a window.
 */
@Composable
private fun MainWindowContent(
    onOpenEditor: () -> Unit,
    viewModel: AppViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    if (uiState is GlobalAppUiLoadingState) {
        return
    }
    LogDateAppRoot(
        // TODO: Allow new window editor to be opened
        uiState as GlobalAppUiLoadedState,
        onShowUnlockPrompt = viewModel::showNativeUnlockPrompt,
    )
}

@Composable
internal fun rememberMainWindowState(appState: LogDateApplicationState): MainWindowState =
    remember {
        MainWindowState(appState = appState)
    }

class MainWindowState(
    private val appState: LogDateApplicationState,
) : LogDateWindowState {
    override fun exit(): Boolean {
        // TODO: Implement cleanup
        return true
    }
}