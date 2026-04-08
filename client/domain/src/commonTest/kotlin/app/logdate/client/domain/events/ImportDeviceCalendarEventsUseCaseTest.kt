package app.logdate.client.domain.events

import app.logdate.client.calendar.DeviceCalendar
import app.logdate.client.calendar.DeviceCalendarEvent
import app.logdate.client.calendar.DeviceCalendarReader
import app.logdate.client.repository.events.EventRepository
import app.logdate.shared.model.Event
import app.logdate.shared.model.ExternalCalendarSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * Tests for [ImportDeviceCalendarEventsUseCase].
 *
 * Each test seeds exactly the device events the branch cares about, runs one import pass,
 * and asserts both the [ImportSummary] counts and the contents of [RecordingEventRepository]
 * so failures point at the row the test is interested in.
 */
class ImportDeviceCalendarEventsUseCaseTest {
    @Test
    fun imports_new_events() =
        runTest {
            val reader = FakeCalendarReader(events = listOf(deviceEvent("evt-1", title = "Recital")))
            val repo = RecordingEventRepository()
            val useCase = ImportDeviceCalendarEventsUseCase(reader, repo, now = { NOW })

            val result = useCase(selectedCalendarIds = setOf("cal-1"))

            assertTrue(result is ImportResult.Success)
            assertEquals(1, result.summary.created)
            assertEquals(0, result.summary.updated)
            assertEquals(0, result.summary.skipped)
            assertEquals(1, repo.created.size)
            val saved = repo.created.single()
            assertEquals("Recital", saved.title)
            assertEquals(ExternalCalendarSource.DEVICE_CALENDAR, saved.externalCalendarSource)
            assertEquals("Google:evt-1", saved.externalCalendarId)
        }

    @Test
    fun updates_events_whose_title_or_time_changed() =
        runTest {
            val existing =
                Event(
                    title = "Old title",
                    startTime = NOW - 2.hours,
                    endTime = NOW - 1.hours,
                    externalCalendarId = "Google:evt-1",
                    externalCalendarSource = ExternalCalendarSource.DEVICE_CALENDAR,
                )
            val reader =
                FakeCalendarReader(
                    events =
                        listOf(
                            deviceEvent(
                                externalId = "evt-1",
                                title = "New title",
                                startTime = NOW + 1.hours,
                                endTime = NOW + 2.hours,
                            ),
                        ),
                )
            val repo = RecordingEventRepository(existingByExternalId = mapOf("Google:evt-1" to existing))
            val useCase = ImportDeviceCalendarEventsUseCase(reader, repo, now = { NOW })

            val result = useCase(selectedCalendarIds = setOf("cal-1"))

            assertTrue(result is ImportResult.Success)
            assertEquals(0, result.summary.created)
            assertEquals(1, result.summary.updated)
            val updated = repo.updated.single()
            assertEquals("New title", updated.title)
            assertEquals(NOW + 1.hours, updated.startTime)
            assertEquals(NOW + 2.hours, updated.endTime)
        }

    @Test
    fun skips_events_that_havent_changed() =
        runTest {
            val existing =
                Event(
                    title = "Recital",
                    startTime = NOW + 1.hours,
                    endTime = NOW + 2.hours,
                    description = "Annual",
                    externalCalendarId = "Google:evt-1",
                    externalCalendarSource = ExternalCalendarSource.DEVICE_CALENDAR,
                )
            val reader =
                FakeCalendarReader(
                    events =
                        listOf(
                            deviceEvent(
                                externalId = "evt-1",
                                title = "Recital",
                                description = "Annual",
                                startTime = NOW + 1.hours,
                                endTime = NOW + 2.hours,
                            ),
                        ),
                )
            val repo = RecordingEventRepository(existingByExternalId = mapOf("Google:evt-1" to existing))
            val useCase = ImportDeviceCalendarEventsUseCase(reader, repo, now = { NOW })

            val result = useCase(selectedCalendarIds = setOf("cal-1"))

            assertTrue(result is ImportResult.Success)
            assertEquals(0, result.summary.created)
            assertEquals(0, result.summary.updated)
            assertEquals(1, result.summary.skipped)
            assertEquals(0, repo.created.size)
            assertEquals(0, repo.updated.size)
        }

    @Test
    fun returns_permission_denied_when_reader_has_no_permission() =
        runTest {
            val reader = FakeCalendarReader(hasPermission = false, events = listOf(deviceEvent("evt-1")))
            val repo = RecordingEventRepository()
            val useCase = ImportDeviceCalendarEventsUseCase(reader, repo, now = { NOW })

            val result = useCase(selectedCalendarIds = setOf("cal-1"))

            assertEquals(ImportResult.PermissionDenied, result)
            assertEquals(0, repo.created.size)
        }

    @Test
    fun returns_empty_summary_when_no_calendars_are_selected() =
        runTest {
            val reader = FakeCalendarReader(events = listOf(deviceEvent("evt-1")))
            val repo = RecordingEventRepository()
            val useCase = ImportDeviceCalendarEventsUseCase(reader, repo, now = { NOW })

            val result = useCase(selectedCalendarIds = emptySet())

            assertTrue(result is ImportResult.Success)
            assertEquals(0, result.summary.created)
            assertEquals(0, repo.created.size)
            // Reader should not have been queried for events at all when the selection is empty.
            assertEquals(0, reader.readEventsCallCount)
        }

    @Test
    fun respects_lookback_and_lookahead_window_passed_to_reader() =
        runTest {
            val reader = FakeCalendarReader(events = emptyList())
            val repo = RecordingEventRepository()
            val useCase = ImportDeviceCalendarEventsUseCase(reader, repo, now = { NOW })

            useCase(selectedCalendarIds = setOf("cal-1"), lookback = 1.hours, lookahead = 2.hours)

            val (start, end) = reader.lastReadRange ?: error("Reader was not called")
            assertEquals(NOW - 1.hours, start)
            assertEquals(NOW + 2.hours, end)
        }

    private fun deviceEvent(
        externalId: String,
        calendarId: String = "cal-1",
        accountName: String = "Google",
        title: String = "Untitled",
        description: String? = null,
        startTime: Instant = NOW + 1.hours,
        endTime: Instant? = NOW + 2.hours,
        placeName: String? = null,
    ): DeviceCalendarEvent =
        DeviceCalendarEvent(
            externalId = externalId,
            calendarId = calendarId,
            accountName = accountName,
            title = title,
            description = description,
            startTime = startTime,
            endTime = endTime,
            placeName = placeName,
        )

    private class FakeCalendarReader(
        private val hasPermission: Boolean = true,
        private val events: List<DeviceCalendarEvent>,
    ) : DeviceCalendarReader {
        var readEventsCallCount: Int = 0
            private set
        var lastReadRange: Pair<Instant, Instant>? = null
            private set

        override suspend fun hasPermission(): Boolean = hasPermission

        override suspend fun listCalendars(): List<DeviceCalendar> = emptyList()

        override suspend fun readEvents(
            calendarIds: Set<String>,
            start: Instant,
            end: Instant,
        ): List<DeviceCalendarEvent> {
            readEventsCallCount += 1
            lastReadRange = start to end
            return events.filter { it.calendarId in calendarIds }
        }
    }

    private class RecordingEventRepository(
        private val existingByExternalId: Map<String, Event> = emptyMap(),
    ) : EventRepository {
        val created: MutableList<Event> = mutableListOf()
        val updated: MutableList<Event> = mutableListOf()

        private val state = MutableStateFlow<List<Event>>(emptyList())

        override fun observeAllEvents(): Flow<List<Event>> = state

        override fun observeEvent(eventId: Uuid): Flow<Event?> = flowOf(null)

        override fun observeEventsForDateRange(
            start: Instant,
            end: Instant,
        ): Flow<List<Event>> = flowOf(emptyList())

        override suspend fun getEventById(eventId: Uuid): Event? = null

        override suspend fun findByExternalCalendarId(externalId: String): Event? = existingByExternalId[externalId]

        override suspend fun createEvent(event: Event): Result<Unit> {
            created += event
            return Result.success(Unit)
        }

        override suspend fun updateEvent(event: Event): Result<Unit> {
            updated += event
            return Result.success(Unit)
        }

        override suspend fun deleteEvent(eventId: Uuid): Result<Unit> = Result.success(Unit)

        override fun observeEventsForNote(noteId: Uuid): Flow<List<Event>> = flowOf(emptyList())

        override fun observeNotesForEvent(eventId: Uuid): Flow<List<Uuid>> = flowOf(emptyList())

        override suspend fun linkNoteToEvent(
            eventId: Uuid,
            noteId: Uuid,
        ): Result<Unit> = Result.success(Unit)

        override suspend fun unlinkNoteFromEvent(
            eventId: Uuid,
            noteId: Uuid,
        ): Result<Unit> = Result.success(Unit)
    }

    companion object {
        private val NOW: Instant = Instant.fromEpochSeconds(1_700_000_000)
    }
}
