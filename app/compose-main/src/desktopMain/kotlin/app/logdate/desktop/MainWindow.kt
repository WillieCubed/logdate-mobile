package app.logdate.desktop

import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.rememberWindowState
import app.logdate.client.ui.LogDateAppRoot
import app.logdate.feature.core.AppViewModel
import app.logdate.feature.core.GlobalAppUiLoadedState
import app.logdate.feature.core.GlobalAppUiLoadingState
import app.logdate.feature.core.GlobalAppUiState
import org.koin.compose.viewmodel.koinViewModel

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
@Composable
private fun MainWindowContent(
    onOpenEditor: () -> Unit,
    viewModel: AppViewModel = koinViewModel(),
) {
    var uiState: GlobalAppUiState by remember { mutableStateOf(GlobalAppUiLoadingState) }
    LaunchedEffect(viewModel.uiState) {
        viewModel.uiState.collect {
            uiState = it
        }
    }

    // Debug print for initial state
    println("Initial uiState: $uiState")

    // Debug the viewModel initialization
    LaunchedEffect(Unit) {
        println("ViewModel hash: ${viewModel.hashCode()}")
        println("Initial viewModel.uiState: ${viewModel.uiState.value}")
    }

    LaunchedEffect(viewModel) {
        println("Starting to collect uiState")
        viewModel.uiState.collect { newState ->
            println("Collected new state: $newState")
            uiState = newState
        }
    }

    // Debug current state before conditional rendering
    println("Current uiState: $uiState")

    if (uiState is GlobalAppUiLoadedState) {
        LogDateAppRoot(
            // TODO: Allow new window editor to be opened
            uiState as GlobalAppUiLoadedState,
            onShowUnlockPrompt = viewModel::showNativeUnlockPrompt,
        )
    } else {
        Text("Loading...")
    }
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