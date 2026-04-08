package app.logdate.feature.core.settings.ui

import app.logdate.client.domain.recommendation.MemoriesSettings
import app.logdate.client.domain.recommendation.MemoriesSettingsRepository
import app.logdate.client.domain.recommendation.RecallMode
import app.logdate.client.domain.recommendation.WidgetContentType
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

@OptIn(ExperimentalCoroutinesApi::class)
class MemoriesSettingsViewModelTest {
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
    fun `widget install state is exposed from controller`() =
        runTest {
            val widgetInstallController =
                FakeMemoriesWidgetInstallController(
                    initialState = MemoriesWidgetInstallUiState.Unsupported,
                )
            val viewModel =
                MemoriesSettingsViewModel(
                    settingsRepository = FakeMemoriesSettingsRepository(),
                    widgetInstallController = widgetInstallController,
                )

            advanceUntilIdle()

            assertEquals(
                MemoriesWidgetInstallUiState.Unsupported,
                viewModel.uiState.value.widgetInstallUiState,
            )
        }

    @Test
    fun `add widget delegates to controller`() =
        runTest {
            val widgetInstallController = FakeMemoriesWidgetInstallController()
            val viewModel =
                MemoriesSettingsViewModel(
                    settingsRepository = FakeMemoriesSettingsRepository(),
                    widgetInstallController = widgetInstallController,
                )

            viewModel.addWidgetToHomeScreen()
            advanceUntilIdle()

            assertEquals(1, widgetInstallController.requestCount)
        }

    @Test
    fun `toggle widget content type keeps at least one enabled type`() =
        runTest {
            val settingsRepository =
                FakeMemoriesSettingsRepository(
                    initialSettings =
                        MemoriesSettings(
                            widgetContentTypes = setOf(WidgetContentType.TEXT),
                        ),
                )
            val viewModel =
                MemoriesSettingsViewModel(
                    settingsRepository = settingsRepository,
                    widgetInstallController = FakeMemoriesWidgetInstallController(),
                )

            viewModel.toggleWidgetContentType(WidgetContentType.TEXT, enabled = false)
            advanceUntilIdle()

            assertEquals(setOf(WidgetContentType.TEXT), settingsRepository.settings.value.widgetContentTypes)
        }

    private class FakeMemoriesSettingsRepository(
        initialSettings: MemoriesSettings =
            MemoriesSettings(
                recallMode = RecallMode.ON_THIS_DAY,
                widgetContentTypes = WidgetContentType.ALL,
            ),
    ) : MemoriesSettingsRepository {
        val settings = MutableStateFlow(initialSettings)

        override suspend fun getSettings(): MemoriesSettings = settings.value

        override fun observeSettings(): Flow<MemoriesSettings> = settings

        override suspend fun updateSettings(settings: MemoriesSettings) {
            this.settings.value = settings
        }
    }

    private class FakeMemoriesWidgetInstallController(
        initialState: MemoriesWidgetInstallUiState = MemoriesWidgetInstallUiState.Available,
    ) : MemoriesWidgetInstallController {
        private val _uiState = MutableStateFlow(initialState)

        var requestCount = 0

        override val uiState: StateFlow<MemoriesWidgetInstallUiState> = _uiState.asStateFlow()

        override suspend fun requestAddToHomeScreen() {
            requestCount++
        }
    }
}
