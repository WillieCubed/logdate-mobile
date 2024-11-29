package app.logdate.client

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import app.logdate.client.ui.LogDateAppRoot
import app.logdate.feature.core.AndroidBiometricGatekeeper
import app.logdate.feature.core.AppViewModel
import app.logdate.feature.core.GlobalAppUiLoadedState
import app.logdate.feature.core.GlobalAppUiLoadingState
import app.logdate.feature.core.GlobalAppUiState
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel

/**
 * The main app activity.
 *
 * This activity is the entry point of the app and is responsible for setting up the app's UI and
 * handling the app's lifecycle.
 *
 * On load, this activity will display a splash screen until the app's UI is ready to be displayed.
 * If the user has not onboarded yet, the app will display the onboarding flow. Otherwise, the app
 * will display the main app UI.
 *
 * This activity is also responsible for providing the app's assist content and direct actions.
 */
class MainActivity : FragmentActivity() {

    private val androidBiometricGatekeeper: AndroidBiometricGatekeeper by inject()

    private val viewModel by viewModel<AppViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)

        androidBiometricGatekeeper.setActivity(this)

        // TODO: Maybe reconsider sealed class approach to uiState loading
        var uiState: GlobalAppUiState by mutableStateOf(GlobalAppUiLoadingState)

        // Update the uiState
        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState
                    .onEach { uiState = it }
                    .collect {}
            }
        }

        splashScreen.setKeepOnScreenCondition {
            uiState is GlobalAppUiLoadingState
        }

        enableEdgeToEdge()
        setContent {
            val state = uiState
            if (state is GlobalAppUiLoadedState) {
                LogDateAppRoot(
                    appUiState = state,
                    onShowUnlockPrompt = viewModel::showNativeUnlockPrompt,
                )
            }
        }
    }

//    override fun onProvideAssistContent(assistContent: AssistContent) {
//        super.onProvideAssistContent(assistContent)
//        assistContent.apply {
//            val assistData = assistantContextProvider.jsonData
//            structuredData = assistData
//            clipData = assistantContextProvider.clipData
//        }
//    }

    // TODO: Ensure assistant can get actions
//    override fun onGetDirectActions(
//        cancellationSignal: CancellationSignal, callback: Consumer<MutableList<DirectAction>>
//    ) {
//        if (voiceInteractor == null) {
//            super.onGetDirectActions(cancellationSignal, callback)
//            return
//        }
//        callback.accept(assistantActionsProvider.supportedActions.map { it.toDirectAction() }
//            .toMutableList())
//    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    LogDateAppRoot(
        appUiState = GlobalAppUiLoadedState(),
        onShowUnlockPrompt = {},
    )
}