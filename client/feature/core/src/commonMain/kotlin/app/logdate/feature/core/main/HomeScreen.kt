@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.core.main

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.logdate.client.awareness.daylight.DaylightClassifier
import app.logdate.client.domain.events.LinkNoteToEventUseCase
import app.logdate.client.domain.recommendation.GetHomeRecommendationUseCase
import app.logdate.client.domain.recommendation.HomeRecommendation
import app.logdate.client.domain.timeline.GetJournalMembershipUseCase
import app.logdate.client.domain.timeline.GetStreamingTimelineUseCase
import app.logdate.client.domain.timeline.GetTimelinePageUseCase
import app.logdate.client.domain.timeline.Moment
import app.logdate.client.domain.timeline.StreamingTimelineRequest
import app.logdate.client.domain.timeline.Timeline
import app.logdate.client.domain.timeline.TimelineDay
import app.logdate.client.domain.timeline.TimelinePage
import app.logdate.client.domain.timeline.TimelinePageRequest
import app.logdate.client.domain.timeline.TimelinePlaceVisit
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.client.repository.transcription.TranscriptionData
import app.logdate.client.repository.transcription.TranscriptionRepository
import app.logdate.client.repository.transcription.TranscriptionStatus
import app.logdate.feature.core.sync.SyncIssuesBanner
import app.logdate.feature.core.sync.SyncPresentationViewModel
import app.logdate.feature.journals.ui.JournalClickCallback
import app.logdate.feature.journals.ui.JournalsOverviewScreen
import app.logdate.feature.rewind.ui.RewindOverviewScreen
import app.logdate.shared.model.Journal
import app.logdate.shared.model.Person
import app.logdate.ui.audio.TranscriptionProvider
import app.logdate.ui.audio.TranscriptionState
import app.logdate.ui.common.applyScreenStyles
import app.logdate.ui.location.PlaceUiState
import app.logdate.ui.platform.LocalPlatformHaptics
import app.logdate.ui.profiles.PersonUiState
import app.logdate.ui.profiles.toUiState
import app.logdate.ui.sync.SyncAction
import app.logdate.ui.sync.SyncErrorBanner
import app.logdate.ui.timeline.AudioNoteUiState
import app.logdate.ui.timeline.HomeTimelineUiState
import app.logdate.ui.timeline.ImageNoteUiState
import app.logdate.ui.timeline.JournalBadgeUiState
import app.logdate.ui.timeline.MediaObjectUiState
import app.logdate.ui.timeline.MomentAudioUiState
import app.logdate.ui.timeline.MomentMediaUiState
import app.logdate.ui.timeline.MomentUiState
import app.logdate.ui.timeline.TextNoteUiState
import app.logdate.ui.timeline.TimelineDaySelection
import app.logdate.ui.timeline.TimelineDayUiState
import app.logdate.ui.timeline.TimelineLoadingState
import app.logdate.ui.timeline.TimelinePane
import app.logdate.ui.timeline.TimelineSuggestionBlock
import app.logdate.ui.timeline.TimelineUiState
import app.logdate.ui.timeline.VideoNoteUiState
import app.logdate.ui.timeline.createSemanticTimelineDayUiState
import app.logdate.ui.timeline.toDayEventUiState
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
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import logdate.client.feature.core.generated.resources.Res
import logdate.client.feature.core.generated.resources.create_new_entry
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import kotlin.uuid.Uuid

@Composable
fun HomeScreen(
    onNewEntry: () -> Unit,
    onOpenJournal: JournalClickCallback,
    onCreateJournal: () -> Unit,
    onBrowseJournals: () -> Unit,
    onOpenRewind: (Uuid) -> Unit,
    onOpenSettings: () -> Unit = {},
    onOpenSearch: () -> Unit = {},
    onOpenDraft: (draftId: String) -> Unit = {},
    onImportBackup: () -> Unit = {},
    onOpenMediaDetail: (Uuid) -> Unit = {},
    onOpenSyncIssues: () -> Unit = {},
    onOpenDay: (LocalDate) -> Unit = {},
    locationContent: @Composable (Modifier) -> Unit = {},
    libraryContent: @Composable (Modifier) -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = koinViewModel(),
    syncPresentationViewModel: SyncPresentationViewModel = koinViewModel(),
) {
    val syncPresentation by syncPresentationViewModel.presentation.collectAsStateWithLifecycle()
    var currentDestination: HomeRouteDestination by rememberSaveable {
        mutableStateOf(HomeRouteDestination.Timeline)
    }
    val snackbarHostState = remember { SnackbarHostState() }

    val haptics = LocalPlatformHaptics.current

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    when (currentDestination) {
                        HomeRouteDestination.Journals -> onCreateJournal()
                        else -> onNewEntry()
                    }
                },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ) {
                Icon(
                    Icons.Default.EditNote,
                    contentDescription = stringResource(Res.string.create_new_entry),
                )
            }
        },
        modifier = modifier,
    ) { innerPadding ->
        // Action-required sync states (auth lapsed, quota exceeded, conflicts) need to surface
        // on every home tab — the auth banner can't only render on Timeline. The Timeline tab
        // itself still renders the banner inside TimelinePane (which is also used standalone in
        // the desktop pane), so we suppress the home-level banner there to avoid duplication.
        val showHomeLevelSyncBanner = currentDestination != HomeRouteDestination.Timeline
        Column(modifier = Modifier.padding(innerPadding)) {
            if (showHomeLevelSyncBanner) {
                SyncErrorBanner(
                    presentation = syncPresentation,
                    onAction = { action ->
                        when (action) {
                            SyncAction.SignIn,
                            SyncAction.ManageStorage,
                            -> onOpenSettings()
                            SyncAction.ReviewConflicts -> onOpenSyncIssues()
                            SyncAction.Retry -> Unit
                        }
                    },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .widthIn(max = 560.dp),
                )
            }
            NavigationSuiteScaffold(
                containerColor = Color.Transparent,
                navigationSuiteColors =
                    NavigationSuiteDefaults.colors(
                        navigationRailContainerColor = Color.Transparent,
                        navigationBarContainerColor = Color.Transparent,
                    ),
                navigationSuiteItems = {
                    HomeRouteDestination.visibleEntries.forEach { destination ->
                        item(
                            selected = destination == currentDestination,
                            onClick = {
                                if (destination != currentDestination) {
                                    haptics.selection()
                                }
                                currentDestination = destination
                            },
                            icon = {
                                Icon(
                                    imageVector =
                                        if (destination == currentDestination) {
                                            destination.selectedIcon
                                        } else {
                                            destination.unselectedIcon
                                        },
                                    contentDescription = destination.label,
                                )
                            },
                            label = { Text(destination.label) },
                        )
                    }
                },
            ) {
                when (currentDestination) {
                    HomeRouteDestination.Timeline -> {
                        val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                        Column(
                            modifier =
                                Modifier
                                    .applyScreenStyles()
                                    .safeDrawingPadding(),
                        ) {
                            SyncIssuesBanner(onOpenSyncIssues = onOpenSyncIssues)
                            val transcriptionState by viewModel.transcriptionState.collectAsStateWithLifecycle()
                            TranscriptionProvider(transcriptionState) {
                                TimelinePane(
                                    uiState =
                                        TimelineUiState(
                                            items = uiState.items,
                                            loadingState = uiState.loadingState,
                                            isLoadingMore = uiState.isLoadingMore,
                                            hasMoreOlderContent = uiState.hasMoreOlderContent,
                                            appendError = uiState.appendError,
                                        ),
                                    onNewEntry = onNewEntry,
                                    onOpenDay = onOpenDay,
                                    onVisibleAudioNoteIdsChanged = viewModel::updateVisibleAudioNoteIds,
                                    onLoadMoreOlder = viewModel::loadMoreOlder,
                                    onProfileClick = onOpenSettings,
                                    onSearchClick = onOpenSearch,
                                    onOpenDraft = onOpenDraft,
                                    onImportBackup = onImportBackup,
                                    timelineSuggestion = uiState.timelineSuggestion,
                                    syncPresentation = syncPresentation,
                                    onSyncAction = { action ->
                                        when (action) {
                                            app.logdate.ui.sync.SyncAction.SignIn,
                                            app.logdate.ui.sync.SyncAction.ManageStorage,
                                            -> onOpenSettings()
                                            app.logdate.ui.sync.SyncAction.ReviewConflicts -> onOpenSyncIssues()
                                            app.logdate.ui.sync.SyncAction.Retry -> Unit
                                        }
                                    },
                                )
                            }
                        }
                    }

                    HomeRouteDestination.LocationHistory -> {
                        locationContent(
                            Modifier
                                .applyScreenStyles()
                                .safeDrawingPadding(),
                        )
                    }

                    HomeRouteDestination.Journals -> {
                        JournalsOverviewScreen(
                            onOpenJournal = onOpenJournal,
                            onBrowseJournals = onBrowseJournals,
                            onCreateJournal = onCreateJournal,
                            modifier = Modifier.applyScreenStyles(),
                        )
                    }

                    HomeRouteDestination.Library -> {
                        libraryContent(Modifier.applyScreenStyles())
                    }

                    HomeRouteDestination.Rewind -> {
                        RewindOverviewScreen(
                            onOpenRewind = onOpenRewind,
                            modifier = Modifier.applyScreenStyles(),
                        )
                    }
                }
            }
        }
    }
}

/**
 * ViewModel for the home screen.
 *
 * Combines the streaming timeline, per-day note selection, and the home recommendation signal
 * into a single [HomeTimelineUiState] flow. The recommendation is produced by
 * [GetHomeRecommendationUseCase], which aggregates multiple data signals (today's entries,
 * unfinished drafts, etc.) and converts the result into a [TimelineSuggestionBlock] for
 * display at the top of the timeline.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(
    private val recentTimelineFlow: Flow<Timeline>,
    private val loadTimelinePage: suspend (TimelinePageRequest) -> TimelinePage,
    private val notesRepository: JournalNotesRepository,
    private val getHomeRecommendation: GetHomeRecommendationUseCase,
    private val linkNoteToEvent: LinkNoteToEventUseCase,
    private val getJournalMembership: GetJournalMembershipUseCase,
    private val transcriptionRepository: TranscriptionRepository,
) : ViewModel() {
    companion object {
        private const val RECENT_TIMELINE_PAGE_SIZE = 50
        private const val APPEND_ERROR_MESSAGE = "Couldn't load older entries."
    }

    constructor(
        getStreamingTimelineUseCase: GetStreamingTimelineUseCase,
        getTimelinePageUseCase: GetTimelinePageUseCase,
        notesRepository: JournalNotesRepository,
        getHomeRecommendation: GetHomeRecommendationUseCase,
        linkNoteToEvent: LinkNoteToEventUseCase,
        getJournalMembership: GetJournalMembershipUseCase,
        transcriptionRepository: TranscriptionRepository,
    ) : this(
        recentTimelineFlow =
            getStreamingTimelineUseCase(
                StreamingTimelineRequest.RecentTimeline(
                    pageSize = RECENT_TIMELINE_PAGE_SIZE,
                ),
            ),
        loadTimelinePage = { request -> getTimelinePageUseCase(request) },
        notesRepository = notesRepository,
        getHomeRecommendation = getHomeRecommendation,
        linkNoteToEvent = linkNoteToEvent,
        getJournalMembership = getJournalMembership,
        transcriptionRepository = transcriptionRepository,
    )

    private val selectedDayFlow = MutableStateFlow<LocalDate?>(null)
    private val appendedTimelineDays = MutableStateFlow<List<TimelineDay>>(emptyList())
    private val isLoadingMore = MutableStateFlow(false)
    private val appendError = MutableStateFlow<String?>(null)
    private val hasLoadedRecentTimeline = MutableStateFlow(false)
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
                SharingStarted.WhileSubscribed(5_000),
                TranscriptionState(requestTranscription = ::requestTranscription),
            )

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

    private val recentTimelineState: StateFlow<Timeline> =
        recentTimelineFlow
            .onEach { hasLoadedRecentTimeline.value = true }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                Timeline(emptyList()),
            )

    private val timelineFeedDays: StateFlow<List<TimelineDay>> =
        combine(recentTimelineState, appendedTimelineDays) { recentTimeline, appendedDays ->
            mergeTimelineDays(existing = appendedDays, incoming = recentTimeline.days)
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            emptyList(),
        )

    private val timelineItems: StateFlow<List<TimelineDayUiState>> =
        timelineFeedDays
            .flatMapLatest { timelineDays ->
                val noteIds = timelineDays.flatMap { day -> day.entries.map(JournalNote::uid) }.toSet()
                getJournalMembership(noteIds).map { membershipMap ->
                    timelineDays.map { timelineDay -> timelineDay.toUiState(membershipMap = membershipMap) }
                }
            }.stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                emptyList(),
            )

    private val selectedItemUiState: StateFlow<TimelineDaySelection> =
        selectedDayFlow
            .map { selectedDay ->
                selectedDay?.let(TimelineDaySelection::DateSelected) ?: TimelineDaySelection.NotSelected
            }.stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                TimelineDaySelection.NotSelected,
            )

    private val selectedDayNotes: StateFlow<List<JournalNote>> =
        selectedDayFlow
            .flatMapLatest { selectedDay ->
                selectedDay?.let(notesRepository::observeNotesForDay) ?: flowOf(emptyList())
            }.stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                emptyList(),
            )

    private val selectedDayUiState: StateFlow<TimelineDayUiState?> =
        combine(
            selectedDayFlow,
            selectedDayNotes,
            timelineFeedDays,
        ) { selectedDayDate, selectedNotes, timelineDays ->
            val day = selectedDayDate ?: return@combine null
            val timelineDay = timelineDays.find { timelineEntry -> timelineEntry.date == day } ?: return@combine null
            val notes = if (selectedNotes.isEmpty()) timelineDay.entries else selectedNotes

            SelectedTimelineDayData(
                timelineDay = timelineDay,
                notes = notes,
            )
        }.flatMapLatest { selectedData ->
            if (selectedData == null) {
                flowOf(null)
            } else {
                val noteIds = selectedData.notes.map(JournalNote::uid).toSet()
                getJournalMembership(noteIds).map { membershipMap ->
                    selectedData.timelineDay.toUiState(
                        overrideNotes = selectedData.notes,
                        membershipMap = membershipMap,
                    )
                }
            }
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            null,
        )

    private val hasMoreOlderContent: StateFlow<Boolean> =
        timelineFeedDays
            .mapLatest { timelineDays ->
                val oldestLoadedTimestamp = timelineDays.oldestLoadedTimestamp() ?: return@mapLatest false
                notesRepository.hasNotesBefore(oldestLoadedTimestamp)
            }.stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                false,
            )

    private val timelineSuggestionState: StateFlow<TimelineSuggestionBlock?> =
        getHomeRecommendation()
            .map { recommendation -> recommendation.toTimelineSuggestionBlock() }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                null,
            )

    val uiState: StateFlow<HomeTimelineUiState> =
        combine(
            combine(
                timelineItems,
                selectedItemUiState,
                selectedDayUiState,
                timelineSuggestionState,
            ) { items, selection, selectedDay, suggestion ->
                HomeTimelineVisualState(
                    items = items,
                    selection = selection,
                    selectedDay = selectedDay,
                    suggestion = suggestion,
                )
            },
            combine(
                hasLoadedRecentTimeline,
                isLoadingMore,
                hasMoreOlderContent,
                appendError,
            ) { hasLoadedRecent, isLoadingMoreOlder, hasMoreOlder, appendOlderError ->
                HomeTimelineLoadingState(
                    hasLoadedRecentTimeline = hasLoadedRecent,
                    isLoadingMore = isLoadingMoreOlder,
                    hasMoreOlderContent = hasMoreOlder,
                    appendError = appendOlderError,
                )
            },
        ) { visualState, loadingState ->
            val showInitialLoading = !loadingState.hasLoadedRecentTimeline && visualState.items.isEmpty()
            HomeTimelineUiState(
                items = visualState.items,
                selectedItem = visualState.selection,
                selectedDay = visualState.selectedDay,
                showEmptyState = loadingState.hasLoadedRecentTimeline && visualState.items.isEmpty(),
                timelineSuggestion = visualState.suggestion,
                isLoading = showInitialLoading,
                isLoadingMore = loadingState.isLoadingMore,
                hasMoreOlderContent = loadingState.hasMoreOlderContent,
                appendError = loadingState.appendError,
                loadingState = if (showInitialLoading) TimelineLoadingState.InitialLoading else TimelineLoadingState.Loaded,
            )
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            HomeTimelineUiState(
                isLoading = true,
                loadingState = TimelineLoadingState.InitialLoading,
            ),
        )

    /**
     * Selects a timeline day for detailed viewing.
     *
     * @param date The date of the day to select
     */
    fun selectDay(date: LocalDate) {
        selectedDayFlow.value = date
    }

    /**
     * Clears the current day selection.
     */
    fun clearSelection() {
        selectedDayFlow.value = null
    }

    /**
     * Attaches a note to an event from the timeline drag-and-drop gesture. The drop
     * payload is plain text, so [noteIdString] and [eventIdString] are the parsed UUID
     * strings — anything that doesn't parse is silently ignored (the drop came from a
     * non-LogDate source). Errors are logged but not surfaced to the user; a failed link
     * is recoverable on the next attempt and the gesture is best-effort.
     */
    fun attachNoteToEvent(
        noteIdString: String,
        eventIdString: String,
    ) {
        val noteId = runCatching { Uuid.parse(noteIdString) }.getOrNull() ?: return
        val eventId = runCatching { Uuid.parse(eventIdString) }.getOrNull() ?: return
        viewModelScope.launch {
            val result = linkNoteToEvent(eventId = eventId, noteId = noteId)
            if (result.isFailure) {
                Napier.w(
                    "Failed to attach note $noteId to event $eventId via drag",
                    result.exceptionOrNull(),
                )
            }
        }
    }

    fun updateVisibleAudioNoteIds(noteIds: Set<Uuid>) {
        visibleAudioNoteIds.value = noteIds
    }

    fun loadMoreOlder() {
        if (isLoadingMore.value) {
            return
        }

        val oldestLoadedTimestamp = timelineFeedDays.value.oldestLoadedTimestamp() ?: return
        if (!hasMoreOlderContent.value) {
            return
        }

        viewModelScope.launch {
            isLoadingMore.value = true
            appendError.value = null

            try {
                val olderPage =
                    loadTimelinePage(
                        TimelinePageRequest(
                            beforeExclusive = oldestLoadedTimestamp,
                            pageSize = RECENT_TIMELINE_PAGE_SIZE,
                        ),
                    )

                if (olderPage.days.isNotEmpty()) {
                    appendedTimelineDays.update { existing ->
                        mergeTimelineDays(existing = existing, incoming = olderPage.days)
                    }
                }
            } catch (error: Exception) {
                Napier.e("Failed to load older timeline history", error)
                appendError.value = APPEND_ERROR_MESSAGE
            } finally {
                isLoadingMore.value = false
            }
        }
    }

    private fun TimelinePlaceVisit.toUiState(): PlaceUiState =
        PlaceUiState(
            id = id,
            title = name,
            latitude = latitude,
            longitude = longitude,
        )

    private fun TimelineDay.toUiState(
        overrideNotes: List<JournalNote> = entries,
        membershipMap: Map<Uuid, List<Journal>> = emptyMap(),
    ): TimelineDayUiState {
        val noteUiStates = overrideNotes.toUiState(membershipMap)
        val placeUiStates = placesVisited.map { place -> place.toUiState() }
        val peopleUiStates = people.map(Person::toUiState)
        val momentUiStates = moments.toMomentUiStates(peopleUiStates)
        val eventUiStates = events.map { it.toDayEventUiState() }

        return createSemanticTimelineDayUiState(
            summary = tldr,
            date = date,
            moments = momentUiStates,
            people = peopleUiStates,
            notes = noteUiStates,
            placesVisited = placeUiStates,
            events = eventUiStates,
            isLoadingSummary = tldr.isEmpty(),
            isLoadingPeople = people.isEmpty() && tldr.isEmpty(),
        )
    }

    private fun List<Moment>.toMomentUiStates(dayPeople: List<PersonUiState>): List<MomentUiState> {
        val heroIndex =
            indices.maxByOrNull { i ->
                val m = this[i]
                m.media.size * 3 + m.textFragments.size * 2 + m.audio.size
            } ?: 0
        return mapIndexed { index, moment ->
            moment.toMomentUiState(isHero = index == heroIndex, dayPeople = dayPeople)
        }
    }

    private fun Moment.toMomentUiState(
        isHero: Boolean,
        dayPeople: List<PersonUiState>,
    ): MomentUiState {
        val timeOfDay = DaylightClassifier().classifyWithoutLocation(estimatedStart)
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
            transcriptionRepository.requestTranscription(noteId)
        }
    }

    private fun requestMissingVisibleTranscriptions(transcriptions: Map<Uuid, TranscriptionData?>) {
        val noteIdsToRequest =
            autoRequestableTimelineTranscriptionIds(
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
                    mediaUris = mediaUris.map { uri -> MediaObjectUiState(uid = uri, uri = uri) },
                    isAiGenerated = isAiGenerated,
                )
            is HomeRecommendation.UpcomingEvent ->
                TimelineSuggestionBlock.UpcomingEvent(
                    eventId = eventId.toString(),
                    title = title,
                    startTime = startTime,
                    placeName = placeName,
                )
            HomeRecommendation.None -> null
        }
}

private fun autoRequestableTimelineTranscriptionIds(
    visibleNoteIds: Set<Uuid>,
    transcriptions: Map<Uuid, TranscriptionData?>,
    alreadyRequestedNoteIds: Set<Uuid>,
): Set<Uuid> =
    visibleNoteIds
        .filterNot { noteId -> noteId in alreadyRequestedNoteIds }
        .filter { noteId ->
            when (transcriptions[noteId]?.status) {
                null,
                TranscriptionStatus.FAILED,
                -> true
                TranscriptionStatus.COMPLETED,
                TranscriptionStatus.IN_PROGRESS,
                TranscriptionStatus.PENDING,
                -> false
            }
        }.toSet()

private fun mergeTimelineDays(
    existing: List<TimelineDay>,
    incoming: List<TimelineDay>,
): List<TimelineDay> {
    val daysByDate = LinkedHashMap<LocalDate, TimelineDay>()

    existing.sortedByDescending(TimelineDay::date).forEach { day ->
        daysByDate[day.date] = day
    }
    incoming.sortedByDescending(TimelineDay::date).forEach { day ->
        daysByDate[day.date] = day
    }

    return daysByDate.values.sortedByDescending(TimelineDay::date)
}

private fun List<TimelineDay>.oldestLoadedTimestamp() = flatMap(TimelineDay::entries).minOfOrNull { note -> note.creationTimestamp }

private data class HomeTimelineVisualState(
    val items: List<TimelineDayUiState>,
    val selection: TimelineDaySelection,
    val selectedDay: TimelineDayUiState?,
    val suggestion: TimelineSuggestionBlock?,
)

private data class SelectedTimelineDayData(
    val timelineDay: TimelineDay,
    val notes: List<JournalNote>,
)

private data class HomeTimelineLoadingState(
    val hasLoadedRecentTimeline: Boolean,
    val isLoadingMore: Boolean,
    val hasMoreOlderContent: Boolean,
    val appendError: String?,
)
