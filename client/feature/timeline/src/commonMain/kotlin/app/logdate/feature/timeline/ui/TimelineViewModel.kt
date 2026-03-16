@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.timeline.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.logdate.client.domain.notes.RemoveNoteUseCase
import app.logdate.client.domain.recommendation.GetHomeRecommendationUseCase
import app.logdate.client.domain.recommendation.HomeRecommendation
import app.logdate.client.domain.timeline.GetStreamingTimelineUseCase
import app.logdate.client.domain.timeline.Moment
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
import app.logdate.ui.timeline.MomentAudioUiState
import app.logdate.ui.timeline.MomentMediaUiState
import app.logdate.ui.timeline.MomentUiState
import app.logdate.ui.timeline.TextNoteUiState
import app.logdate.ui.timeline.TimelineDaySelection
import app.logdate.ui.timeline.TimelineDayUiState
import app.logdate.ui.timeline.TimelineLoadingState
import app.logdate.ui.timeline.TimelineSuggestionBlock
import app.logdate.ui.timeline.VideoNoteUiState
import app.logdate.ui.timeline.createSemanticTimelineDayUiState
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
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
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

    private fun TimelineDay.toUiState(): TimelineDayUiState {
        val noteUiStates = entries.toUiState()
        val placeUiStates = placesVisited.map { place -> place.toUiState() }
        val peopleUiStates = people.map(Person::toUiState)

        return if (moments.isNotEmpty()) {
            val momentUiStates = moments.toMomentUiStates()
            createSemanticTimelineDayUiState(
                summary = tldr,
                date = date,
                moments = momentUiStates,
                people = peopleUiStates,
                notes = noteUiStates,
                placesVisited = placeUiStates,
                isLoadingSummary = tldr.isEmpty(),
                isLoadingPeople = people.isEmpty() && tldr.isEmpty(),
            )
        } else {
            createTimelineDayUiState(
                summary = tldr,
                date = date,
                people = peopleUiStates,
                events = events,
                placesVisited = placeUiStates,
                notes = noteUiStates,
                isLoadingSummary = tldr.isEmpty(),
                isLoadingPeople = people.isEmpty() && tldr.isEmpty(),
            )
        }
    }

    private fun List<Moment>.toMomentUiStates(): List<MomentUiState> {
        val heroIndex = selectHeroIndex(this)
        return mapIndexed { index, moment -> moment.toMomentUiState(isHero = index == heroIndex) }
    }

    private fun Moment.toMomentUiState(isHero: Boolean): MomentUiState {
        val timezone = TimeZone.currentSystemDefault()
        val startLocal = estimatedStart.toLocalDateTime(timezone)
        val timeOfDay =
            when (startLocal.hour) {
                in 0..11 -> "morning"
                in 12..17 -> "afternoon"
                else -> "evening"
            }
        return MomentUiState(
            id = id.toString(),
            label = label,
            timeOfDay = timeOfDay,
            textSnippet = textFragments.firstOrNull()?.text?.take(140),
            media = media.map { MomentMediaUiState(uri = it.uri, isVideo = it.isVideo) },
            audio = audio.firstOrNull()?.let { MomentAudioUiState(uri = it.uri, durationMs = it.durationMs) },
            places = places.map { PlaceUiState(id = it.id, title = it.name, latitude = it.latitude, longitude = it.longitude) },
            people = people,
            isHero = isHero,
        )
    }

    private fun selectHeroIndex(moments: List<Moment>): Int {
        if (moments.isEmpty()) return -1
        return moments.indices.maxByOrNull { index ->
            val m = moments[index]
            m.media.size * 3 + m.textFragments.size * 2 + m.audio.size
        } ?: 0
    }

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
