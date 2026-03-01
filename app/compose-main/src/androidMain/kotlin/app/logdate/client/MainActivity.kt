package app.logdate.client

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.navigation3.runtime.NavKey
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import app.logdate.client.database.DatabaseRecoveryController
import app.logdate.client.database.DatabaseStartupMonitor
import app.logdate.client.database.DatabaseStartupState
import app.logdate.feature.core.AndroidBiometricGatekeeper
import app.logdate.feature.core.AppViewModel
import app.logdate.feature.core.BiometricGatekeeper
import app.logdate.feature.core.GlobalAppUiLoadedState
import app.logdate.feature.core.GlobalAppUiLoadingState
import app.logdate.feature.core.GlobalAppUiState
import app.logdate.feature.core.di.ActivityProvider
import app.logdate.feature.core.export.AndroidExportLauncher
import app.logdate.feature.core.restore.AndroidRestoreLauncher
import app.logdate.client.media.audio.EXTRA_NOTE_ID
import app.logdate.client.media.audio.EXTRA_NAV_SOURCE
import app.logdate.client.media.audio.NAV_SOURCE_AUDIO_PLAYBACK
import app.logdate.navigation.routes.core.NoteViewerRoute
import io.github.vinceglb.filekit.core.FileKit
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel
import kotlin.uuid.Uuid

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
    private val androidRestoreLauncher: AndroidRestoreLauncher by inject()
    private val databaseStartupMonitor: DatabaseStartupMonitor by inject()
    private val databaseRecoveryController: DatabaseRecoveryController by inject()

    private val viewModel by viewModel<AppViewModel>()

    private var pendingNavKey by mutableStateOf<NavKey?>(null)
    private var databaseStartupState by mutableStateOf<DatabaseStartupState>(DatabaseStartupState.Ready)
    
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

    private val openDocumentLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uri = if (result.resultCode == RESULT_OK) {
            result.data?.data
        } else {
            null
        }
        androidRestoreLauncher.onRestoreSourceSelected(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.i(APP_LAUNCH_TAG, "MainActivity onCreate: starting launch")
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)

        // Set up FileKit for file operations
        FileKit.init(this)
        Log.i(APP_LAUNCH_TAG, "MainActivity onCreate: FileKit initialized")
        
        // Set up biometric gatekeeper
        (biometricGatekeeper as? AndroidBiometricGatekeeper)?.setActivity(this)
        Log.i(APP_LAUNCH_TAG, "MainActivity onCreate: biometric gatekeeper configured")
        
        // Register this activity for use by the export launcher
        activityProvider.currentActivity = this
        androidExportLauncher.setupActivityResultLauncher(createDocumentLauncher)
        androidExportLauncher.setupWorkObserver(this)
        androidRestoreLauncher.setupActivityResultLauncher(openDocumentLauncher)
        androidRestoreLauncher.setupWorkObserver(this)
        Log.i(APP_LAUNCH_TAG, "MainActivity onCreate: export/restore launchers configured")
        
        // Set up multi-window support
        setupMultiWindowSupport()
        Log.i(APP_LAUNCH_TAG, "MainActivity onCreate: multi-window support configured")

        // TODO: Maybe reconsider sealed class approach to uiState loading
        var uiState: GlobalAppUiState by mutableStateOf(GlobalAppUiLoadingState)

        // Update the uiState
        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState
                        .onEach { uiState = it }
                        .collect {}
                }
                launch {
                    databaseStartupMonitor.state
                        .onEach { state ->
                            databaseStartupState = state
                            if (state is DatabaseStartupState.RecoveryRequired) {
                                Log.w(
                                    APP_LAUNCH_TAG,
                                    "MainActivity launch gate: recovery required (${state.reason})",
                                )
                            } else {
                                Log.i(APP_LAUNCH_TAG, "MainActivity launch gate: database state ready")
                            }
                        }
                        .collect {}
                }
            }
        }

        splashScreen.setKeepOnScreenCondition {
            uiState is GlobalAppUiLoadingState
        }

        enableEdgeToEdge()
        pendingNavKey = resolveNavKey(intent)

        setContent {
            val state = uiState
            if (state is GlobalAppUiLoadedState) {
                MainActivityUiRoot(
                    appUiState = state,
                    onShowUnlockPrompt = viewModel::showNativeUnlockPrompt,
                    pendingNavKey = pendingNavKey,
                    onDeepLinkHandled = { pendingNavKey = null },
                    databaseStartupState = databaseStartupState,
                    onResetEncryptedStorage = ::resetEncryptedStorageAndRestart,
                )
            }
        }
        Log.i(APP_LAUNCH_TAG, "MainActivity onCreate: Compose content attached")
        
        // Handle the intent if this activity was launched with one
        intent?.let { handleMultiWindowIntent(it) }
    }
    
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleMultiWindowIntent(intent)
        resolveNavKey(intent)?.let { pendingNavKey = it }
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
        viewModel.onAppBackgrounded()
        if (activityProvider.currentActivity === this) {
            // Only clear the reference if it's still pointing to this activity
            activityProvider.currentActivity = null
        }
    }

    private fun resetEncryptedStorageAndRestart() {
        lifecycleScope.launch {
            Log.w(APP_LAUNCH_TAG, "Recovery action: user requested encrypted storage reset")
            databaseRecoveryController.quarantineAndResetEncryptedStorage()
                .onSuccess { backup ->
                    Log.w(
                        APP_LAUNCH_TAG,
                        "Recovery action: reset complete, backup preserved at ${backup.absolutePath}",
                    )
                    restartApplicationProcess()
                }
                .onFailure { error ->
                    Log.e(APP_LAUNCH_TAG, "Recovery action: failed to reset encrypted storage", error)
                }
        }
    }

    private fun restartApplicationProcess() {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        if (launchIntent != null) {
            startActivity(launchIntent)
        }
        finishAffinity()
        Runtime.getRuntime().exit(0)
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

private fun resolveNavKey(intent: Intent?): NavKey? {
    if (intent == null) return null
    if (intent.getStringExtra(EXTRA_NAV_SOURCE) != NAV_SOURCE_AUDIO_PLAYBACK) return null
    val noteId = intent.getStringExtra(EXTRA_NOTE_ID) ?: return null
    return runCatching { NoteViewerRoute(Uuid.parse(noteId)) }.getOrNull()
}

@Preview
@Composable
fun AppAndroidPreview() {
    MainActivityUiRoot(
        appUiState = GlobalAppUiLoadedState(),
        onShowUnlockPrompt = { /* No-op for preview */ }
    )
}

private const val APP_LAUNCH_TAG = "LogDateAppLaunch"
