@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.core.main

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.logdate.client.awareness.daylight.DaylightClassifier
import app.logdate.client.domain.recommendation.GetHomeRecommendationUseCase
import app.logdate.client.domain.recommendation.HomeRecommendation
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
import app.logdate.feature.journals.ui.JournalClickCallback
import app.logdate.feature.journals.ui.JournalsOverviewScreen
import app.logdate.feature.rewind.ui.RewindOverviewScreen
import app.logdate.shared.model.Person
import app.logdate.ui.common.applyScreenStyles
import app.logdate.ui.location.PlaceUiState
import app.logdate.ui.profiles.PersonUiState
import app.logdate.ui.profiles.toUiState
import app.logdate.ui.timeline.AudioNoteUiState
import app.logdate.ui.timeline.HomeTimelineUiState
import app.logdate.ui.timeline.ImageNoteUiState
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
    locationContent: @Composable (Modifier) -> Unit = {},
    libraryContent: @Composable (Modifier) -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = koinViewModel(),
) {
    var currentDestination: HomeRouteDestination by rememberSaveable {
        mutableStateOf(HomeRouteDestination.Timeline)
    }
    val snackbarHostState = remember { SnackbarHostState() }

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
                        onClick = { currentDestination = destination },
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
            modifier = Modifier.padding(innerPadding),
        ) {
            when (currentDestination) {
                HomeRouteDestination.Timeline -> {
                    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
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
                        onOpenDay = { date -> viewModel.selectDay(date) },
                        onLoadMoreOlder = viewModel::loadMoreOlder,
                        onProfileClick = onOpenSettings,
                        onSearchClick = onOpenSearch,
                        onOpenDraft = onOpenDraft,
                        onImportBackup = onImportBackup,
                        timelineSuggestion = uiState.timelineSuggestion,
                        modifier =
                            Modifier
                                .applyScreenStyles()
                                .safeDrawingPadding(),
                    )
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
    )

    private val selectedDayFlow = MutableStateFlow<LocalDate?>(null)
    private val appendedTimelineDays = MutableStateFlow<List<TimelineDay>>(emptyList())
    private val isLoadingMore = MutableStateFlow(false)
    private val appendError = MutableStateFlow<String?>(null)
    private val hasLoadedRecentTimeline = MutableStateFlow(false)

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
            .map { timelineDays ->
                timelineDays.map { timelineDay -> timelineDay.toUiState() }
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

            timelineDay.toUiState(
                overrideNotes = if (selectedNotes.isEmpty()) timelineDay.entries else selectedNotes,
            )
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

    private fun TimelineDay.toUiState(overrideNotes: List<JournalNote> = entries): TimelineDayUiState {
        val noteUiStates = overrideNotes.toUiState()
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
                    )
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

private data class HomeTimelineLoadingState(
    val hasLoadedRecentTimeline: Boolean,
    val isLoadingMore: Boolean,
    val hasMoreOlderContent: Boolean,
    val appendError: String?,
)
