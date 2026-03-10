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
import androidx.navigation3.runtime.NavKey
import app.logdate.client.database.DatabaseRecoveryController
import app.logdate.client.database.DatabaseStartupMonitor
import app.logdate.client.database.DatabaseStartupState
import app.logdate.client.launch.LaunchBootstrapState
import app.logdate.client.launch.LaunchStage
import app.logdate.client.launch.LaunchStageSnapshot
import app.logdate.client.launch.markCompleted
import app.logdate.client.launch.reduceLaunchBootstrapState
import app.logdate.client.media.audio.EXTRA_NAV_SOURCE
import app.logdate.client.media.audio.EXTRA_NOTE_ID
import app.logdate.client.media.audio.NAV_SOURCE_AUDIO_PLAYBACK
import app.logdate.client.updates.ActivityResultAppUpdateFlowLauncher
import app.logdate.client.updates.PlayInAppUpdateController
import app.logdate.feature.core.AndroidBiometricGatekeeper
import app.logdate.feature.core.AppViewModel
import app.logdate.feature.core.BiometricGatekeeper
import app.logdate.feature.core.GlobalAppUiLoadedState
import app.logdate.feature.core.GlobalAppUiLoadingState
import app.logdate.feature.core.GlobalAppUiState
import app.logdate.feature.core.di.ActivityProvider
import app.logdate.feature.core.export.AndroidExportLauncher
import app.logdate.feature.core.isAppUnlocked
import app.logdate.feature.core.restore.AndroidRestoreLauncher
import app.logdate.feature.core.settings.updates.AppUpdateCheckTrigger
import app.logdate.feature.core.settings.updates.AppUpdateUiState
import app.logdate.navigation.routes.core.NoteViewerRoute
import io.github.aakira.napier.Napier
import io.github.vinceglb.filekit.core.FileKit
import kotlinx.coroutines.delay
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
    private val playInAppUpdateController: PlayInAppUpdateController by inject()

    private val viewModel by viewModel<AppViewModel>()

    private var appUiState by mutableStateOf<GlobalAppUiState>(GlobalAppUiLoadingState)
    private var pendingNavKey by mutableStateOf<NavKey?>(null)
    private var databaseStartupState by mutableStateOf<DatabaseStartupState>(DatabaseStartupState.Ready)
    private var appUpdateUiState by mutableStateOf(AppUpdateUiState())
    private var launchSnapshot by mutableStateOf(LaunchStageSnapshot())
    private var hasCheckedForAppUpdates by mutableStateOf(false)

    // Register the document picker for export functionality
    private val createDocumentLauncher =
        registerForActivityResult(
            ActivityResultContracts.StartActivityForResult(),
        ) { result ->
            val uri =
                if (result.resultCode == RESULT_OK) {
                    result.data?.data
                } else {
                    null
                }
            androidExportLauncher.onExportDestinationSelected(uri)
        }

    private val openDocumentLauncher =
        registerForActivityResult(
            ActivityResultContracts.StartActivityForResult(),
        ) { result ->
            val uri =
                if (result.resultCode == RESULT_OK) {
                    result.data?.data
                } else {
                    null
                }
            androidRestoreLauncher.onRestoreSourceSelected(uri)
        }

    // Bridges Play Core's update UI back into the controller through the Activity Result API.
    private val appUpdateLauncher =
        registerForActivityResult(
            ActivityResultContracts.StartIntentSenderForResult(),
        ) { result ->
            playInAppUpdateController.onUpdateFlowResult(result.resultCode)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        Napier.i("MainActivity onCreate: starting launch", tag = APP_LAUNCH_TAG)
        markLaunchStage(LaunchStage.ActivityCreated)
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)

        // Set up FileKit for file operations
        FileKit.init(this)
        Napier.i("MainActivity onCreate: FileKit initialized", tag = APP_LAUNCH_TAG)

        // Set up biometric gatekeeper
        (biometricGatekeeper as? AndroidBiometricGatekeeper)?.setActivity(this)
        Napier.i("MainActivity onCreate: biometric gatekeeper configured", tag = APP_LAUNCH_TAG)

        // Register this activity for use by the export launcher
        activityProvider.currentActivity = this
        androidExportLauncher.setupActivityResultLauncher(createDocumentLauncher)
        androidExportLauncher.setupWorkObserver(this)
        androidRestoreLauncher.setupActivityResultLauncher(openDocumentLauncher)
        androidRestoreLauncher.setupWorkObserver(this)
        playInAppUpdateController.attachLauncher(ActivityResultAppUpdateFlowLauncher(appUpdateLauncher))
        Napier.i("MainActivity onCreate: export/restore/update launchers configured", tag = APP_LAUNCH_TAG)

        // Set up multi-window support
        setupMultiWindowSupport()
        Napier.i("MainActivity onCreate: multi-window support configured", tag = APP_LAUNCH_TAG)

        lifecycleScope.launch {
            delay(LAUNCH_WATCHDOG_TIMEOUT_MS)
            launchSnapshot = launchSnapshot.copy(hasWatchdogExpired = true)
            val launchState = reduceLaunchBootstrapState(launchSnapshot)
            if (launchState is LaunchBootstrapState.SplashReleased) {
                Napier.w(
                    "MainActivity launch watchdog expired after ${LAUNCH_WATCHDOG_TIMEOUT_MS}ms; " +
                        "releasing splash while waiting for ${LaunchStage.AppUiLoaded.analyticsName}; " +
                        "last completed stage=${launchSnapshot.latestCompletedStage.analyticsName}",
                    tag = APP_LAUNCH_TAG,
                )
            }
        }

        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState
                        .onEach { state ->
                            appUiState = state
                            if (state is GlobalAppUiLoadedState) {
                                markLaunchStage(LaunchStage.AppUiLoaded)
                            }
                            maybeCheckForUpdates(state)
                        }.collect {}
                }
                launch {
                    databaseStartupMonitor.state
                        .onEach { state ->
                            databaseStartupState = state
                            markLaunchStage(LaunchStage.DatabaseStateObserved)
                            if (state is DatabaseStartupState.RecoveryRequired) {
                                Napier.w(
                                    "MainActivity launch gate: recovery required (${state.reason})",
                                    tag = APP_LAUNCH_TAG,
                                )
                            } else {
                                Napier.i("MainActivity launch gate: database state ready", tag = APP_LAUNCH_TAG)
                            }
                        }.collect {}
                }
                launch {
                    playInAppUpdateController.uiState
                        .onEach { state -> appUpdateUiState = state }
                        .collect {}
                }
            }
        }

        splashScreen.setKeepOnScreenCondition {
            currentLaunchBootstrapState() is LaunchBootstrapState.BlockingSplash
        }

        enableEdgeToEdge()
        pendingNavKey = resolveNavKey(intent)

        setContent {
            val state = appUiState as? GlobalAppUiLoadedState
            if (state != null) {
                MainActivityUiRoot(
                    appUiState = state,
                    onShowUnlockPrompt = viewModel::showNativeUnlockPrompt,
                    pendingNavKey = pendingNavKey,
                    onDeepLinkHandled = { pendingNavKey = null },
                    onInitialNavigationReady = { markLaunchStage(LaunchStage.InitialNavigationReady) },
                    databaseStartupState = databaseStartupState,
                    onResetEncryptedStorage = ::resetEncryptedStorageAndRestart,
                    appUpdateUiState = appUpdateUiState,
                    onCompleteAppUpdate = {
                        lifecycleScope.launch {
                            playInAppUpdateController.completeUpdate()
                        }
                    },
                )
            } else {
                MainActivityLoadingRoot()
            }
        }
        markLaunchStage(LaunchStage.ComposeAttached)
        Napier.i("MainActivity onCreate: Compose content attached", tag = APP_LAUNCH_TAG)

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
        lifecycleScope.launch {
            playInAppUpdateController.resumeIfUpdateInProgress()
        }
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
            Napier.w("Recovery action: user requested encrypted storage reset", tag = APP_LAUNCH_TAG)
            databaseRecoveryController
                .quarantineAndResetEncryptedStorage()
                .onSuccess { backup ->
                    Napier.w(
                        "Recovery action: reset complete, backup preserved at ${backup.absolutePath}",
                        tag = APP_LAUNCH_TAG,
                    )
                    restartApplicationProcess()
                }.onFailure { error ->
                    Napier.e("Recovery action: failed to reset encrypted storage", error, tag = APP_LAUNCH_TAG)
                }
        }
    }

    private fun restartApplicationProcess() {
        val launchIntent =
            packageManager.getLaunchIntentForPackage(packageName)?.apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
        if (launchIntent != null) {
            startActivity(launchIntent)
        }
        finishAffinity()
        Runtime.getRuntime().exit(0)
    }

    /**
     * Starts one automatic Play update check after the app is actually usable.
     *
     * This prevents the update flow from competing with onboarding or the biometric unlock gate.
     */
    private fun maybeCheckForUpdates(state: GlobalAppUiState) {
        val loadedState = state as? GlobalAppUiLoadedState ?: return
        if (hasCheckedForAppUpdates) {
            return
        }
        if (!loadedState.isOnboarded || !loadedState.isAppUnlocked) {
            return
        }

        hasCheckedForAppUpdates = true
        lifecycleScope.launch {
            playInAppUpdateController.checkForUpdates(AppUpdateCheckTrigger.Automatic)
        }
    }

    private fun currentLaunchBootstrapState(): LaunchBootstrapState = reduceLaunchBootstrapState(launchSnapshot)

    private fun markLaunchStage(stage: LaunchStage) {
        val updatedSnapshot = launchSnapshot.markCompleted(stage)
        if (updatedSnapshot == launchSnapshot) {
            return
        }
        launchSnapshot = updatedSnapshot
        Napier.i("MainActivity launch stage: ${stage.analyticsName}", tag = APP_LAUNCH_TAG)
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

/** Resolves the optional deep-link destination used when audio playback re-enters the app. */
private fun resolveNavKey(intent: Intent?): NavKey? {
    if (intent == null) return null
    if (intent.getStringExtra(EXTRA_NAV_SOURCE) != NAV_SOURCE_AUDIO_PLAYBACK) return null
    val noteId = intent.getStringExtra(EXTRA_NOTE_ID) ?: return null
    return runCatching { NoteViewerRoute(Uuid.parse(noteId)) }.getOrNull()
}

/** Compose preview for the Android activity root. */
@Preview
@Suppress("ktlint:standard:function-naming")
@Composable
fun AppAndroidPreview() {
    MainActivityUiRoot(
        appUiState = GlobalAppUiLoadedState(),
        onShowUnlockPrompt = { /* No-op for preview */ },
    )
}

private const val APP_LAUNCH_TAG = "LogDateAppLaunch"
private const val LAUNCH_WATCHDOG_TIMEOUT_MS = 750L
