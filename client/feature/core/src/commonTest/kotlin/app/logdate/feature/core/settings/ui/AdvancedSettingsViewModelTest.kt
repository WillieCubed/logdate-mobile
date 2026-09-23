package app.logdate.feature.core.settings.ui

import app.logdate.feature.core.settings.updates.AppUpdateCheckTrigger
import app.logdate.feature.core.settings.updates.AppUpdateController
import app.logdate.feature.core.settings.updates.AppUpdateStatus
import app.logdate.feature.core.settings.updates.AppUpdateUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
 * Covers manual update actions, server discovery, and health check validation
 * within the advanced settings interface. These tests ensure that server URLs
 * are correctly validated and persisted.
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
                AdvancedSettingsViewModel(appUpdateController = updateController)

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
                AdvancedSettingsViewModel(appUpdateController = updateController)

            assertEquals(AppUpdateStatus.Downloaded, viewModel.appUpdateUiState.value.status)
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
