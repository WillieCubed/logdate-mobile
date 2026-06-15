package app.logdate.client

import android.annotation.SuppressLint
import android.app.HandoffActivityData
import android.app.HandoffActivityDataRequestInfo
import android.app.HandoffActivityParams
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import app.logdate.client.ambient.AMBIENT_PROMPT_TARGET_EVENT_DETAIL
import app.logdate.client.ambient.AMBIENT_PROMPT_TARGET_MEMORY_RECALL
import app.logdate.client.ambient.AMBIENT_PROMPT_TARGET_NEW_ENTRY
import app.logdate.client.ambient.EXTRA_AMBIENT_PROMPT_DRAFT_ID
import app.logdate.client.ambient.EXTRA_AMBIENT_PROMPT_EVENT_ID
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
import app.logdate.client.rewind.EXTRA_REWIND_NOTIFICATION_ID
import app.logdate.client.rewind.EXTRA_REWIND_NOTIFICATION_TARGET
import app.logdate.client.rewind.REWIND_NOTIFICATION_TARGET_DETAIL
import app.logdate.client.testing.navigation.readNavigationTestDestination
import app.logdate.client.testing.onboarding.OnboardingTestFixtureApplier
import app.logdate.client.testing.onboarding.readOnboardingTestFixture
import app.logdate.client.ui.navigation.LocationTimelineRoute
import app.logdate.client.updates.ActivityResultAppUpdateFlowLauncher
import app.logdate.client.updates.PlayInAppUpdateController
import app.logdate.client.watch.WatchCompanionAssociationManager
import app.logdate.feature.core.AndroidBiometricGatekeeper
import app.logdate.feature.core.AppViewModel
import app.logdate.feature.core.BiometricGatekeeper
import app.logdate.feature.core.GlobalAppUiLoadedState
import app.logdate.feature.core.GlobalAppUiLoadingState
import app.logdate.feature.core.GlobalAppUiState
import app.logdate.feature.core.notifications.EXTRA_NAV_SOURCE as EXTRA_DATA_TRANSFER_NAV_SOURCE
import app.logdate.feature.core.notifications.NAV_SOURCE_DATA_TRANSFER
import app.logdate.feature.core.di.ActivityProvider
import app.logdate.feature.core.export.AndroidExportLauncher
import app.logdate.feature.core.isAppUnlocked
import app.logdate.feature.core.restore.AndroidRestoreLauncher
import app.logdate.feature.core.settings.navigation.ExportSettingsRoute
import app.logdate.feature.core.settings.updates.AppUpdateCheckTrigger
import app.logdate.feature.editor.navigation.EntryEditorRoute
import app.logdate.feature.events.navigation.EventDetailRoute
import app.logdate.feature.onboarding.flow.OnboardingDeviceStateRepository
import app.logdate.feature.rewind.navigation.RewindDetailRoute
import app.logdate.navigation.LogDateNavDisplay
import app.logdate.navigation.TimelineDetailRoute
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
 * Bridges Android lifecycle and platform launchers (export, restore, app updates, watch
 * association, biometric gatekeeper, multi-window editor windows, deep links, and the
 * Android 16+ handoff API) into the shared [LogDateNavDisplay] graph.
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
    private var currentNavKey by mutableStateOf<NavKey?>(null)
    private var databaseStartupState by mutableStateOf<DatabaseStartupState>(DatabaseStartupState.Ready)
    private var launchSnapshot by mutableStateOf(LaunchStageSnapshot())
    private var hasCheckedForAppUpdates by mutableStateOf(false)
    private var hasDetectedPostRestore by mutableStateOf(false)

    private val createDocumentLauncher =
        registerForActivityResult(
            ActivityResultContracts.StartActivityForResult(),
        ) { result ->
            val uri = if (result.resultCode == RESULT_OK) result.data?.data else null
            androidExportLauncher.onExportDestinationSelected(uri)
        }

    private val openDocumentLauncher =
        registerForActivityResult(
            ActivityResultContracts.StartActivityForResult(),
        ) { result ->
            val uri = if (result.resultCode == RESULT_OK) result.data?.data else null
            androidRestoreLauncher.onRestoreSourceSelected(uri)
        }

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

        FileKit.init(this)
        Napier.i("MainActivity onCreate: FileKit initialized", tag = APP_LAUNCH_TAG)

        (biometricGatekeeper as? AndroidBiometricGatekeeper)?.setActivity(this)
        Napier.i("MainActivity onCreate: biometric gatekeeper configured", tag = APP_LAUNCH_TAG)

        activityProvider.currentActivity = this
        androidExportLauncher.setupActivityResultLauncher(createDocumentLauncher)
        androidExportLauncher.setupWorkObserver(this)
        androidRestoreLauncher.setupActivityResultLauncher(openDocumentLauncher)
        androidRestoreLauncher.setupWorkObserver(this)
        playInAppUpdateController.attachLauncher(ActivityResultAppUpdateFlowLauncher(appUpdateLauncher))
        watchCompanionAssociationManager.attachLauncher(watchAssociationLauncher)
        Napier.i("MainActivity onCreate: export/restore/update launchers configured", tag = APP_LAUNCH_TAG)

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
            }
        }

        splashScreen.setKeepOnScreenCondition {
            currentLaunchBootstrapState() is LaunchBootstrapState.BlockingSplash
        }

        enableEdgeToEdge()

        if (Build.VERSION.SDK_INT >= 37) {
            // TODO: Enable web handoff once logdate.app can reconstruct app state from the URL.
            //  Flip setAllowHandoffWithoutPackageInstalled to true and verify each deep-link path
            //  renders the correct content on the web before enabling.
            val handoffParams =
                HandoffActivityParams
                    .Builder()
                    .setAllowHandoffWithoutPackageInstalled(false)
                    .build()
            setHandoffEnabled(true, handoffParams)
        }

        pendingNavKey = resolveMainActivityNavKey(intent)

        setContent {
            val state = appUiState as? GlobalAppUiLoadedState
            if (state != null) {
                androidx.compose.foundation.layout.Box(
                    modifier =
                        androidx.compose.ui.Modifier
                            .fillMaxSize(),
                ) {
                    LogDateNavDisplay(
                        appUiState = state,
                        onShowUnlockPrompt = viewModel::showNativeUnlockPrompt,
                        pendingNavKey = pendingNavKey,
                        onPendingNavKeyConsumed = { pendingNavKey = null },
                        onCurrentNavKeyChanged = { currentNavKey = it },
                        onShareSearchResult = ::shareSearchResult,
                    )
                    val updateState by playInAppUpdateController.uiState.collectAsState()
                    app.logdate.feature.core.settings.updates.AppUpdatePrompt(
                        uiState = updateState,
                        onLaunchUpdate = {
                            lifecycleScope.launch {
                                playInAppUpdateController.checkForUpdates(AppUpdateCheckTrigger.Manual)
                            }
                        },
                        onCompleteUpdate = {
                            lifecycleScope.launch {
                                playInAppUpdateController.completeUpdate()
                            }
                        },
                        modifier =
                            androidx.compose.ui.Modifier
                                .align(androidx.compose.ui.Alignment.TopCenter)
                                .windowInsetsPadding(WindowInsets.statusBars),
                    )
                }
            }
        }
        markLaunchStage(LaunchStage.ComposeAttached)
        Napier.i("MainActivity onCreate: Compose content attached", tag = APP_LAUNCH_TAG)

        if (intent?.let { handleMultiWindowIntent(it) } == true) {
            return
        }
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
        if (handleMultiWindowIntent(intent)) {
            return
        }
        resolveMainActivityNavKey(intent)?.let { pendingNavKey = it }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        createMultiWindowMenuOptions(menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (handleMultiWindowMenuSelection(item)) {
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    @SuppressLint("NewApi")
    override fun onHandoffActivityDataRequested(info: HandoffActivityDataRequestInfo): HandoffActivityData? {
        val url = currentNavKey?.toWebUrl() ?: return null
        return HandoffActivityData
            .Builder(ComponentName(this, MainActivity::class.java))
            .setFallbackUri(Uri.parse(url))
            .build()
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
            activityProvider.currentActivity = null
        }
    }

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

        when (detected) {
            PostRestoreType.NONE -> postRestoreDetector.markDeviceInitialized()
            PostRestoreType.DEVICE_TRANSFER -> {
                Napier.i("D2D restore: database opened successfully, writing sentinel", tag = APP_LAUNCH_TAG)
                postRestoreDetector.markDeviceInitialized()
            }
            PostRestoreType.CLOUD_RESTORE -> {
                Napier.i("Cloud restore: showing contextual UI", tag = APP_LAUNCH_TAG)
                viewModel.tryRestoreSignInAfterCloudRestore()
            }
        }
    }

    private fun maybeCheckForUpdates(state: GlobalAppUiState) {
        val loadedState = state as? GlobalAppUiLoadedState ?: return
        if (hasCheckedForAppUpdates) return
        if (!loadedState.isOnboarded || !loadedState.isAppUnlocked) return

        hasCheckedForAppUpdates = true
        lifecycleScope.launch {
            playInAppUpdateController.checkForUpdates(AppUpdateCheckTrigger.Automatic)
        }
    }

    private fun currentLaunchBootstrapState(): LaunchBootstrapState = reduceLaunchBootstrapState(launchSnapshot)

    /**
     * Builds and dispatches an `ACTION_SEND` chooser for a search result. Wired into
     * [LogDateNavDisplay]'s `onShareSearchResult` so the long-press / right-click bottom sheet
     * surfaces a Share action on Android. Other platforms leave the parameter null and the
     * action is hidden from the sheet.
     */
    private fun shareSearchResult(result: app.logdate.client.repository.search.SearchResult) {
        val intent =
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                val snippet = result.content.replace("[", "").replace("]", "")
                val url = canonicalSearchResultUrl(result)
                val body =
                    if (url != null) {
                        if (snippet.isBlank()) url else "$snippet\n\n$url"
                    } else {
                        snippet
                    }
                putExtra(Intent.EXTRA_TEXT, body)
            }
        startActivity(Intent.createChooser(intent, null))
    }

    private fun canonicalSearchResultUrl(result: app.logdate.client.repository.search.SearchResult): String? {
        val origin = BuildConfig.LOGDATE_API_BASE_URL.removeSuffix("/")
        return when (result.contentType) {
            app.logdate.client.repository.search.SearchContentType.JOURNAL -> "$origin/journal/${result.uid}"
            app.logdate.client.repository.search.SearchContentType.TEXT_NOTE -> "$origin/note/${result.uid}"
            app.logdate.client.repository.search.SearchContentType.POSTCARD -> "$origin/postcard/${result.uid}"
            app.logdate.client.repository.search.SearchContentType.REWIND -> "$origin/rewind/${result.uid}"
            else -> null
        }
    }

    private fun markLaunchStage(stage: LaunchStage) {
        val updatedSnapshot = launchSnapshot.markCompleted(stage)
        if (updatedSnapshot == launchSnapshot) return
        launchSnapshot = updatedSnapshot
        Napier.i("MainActivity launch stage: ${stage.analyticsName}", tag = APP_LAUNCH_TAG)
    }
}

/**
 * Resolves the optional launch destination from the activity intent.
 *
 * This is the single resolver used by `MainActivity` for deep links, notification taps,
 * widget launches, ambient prompts, and the Android 16 handoff fallback URI.
 */
fun resolveMainActivityNavKey(intent: Intent?): NavKey? {
    if (intent == null) return null
    intent.readNavigationTestDestination()?.let { return it }
    return when {
        intent.getStringExtra(EXTRA_NAV_SOURCE) == NAV_SOURCE_AUDIO_PLAYBACK -> {
            val noteId = intent.getStringExtra(EXTRA_NOTE_ID) ?: return null
            runCatching {
                app.logdate.feature.journals.navigation
                    .NoteDetailRoute(Uuid.parse(noteId))
            }.getOrNull()
        }

        intent.getStringExtra(EXTRA_NAV_SOURCE) == NAV_SOURCE_ON_THIS_DAY_WIDGET -> {
            val dateStr = intent.getStringExtra(EXTRA_WIDGET_TARGET_DATE) ?: return null
            runCatching {
                kotlinx.datetime.LocalDate.parse(dateStr)
                TimelineDetailRoute(dateStr)
            }.getOrNull()
        }

        intent.getStringExtra(EXTRA_LOCATION_NAV_SOURCE) == NAV_SOURCE_LOCATION_HISTORY -> {
            LocationTimelineRoute
        }

        intent.getStringExtra(EXTRA_AMBIENT_PROMPT_TARGET) == AMBIENT_PROMPT_TARGET_NEW_ENTRY -> {
            EntryEditorRoute()
        }

        intent.getStringExtra(EXTRA_AMBIENT_PROMPT_TARGET) == AMBIENT_PROMPT_TARGET_DRAFT -> {
            val draftId = intent.getStringExtra(EXTRA_AMBIENT_PROMPT_DRAFT_ID) ?: return null
            runCatching { EntryEditorRoute(draftId = Uuid.parse(draftId).toString()) }.getOrNull()
        }

        intent.getStringExtra(EXTRA_AMBIENT_PROMPT_TARGET) == AMBIENT_PROMPT_TARGET_MEMORY_RECALL -> {
            val dateStr = intent.getStringExtra(EXTRA_AMBIENT_PROMPT_RECALL_DATE) ?: return null
            runCatching {
                kotlinx.datetime.LocalDate.parse(dateStr)
                TimelineDetailRoute(dateStr)
            }.getOrNull()
        }

        intent.getStringExtra(EXTRA_AMBIENT_PROMPT_TARGET) == AMBIENT_PROMPT_TARGET_EVENT_DETAIL -> {
            val eventId = intent.getStringExtra(EXTRA_AMBIENT_PROMPT_EVENT_ID) ?: return null
            runCatching { EventDetailRoute(eventId) }.getOrNull()
        }

        intent.getStringExtra(EXTRA_REWIND_NOTIFICATION_TARGET) == REWIND_NOTIFICATION_TARGET_DETAIL -> {
            val rewindId = intent.getStringExtra(EXTRA_REWIND_NOTIFICATION_ID) ?: return null
            runCatching { RewindDetailRoute(Uuid.parse(rewindId)) }.getOrNull()
        }

        intent.getStringExtra(EXTRA_DATA_TRANSFER_NAV_SOURCE) == NAV_SOURCE_DATA_TRANSFER -> {
            ExportSettingsRoute
        }

        // Deep link URIs: logdate://journal/{id}, logdate://day/{date}, etc.
        intent.data != null -> resolveDeepLinkUri(intent.data!!)

        else -> null
    }
}

@Preview
@Suppress("ktlint:standard:function-naming")
@Composable
fun AppAndroidPreview() {
    LogDateNavDisplay(
        appUiState = GlobalAppUiLoadedState(),
        onShowUnlockPrompt = { },
    )
}

private const val APP_LAUNCH_TAG = "LogDateAppLaunch"
private const val LAUNCH_WATCHDOG_TIMEOUT_MS = 750L
