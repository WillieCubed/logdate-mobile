package app.logdate.feature.events.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.logdate.client.domain.events.DeleteEventUseCase
import app.logdate.client.domain.events.GetEventByIdUseCase
import app.logdate.client.domain.events.ObserveNotesForEventUseCase
import app.logdate.client.domain.events.UpdateEventUseCase
import app.logdate.shared.model.Event
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.uuid.Uuid

/**
 * UI state surface for the event detail / edit screen.
 *
 * The screen always sits in exactly one of three states. The transition diagram is:
 *
 * ```
 * Loading ──► Loaded   (event resolved from the repository)
 * Loading ──► NotFound (no event with the requested id, or it was soft-deleted)
 * Loaded  ──► NotFound (the event is deleted while the screen is open)
 * ```
 *
 * Edit operations (`updateTitle`, `updateDescription`) only mutate [Loaded] in place — they do
 * not transition between states. The [Loaded] state holds the *draft* event being edited, which
 * differs from the persisted event until [EventDetailViewModel.save] is called.
 */
sealed interface EventDetailUiState {
    /**
     * Initial state, shown while the event is being fetched for the first time. The screen
     * displays a centered progress indicator.
     */
    data object Loading : EventDetailUiState

    /**
     * Terminal state shown when the requested event id does not exist (or has been soft-deleted).
     * The screen displays a friendly "Event not found" message instead of the editor.
     */
    data object NotFound : EventDetailUiState

    /**
     * The event has loaded successfully and the user can view or edit it.
     *
     * @property event The current draft of the event. This may differ from what is persisted in
     *   the database if the user has typed into the title or description fields without yet
     *   pressing Save. The id, time bounds, place, cover image, and external calendar fields
     *   are read-only at this stage of the feature — only the title and description can change.
     * @property linkedNoteCount The number of journal notes currently attached to this event.
     *   Updated reactively as the underlying junction table changes. The screen renders a
     *   "N linked items" line when this is greater than zero.
     * @property isSaving `true` while a save round-trip to the repository is in flight. The Save
     *   button is disabled and shows "Saving…" during this period to prevent double submission.
     * @property errorMessage Non-null if the most recent save or delete operation failed. The
     *   screen renders this in the error color above the Save button. Cleared on the next
     *   successful save.
     */
    data class Loaded(
        val event: Event,
        val linkedNoteCount: Int = 0,
        val isSaving: Boolean = false,
        val errorMessage: String? = null,
    ) : EventDetailUiState
}

/**
 * ViewModel powering the event detail / edit screen.
 *
 * Responsibilities:
 *
 * 1. Resolve an event by id and expose it as [uiState] for the screen to render.
 * 2. Observe the linked-note count for that event and keep it fresh as junction rows change.
 * 3. Hold the in-progress edit draft separately from the persisted event so the user can type
 *    freely and only commit on Save.
 * 4. Mediate Save and Delete operations through the corresponding domain use cases, surfacing
 *    success or failure as updates to [uiState].
 *
 * The ViewModel does not call repositories directly; all data access goes through use cases as
 * required by the project's clean-architecture rules.
 *
 * @param getEventById Reactive lookup that emits the latest copy of an event by id, or `null`
 *   when the event does not exist or has been soft-deleted.
 * @param observeNotesForEvent Reactive lookup of the note ids currently linked to an event;
 *   used to compute [EventDetailUiState.Loaded.linkedNoteCount].
 * @param updateEvent Persists edits to an event's mutable metadata.
 * @param deleteEvent Soft-deletes the event so it disappears from the timeline and detail view.
 */
class EventDetailViewModel(
    private val getEventById: GetEventByIdUseCase,
    private val observeNotesForEvent: ObserveNotesForEventUseCase,
    private val updateEvent: UpdateEventUseCase,
    private val deleteEvent: DeleteEventUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow<EventDetailUiState>(EventDetailUiState.Loading)

    /**
     * Observable UI state. The screen collects this with `collectAsStateWithLifecycle()` and
     * branches on the sealed [EventDetailUiState] hierarchy to choose what to render.
     */
    val uiState: StateFlow<EventDetailUiState> = _uiState.asStateFlow()

    /**
     * Begins observing the event with the given id and the count of notes linked to it.
     *
     * Safe to call multiple times — each call starts fresh collectors in [viewModelScope]. The
     * screen calls this from a `LaunchedEffect(eventId)` so it runs once per id.
     *
     * Behavior:
     * - Emits [EventDetailUiState.Loading] until the first event value arrives.
     * - Transitions to [EventDetailUiState.Loaded] (preserving any previously observed
     *   `linkedNoteCount`) once the event is resolved.
     * - Transitions to [EventDetailUiState.NotFound] if the event is missing or gets
     *   soft-deleted while the screen is open.
     * - Updates `linkedNoteCount` whenever the underlying junction table changes.
     *
     * @param eventId The id of the event to observe.
     */
    fun loadEvent(eventId: Uuid) {
        viewModelScope.launch {
            getEventById(eventId).collect { event ->
                _uiState.update { current ->
                    if (event == null) {
                        EventDetailUiState.NotFound
                    } else {
                        val previousCount = (current as? EventDetailUiState.Loaded)?.linkedNoteCount ?: 0
                        EventDetailUiState.Loaded(event = event, linkedNoteCount = previousCount)
                    }
                }
            }
        }
        viewModelScope.launch {
            observeNotesForEvent(eventId).collect { noteIds ->
                _uiState.update { current ->
                    if (current is EventDetailUiState.Loaded) current.copy(linkedNoteCount = noteIds.size) else current
                }
            }
        }
    }

    /**
     * Stages a new title in the in-memory draft. Does not persist anything — call [save] to
     * commit the change. No-op if the screen is not in [EventDetailUiState.Loaded].
     */
    fun updateTitle(title: String) {
        _uiState.update { current ->
            if (current is EventDetailUiState.Loaded) current.copy(event = current.event.copy(title = title)) else current
        }
    }

    /**
     * Stages a new description in the in-memory draft. Blank input is normalized to `null` so
     * the persisted column matches the schema convention of "no description = null". No-op if
     * the screen is not in [EventDetailUiState.Loaded].
     */
    fun updateDescription(description: String) {
        _uiState.update { current ->
            if (current is EventDetailUiState.Loaded) {
                current.copy(event = current.event.copy(description = description.ifBlank { null }))
            } else {
                current
            }
        }
    }

    /**
     * Persists the current draft via [updateEvent].
     *
     * Marks the state as saving for the duration of the round-trip so the screen can disable
     * the Save button and show a "Saving…" label. On failure, populates
     * [EventDetailUiState.Loaded.errorMessage] with a generic message and logs the underlying
     * error via Napier (the user-visible message intentionally does not leak exception details).
     *
     * No-op if the screen is not in [EventDetailUiState.Loaded].
     */
    fun save() {
        val current = _uiState.value as? EventDetailUiState.Loaded ?: return
        _uiState.value = current.copy(isSaving = true, errorMessage = null)
        viewModelScope.launch {
            updateEvent(current.event)
                .onFailure { error ->
                    Napier.e("Failed to save event ${current.event.id}", error)
                    _uiState.update {
                        if (it is EventDetailUiState.Loaded) it.copy(isSaving = false, errorMessage = "Couldn't save event") else it
                    }
                }.onSuccess {
                    _uiState.update { if (it is EventDetailUiState.Loaded) it.copy(isSaving = false) else it }
                }
        }
    }

    /**
     * Soft-deletes the current event via [deleteEvent], then invokes [onDeleted] so the caller
     * can dismiss the screen.
     *
     * On failure, [onDeleted] is not invoked and an error message is surfaced through
     * [EventDetailUiState.Loaded.errorMessage]. No-op if the screen is not in
     * [EventDetailUiState.Loaded].
     *
     * @param onDeleted Callback invoked exactly once on a successful delete. Typically
     *   pops the navigation back stack.
     */
    fun delete(onDeleted: () -> Unit) {
        val current = _uiState.value as? EventDetailUiState.Loaded ?: return
        viewModelScope.launch {
            deleteEvent(current.event.id)
                .onSuccess { onDeleted() }
                .onFailure { error ->
                    Napier.e("Failed to delete event ${current.event.id}", error)
                    _uiState.update {
                        if (it is EventDetailUiState.Loaded) it.copy(errorMessage = "Couldn't delete event") else it
                    }
                }
        }
    }
}
