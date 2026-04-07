package app.logdate.feature.events.ui

import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * Bundle of every action the event detail screen can dispatch.
 *
 * Following the Android architecture guide's "state + actions" pattern: the screen receives
 * an immutable [EventDetailUiState] plus an [EventDetailActions] handler. The ViewModel
 * implements this interface directly so callers can pass `viewModel` as the actions slot, and
 * lambda identity stays stable across recompositions without manual `remember`.
 *
 * Tests can implement this with a recording double instead of constructing a real ViewModel.
 */
interface EventDetailActions {
    fun loadEvent(eventId: Uuid)

    fun updateTitle(title: String)

    fun updateDescription(description: String)

    fun updateStartTime(startTime: Instant)

    fun updateEndTime(endTime: Instant?)

    fun togglePointInTime(pointInTime: Boolean)

    fun updatePlace(placeId: Uuid?)

    fun updateCoverImage(uri: String?)

    fun openPlacePicker()

    fun dismissPlacePicker()

    fun openAttachSheet()

    fun dismissAttachSheet()

    fun linkNote(noteId: Uuid)

    fun unlinkNote(noteId: Uuid)

    fun save()

    fun delete(onDeleted: () -> Unit)
}
