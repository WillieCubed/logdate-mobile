package app.logdate.mobile

import android.app.DirectAction
import android.app.assist.AssistContent
import android.os.Bundle
import android.os.CancellationSignal
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import app.logdate.core.assist.AssistantActionsProvider
import app.logdate.core.assist.AssistantContextProvider
import app.logdate.core.assist.toDirectAction
import app.logdate.mobile.ui.AppViewModel
import app.logdate.mobile.ui.BiometricActivityProvider
import app.logdate.mobile.ui.BiometricGatekeeperComponent
import app.logdate.mobile.ui.LaunchAppUiState
import app.logdate.mobile.ui.LogdateAppRoot
import app.logdate.mobile.ui.common.rememberMainAppState
import app.logdate.ui.theme.LogDateTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.util.function.Consumer
import javax.inject.Inject

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
@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
@AndroidEntryPoint
class MainActivity : FragmentActivity() {
    // Use FragmentActivity because we need to show the biometric prompt

    @Inject
    lateinit var assistantContextProvider: AssistantContextProvider

    @Inject
    lateinit var assistantActionsProvider: AssistantActionsProvider

    lateinit var biometricGatekeeperComponent: FragmentActivity

    private val viewModel by viewModels<AppViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)


        biometricGatekeeperComponent = (applicationContext as BiometricActivityProvider)
            .provideBiometricActivity()

        biometricGatekeeperComponent.inject(this)

        var uiState: LaunchAppUiState by mutableStateOf(LaunchAppUiState.Loading)

        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState
                    .onEach { uiState = it }
                    .collect {}
            }
        }

        splashScreen.setKeepOnScreenCondition {
            when (uiState) {
                LaunchAppUiState.Loading -> true
                is LaunchAppUiState.Loaded -> false
            }
        }

        enableEdgeToEdge()
        setContent {
            val appState = rememberMainAppState(
                windowSizeClass = calculateWindowSizeClass(this@MainActivity),
            )
            LogDateTheme {
                LogdateAppRoot(
                    appState,
                )
            }
        }
    }

    override fun onProvideAssistContent(assistContent: AssistContent) {
        super.onProvideAssistContent(assistContent)
        assistContent.apply {
            val assistData = assistantContextProvider.jsonData
            structuredData = assistData
            clipData = assistantContextProvider.clipData
        }
    }

    override fun onGetDirectActions(
        cancellationSignal: CancellationSignal, callback: Consumer<MutableList<DirectAction>>
    ) {
        if (voiceInteractor == null) {
            super.onGetDirectActions(cancellationSignal, callback)
            return
        }
        callback.accept(assistantActionsProvider.supportedActions.map { it.toDirectAction() }
            .toMutableList())
    }
}
