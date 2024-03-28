package app.logdate.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import app.logdate.mobile.ui.AppViewModel
import app.logdate.mobile.ui.LaunchAppUiState
import app.logdate.mobile.ui.LogdateAppRoot
import app.logdate.mobile.ui.common.rememberMainAppState
import app.logdate.ui.theme.LogDateTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel by viewModels<AppViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)

        var uiState: LaunchAppUiState by mutableStateOf(LaunchAppUiState.Loading)

        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.onEach {
                    uiState = it
                }.collect {
                    when (it) {
                        is LaunchAppUiState.Loaded ->
                            setContent {
                                val appState = rememberMainAppState(
                                    windowSizeClass = calculateWindowSizeClass(this@MainActivity),
                                )
                                LogDateTheme {
                                    LogdateAppRoot(appState, onboarded = it.isOnboarded)
                                }
                            }

                        LaunchAppUiState.Loading -> {}
                    }
                }
            }
        }

        splashScreen.setKeepOnScreenCondition {
            when (uiState) {
                LaunchAppUiState.Loading -> true
                is LaunchAppUiState.Loaded -> false
            }
        }

        enableEdgeToEdge()
    }
}

@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
@Preview(showBackground = true, name = "App Preview - Phone")
@Composable
fun AppPreview_Mobile() {
    LogDateTheme {
        val appState = rememberMainAppState(
            windowSizeClass = WindowSizeClass.calculateFromSize(DpSize(412.dp, 918.dp)),
        )
        LogdateAppRoot(appState)
    }
}

@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
@Preview(showBackground = true, name = "App Preview - Tablet", device = "id:pixel_tablet")
@Composable
fun AppPreview_Tablet() {
    LogDateTheme {
        val appState = rememberMainAppState(
            windowSizeClass = WindowSizeClass.calculateFromSize(DpSize(1280.dp, 720.dp)),
        )
        LogdateAppRoot(appState)
    }
}