package app.logdate.feature.events.ui.calendarsync

import androidx.lifecycle.viewModelScope
import app.logdate.client.calendar.DeviceCalendar
import app.logdate.client.calendar.DeviceCalendarEvent
import app.logdate.client.calendar.DeviceCalendarReader
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
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * Tests for [CalendarSyncOverviewViewModel].
 *
 * The trimmed VM only owns permission state, the master toggle, and the
 * "N of M selected" count for the per-calendar picker. The "sync now" affordance and
 * the worker run stats are deliberately gone, so the assertions cover only that.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CalendarSyncOverviewViewModelTest {
    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var preferences: LogdatePreferencesDataSource
    private lateinit var reader: FakeDeviceCalendarReader

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        preferences = LogdatePreferencesDataSource(InMemoryPreferencesDataStore())
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
    fun selected_calendar_count_reflects_persisted_selection() =
        runTest(testDispatcher) {
            preferences.setDeviceCalendarEnabledIds(setOf("cal-1", "cal-3"))
            val viewModel = newViewModel()
            val collectJob = startCollecting(viewModel.uiState)

            assertEquals(2, viewModel.uiState.value.selectedCalendarCount)
            tearDownViewModel(viewModel, collectJob)
        }

    private fun newViewModel(): CalendarSyncOverviewViewModel =
        CalendarSyncOverviewViewModel(
            preferences = preferences,
            deviceCalendarReader = reader,
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
