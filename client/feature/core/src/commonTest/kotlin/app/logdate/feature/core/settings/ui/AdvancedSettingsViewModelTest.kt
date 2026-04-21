package app.logdate.feature.core.settings.ui

import app.logdate.client.networking.ServerDiscoveryClient
import app.logdate.client.networking.ServerHealthChecker
import app.logdate.client.networking.ServerHealthInfo
import app.logdate.feature.core.settings.updates.AppUpdateCheckTrigger
import app.logdate.feature.core.settings.updates.AppUpdateController
import app.logdate.feature.core.settings.updates.AppUpdateStatus
import app.logdate.feature.core.settings.updates.AppUpdateUiState
import app.logdate.shared.config.DefaultLogDateConfigRepository
import app.logdate.shared.model.DeploymentKind
import app.logdate.shared.model.ServerCapability
import app.logdate.shared.model.ServerDescriptor
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
                AdvancedSettingsViewModel(
                    serverConfigurationCoordinator =
                        ServerConfigurationCoordinator(
                            serverHealthChecker = FakeServerHealthChecker(),
                            serverDiscoveryClient = FakeServerDiscoveryClient(),
                            configRepository = DefaultLogDateConfigRepository(),
                        ),
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
                    serverConfigurationCoordinator =
                        ServerConfigurationCoordinator(
                            serverHealthChecker = FakeServerHealthChecker(),
                            serverDiscoveryClient = FakeServerDiscoveryClient(),
                            configRepository = DefaultLogDateConfigRepository(),
                        ),
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
                    serverConfigurationCoordinator =
                        ServerConfigurationCoordinator(
                            serverHealthChecker = FakeServerHealthChecker(),
                            serverDiscoveryClient = FakeServerDiscoveryClient(),
                            configRepository = configRepository,
                        ),
                    appUpdateController = FakeAppUpdateController(),
                )

            viewModel.selectServerPreset(ServerPreset.CUSTOM)
            viewModel.updateCustomServerUrl("http://10.0.2.2:8765")
            viewModel.validateAndSaveServer()
            advanceUntilIdle()

            assertTrue(configRepository.backendUrl.value.startsWith("http://10.0.2.2:8765"))
            assertEquals("http://10.0.2.2:8765", configRepository.serverDescriptor.value?.serverOrigin)
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

    private class FakeServerDiscoveryClient : ServerDiscoveryClient {
        override suspend fun discoverServer(serverOrigin: String): Result<ServerDescriptor> =
            Result.success(
                ServerDescriptor(
                    serverOrigin = serverOrigin,
                    apiBaseUrl = "${serverOrigin.trimEnd('/')}/api/v1",
                    deploymentKind = DeploymentKind.SELF_HOSTED,
                    displayName = "Test Server",
                    handleDomain = "example.com",
                    capabilities =
                        listOf(
                            ServerCapability.AUTH_PASSKEY,
                            ServerCapability.SYNC_CONTENT,
                            ServerCapability.SYNC_MEDIA,
                        ),
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
