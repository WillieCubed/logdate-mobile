package app.logdate.client

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
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
import app.logdate.feature.core.AndroidBiometricGatekeeper
import app.logdate.feature.core.AppViewModel
import app.logdate.feature.core.BiometricGatekeeper
import app.logdate.feature.core.GlobalAppUiLoadedState
import app.logdate.feature.core.GlobalAppUiLoadingState
import app.logdate.feature.core.GlobalAppUiState
import app.logdate.feature.core.di.ActivityProvider
import app.logdate.feature.core.export.AndroidExportLauncher
import io.github.vinceglb.filekit.core.FileKit
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.compose.KoinContext

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

    private val biometricGatekeeper: BiometricGatekeeper by inject()
    private val activityProvider: ActivityProvider by inject()
    private val androidExportLauncher: AndroidExportLauncher by inject()

    private val viewModel by viewModel<AppViewModel>()
    
    // Register the document picker for export functionality
    private val createDocumentLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uri = if (result.resultCode == RESULT_OK) {
            result.data?.data
        } else {
            null
        }
        androidExportLauncher.onExportDestinationSelected(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)

        // Set up FileKit for file operations
        FileKit.init(this)
        
        // Set up biometric gatekeeper
        (biometricGatekeeper as? AndroidBiometricGatekeeper)?.setActivity(this)
        
        // Register this activity for use by the export launcher
        activityProvider.currentActivity = this
        androidExportLauncher.setupActivityResultLauncher(createDocumentLauncher)
        androidExportLauncher.setupWorkObserver(this)
        
        // Set up multi-window support
        setupMultiWindowSupport()

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
                KoinContext {
                    MainActivityUiRoot(
                        appUiState = state,
                        onShowUnlockPrompt = viewModel::showNativeUnlockPrompt,
                    )
                }
            }
        }
        
        // Handle the intent if this activity was launched with one
        intent?.let { handleMultiWindowIntent(it) }
    }
    
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleMultiWindowIntent(intent)
    }
    
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Add multi-window options to the menu
        createMultiWindowMenuOptions(menu)
        return true
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle multi-window menu selections
        if (handleMultiWindowMenuSelection(item)) {
            return true
        }
        
        return super.onOptionsItemSelected(item)
    }
    
    override fun onResume() {
        super.onResume()
        activityProvider.currentActivity = this
    }
    
    override fun onPause() {
        super.onPause()
        if (activityProvider.currentActivity === this) {
            // Only clear the reference if it's still pointing to this activity
            activityProvider.currentActivity = null
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
    MainActivityUiRoot(
        appUiState = GlobalAppUiLoadedState(),
        onShowUnlockPrompt = { /* No-op for preview */ }
    )
}