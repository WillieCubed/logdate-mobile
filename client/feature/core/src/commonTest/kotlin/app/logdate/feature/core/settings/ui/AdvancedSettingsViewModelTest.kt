package app.logdate.feature.core.settings.ui

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import app.logdate.client.datastore.LogdatePreferencesDataSource
import app.logdate.feature.core.settings.updates.AppUpdateCheckTrigger
import app.logdate.feature.core.settings.updates.AppUpdateController
import app.logdate.feature.core.settings.updates.AppUpdateStatus
import app.logdate.feature.core.settings.updates.AppUpdateUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Unit tests for [AdvancedSettingsViewModel].
 *
 * Covers app update actions and durable developer-mode visibility, including
 * persistence failures and retry.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AdvancedSettingsViewModelTest {
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
    fun `manual app update check delegates to controller`() =
        runTest {
            val updateController = FakeAppUpdateController()
            val viewModel =
                AdvancedSettingsViewModel(
                    appUpdateController = updateController,
                    preferences = LogdatePreferencesDataSource(TestPreferencesStore()),
                )

            viewModel.checkForAppUpdates()
            advanceUntilIdle()

            assertEquals(listOf(AppUpdateCheckTrigger.Manual), updateController.checkRequests)
        }

    @Test
    fun `app update ui state is exposed from controller`() =
        runTest {
            val updateController =
                FakeAppUpdateController(
                    initialState =
                        AppUpdateUiState(
                            currentVersionName = "0.1.0",
                            status = AppUpdateStatus.Downloaded,
                        ),
                )
            val viewModel =
                AdvancedSettingsViewModel(
                    appUpdateController = updateController,
                    preferences = LogdatePreferencesDataSource(TestPreferencesStore()),
                )

            assertEquals(AppUpdateStatus.Downloaded, viewModel.appUpdateUiState.value.status)
        }

    @Test
    fun `developer visibility is restored and hide is persisted`() =
        runTest {
            val store = TestPreferencesStore()
            val preferences = LogdatePreferencesDataSource(store)
            preferences.setDeveloperModeEnabled(true)
            val viewModel = AdvancedSettingsViewModel(FakeAppUpdateController(), preferences)
            advanceUntilIdle()
            assertEquals(true, viewModel.developerModeEnabled.value)
            viewModel.setDeveloperModeEnabled(false)
            advanceUntilIdle()
            assertEquals(false, viewModel.developerModeEnabled.value)
            val nextLaunch = AdvancedSettingsViewModel(FakeAppUpdateController(), LogdatePreferencesDataSource(store))
            advanceUntilIdle()
            assertEquals(false, nextLaunch.developerModeEnabled.value)
        }

    @Test
    fun `failed preference write keeps visibility and reports a retryable failure`() =
        runTest {
            val store = TestPreferencesStore()
            val preferences = LogdatePreferencesDataSource(store)
            preferences.setDeveloperModeEnabled(true)
            val viewModel = AdvancedSettingsViewModel(FakeAppUpdateController(), preferences)
            advanceUntilIdle()
            store.failWrites = true
            viewModel.setDeveloperModeEnabled(false)
            advanceUntilIdle()
            assertEquals(true, viewModel.developerModeEnabled.value)
            assertEquals(true, viewModel.developerModeSaveFailed.value)
            store.failWrites = false
            viewModel.setDeveloperModeEnabled(false)
            advanceUntilIdle()
            assertEquals(false, viewModel.developerModeEnabled.value)
            assertEquals(false, viewModel.developerModeSaveFailed.value)
        }

    private class TestPreferencesStore : DataStore<Preferences> {
        private val state = MutableStateFlow(emptyPreferences())
        var failWrites = false
        override val data: Flow<Preferences> = state

        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
            check(!failWrites) { "Storage unavailable" }
            return transform(state.value).also { state.value = it }
        }
    }

    private class FakeAppUpdateController(
        initialState: AppUpdateUiState =
            AppUpdateUiState(
                currentVersionName = "0.1.0",
            ),
    ) : AppUpdateController {
        private val _uiState = MutableStateFlow(initialState)

        val checkRequests = mutableListOf<AppUpdateCheckTrigger>()
        var completeUpdateRequests = 0

        override val uiState: StateFlow<AppUpdateUiState> = _uiState.asStateFlow()

        override suspend fun checkForUpdates(trigger: AppUpdateCheckTrigger) {
            checkRequests += trigger
        }

        override suspend fun completeUpdate() {
            completeUpdateRequests++
        }
    }
}
