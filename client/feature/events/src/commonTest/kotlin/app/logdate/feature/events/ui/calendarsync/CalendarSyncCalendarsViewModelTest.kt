package app.logdate.feature.events.ui.calendarsync

import app.logdate.client.calendar.DeviceCalendar
import app.logdate.client.calendar.DeviceCalendarEvent
import app.logdate.client.calendar.DeviceCalendarReader
import app.logdate.client.datastore.LogdatePreferencesDataSource
import app.logdate.feature.events.test.InMemoryPreferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
 * Tests for [CalendarSyncCalendarsViewModel].
 *
 * The picker VM loads calendars from a [DeviceCalendarReader] on entry, tracks a working
 * selection set, and persists it on save. Each test wires a fake reader and an in-memory
 * preferences store so we can assert both the visible state and the persisted side effect.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CalendarSyncCalendarsViewModelTest {
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
    fun `init loads calendars and initial selection from preferences`() =
        runTest(testDispatcher) {
            reader.calendars =
                listOf(
                    deviceCalendar("cal-1", "Personal"),
                    deviceCalendar("cal-2", "Work"),
                )
            preferences.setDeviceCalendarEnabledIds(setOf("cal-1"))

            val viewModel = CalendarSyncCalendarsViewModel(reader, preferences)

            val state = viewModel.state.value
            assertFalse(state.isLoading)
            assertEquals(2, state.calendars.size)
            assertEquals(setOf("cal-1"), state.selectedIds)
        }

    @Test
    fun `toggle calendar adds then removes from selection`() =
        runTest(testDispatcher) {
            reader.calendars = listOf(deviceCalendar("cal-1", "Personal"))
            val viewModel = CalendarSyncCalendarsViewModel(reader, preferences)

            viewModel.toggleCalendar("cal-1")
            assertEquals(setOf("cal-1"), viewModel.state.value.selectedIds)

            viewModel.toggleCalendar("cal-1")
            assertTrue(
                viewModel.state.value.selectedIds
                    .isEmpty(),
            )
        }

    @Test
    fun `toggle calendar persists selection immediately`() =
        runTest(testDispatcher) {
            reader.calendars = listOf(deviceCalendar("cal-1", "Personal"))
            val viewModel = CalendarSyncCalendarsViewModel(reader, preferences)

            viewModel.toggleCalendar("cal-1")

            assertEquals(setOf("cal-1"), preferences.getDeviceCalendarEnabledIds())
        }

    @Test
    fun `toggle calendar off removes from persisted selection`() =
        runTest(testDispatcher) {
            reader.calendars = listOf(deviceCalendar("cal-1", "Personal"))
            preferences.setDeviceCalendarEnabledIds(setOf("cal-1"))
            val viewModel = CalendarSyncCalendarsViewModel(reader, preferences)

            viewModel.toggleCalendar("cal-1")

            assertTrue(preferences.getDeviceCalendarEnabledIds().isEmpty())
        }

    @Test
    fun `init with no calendars emits empty loaded state`() =
        runTest(testDispatcher) {
            val viewModel = CalendarSyncCalendarsViewModel(reader, preferences)

            val state = viewModel.state.value
            assertFalse(state.isLoading)
            assertTrue(state.calendars.isEmpty())
            assertTrue(state.selectedIds.isEmpty())
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
