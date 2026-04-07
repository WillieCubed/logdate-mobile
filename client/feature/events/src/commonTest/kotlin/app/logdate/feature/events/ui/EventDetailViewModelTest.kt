package app.logdate.feature.events.ui

import app.logdate.client.domain.events.DeleteEventUseCase
import app.logdate.client.domain.events.GetEventByIdUseCase
import app.logdate.client.domain.events.ObserveNotesForEventUseCase
import app.logdate.client.domain.events.UpdateEventUseCase
import app.logdate.client.repository.events.EventRepository
import app.logdate.shared.model.Event
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * Unit tests for [EventDetailViewModel].
 *
 * Each test wires a fresh [FakeEventRepository] into the real domain use cases and into a new
 * [EventDetailViewModel], drives the ViewModel through `runTest`, and inspects the final
 * [EventDetailUiState] via direct `value` reads. The dispatcher is overridden to a
 * [StandardTestDispatcher] so `advanceUntilIdle()` deterministically flushes coroutines
 * launched in `viewModelScope`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EventDetailViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /**
     * Calling `loadEvent` for an id that exists in the repository transitions the state from
     * the initial `Loading` to `Loaded` carrying that exact event.
     */
    @Test
    fun loadEvent_existing_id_transitions_to_loaded() =
        runTest {
            val event = sampleEvent(title = "Recital")
            val repo = FakeEventRepository(events = listOf(event))
            val viewModel = newViewModel(repo)

            viewModel.loadEvent(event.id)
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertTrue(state is EventDetailUiState.Loaded)
            assertEquals(event, state.event)
        }

    /**
     * If the requested event does not exist, the ViewModel ends in `NotFound` so the screen
     * can render its empty-state copy instead of the editor.
     */
    @Test
    fun loadEvent_missing_id_transitions_to_not_found() =
        runTest {
            val repo = FakeEventRepository()
            val viewModel = newViewModel(repo)

            viewModel.loadEvent(Uuid.random())
            advanceUntilIdle()

            assertEquals(EventDetailUiState.NotFound, viewModel.uiState.value)
        }

    /**
     * Editing the title changes the in-memory draft only — the repository's update method is
     * not called until the user explicitly saves.
     */
    @Test
    fun updateTitle_mutates_loaded_draft_without_persisting() =
        runTest {
            val event = sampleEvent(title = "Old")
            val repo = FakeEventRepository(events = listOf(event))
            val viewModel = newViewModel(repo)
            viewModel.loadEvent(event.id)
            advanceUntilIdle()

            viewModel.updateTitle("New")
            advanceUntilIdle()

            val state = viewModel.uiState.value as EventDetailUiState.Loaded
            assertEquals("New", state.event.title)
            assertNull(repo.lastUpdated, "save should not be called yet")
        }

    /**
     * On a successful save the ViewModel clears the `isSaving` flag and any prior error,
     * keeps the screen in `Loaded`, and the repository captured the event.
     */
    @Test
    fun save_success_clears_isSaving_and_keeps_loaded_state() =
        runTest {
            val event = sampleEvent()
            val repo = FakeEventRepository(events = listOf(event))
            val viewModel = newViewModel(repo)
            viewModel.loadEvent(event.id)
            advanceUntilIdle()

            viewModel.save()
            advanceUntilIdle()

            val state = viewModel.uiState.value as EventDetailUiState.Loaded
            assertEquals(false, state.isSaving)
            assertNull(state.errorMessage)
            assertNotNull(repo.lastUpdated)
        }

    /**
     * When the repository fails the update, the ViewModel surfaces an error message in the
     * loaded state and clears `isSaving` so the user can try again.
     */
    @Test
    fun save_failure_surfaces_error_message() =
        runTest {
            val event = sampleEvent()
            val repo = FakeEventRepository(events = listOf(event), failUpdates = true)
            val viewModel = newViewModel(repo)
            viewModel.loadEvent(event.id)
            advanceUntilIdle()

            viewModel.save()
            advanceUntilIdle()

            val state = viewModel.uiState.value as EventDetailUiState.Loaded
            assertNotNull(state.errorMessage)
            assertEquals(false, state.isSaving)
        }

    /**
     * On a successful delete the ViewModel invokes the `onDeleted` callback (so the screen
     * can dismiss itself) and the repository sees the deletion request.
     */
    @Test
    fun delete_success_invokes_callback() =
        runTest {
            val event = sampleEvent()
            val repo = FakeEventRepository(events = listOf(event))
            val viewModel = newViewModel(repo)
            viewModel.loadEvent(event.id)
            advanceUntilIdle()
            var deletedFired = false

            viewModel.delete { deletedFired = true }
            advanceUntilIdle()

            assertTrue(deletedFired)
            assertEquals(event.id, repo.lastDeleted)
        }

    /**
     * When the repository fails to delete, the screen stays in `Loaded` with an error
     * message so the user is informed and can retry.
     */
    @Test
    fun delete_failure_surfaces_error_message() =
        runTest {
            val event = sampleEvent()
            val repo = FakeEventRepository(events = listOf(event), failDeletes = true)
            val viewModel = newViewModel(repo)
            viewModel.loadEvent(event.id)
            advanceUntilIdle()

            viewModel.delete { }
            advanceUntilIdle()

            val state = viewModel.uiState.value as EventDetailUiState.Loaded
            assertNotNull(state.errorMessage)
        }

    /**
     * The linked-note count is reactive: pushing new note ids through the upstream flow
     * updates `linkedNoteCount` on the loaded state without re-issuing `loadEvent`.
     */
    @Test
    fun linkedNoteCount_updates_when_notes_flow_emits() =
        runTest {
            val event = sampleEvent()
            val noteIdsFlow = MutableStateFlow<List<Uuid>>(emptyList())
            val repo = FakeEventRepository(events = listOf(event), notesForEventFlow = noteIdsFlow)
            val viewModel = newViewModel(repo)

            viewModel.loadEvent(event.id)
            advanceUntilIdle()
            assertEquals(0, (viewModel.uiState.value as EventDetailUiState.Loaded).linkedNoteCount)

            noteIdsFlow.value = listOf(Uuid.random(), Uuid.random())
            advanceUntilIdle()

            assertEquals(2, (viewModel.uiState.value as EventDetailUiState.Loaded).linkedNoteCount)
        }

    /**
     * Helper that constructs a [EventDetailViewModel] wired to the real domain use cases over
     * the supplied fake repository. Keeps individual tests focused on assertions rather than
     * boilerplate.
     */
    private fun newViewModel(repo: FakeEventRepository): EventDetailViewModel =
        EventDetailViewModel(
            getEventById = GetEventByIdUseCase(repo),
            observeNotesForEvent = ObserveNotesForEventUseCase(repo),
            updateEvent = UpdateEventUseCase(repo),
            deleteEvent = DeleteEventUseCase(repo),
        )

    /**
     * Builds an [Event] with a deterministic time range so tests don't depend on the system
     * clock. Override `id` or `title` for cases that need specific values.
     */
    private fun sampleEvent(
        id: Uuid = Uuid.random(),
        title: String = "Sample",
    ): Event =
        Event(
            id = id,
            title = title,
            startTime = Instant.fromEpochSeconds(1_000),
            endTime = Instant.fromEpochSeconds(2_000),
            created = Instant.fromEpochSeconds(0),
            lastUpdated = Instant.fromEpochSeconds(0),
        )
}

/**
 * Tiny in-memory [EventRepository] used only by [EventDetailViewModelTest].
 *
 * Holds a list of pre-seeded events plus knobs for forcing failure on updates and deletes.
 * The `notesForEventFlow` is exposed so tests can drive the linked-note count after the
 * ViewModel has subscribed.
 *
 * Mutations are recorded via `lastUpdated` / `lastDeleted` and successful writes mutate the
 * shared state flow so subsequent observers see the change.
 */
private class FakeEventRepository(
    events: List<Event> = emptyList(),
    private val failUpdates: Boolean = false,
    private val failDeletes: Boolean = false,
    private val notesForEventFlow: MutableStateFlow<List<Uuid>> = MutableStateFlow(emptyList()),
) : EventRepository {
    private val state = MutableStateFlow(events)

    var lastUpdated: Event? = null
    var lastDeleted: Uuid? = null

    override fun observeAllEvents(): Flow<List<Event>> = state

    override fun observeEvent(eventId: Uuid): Flow<Event?> = state.map { list -> list.firstOrNull { it.id == eventId } }

    override fun observeEventsForDateRange(
        start: Instant,
        end: Instant,
    ): Flow<List<Event>> = MutableStateFlow(emptyList())

    override suspend fun getEventById(eventId: Uuid): Event? = state.value.firstOrNull { it.id == eventId }

    override suspend fun updateEvent(event: Event): Result<Unit> {
        if (failUpdates) return Result.failure(RuntimeException("boom"))
        lastUpdated = event
        state.value = state.value.map { if (it.id == event.id) event else it }
        return Result.success(Unit)
    }

    override suspend fun deleteEvent(eventId: Uuid): Result<Unit> {
        if (failDeletes) return Result.failure(RuntimeException("boom"))
        lastDeleted = eventId
        state.value = state.value.filterNot { it.id == eventId }
        return Result.success(Unit)
    }

    override fun observeEventsForNote(noteId: Uuid): Flow<List<Event>> = MutableStateFlow(emptyList())

    override fun observeNotesForEvent(eventId: Uuid): Flow<List<Uuid>> = notesForEventFlow

    override suspend fun linkNoteToEvent(
        eventId: Uuid,
        noteId: Uuid,
    ): Result<Unit> = Result.success(Unit)

    override suspend fun unlinkNoteFromEvent(
        eventId: Uuid,
        noteId: Uuid,
    ): Result<Unit> = Result.success(Unit)
}
