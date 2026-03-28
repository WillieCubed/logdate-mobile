@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.timeline.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.logdate.client.domain.notes.RemoveNoteUseCase
import app.logdate.client.domain.recommendation.GetHomeRecommendationUseCase
import app.logdate.client.domain.recommendation.HomeRecommendation
import app.logdate.client.domain.timeline.GetJournalMembershipUseCase
import app.logdate.client.domain.timeline.GetStreamingTimelineUseCase
import app.logdate.client.domain.timeline.Moment
import app.logdate.client.domain.timeline.StreamingTimelineRequest
import app.logdate.client.domain.timeline.TimelineDay
import app.logdate.client.domain.timeline.TimelinePlaceVisit
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.transcription.TranscriptionData
import app.logdate.client.repository.transcription.TranscriptionRepository
import app.logdate.client.repository.transcription.TranscriptionStatus
import app.logdate.client.repository.user.UserStateRepository
import app.logdate.shared.model.Journal
import app.logdate.shared.model.Person
import app.logdate.ui.audio.TranscriptionState
import app.logdate.ui.location.PlaceUiState
import app.logdate.ui.profiles.PersonUiState
import app.logdate.ui.profiles.toUiState
import app.logdate.ui.timeline.AudioNoteUiState
import app.logdate.ui.timeline.HomeTimelineUiState
import app.logdate.ui.timeline.ImageNoteUiState
import app.logdate.ui.timeline.JournalBadgeUiState
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
import io.github.aakira.napier.Napier
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.uuid.Uuid

/**
 * A view model for the timeline overview screen.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TimelineViewModel(
    getStreamingTimeline: GetStreamingTimelineUseCase,
    private val getHomeRecommendation: GetHomeRecommendationUseCase,
    private val removeNoteUseCase: RemoveNoteUseCase,
    private val userStateRepository: UserStateRepository,
    private val transcriptionRepository: TranscriptionRepository,
    private val getJournalMembership: GetJournalMembershipUseCase,
) : ViewModel() {
    private val visibleAudioNoteIds = MutableStateFlow<Set<Uuid>>(emptySet())
    private val transcriptionCache = MutableStateFlow<Map<Uuid, TranscriptionData?>>(emptyMap())
    private val autoRequestedNoteIds = mutableSetOf<Uuid>()

    val transcriptionState: StateFlow<TranscriptionState> =
        transcriptionCache
            .map { cache ->
                TranscriptionState(
                    requestTranscription = ::requestTranscription,
                    getTranscriptionText = { noteId ->
                        cache[noteId]
                            ?.text
                            ?.trim()
                            ?.takeIf(String::isNotEmpty)
                    },
                    isTranscriptionInProgress = { noteId ->
                        cache[noteId]?.status in setOf(TranscriptionStatus.PENDING, TranscriptionStatus.IN_PROGRESS)
                    },
                    getTranscriptionError = { noteId ->
                        cache[noteId]
                            ?.takeIf { it.status == TranscriptionStatus.FAILED }
                            ?.errorMessage
                    },
                )
            }.stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5000),
                TranscriptionState(requestTranscription = ::requestTranscription),
            )

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

    init {
        viewModelScope.launch {
            visibleAudioNoteIds
                .flatMapLatest(::observeVisibleTranscriptions)
                .onEach { transcriptions ->
                    transcriptionCache.value = transcriptions
                    requestMissingVisibleTranscriptions(transcriptions)
                }.collect {}
        }
    }

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
        ).flatMapLatest { timeline ->
            val allNoteIds =
                timeline.days
                    .flatMap { day -> day.entries.map { it.uid } }
                    .toSet()
            getJournalMembership(allNoteIds).map { membershipMap ->
                timeline to membershipMap
            }
        }.combine(selectedItemUiState) { (timeline, membershipMap), selection ->
            val items = timeline.days.map { day -> day.toUiState(membershipMap) }
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

    fun updateVisibleAudioNoteIds(noteIds: Set<Uuid>) {
        visibleAudioNoteIds.value = noteIds
    }

    private fun TimelinePlaceVisit.toUiState(): PlaceUiState =
        PlaceUiState(
            id = id,
            title = name,
            latitude = latitude,
            longitude = longitude,
        )

    private fun TimelineDay.toUiState(membershipMap: Map<Uuid, List<Journal>> = emptyMap()): TimelineDayUiState {
        val noteUiStates = entries.toUiState(membershipMap)
        val placeUiStates = placesVisited.map { place -> place.toUiState() }
        val peopleUiStates = people.map(Person::toUiState)
        val momentUiStates = moments.toMomentUiStates(peopleUiStates, membershipMap)

        return createSemanticTimelineDayUiState(
            summary = tldr,
            date = date,
            moments = momentUiStates,
            people = peopleUiStates,
            notes = noteUiStates,
            placesVisited = placeUiStates,
            isLoadingSummary = tldr.isEmpty(),
            isLoadingPeople = people.isEmpty() && tldr.isEmpty(),
        )
    }

    private fun List<Moment>.toMomentUiStates(
        dayPeople: List<PersonUiState>,
        membershipMap: Map<Uuid, List<Journal>> = emptyMap(),
    ): List<MomentUiState> {
        val heroIndex = selectHeroIndex(this)
        return mapIndexed { index, moment ->
            moment.toMomentUiState(isHero = index == heroIndex, dayPeople = dayPeople, membershipMap = membershipMap)
        }
    }

    private fun Moment.toMomentUiState(
        isHero: Boolean,
        dayPeople: List<PersonUiState>,
        membershipMap: Map<Uuid, List<Journal>> = emptyMap(),
    ): MomentUiState {
        val timezone = TimeZone.currentSystemDefault()
        val startLocal = estimatedStart.toLocalDateTime(timezone)
        val timeOfDay =
            when (startLocal.hour) {
                in 0..11 -> "morning"
                in 12..17 -> "afternoon"
                else -> "evening"
            }
        val resolvedPeople =
            people.mapNotNull { name ->
                dayPeople.find { it.name.equals(name, ignoreCase = true) }
            }
        return MomentUiState(
            id = id.toString(),
            label = label,
            timeOfDay = timeOfDay,
            textSnippet = textFragments.firstOrNull()?.text,
            media = media.map { MomentMediaUiState(uri = it.uri, isVideo = it.isVideo) },
            audio =
                audio.firstOrNull()?.let {
                    MomentAudioUiState(uri = it.uri, durationMs = it.durationMs, noteId = it.sourceNoteId)
                },
            places = places.map { PlaceUiState(id = it.id, title = it.name, latitude = it.latitude, longitude = it.longitude) },
            people = resolvedPeople,
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

    private fun List<JournalNote>.toUiState(
        membershipMap: Map<Uuid, List<Journal>> = emptyMap(),
    ): List<app.logdate.ui.timeline.NoteUiState> =
        sortedByDescending { note -> note.creationTimestamp }.map { note ->
            val noteJournals =
                membershipMap[note.uid]
                    .orEmpty()
                    .map { JournalBadgeUiState(journalId = it.id, title = it.title) }
            when (note) {
                is JournalNote.Text ->
                    TextNoteUiState(
                        noteId = note.uid,
                        text = note.content,
                        timestamp = note.creationTimestamp,
                        journals = noteJournals,
                    )
                is JournalNote.Image ->
                    ImageNoteUiState(
                        noteId = note.uid,
                        uri = note.mediaRef,
                        timestamp = note.creationTimestamp,
                        caption = note.caption,
                        journals = noteJournals,
                    )
                is JournalNote.Audio ->
                    AudioNoteUiState(
                        noteId = note.uid,
                        uri = note.mediaRef,
                        timestamp = note.creationTimestamp,
                        duration = note.durationMs,
                        journals = noteJournals,
                    )
                is JournalNote.Video ->
                    VideoNoteUiState(
                        noteId = note.uid,
                        uri = note.mediaRef,
                        timestamp = note.creationTimestamp,
                        caption = note.caption,
                        journals = noteJournals,
                    )
            }
        }

    private fun observeVisibleTranscriptions(noteIds: Set<Uuid>): Flow<Map<Uuid, TranscriptionData?>> {
        val sortedIds = noteIds.sorted()
        if (sortedIds.isEmpty()) {
            return flowOf(emptyMap())
        }

        return combine(
            sortedIds.map { noteId ->
                transcriptionRepository.observeTranscription(noteId).map { transcription ->
                    noteId to transcription
                }
            },
        ) { notePairs ->
            notePairs.toMap()
        }
    }

    private fun requestTranscription(noteId: Uuid) {
        viewModelScope.launch {
            Napier.d("Requesting transcription for note: $noteId")
            transcriptionRepository.requestTranscription(noteId)
        }
    }

    private fun requestMissingVisibleTranscriptions(transcriptions: Map<Uuid, TranscriptionData?>) {
        val noteIdsToRequest =
            autoRequestableNoteIds(
                visibleNoteIds = visibleAudioNoteIds.value,
                transcriptions = transcriptions,
                alreadyRequestedNoteIds = autoRequestedNoteIds,
            )

        noteIdsToRequest.forEach { noteId ->
            autoRequestedNoteIds.add(noteId)
            viewModelScope.launch {
                val queued = transcriptionRepository.requestTranscription(noteId)
                if (!queued) {
                    autoRequestedNoteIds.remove(noteId)
                }
            }
        }
    }
}
