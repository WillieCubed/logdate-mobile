package app.logdate

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.window.ComposeUIViewController
import app.logdate.client.ui.LogDateAppRoot
import app.logdate.feature.core.AppViewModel
import app.logdate.feature.core.GlobalAppUiLoadedState
import org.koin.compose.viewmodel.koinViewModel

@Suppress("ktlint:standard:function-naming")
fun MainViewController() =
    ComposeUIViewController {
        startCrashReportingUserBridge()
        val viewModel: AppViewModel = koinViewModel()
        val uiState by viewModel.uiState.collectAsState()
        val loadedState = (uiState as? GlobalAppUiLoadedState) ?: GlobalAppUiLoadedState()
        LogDateAppRoot(
            appUiState = loadedState,
            onShowUnlockPrompt = viewModel::showNativeUnlockPrompt,
        )
    }
