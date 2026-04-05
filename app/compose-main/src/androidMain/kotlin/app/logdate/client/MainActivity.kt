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
import app.logdate.client.ambient.AMBIENT_PROMPT_TARGET_DRAFT
import app.logdate.client.ambient.AMBIENT_PROMPT_TARGET_MEMORY_RECALL
import app.logdate.client.ambient.AMBIENT_PROMPT_TARGET_NEW_ENTRY
import app.logdate.client.ambient.EXTRA_AMBIENT_PROMPT_DRAFT_ID
import app.logdate.client.ambient.EXTRA_AMBIENT_PROMPT_RECALL_DATE
import app.logdate.client.ambient.EXTRA_AMBIENT_PROMPT_TARGET
import app.logdate.client.database.DatabaseRecoveryController
import app.logdate.client.database.DatabaseStartupMonitor
import app.logdate.client.database.DatabaseStartupState
import app.logdate.client.datastore.SessionStorage
import app.logdate.client.device.restore.PostRestoreDetector
import app.logdate.client.device.restore.PostRestoreType
import app.logdate.client.domain.dayboundary.DayBoundarySettingsRepository
import app.logdate.client.domain.recommendation.MemoriesSettingsRepository
import app.logdate.client.feature.widgets.EXTRA_WIDGET_TARGET_DATE
import app.logdate.client.feature.widgets.NAV_SOURCE_ON_THIS_DAY_WIDGET
import app.logdate.client.launch.LaunchBootstrapState
import app.logdate.client.launch.LaunchStage
import app.logdate.client.launch.LaunchStageSnapshot
import app.logdate.client.launch.markCompleted
import app.logdate.client.launch.reduceLaunchBootstrapState
import app.logdate.client.location.settings.LocationTrackingSettingsRepository
import app.logdate.client.location.tracking.LocationTrackingManager
import app.logdate.client.location.tracking.NAV_SOURCE_LOCATION_HISTORY
import app.logdate.client.media.audio.EXTRA_NAV_SOURCE
import app.logdate.client.media.audio.EXTRA_NOTE_ID
import app.logdate.client.media.audio.NAV_SOURCE_AUDIO_PLAYBACK
import app.logdate.client.repository.profile.ProfileRepository
import app.logdate.client.repository.user.UserStateRepository
import app.logdate.client.sharing.NoOpSharingLauncher
import app.logdate.client.sharing.SharingLauncher
import app.logdate.client.testing.navigation.readNavigationTestDestination
import app.logdate.client.testing.onboarding.OnboardingTestFixtureApplier
import app.logdate.client.testing.onboarding.readOnboardingTestFixture
import app.logdate.client.updates.ActivityResultAppUpdateFlowLauncher
import app.logdate.client.updates.PlayInAppUpdateController
import app.logdate.client.watch.WatchCompanionAssociationManager
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
import app.logdate.feature.onboarding.flow.OnboardingDeviceStateRepository
import app.logdate.navigation.routes.core.EntryEditor
import app.logdate.navigation.routes.core.LocationRoute
import app.logdate.navigation.routes.core.NoteViewerRoute
import app.logdate.navigation.routes.core.TimelineDetail
import io.github.aakira.napier.Napier
import io.github.vinceglb.filekit.core.FileKit
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel
import kotlin.uuid.Uuid
import app.logdate.client.location.tracking.EXTRA_NAV_SOURCE as EXTRA_LOCATION_NAV_SOURCE

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
    private val postRestoreDetector: PostRestoreDetector by inject()
    private val playInAppUpdateController: PlayInAppUpdateController by inject()
    private val watchCompanionAssociationManager: WatchCompanionAssociationManager by inject()
    private val locationTrackingManager: LocationTrackingManager by inject()
    private val sharingLauncher: SharingLauncher by inject()
    private val profileRepository: ProfileRepository by inject()
    private val userStateRepository: UserStateRepository by inject()
    private val sessionStorage: SessionStorage by inject()
    private val memoriesSettingsRepository: MemoriesSettingsRepository by inject()
    private val locationTrackingSettingsRepository: LocationTrackingSettingsRepository by inject()
    private val dayBoundarySettingsRepository: DayBoundarySettingsRepository by inject()
    private val onboardingDeviceStateRepository: OnboardingDeviceStateRepository by inject()

    private val viewModel by viewModel<AppViewModel>()

    private var appUiState by mutableStateOf<GlobalAppUiState>(GlobalAppUiLoadingState)
    private var pendingNavKey by mutableStateOf<NavKey?>(null)
    private var databaseStartupState by mutableStateOf<DatabaseStartupState>(DatabaseStartupState.Ready)
    private var appUpdateUiState by mutableStateOf(AppUpdateUiState())
    private var launchSnapshot by mutableStateOf(LaunchStageSnapshot())
    private var hasCheckedForAppUpdates by mutableStateOf(false)
    private var postRestoreType by mutableStateOf(PostRestoreType.NONE)
    private var hasDetectedPostRestore by mutableStateOf(false)

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

    private val watchAssociationLauncher =
        registerForActivityResult(
            ActivityResultContracts.StartIntentSenderForResult(),
        ) { result ->
            watchCompanionAssociationManager.onAssociationFlowResult(result.resultCode)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        Napier.i("MainActivity onCreate: starting launch", tag = APP_LAUNCH_TAG)
        markLaunchStage(LaunchStage.ActivityCreated)
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            applyOnboardingTestFixtureFromLaunchIntent()
        }

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
        watchCompanionAssociationManager.attachLauncher(watchAssociationLauncher)
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
                                detectPostRestoreOnce(state)
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
                    onInitialNavigationReady = {
                        markLaunchStage(LaunchStage.InitialNavigationReady)
                        reportFullyDrawn()
                    },
                    databaseStartupState = databaseStartupState,
                    onResetEncryptedStorage = ::resetEncryptedStorageAndRestart,
                    isPostCloudRestore = postRestoreType == PostRestoreType.CLOUD_RESTORE,
                    onAcknowledgeCloudRestore = ::acknowledgeCloudRestore,
                    appUpdateUiState = appUpdateUiState,
                    onCompleteAppUpdate = {
                        lifecycleScope.launch {
                            playInAppUpdateController.completeUpdate()
                        }
                    },
                    sharingLauncher = sharingLauncher,
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

    private fun applyOnboardingTestFixtureFromLaunchIntent() {
        val fixture = intent?.readOnboardingTestFixture() ?: return
        runBlocking {
            OnboardingTestFixtureApplier(
                profileRepository = profileRepository,
                userStateRepository = userStateRepository,
                sessionStorage = sessionStorage,
                memoriesSettingsRepository = memoriesSettingsRepository,
                locationTrackingSettingsRepository = locationTrackingSettingsRepository,
                dayBoundarySettingsRepository = dayBoundarySettingsRepository,
                onboardingDeviceStateRepository = onboardingDeviceStateRepository,
            ).apply(fixture)
        }
        intent?.removeExtra(app.logdate.client.testing.onboarding.ONBOARDING_TEST_FIXTURE_EXTRA)
        Napier.i("Applied onboarding test fixture from launch intent", tag = APP_LAUNCH_TAG)
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
        locationTrackingManager.onActivityResumed()
        activityProvider.currentActivity = this
        lifecycleScope.launch {
            playInAppUpdateController.resumeIfUpdateInProgress()
        }
    }

    override fun onPause() {
        super.onPause()
        locationTrackingManager.onActivityPaused()
        viewModel.onAppBackgrounded()
        if (activityProvider.currentActivity === this) {
            // Only clear the reference if it's still pointing to this activity
            activityProvider.currentActivity = null
        }
    }

    /**
     * Runs post-restore detection exactly once per app launch.
     *
     * D2D transfers should now work transparently thanks to the passphrase backup
     * store: even when the KeyStore doesn't survive, DatabasePassphraseProvider
     * recovers the passphrase from the backup file and re-populates SecureStorage.
     *
     * If D2D is detected but the database STILL requires recovery (passphrase backup
     * was also missing — possible on very old installs that predate the backup store),
     * downgrade to cloud restore UX so the user sees the contextual empty state
     * instead of the blocking recovery dialog.
     */
    private fun detectPostRestoreOnce(state: GlobalAppUiLoadedState) {
        if (hasDetectedPostRestore) return
        hasDetectedPostRestore = true

        var detected = postRestoreDetector.detect(isOnboarded = state.isOnboarded)

        if (detected == PostRestoreType.DEVICE_TRANSFER &&
            databaseStartupState is DatabaseStartupState.RecoveryRequired
        ) {
            Napier.w(
                "D2D restore detected but database recovery required — " +
                    "passphrase backup was missing, treating as cloud restore",
                tag = APP_LAUNCH_TAG,
            )
            detected = PostRestoreType.CLOUD_RESTORE
        }

        postRestoreType = detected

        when (detected) {
            PostRestoreType.NONE -> {
                postRestoreDetector.markDeviceInitialized()
            }
            PostRestoreType.DEVICE_TRANSFER -> {
                Napier.i("D2D restore: database opened successfully, writing sentinel", tag = APP_LAUNCH_TAG)
                postRestoreDetector.markDeviceInitialized()
            }
            PostRestoreType.CLOUD_RESTORE -> {
                Napier.i("Cloud restore: showing contextual UI", tag = APP_LAUNCH_TAG)
            }
        }
    }

    private fun acknowledgeCloudRestore() {
        postRestoreDetector.markDeviceInitialized()
        postRestoreType = PostRestoreType.NONE
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

/** Resolves the optional deep-link destination from intent extras. */
private fun resolveNavKey(intent: Intent?): NavKey? {
    if (intent == null) return null
    intent.readNavigationTestDestination()?.let { return it }
    return when {
        intent.getStringExtra(EXTRA_NAV_SOURCE) == NAV_SOURCE_AUDIO_PLAYBACK -> {
            val noteId = intent.getStringExtra(EXTRA_NOTE_ID) ?: return null
            runCatching { NoteViewerRoute(Uuid.parse(noteId)) }.getOrNull()
        }

        intent.getStringExtra(EXTRA_NAV_SOURCE) == NAV_SOURCE_ON_THIS_DAY_WIDGET -> {
            val dateStr = intent.getStringExtra(EXTRA_WIDGET_TARGET_DATE) ?: return null
            runCatching { TimelineDetail(kotlinx.datetime.LocalDate.parse(dateStr)) }.getOrNull()
        }

        intent.getStringExtra(EXTRA_LOCATION_NAV_SOURCE) == NAV_SOURCE_LOCATION_HISTORY -> {
            LocationRoute
        }

        intent.getStringExtra(EXTRA_AMBIENT_PROMPT_TARGET) == AMBIENT_PROMPT_TARGET_NEW_ENTRY -> {
            EntryEditor()
        }

        intent.getStringExtra(EXTRA_AMBIENT_PROMPT_TARGET) == AMBIENT_PROMPT_TARGET_DRAFT -> {
            val draftId = intent.getStringExtra(EXTRA_AMBIENT_PROMPT_DRAFT_ID) ?: return null
            runCatching { EntryEditor(draftId = Uuid.parse(draftId)) }.getOrNull()
        }

        intent.getStringExtra(EXTRA_AMBIENT_PROMPT_TARGET) == AMBIENT_PROMPT_TARGET_MEMORY_RECALL -> {
            val dateStr = intent.getStringExtra(EXTRA_AMBIENT_PROMPT_RECALL_DATE) ?: return null
            runCatching { TimelineDetail(kotlinx.datetime.LocalDate.parse(dateStr)) }.getOrNull()
        }

        else -> null
    }
}

/** Compose preview for the Android activity root. */
@Preview
@Suppress("ktlint:standard:function-naming")
@Composable
fun AppAndroidPreview() {
    MainActivityUiRoot(
        appUiState = GlobalAppUiLoadedState(),
        onShowUnlockPrompt = { /* No-op for preview */ },
        sharingLauncher = NoOpSharingLauncher,
    )
}

private const val APP_LAUNCH_TAG = "LogDateAppLaunch"
private const val LAUNCH_WATCHDOG_TIMEOUT_MS = 750L
