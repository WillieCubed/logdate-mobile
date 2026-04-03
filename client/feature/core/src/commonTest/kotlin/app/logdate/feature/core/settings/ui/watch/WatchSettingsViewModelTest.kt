package app.logdate.feature.core.settings.ui.watch

import app.logdate.client.domain.watch.WatchNotificationSettings
import app.logdate.client.domain.watch.WatchSettingsRepository
import app.logdate.client.domain.watch.WatchSyncSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class WatchSettingsViewModelTest {
    private val testDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `begin association delegates to connection manager`() =
        runTest {
            val connectionManager = FakeWatchConnectionManager()
            val viewModel =
                WatchSettingsViewModel(
                    connectionManager = connectionManager,
                    settingsRepository = FakeWatchSettingsRepository(),
                )

            viewModel.beginAssociation()
            advanceUntilIdle()

            assertEquals(1, connectionManager.beginAssociationCalls)
        }

    @Test
    fun `connection state is exposed from connection manager`() =
        runTest {
            val connectionManager =
                FakeWatchConnectionManager(
                    initialState = WatchConnectionState.NeedsAssociation("Pixel Watch"),
                )
            val viewModel =
                WatchSettingsViewModel(
                    connectionManager = connectionManager,
                    settingsRepository = FakeWatchSettingsRepository(),
                )
            backgroundScope.launch { viewModel.connectionState.collect {} }
            advanceUntilIdle()

            assertEquals(
                WatchConnectionState.NeedsAssociation("Pixel Watch"),
                viewModel.connectionState.value,
            )
        }

    private class FakeWatchConnectionManager(
        initialState: WatchConnectionState = WatchConnectionState.Loading,
    ) : WatchConnectionManager {
        private val state = MutableStateFlow(initialState)

        var beginAssociationCalls: Int = 0

        override fun observeConnectionState(): Flow<WatchConnectionState> = state.asStateFlow()

        override suspend fun beginAssociation() {
            beginAssociationCalls++
        }

        override suspend fun requestSync() {
        }

        override suspend fun installAppOnWatch() {
        }

        override suspend fun openAppOnWatch() {
        }
    }

    private class FakeWatchSettingsRepository : WatchSettingsRepository {
        private val syncSettings = MutableStateFlow(WatchSyncSettings())
        private val notificationSettings = MutableStateFlow(WatchNotificationSettings())

        override fun observeSyncSettings(): StateFlow<WatchSyncSettings> = syncSettings.asStateFlow()

        override fun observeNotificationSettings(): StateFlow<WatchNotificationSettings> = notificationSettings.asStateFlow()

        override suspend fun setSyncVoiceNotes(enabled: Boolean) {
        }

        override suspend fun setSyncTextEntries(enabled: Boolean) {
        }

        override suspend fun setSyncMoodCheckIns(enabled: Boolean) {
        }

        override suspend fun setSyncHealthData(enabled: Boolean) {
        }

        override suspend fun setAutoSync(enabled: Boolean) {
        }

        override suspend fun setShowEntryNotifications(enabled: Boolean) {
        }

        override suspend fun setIncludeAudioPreview(enabled: Boolean) {
        }
    }
}
