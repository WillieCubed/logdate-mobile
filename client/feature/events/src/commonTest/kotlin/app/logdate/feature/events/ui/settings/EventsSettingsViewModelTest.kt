package app.logdate.feature.events.ui.settings

import androidx.lifecycle.viewModelScope
import app.logdate.client.datastore.LogdatePreferencesDataSource
import app.logdate.client.domain.events.EventInferenceFailure
import app.logdate.client.domain.events.EventInferenceLauncher
import app.logdate.client.domain.events.EventInferenceSensitivity
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
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * Tests for [EventsSettingsViewModel].
 *
 * Each test pairs a real [LogdatePreferencesDataSource] (backed by an in-memory
 * [InMemoryPreferencesDataStore]) with a [RecordingLauncher] so we can drive the VM
 * end-to-end and assert the user-visible state transitions. The clock is injected as a
 * mutable lambda so the relative-time bucket is deterministic.
 *
 * The test dispatcher is [UnconfinedTestDispatcher] so the VM's `combine` and `stateIn`
 * propagate emissions synchronously on the test thread. With a `StandardTestDispatcher`
 * the `nowTicker` flow's `delay(60s)` would park in virtual time and `runTest` would
 * hang at teardown waiting for it to clean up.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EventsSettingsViewModelTest {
    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var preferences: LogdatePreferencesDataSource
    private lateinit var launcher: RecordingLauncher
    private val clockValue: Instant = Instant.fromEpochSeconds(1_700_000_000)

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        preferences = LogdatePreferencesDataSource(InMemoryPreferencesDataStore())
        launcher = RecordingLauncher()
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun emits_default_state_when_no_preferences_set() =
        runTest(testDispatcher) {
            val viewModel = newViewModel()
            val collectJob = startCollecting(viewModel.uiState)

            val state = viewModel.uiState.value

            assertFalse(state.isAutoEventsEnabled)
            assertEquals(EventInferenceSensitivity.MEDIUM, state.sensitivity)
            assertTrue(state.isSmartNamingEnabled)
            assertEquals(null, state.lastRunAt)
            assertEquals(null, state.lastFailure)
            assertFalse(state.isRunInFlight)
            tearDownViewModel(viewModel, collectJob)
        }

    @Test
    fun setAutoEventsEnabled_persists_to_preferences() =
        runTest(testDispatcher) {
            val viewModel = newViewModel()
            val collectJob = startCollecting(viewModel.uiState)

            viewModel.setAutoEventsEnabled(true)

            assertTrue(preferences.isEventsEnabled())
            assertTrue(viewModel.uiState.value.isAutoEventsEnabled)
            tearDownViewModel(viewModel, collectJob)
        }

    @Test
    fun setSensitivity_persists_enum_name_and_round_trips_through_state() =
        runTest(testDispatcher) {
            val viewModel = newViewModel()
            val collectJob = startCollecting(viewModel.uiState)

            viewModel.setSensitivity(EventInferenceSensitivity.HIGH)

            assertEquals(EventInferenceSensitivity.HIGH, viewModel.uiState.value.sensitivity)
            assertEquals("HIGH", preferences.getEventInferenceSensitivity())
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
    fun runNow_locks_button_until_stats_advance_past_threshold() =
        runTest(testDispatcher) {
            preferences.setEventsEnabled(true)
            val viewModel = newViewModel()
            val collectJob = startCollecting(viewModel.uiState)

            viewModel.runNow()

            assertEquals(1, launcher.runCount)
            assertTrue(viewModel.uiState.value.isRunInFlight)

            // Worker writes a fresher run timestamp; the lock should release on the next emission.
            preferences.recordEventInferenceRun(
                runAt = Instant.fromEpochSeconds(1_700_000_500),
                createdThisRun = 2,
                errorKind = null,
            )

            val unlocked = viewModel.uiState.value
            assertFalse(unlocked.isRunInFlight)
            assertEquals(2, unlocked.lastCreatedCount)
            tearDownViewModel(viewModel, collectJob)
        }

    @Test
    fun runNow_is_idempotent_while_lock_is_held() =
        runTest(testDispatcher) {
            preferences.setEventsEnabled(true)
            val viewModel = newViewModel()
            val collectJob = startCollecting(viewModel.uiState)

            viewModel.runNow()
            viewModel.runNow()
            viewModel.runNow()

            assertEquals(1, launcher.runCount)
            tearDownViewModel(viewModel, collectJob)
        }

    @Test
    fun lastFailure_is_decoded_from_persisted_kind() =
        runTest(testDispatcher) {
            preferences.setEventsEnabled(true)
            preferences.recordEventInferenceRun(
                runAt = Instant.fromEpochSeconds(1_700_000_100),
                createdThisRun = 0,
                errorKind = EventInferenceFailure.NamingFailed.name,
            )
            val viewModel = newViewModel()
            val collectJob = startCollecting(viewModel.uiState)

            assertEquals(EventInferenceFailure.NamingFailed, viewModel.uiState.value.lastFailure)
            tearDownViewModel(viewModel, collectJob)
        }

    @Test
    fun unknown_persisted_sensitivity_falls_back_to_medium() =
        runTest(testDispatcher) {
            preferences.setEventInferenceSensitivity("WAT")
            val viewModel = newViewModel()
            val collectJob = startCollecting(viewModel.uiState)

            assertEquals(EventInferenceSensitivity.MEDIUM, viewModel.uiState.value.sensitivity)
            tearDownViewModel(viewModel, collectJob)
        }

    private fun newViewModel(): EventsSettingsViewModel =
        EventsSettingsViewModel(
            preferences = preferences,
            inferenceLauncher = launcher,
            clock = { clockValue },
        )

    /**
     * Starts a background collector on [stateFlow] so the WhileSubscribed combine inside
     * the VM actually runs. Without this `uiState.value` returns the initial seed forever
     * and the runNow guard never sees the in-flight transition.
     */
    private fun TestScope.startCollecting(stateFlow: StateFlow<*>): Job = stateFlow.onEach { }.launchIn(this)

    /**
     * Tears down the VM at the end of a test. Cancels both the test-side collector and
     * everything `viewModelScope` launched (the `stateIn` plus its `nowTicker`) and
     * suspends until each child has actually unwound, so `runTest`'s "no leftover
     * coroutines" check passes. There's no public way to clear a `ViewModel` from
     * outside the lifecycle, so we cancel its scope's children directly via
     * `cancelAndJoin`.
     */
    private suspend fun tearDownViewModel(
        viewModel: EventsSettingsViewModel,
        collectJob: Job,
    ) {
        collectJob.cancelAndJoin()
        val scopeJob = viewModel.viewModelScope.coroutineContext[Job]
        scopeJob?.children?.toList()?.forEach { child -> child.cancelAndJoin() }
    }

    private class RecordingLauncher : EventInferenceLauncher {
        var runCount: Int = 0
            private set

        override fun runNow() {
            runCount += 1
        }
    }
}
