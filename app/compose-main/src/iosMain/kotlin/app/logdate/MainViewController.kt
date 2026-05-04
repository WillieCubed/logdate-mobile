package app.logdate

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.window.ComposeUIViewController
import app.logdate.feature.core.AppViewModel
import app.logdate.feature.core.GlobalAppUiLoadedState
import app.logdate.navigation.LogDateNavDisplay
import org.koin.compose.viewmodel.koinViewModel
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.UIKit.UIApplicationDidEnterBackgroundNotification

@Suppress("ktlint:standard:function-naming")
fun MainViewController() =
    ComposeUIViewController {
        startCrashReportingUserBridge()
        val viewModel: AppViewModel = koinViewModel()
        DisposableEffect(viewModel) {
            val observer =
                NSNotificationCenter.defaultCenter.addObserverForName(
                    name = UIApplicationDidEnterBackgroundNotification,
                    `object` = null,
                    queue = NSOperationQueue.mainQueue,
                    usingBlock = { viewModel.onAppBackgrounded() },
                )
            onDispose {
                NSNotificationCenter.defaultCenter.removeObserver(observer)
            }
        }
        val uiState by viewModel.uiState.collectAsState()
        val loadedState = (uiState as? GlobalAppUiLoadedState) ?: GlobalAppUiLoadedState()
        LogDateNavDisplay(
            appUiState = loadedState,
            onShowUnlockPrompt = viewModel::showNativeUnlockPrompt,
        )
    }
