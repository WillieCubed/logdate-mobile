package app.logdate.feature.events.ui.settings

import androidx.lifecycle.viewModelScope
import app.logdate.client.datastore.LogdatePreferencesDataSource
import app.logdate.feature.events.test.InMemoryPreferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests for [EventsSettingsViewModel]. Covers the master toggle, the smart-names toggle,
 * and the default-on behavior of both — the only state the VM owns.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EventsSettingsViewModelTest {
    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var preferences: LogdatePreferencesDataSource

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        preferences = LogdatePreferencesDataSource(InMemoryPreferencesDataStore())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun emits_default_state_on_a_fresh_install() =
        runTest(testDispatcher) {
            val viewModel = newViewModel()
            val collectJob = startCollecting(viewModel.uiState)

            val state = viewModel.uiState.value

            assertTrue(state.isAutoEventsEnabled)
            assertTrue(state.isSmartNamingEnabled)
            tearDownViewModel(viewModel, collectJob)
        }

    @Test
    fun setAutoEventsEnabled_persists_to_preferences() =
        runTest(testDispatcher) {
            val viewModel = newViewModel()
            val collectJob = startCollecting(viewModel.uiState)

            viewModel.setAutoEventsEnabled(false)

            assertFalse(preferences.isEventsEnabled())
            assertFalse(viewModel.uiState.value.isAutoEventsEnabled)
            tearDownViewModel(viewModel, collectJob)
        }

    @Test
    fun setSmartNamingEnabled_round_trips() =
        runTest(testDispatcher) {
            val viewModel = newViewModel()
            val collectJob = startCollecting(viewModel.uiState)

            viewModel.setSmartNamingEnabled(false)

            assertFalse(preferences.isEventInferenceAiNamingEnabled())
            assertFalse(viewModel.uiState.value.isSmartNamingEnabled)
            tearDownViewModel(viewModel, collectJob)
        }

    @Test
    fun reflects_persisted_disabled_state() =
        runTest(testDispatcher) {
            preferences.setEventsEnabled(false)
            val viewModel = newViewModel()
            val collectJob = startCollecting(viewModel.uiState)

            assertFalse(viewModel.uiState.value.isAutoEventsEnabled)
            // Re-enable, then drive back through the toggle.
            viewModel.setAutoEventsEnabled(true)
            assertTrue(viewModel.uiState.value.isAutoEventsEnabled)
            tearDownViewModel(viewModel, collectJob)
        }

    private fun newViewModel(): EventsSettingsViewModel = EventsSettingsViewModel(preferences = preferences)

    private fun TestScope.startCollecting(stateFlow: StateFlow<*>): Job = stateFlow.onEach { }.launchIn(this)

    private suspend fun tearDownViewModel(
        viewModel: EventsSettingsViewModel,
        collectJob: Job,
    ) {
        collectJob.cancelAndJoin()
        val scopeJob = viewModel.viewModelScope.coroutineContext[Job]
        scopeJob?.children?.toList()?.forEach { child -> child.cancelAndJoin() }
    }
}
