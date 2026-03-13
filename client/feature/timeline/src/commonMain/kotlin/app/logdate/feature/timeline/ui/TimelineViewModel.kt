@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.timeline.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.logdate.client.domain.notes.RemoveNoteUseCase
import app.logdate.client.domain.recommendation.GetHomeRecommendationUseCase
import app.logdate.client.domain.recommendation.HomeRecommendation
import app.logdate.client.domain.timeline.GetStreamingTimelineUseCase
import app.logdate.client.domain.timeline.StreamingTimelineRequest
import app.logdate.client.domain.timeline.TimelineDay
import app.logdate.client.domain.timeline.TimelinePlaceVisit
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.user.UserStateRepository
import app.logdate.shared.model.Person
import app.logdate.ui.audio.TranscriptionState
import app.logdate.ui.location.PlaceUiState
import app.logdate.ui.profiles.toUiState
import app.logdate.ui.timeline.AudioNoteUiState
import app.logdate.ui.timeline.HomeTimelineUiState
import app.logdate.ui.timeline.ImageNoteUiState
import app.logdate.ui.timeline.TextNoteUiState
import app.logdate.ui.timeline.TimelineDaySelection
import app.logdate.ui.timeline.TimelineDayUiState
import app.logdate.ui.timeline.TimelineLoadingState
import app.logdate.ui.timeline.TimelineSuggestionBlock
import app.logdate.ui.timeline.VideoNoteUiState
import app.logdate.ui.timeline.createTimelineDayUiState
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlin.uuid.Uuid

/**
 * A view model for the timeline overview screen.
 */
class TimelineViewModel(
    getStreamingTimeline: GetStreamingTimelineUseCase,
    private val getHomeRecommendation: GetHomeRecommendationUseCase,
    private val removeNoteUseCase: RemoveNoteUseCase,
    private val userStateRepository: UserStateRepository,
) : ViewModel() {
    // Transcription state
    private val _transcriptionState =
        MutableStateFlow(
            TranscriptionState(
                requestTranscription = { noteId ->
                    // Example implementation
                    viewModelScope.launch {
                        Napier.d("Requesting transcription for note: $noteId")
                        // Here we would call the actual transcription service
                        // For now, we'll simulate it with delay
                        kotlinx.coroutines.delay(2000)
                    }
                },
                getTranscriptionText = { noteId ->
                    // Simulate some example transcriptions
                    when {
                        noteId.toString().contains("1") -> "This is a sample transcription."
                        noteId.toString().contains("5") -> "Another example transcription text."
                        else -> null
                    }
                },
                isTranscriptionInProgress = { noteId ->
                    // Simulate in progress for certain IDs
                    noteId.toString().contains("2")
                },
                getTranscriptionError = { noteId ->
                    // Simulate errors for certain IDs
                    if (noteId.toString().contains("3")) "Service unavailable" else null
                },
            ),
        )

    val transcriptionState: StateFlow<TranscriptionState> = _transcriptionState.asStateFlow()

    private val selectedItemUiState =
        MutableStateFlow<TimelineDaySelection>(TimelineDaySelection.NotSelected)

    /**
     * Sets the selected day for timeline detail view.
     *
     * @param date The date to select, or LocalDate.fromEpochDays(0) to clear selection
     */
    fun setSelectedDay(date: LocalDate) {
        // TODO: Using epoch day 0 (1970-01-01) as a sentinel value could be problematic
        // if that's actually a valid date in the timeline. Consider using a more explicit
        // approach for selection/deselection.
        selectedItemUiState.value =
            if (date.toEpochDays() == 0L) {
                TimelineDaySelection.NotSelected
            } else {
                TimelineDaySelection.DateSelected(date)
            }
    }

    private val snackbarMessageState = MutableStateFlow<String?>(null)

    // Collect the user data and map to the birthday value
    val birthday =
        userStateRepository.userData
            .map { it.birthday }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5000),
                null,
            )

    // Use streaming timeline for incremental loading with immediate first paint
    val uiState: StateFlow<HomeTimelineUiState> =
        getStreamingTimeline(
            StreamingTimelineRequest.RecentTimeline(),
        ).combine(selectedItemUiState) { timeline, selection ->
            val items = timeline.days.map { day -> day.toUiState() }
            val loadingState = if (items.isEmpty()) TimelineLoadingState.InitialLoading else TimelineLoadingState.Loaded

            HomeTimelineUiState(
                items = items,
                selectedItem = selection,
                selectedDay =
                    when (selection) {
                        is TimelineDaySelection.DateSelected -> items.find { item -> item.date == selection.date }
                        is TimelineDaySelection.Selected -> items.find { item -> item.date == selection.day }
                        TimelineDaySelection.NotSelected -> null
                    },
                loadingState = loadingState,
                isLoading = items.isEmpty(),
                timelineSuggestion = null,
                snackbarMessage = snackbarMessageState.value,
            )
        }.combine(getHomeRecommendation().map { it.toTimelineSuggestionBlock() }) { state, suggestion ->
            state.copy(timelineSuggestion = suggestion)
        }.combine(snackbarMessageState) { state, snackbarMessage ->
            state.copy(snackbarMessage = snackbarMessage)
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            // Immediate first paint with skeleton
            HomeTimelineUiState(
                loadingState = TimelineLoadingState.InitialLoading,
                isLoading = true,
            ),
        )

    /**
     * Removes a note from the timeline.
     */
    fun deleteItem(uid: Uuid) {
        viewModelScope.launch {
            removeNoteUseCase(uid)
            // TODO: Use UI to notify user of deletion
        }
    }

    /**
     * Shows a snackbar message when the user clicks "Add to memories"
     */
    fun showAddToMemoriesSnackbar(memoryId: String) {
        snackbarMessageState.value = "Adding to memories coming soon!"

        // Automatically clear the snackbar after a few seconds
        viewModelScope.launch {
            kotlinx.coroutines.delay(3000)
            if (snackbarMessageState.value == "Adding to memories coming soon!") {
                snackbarMessageState.value = null
            }
        }
    }

    /**
     * Dismisses any currently showing snackbar
     */
    fun dismissSnackbar() {
        snackbarMessageState.value = null
    }

    private fun TimelinePlaceVisit.toUiState(): PlaceUiState =
        PlaceUiState(
            id = id,
            title = name,
            latitude = latitude,
            longitude = longitude,
        )

    private fun TimelineDay.toUiState(): TimelineDayUiState =
        createTimelineDayUiState(
            summary = tldr,
            date = date,
            people = people.map(Person::toUiState),
            events = events,
            placesVisited = placesVisited.map { place -> place.toUiState() },
            notes = entries.toUiState(),
            isLoadingSummary = tldr.isEmpty(),
            isLoadingPeople = people.isEmpty() && tldr.isEmpty(),
        )

    private fun HomeRecommendation.toTimelineSuggestionBlock(): TimelineSuggestionBlock? =
        when (this) {
            is HomeRecommendation.EmptyDay ->
                TimelineSuggestionBlock.EmptyDay(
                    message = message,
                    locationName = locationName,
                )
            is HomeRecommendation.CompleteYourDraft ->
                TimelineSuggestionBlock.CompleteDraft(
                    draftId = draftId.toString(),
                    notePreview = notePreview?.takeIf(String::isNotBlank),
                )
            is HomeRecommendation.MemoryRecall ->
                TimelineSuggestionBlock.MemoryRecall(
                    memoryDate = date,
                    title = summary,
                    people = people,
                    mediaUris =
                        mediaUris.map { uri ->
                            app.logdate.ui.timeline
                                .MediaObjectUiState(uid = uri, uri = uri)
                        },
                    isAiGenerated = isAiGenerated,
                )
            HomeRecommendation.None -> null
        }

    private fun List<JournalNote>.toUiState(): List<app.logdate.ui.timeline.NoteUiState> =
        sortedByDescending { note -> note.creationTimestamp }.map { note ->
            when (note) {
                is JournalNote.Text ->
                    TextNoteUiState(
                        noteId = note.uid,
                        text = note.content,
                        timestamp = note.creationTimestamp,
                    )
                is JournalNote.Image ->
                    ImageNoteUiState(
                        noteId = note.uid,
                        uri = note.mediaRef,
                        timestamp = note.creationTimestamp,
                        caption = note.caption,
                    )
                is JournalNote.Audio ->
                    AudioNoteUiState(
                        noteId = note.uid,
                        uri = note.mediaRef,
                        timestamp = note.creationTimestamp,
                        duration = note.durationMs,
                    )
                is JournalNote.Video ->
                    VideoNoteUiState(
                        noteId = note.uid,
                        uri = note.mediaRef,
                        timestamp = note.creationTimestamp,
                        caption = note.caption,
                    )
            }
        }
}
