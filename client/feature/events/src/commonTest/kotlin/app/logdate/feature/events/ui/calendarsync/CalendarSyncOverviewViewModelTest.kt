package app.logdate.feature.events.ui.calendarsync

import androidx.lifecycle.viewModelScope
import app.logdate.client.calendar.DeviceCalendar
import app.logdate.client.calendar.DeviceCalendarEvent
import app.logdate.client.calendar.DeviceCalendarReader
import app.logdate.client.datastore.LogdatePreferencesDataSource
import app.logdate.client.domain.events.CalendarImportFailure
import app.logdate.client.domain.events.CalendarImportLauncher
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
 * Tests for [CalendarSyncOverviewViewModel].
 *
 * Like [EventsSettingsViewModelTest], this test suite uses an in-memory
 * [LogdatePreferencesDataSource], a recording [CalendarImportLauncher], and a fake
 * [DeviceCalendarReader] so the VM can be exercised end-to-end without WorkManager or
 * the Android content provider.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CalendarSyncOverviewViewModelTest {
    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var preferences: LogdatePreferencesDataSource
    private lateinit var launcher: RecordingLauncher
    private lateinit var reader: FakeDeviceCalendarReader
    private val clockValue: Instant = Instant.fromEpochSeconds(1_700_000_000)

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        preferences = LogdatePreferencesDataSource(InMemoryPreferencesDataStore())
        launcher = RecordingLauncher()
        reader = FakeDeviceCalendarReader()
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

            assertFalse(state.isSyncEnabled)
            assertEquals(0, state.selectedCalendarCount)
            assertEquals(0, state.totalCalendarCount)
            assertEquals(PermissionState.Unknown, state.permissionState)
            assertEquals(null, state.lastFailure)
            tearDownViewModel(viewModel, collectJob)
        }

    @Test
    fun setSyncEnabled_persists_to_preferences() =
        runTest(testDispatcher) {
            val viewModel = newViewModel()
            val collectJob = startCollecting(viewModel.uiState)

            viewModel.setSyncEnabled(true)

            assertTrue(preferences.isDeviceCalendarSyncEnabled())
            assertTrue(viewModel.uiState.value.isSyncEnabled)
            tearDownViewModel(viewModel, collectJob)
        }

    @Test
    fun setPermissionState_granted_refreshes_total_calendar_count() =
        runTest(testDispatcher) {
            reader.calendars =
                listOf(
                    deviceCalendar("cal-1", "Personal"),
                    deviceCalendar("cal-2", "Work"),
                )
            val viewModel = newViewModel()
            val collectJob = startCollecting(viewModel.uiState)

            viewModel.setPermissionState(PermissionState.Granted)

            val state = viewModel.uiState.value
            assertEquals(PermissionState.Granted, state.permissionState)
            assertEquals(2, state.totalCalendarCount)
            tearDownViewModel(viewModel, collectJob)
        }

    @Test
    fun runNow_locks_button_until_stats_advance_past_threshold() =
        runTest(testDispatcher) {
            preferences.setDeviceCalendarSyncEnabled(true)
            preferences.setDeviceCalendarEnabledIds(setOf("cal-1"))
            val viewModel = newViewModel()
            val collectJob = startCollecting(viewModel.uiState)

            viewModel.runNow()

            assertEquals(1, launcher.runCount)
            assertTrue(viewModel.uiState.value.isRunInFlight)

            preferences.recordDeviceCalendarSyncRun(
                runAt = Instant.fromEpochSeconds(1_700_000_500),
                created = 3,
                updated = 1,
                errorKind = null,
            )

            val unlocked = viewModel.uiState.value
            assertFalse(unlocked.isRunInFlight)
            assertEquals(3, unlocked.lastCreatedCount)
            assertEquals(1, unlocked.lastUpdatedCount)
            tearDownViewModel(viewModel, collectJob)
        }

    @Test
    fun runNow_is_idempotent_while_lock_is_held() =
        runTest(testDispatcher) {
            preferences.setDeviceCalendarSyncEnabled(true)
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
            preferences.recordDeviceCalendarSyncRun(
                runAt = Instant.fromEpochSeconds(1_700_000_100),
                created = 0,
                updated = 0,
                errorKind = CalendarImportFailure.PermissionDenied.name,
            )
            val viewModel = newViewModel()
            val collectJob = startCollecting(viewModel.uiState)

            assertEquals(CalendarImportFailure.PermissionDenied, viewModel.uiState.value.lastFailure)
            tearDownViewModel(viewModel, collectJob)
        }

    private fun newViewModel(): CalendarSyncOverviewViewModel =
        CalendarSyncOverviewViewModel(
            preferences = preferences,
            launcher = launcher,
            deviceCalendarReader = reader,
            clock = { clockValue },
        )

    private fun TestScope.startCollecting(stateFlow: StateFlow<*>): Job = stateFlow.onEach { }.launchIn(this)

    private suspend fun tearDownViewModel(
        viewModel: CalendarSyncOverviewViewModel,
        collectJob: Job,
    ) {
        collectJob.cancelAndJoin()
        val scopeJob = viewModel.viewModelScope.coroutineContext[Job]
        scopeJob?.children?.toList()?.forEach { child -> child.cancelAndJoin() }
    }

    private fun deviceCalendar(
        id: String,
        displayName: String,
    ): DeviceCalendar =
        DeviceCalendar(
            id = id,
            displayName = displayName,
            accountName = "Personal",
            accountType = "google",
        )

    private class RecordingLauncher : CalendarImportLauncher {
        var runCount: Int = 0
            private set

        override fun runNow() {
            runCount += 1
        }
    }

    private class FakeDeviceCalendarReader : DeviceCalendarReader {
        var calendars: List<DeviceCalendar> = emptyList()

        override suspend fun hasPermission(): Boolean = true

        override suspend fun listCalendars(): List<DeviceCalendar> = calendars

        override suspend fun readEvents(
            calendarIds: Set<String>,
            start: Instant,
            end: Instant,
        ): List<DeviceCalendarEvent> = emptyList()
    }
}
