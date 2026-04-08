package app.logdate.feature.events.ui.calendarsync

import androidx.lifecycle.viewModelScope
import app.logdate.client.domain.events.ObserveImportedEventsUseCase
import app.logdate.client.repository.events.EventRepository
import app.logdate.shared.model.Event
import app.logdate.shared.model.ExternalCalendarSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
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
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * Tests for [CalendarSyncActivityViewModel].
 *
 * The activity VM is a thin wrapper around [ObserveImportedEventsUseCase] — it just
 * exposes the use case's flow as a `StateFlow`. The tests verify that the filter and
 * sort live in the use case rather than the VM by feeding the use case directly.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CalendarSyncActivityViewModelTest {
    private val testDispatcher = UnconfinedTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun emits_empty_list_initially() =
        runTest(testDispatcher) {
            val repo = FakeEventRepository(events = emptyList())
            val useCase = ObserveImportedEventsUseCase(repo)
            val viewModel = CalendarSyncActivityViewModel(useCase)
            val collectJob = startCollecting(viewModel.events)

            assertTrue(viewModel.events.value.isEmpty())
            tearDownViewModel(viewModel, collectJob)
        }

    @Test
    fun emits_imported_events_sorted_newest_first() =
        runTest(testDispatcher) {
            val older =
                importedEvent(
                    title = "Older",
                    lastUpdated = Instant.fromEpochSeconds(1_700_000_000),
                )
            val newer =
                importedEvent(
                    title = "Newer",
                    lastUpdated = Instant.fromEpochSeconds(1_700_000_500),
                )
            val repo = FakeEventRepository(events = listOf(older, newer))
            val useCase = ObserveImportedEventsUseCase(repo)
            val viewModel = CalendarSyncActivityViewModel(useCase)
            val collectJob = startCollecting(viewModel.events)

            val emitted = viewModel.events.value
            assertEquals(listOf("Newer", "Older"), emitted.map(Event::title))
            tearDownViewModel(viewModel, collectJob)
        }

    @Test
    fun filters_out_non_imported_events() =
        runTest(testDispatcher) {
            val imported =
                importedEvent(
                    title = "From calendar",
                    lastUpdated = Instant.fromEpochSeconds(1_700_000_500),
                )
            val inferred =
                Event(
                    title = "Inferred",
                    startTime = Instant.fromEpochSeconds(1_700_000_000),
                    externalCalendarSource = null,
                )
            val repo = FakeEventRepository(events = listOf(imported, inferred))
            val useCase = ObserveImportedEventsUseCase(repo)
            val viewModel = CalendarSyncActivityViewModel(useCase)
            val collectJob = startCollecting(viewModel.events)

            assertEquals(listOf("From calendar"), viewModel.events.value.map(Event::title))
            tearDownViewModel(viewModel, collectJob)
        }

    private fun importedEvent(
        title: String,
        lastUpdated: Instant,
    ): Event =
        Event(
            title = title,
            startTime = Instant.fromEpochSeconds(1_700_000_000),
            externalCalendarId = "Google:$title",
            externalCalendarSource = ExternalCalendarSource.DEVICE_CALENDAR,
            lastUpdated = lastUpdated,
        )

    private fun TestScope.startCollecting(stateFlow: StateFlow<*>): Job = stateFlow.onEach { }.launchIn(this)

    private suspend fun tearDownViewModel(
        viewModel: CalendarSyncActivityViewModel,
        collectJob: Job,
    ) {
        collectJob.cancelAndJoin()
        val scopeJob = viewModel.viewModelScope.coroutineContext[Job]
        scopeJob?.children?.toList()?.forEach { child -> child.cancelAndJoin() }
    }

    private class FakeEventRepository(
        events: List<Event>,
    ) : EventRepository {
        private val state = MutableStateFlow(events)

        override fun observeAllEvents(): Flow<List<Event>> = state.asStateFlow()

        override fun observeEvent(eventId: Uuid): Flow<Event?> = flowOf(null)

        override fun observeEventsForDateRange(
            start: Instant,
            end: Instant,
        ): Flow<List<Event>> = flowOf(emptyList())

        override suspend fun getEventById(eventId: Uuid): Event? = null

        override suspend fun findByExternalCalendarId(externalId: String): Event? = null

        override suspend fun createEvent(event: Event): Result<Unit> = Result.success(Unit)

        override suspend fun updateEvent(event: Event): Result<Unit> = Result.success(Unit)

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
}
