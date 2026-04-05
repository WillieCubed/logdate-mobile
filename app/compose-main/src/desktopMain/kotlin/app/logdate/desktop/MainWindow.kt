package app.logdate.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.rememberWindowState
import app.logdate.client.ui.LogDateAppRoot
import app.logdate.feature.core.AppViewModel
import app.logdate.feature.core.GlobalAppUiLoadedState
import org.koin.compose.viewmodel.koinViewModel

@Suppress("ktlint:standard:function-naming")
@Composable
internal fun MainWindow(
    appState: LogDateApplicationState,
    onOpenEditor: () -> Unit,
    state: MainWindowState = rememberMainWindowState(appState),
) {
    val title by rememberSaveable(state) { mutableStateOf("LogDate") }
    Window(
        onCloseRequest = state::exit,
        // TODO: Remember window size preference
        state = rememberWindowState(width = 1024.dp, height = 720.dp),
        title = title,
    ) {
        MainWindowContent(
            onOpenEditor = onOpenEditor,
        )
    }
}

/**
 * Must be called from inside a window.
 */
@Suppress("ktlint:standard:function-naming")
@Composable
private fun MainWindowContent(
    onOpenEditor: () -> Unit,
    viewModel: AppViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val loadedState = (uiState as? GlobalAppUiLoadedState) ?: GlobalAppUiLoadedState()

    LogDateAppRoot(
        appUiState = loadedState,
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
