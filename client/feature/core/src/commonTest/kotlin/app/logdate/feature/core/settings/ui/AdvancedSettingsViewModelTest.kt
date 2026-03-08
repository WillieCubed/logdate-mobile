package app.logdate.feature.core.settings.ui

import app.logdate.client.networking.ServerHealthChecker
import app.logdate.client.networking.ServerHealthInfo
import app.logdate.feature.core.settings.updates.AppUpdateCheckTrigger
import app.logdate.feature.core.settings.updates.AppUpdateController
import app.logdate.feature.core.settings.updates.AppUpdateStatus
import app.logdate.feature.core.settings.updates.AppUpdateUiState
import app.logdate.shared.config.DefaultLogDateConfigRepository
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
import kotlin.test.assertTrue

/** Covers manual update actions and server persistence behavior in advanced settings. */
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
                    serverHealthChecker = FakeServerHealthChecker(),
                    configRepository = DefaultLogDateConfigRepository(),
                    appUpdateController = updateController,
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
                    serverHealthChecker = FakeServerHealthChecker(),
                    configRepository = DefaultLogDateConfigRepository(),
                    appUpdateController = updateController,
                )

            assertEquals(AppUpdateStatus.Downloaded, viewModel.appUpdateUiState.value.status)
        }

    @Test
    fun `validating and saving local server persists address`() =
        runTest {
            val configRepository = DefaultLogDateConfigRepository()
            val viewModel =
                AdvancedSettingsViewModel(
                    serverHealthChecker = FakeServerHealthChecker(),
                    configRepository = configRepository,
                    appUpdateController = FakeAppUpdateController(),
                )

            viewModel.selectServerPreset(ServerPreset.LOCAL)
            viewModel.updateLocalServerAddress("10.0.2.2:8765")
            viewModel.validateAndSaveServer()
            advanceUntilIdle()

            assertEquals("10.0.2.2:8765", configRepository.localServerAddress.value)
            assertTrue(configRepository.backendUrl.value.startsWith("http://10.0.2.2:8765"))
        }

    private class FakeServerHealthChecker : ServerHealthChecker {
        override suspend fun checkServerHealth(baseUrl: String): Result<ServerHealthInfo> =
            Result.success(
                ServerHealthInfo(
                    status = "healthy",
                    version = "1.0.0",
                ),
            )
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
