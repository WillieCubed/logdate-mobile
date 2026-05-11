@file:OptIn(FlowPreview::class)

package app.logdate.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.rememberWindowState
import app.logdate.feature.core.AppViewModel
import app.logdate.feature.core.GlobalAppUiLoadedState
import app.logdate.navigation.LogDateNavDisplay
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import org.koin.compose.viewmodel.koinViewModel

private val DEFAULT_MAIN_WINDOW_SIZE = DpSize(1024.dp, 720.dp)
private const val RESIZE_PERSIST_DEBOUNCE_MS = 300L

@Suppress("ktlint:standard:function-naming")
@Composable
internal fun MainWindow(
    appState: LogDateApplicationState,
    onOpenEditor: () -> Unit,
    state: MainWindowState = rememberMainWindowState(appState),
) {
    val preferences = remember { DesktopWindowPreferences() }
    val windowState = rememberWindowState(size = preferences.readMainWindowSize(DEFAULT_MAIN_WINDOW_SIZE))

    LaunchedEffect(Unit) {
        snapshotFlow { windowState.size }
            .distinctUntilChanged()
            .debounce(RESIZE_PERSIST_DEBOUNCE_MS)
            .collect { size -> preferences.writeMainWindowSize(size) }
    }

    Window(
        onCloseRequest = { appState.closeWindow(state) },
        state = windowState,
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
@Suppress("ktlint:standard:function-naming")
@Composable
private fun MainWindowContent(
    onOpenEditor: () -> Unit,
    viewModel: AppViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val loadedState = (uiState as? GlobalAppUiLoadedState) ?: GlobalAppUiLoadedState()

    LogDateNavDisplay(
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
    override fun exit(): Boolean = true
}
