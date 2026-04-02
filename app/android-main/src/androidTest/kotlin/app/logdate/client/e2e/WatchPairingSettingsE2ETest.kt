package app.logdate.client.e2e

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.logdate.client.MainActivity
import app.logdate.client.domain.watch.WatchNotificationSettings
import app.logdate.client.domain.watch.WatchSettingsRepository
import app.logdate.client.domain.watch.WatchSyncSettings
import app.logdate.client.testing.launch.ActivityLaunchTestOverrides
import app.logdate.client.testing.navigation.NavigationTestDestination
import app.logdate.client.testing.onboarding.OnboardingTestFixture
import app.logdate.feature.core.settings.ui.watch.WatchConnectionManager
import app.logdate.feature.core.settings.ui.watch.WatchConnectionState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.Description
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.junit.runners.model.Statement
import org.koin.core.module.Module
import org.koin.dsl.module
import kotlin.test.assertEquals

/**
 * Instrumented integration coverage for the phone-side watch settings flow.
 *
 * These tests exercise the real [MainActivity], navigation graph, DI wiring,
 * shared watch ViewModel, and Compose UI. The actual Companion Device chooser
 * remains outside test control, so the watch services are replaced with fakes.
 */
@RunWith(AndroidJUnit4::class)
class WatchPairingSettingsE2ETest {
    private val fakeConnectionManager = RecordingWatchConnectionManager()
    private val fakeSettingsRepository = FakeWatchSettingsRepository()

    private val overrideModule: Module =
        module {
            single<WatchConnectionManager> { fakeConnectionManager }
            single<WatchSettingsRepository> { fakeSettingsRepository }
        }

    private val koinRule = OnboardingKoinModuleOverrideRule(overrideModule)
    private val launchOverrideRule =
        WatchLaunchOverrideRule(
            onboardingFixture = OnboardingTestFixture.ONBOARDED_HOME,
            navigationDestination = NavigationTestDestination.WatchSettings,
        )
    private val composeRule = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val ruleChain: RuleChain = RuleChain.outerRule(koinRule).around(launchOverrideRule).around(composeRule)

    @Before
    fun resetFakes() {
        fakeConnectionManager.reset()
        fakeSettingsRepository.reset()
    }

    @Test
    fun watchSettings_pairWatchActionUsesConnectionManager() {
        fakeConnectionManager.updateState(WatchConnectionState.NeedsAssociation("Pixel Watch"))

        composeRule.onNodeWithText("Pair LogDate with your watch").assertIsDisplayed()
        composeRule.onNodeWithText("Pair Watch").performClick()

        assertEquals(1, fakeConnectionManager.beginAssociationCalls)
    }

    @Test
    fun watchSettings_pendingAssociationStateIsRendered() {
        fakeConnectionManager.updateState(WatchConnectionState.AssociationPending)

        composeRule.onNodeWithText("Finish pairing on this phone").assertIsDisplayed()
        composeRule.onNodeWithText(
            "Complete the system pairing prompt, then LogDate will reconnect to your watch automatically.",
        ).assertIsDisplayed()
    }

    @Test
    fun troubleshooting_installAndOpenActionsUseConnectionManager() {
        fakeConnectionManager.updateState(
            WatchConnectionState.Connected(
                watchName = "Pixel Watch",
                lastSynced = null,
                pendingCount = 0,
            ),
        )

        composeRule.onNodeWithText("Troubleshooting").performClick()
        composeRule.onNodeWithText("Install or update on watch").performClick()
        composeRule.onNodeWithText("Open LogDate on watch").performClick()

        assertEquals(1, fakeConnectionManager.installCalls)
        assertEquals(1, fakeConnectionManager.openCalls)
    }
}

private class WatchLaunchOverrideRule(
    private val onboardingFixture: OnboardingTestFixture,
    private val navigationDestination: NavigationTestDestination,
) : TestRule {
    override fun apply(
        base: Statement,
        description: Description,
    ): Statement =
        object : Statement() {
            override fun evaluate() {
                ActivityLaunchTestOverrides.onboardingFixture = onboardingFixture
                ActivityLaunchTestOverrides.navigationDestination = navigationDestination
                try {
                    base.evaluate()
                } finally {
                    ActivityLaunchTestOverrides.clear()
                }
            }
        }
}

private class RecordingWatchConnectionManager : WatchConnectionManager {
    private val state = MutableStateFlow<WatchConnectionState>(WatchConnectionState.Loading)

    var beginAssociationCalls: Int = 0
        private set
    var requestSyncCalls: Int = 0
        private set
    var installCalls: Int = 0
        private set
    var openCalls: Int = 0
        private set

    fun reset() {
        state.value = WatchConnectionState.Loading
        beginAssociationCalls = 0
        requestSyncCalls = 0
        installCalls = 0
        openCalls = 0
    }

    fun updateState(connectionState: WatchConnectionState) {
        state.value = connectionState
    }

    override fun observeConnectionState(): Flow<WatchConnectionState> = state.asStateFlow()

    override suspend fun beginAssociation() {
        beginAssociationCalls++
    }

    override suspend fun requestSync() {
        requestSyncCalls++
    }

    override suspend fun installAppOnWatch() {
        installCalls++
    }

    override suspend fun openAppOnWatch() {
        openCalls++
    }
}

private class FakeWatchSettingsRepository : WatchSettingsRepository {
    private val syncSettings = MutableStateFlow(WatchSyncSettings())
    private val notificationSettings = MutableStateFlow(WatchNotificationSettings())

    fun reset() {
        syncSettings.value = WatchSyncSettings()
        notificationSettings.value = WatchNotificationSettings()
    }

    override fun observeSyncSettings(): StateFlow<WatchSyncSettings> = syncSettings.asStateFlow()

    override fun observeNotificationSettings(): StateFlow<WatchNotificationSettings> =
        notificationSettings.asStateFlow()

    override suspend fun setSyncVoiceNotes(enabled: Boolean) {
        syncSettings.value = syncSettings.value.copy(syncVoiceNotes = enabled)
    }

    override suspend fun setSyncTextEntries(enabled: Boolean) {
        syncSettings.value = syncSettings.value.copy(syncTextEntries = enabled)
    }

    override suspend fun setSyncMoodCheckIns(enabled: Boolean) {
        syncSettings.value = syncSettings.value.copy(syncMoodCheckIns = enabled)
    }

    override suspend fun setSyncHealthData(enabled: Boolean) {
        syncSettings.value = syncSettings.value.copy(syncHealthData = enabled)
    }

    override suspend fun setAutoSync(enabled: Boolean) {
        syncSettings.value = syncSettings.value.copy(autoSync = enabled)
    }

    override suspend fun setShowEntryNotifications(enabled: Boolean) {
        notificationSettings.value = notificationSettings.value.copy(showEntryNotifications = enabled)
    }

    override suspend fun setIncludeAudioPreview(enabled: Boolean) {
        notificationSettings.value = notificationSettings.value.copy(includeAudioPreview = enabled)
    }
}
