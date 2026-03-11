@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.core.main

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.logdate.client.domain.recommendation.GetHomeRecommendationUseCase
import app.logdate.client.domain.recommendation.HomeRecommendation
import app.logdate.client.domain.timeline.GetStreamingTimelineUseCase
import app.logdate.client.domain.timeline.GetTimelinePageUseCase
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
import app.logdate.ui.profiles.toUiState
import app.logdate.ui.timeline.AudioNoteUiState
import app.logdate.ui.timeline.HomeTimelineUiState
import app.logdate.ui.timeline.ImageNoteUiState
import app.logdate.ui.timeline.TextNoteUiState
import app.logdate.ui.timeline.TimelineDaySelection
import app.logdate.ui.timeline.TimelineDayUiState
import app.logdate.ui.timeline.TimelineLoadingState
import app.logdate.ui.timeline.TimelinePane
import app.logdate.ui.timeline.TimelineSuggestionBlock
import app.logdate.ui.timeline.TimelineUiState
import app.logdate.ui.timeline.VideoNoteUiState
import app.logdate.ui.timeline.createTimelineDayUiState
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
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
    locationContent: @Composable (Modifier) -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = koinViewModel(),
) {
    HomeScaffoldWrapper(
        showFab = true,
        onFabClick = { destination ->
            when (destination) {
                HomeRouteDestination.Timeline -> onNewEntry()
                HomeRouteDestination.Journals -> onCreateJournal()
                else -> onNewEntry()
            }
        },
        modifier = modifier,
    ) { currentDestination ->
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
                    onShareMemory = {},
                    onOpenDay = { date -> viewModel.selectDay(date) },
                    onLoadMoreOlder = viewModel::loadMoreOlder,
                    onProfileClick = onOpenSettings,
                    timelineSuggestion = uiState.timelineSuggestion,
                    // onHistoryClick handled in TimelinePaneScreen
                    modifier =
                        Modifier
                            .applyScreenStyles()
                            .safeDrawingPadding(),
                )
            }

            HomeRouteDestination.Rewind -> {
                RewindOverviewScreen(
                    onOpenRewind = onOpenRewind,
                    modifier = Modifier.applyScreenStyles(),
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

            HomeRouteDestination.LocationHistory -> {
                locationContent(
                    Modifier
                        .applyScreenStyles()
                        .safeDrawingPadding(),
                )
            }
        }
    }
}

@Composable
internal fun HomeScaffoldWrapper(
    showFab: Boolean,
    onFabClick: (HomeRouteDestination) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (HomeRouteDestination) -> Unit = {},
) {
    var currentDestination: HomeRouteDestination by rememberSaveable {
        mutableStateOf(HomeRouteDestination.Timeline)
    }

    Box(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainer)) {
        content(currentDestination)

        // Bottom navigation bar
        NavigationBar(
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            HomeRouteDestination.ALL.forEach { destination ->
                NavigationBarItem(
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
                    selected = destination == currentDestination,
                    onClick = { currentDestination = destination },
                )
            }
        }

        if (showFab) {
            FloatingActionButton(
                onClick = { onFabClick(currentDestination) },
                modifier =
                    Modifier
                        .align(Alignment.BottomEnd)
                        .systemBarsPadding()
                        .padding(16.dp),
            ) {
                Icon(
                    Icons.Default.EditNote,
                    contentDescription = stringResource(Res.string.create_new_entry),
                )
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

    private val selectedItemUiState =
        MutableStateFlow<TimelineDaySelection>(TimelineDaySelection.NotSelected)

    private val selectedNotes = MutableStateFlow(emptyList<JournalNote>())
    private val selectedDayFlow = MutableStateFlow<LocalDate?>(null)
    private val loadedTimelineDays = MutableStateFlow<List<TimelineDay>>(emptyList())
    private val isLoadingMore = MutableStateFlow(false)
    private val appendError = MutableStateFlow<String?>(null)

    private val timelineInputs =
        combine(
            loadedTimelineDays,
            notesRepository.allNotesObserved,
            selectedNotes,
            selectedItemUiState,
            selectedDayFlow,
        ) { timelineDays, allNotes, notes, selection, selectedDayDate ->
            HomeTimelineInputs(
                timelineDays = timelineDays,
                allNotes = allNotes,
                selectedNotes = notes,
                selection = selection,
                selectedDayDate = selectedDayDate,
            )
        }

    init {
        viewModelScope.launch {
            recentTimelineFlow.collect { timeline ->
                loadedTimelineDays.value =
                    if (timeline.days.isEmpty()) {
                        emptyList()
                    } else {
                        mergeRecentTimelineDays(
                            existing = loadedTimelineDays.value,
                            recent = timeline.days,
                        )
                    }
            }
        }
    }

    val uiState: StateFlow<HomeTimelineUiState> =
        combine(
            timelineInputs,
            getHomeRecommendation(),
            isLoadingMore,
            appendError,
        ) { inputs, recommendation, isLoadingMoreOlder, appendOlderError ->
            val items =
                inputs.timelineDays.map { day ->
                    day.toUiState(
                        overrideNotes =
                            if (day.date == inputs.selectedDayDate && inputs.selectedNotes.isNotEmpty()) {
                                inputs.selectedNotes
                            } else {
                                day.entries
                            },
                    )
                }

            // Determine the selected day based on the current selection state
            val selectedDay =
                when (inputs.selection) {
                    is TimelineDaySelection.Selected -> {
                        items.find { it.date == inputs.selection.day }
                    }
                    is TimelineDaySelection.DateSelected -> {
                        items.find { it.date == inputs.selection.date }
                    }
                    TimelineDaySelection.NotSelected -> null
                }

            val oldestLoadedTimestamp = inputs.timelineDays.oldestLoadedTimestamp()
            val hasMoreOlderContent =
                oldestLoadedTimestamp?.let { cursor ->
                    inputs.allNotes.any { note -> note.creationTimestamp < cursor }
                } ?: false

            HomeTimelineUiState(
                items = items,
                selectedItem = inputs.selection,
                selectedDay = selectedDay,
                showEmptyState = items.isEmpty(),
                timelineSuggestion =
                    when (recommendation) {
                        is HomeRecommendation.CompleteYourDraft ->
                            TimelineSuggestionBlock.OngoingEvent(
                                memoryId = recommendation.draftId.toString(),
                                message =
                                    "You have an unfinished entry." +
                                        recommendation.notePreview?.let { " \"${it.take(60)}\"" }.orEmpty(),
                            )
                        is HomeRecommendation.CaptureToday ->
                            TimelineSuggestionBlock.OngoingEvent(
                                memoryId = "",
                                message = recommendation.message,
                            )
                        HomeRecommendation.None -> null
                    },
                isLoading = items.isEmpty(),
                isLoadingMore = isLoadingMoreOlder,
                hasMoreOlderContent = hasMoreOlderContent,
                appendError = appendOlderError,
                loadingState = if (items.isEmpty()) TimelineLoadingState.InitialLoading else TimelineLoadingState.Loaded,
            )
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
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
        selectedItemUiState.value =
            TimelineDaySelection.Selected(
                id = date.toString(),
                day = date,
            )
        selectedDayFlow.value = date

        // Automatically fetch notes when a day is selected
        fetchNotesForDate(date)
    }

    /**
     * Clears the current day selection.
     */
    fun clearSelection() {
        selectedItemUiState.value = TimelineDaySelection.NotSelected
        selectedDayFlow.value = null
    }

    fun loadMoreOlder() {
        if (isLoadingMore.value) {
            return
        }

        val oldestLoadedTimestamp = loadedTimelineDays.value.oldestLoadedTimestamp() ?: return
        if (!uiState.value.hasMoreOlderContent) {
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
                    loadedTimelineDays.update { existing ->
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

    /**
     * Fetches notes for a specific date.
     *
     * @param date The date to fetch notes for
     */
    fun fetchNotesForDate(date: LocalDate) {
        Napier.d(
            tag = "HomeViewModel",
            message = "EXPLICIT FETCH: Getting notes for date $date",
        )

        // Create time range for the day
        val tz = TimeZone.currentSystemDefault()
        val startInstant = date.atStartOfDayIn(tz)
        val endInstant = date.plus(1, DateTimeUnit.DAY).atStartOfDayIn(tz)

        // Simply fetch notes for this date range
        viewModelScope.launch {
            try {
                // Direct repository call for simplicity
                val notes = notesRepository.observeNotesInRange(startInstant, endInstant).first()

                Napier.d(
                    tag = "HomeViewModel",
                    message =
                        "EXPLICIT FETCH RESULT: Found ${notes.size} notes for $date: " +
                            "${notes.count { it is JournalNote.Text }} text, " +
                            "${notes.count { it is JournalNote.Image }} image, " +
                            "${notes.count { it is JournalNote.Audio }} audio, " +
                            "${notes.count { it is JournalNote.Video }} video notes",
                )

                // Log audio notes specifically
                val audioNotes = notes.filterIsInstance<JournalNote.Audio>()
                if (audioNotes.isNotEmpty()) {
                    Napier.d(
                        tag = "HomeViewModel",
                        message =
                            "AUDIO NOTES IN FETCH: ${audioNotes.size} audio notes for date $date - " +
                                "UIDs: ${audioNotes.map { it.uid }}, " +
                                "URIs: ${audioNotes.map { it.mediaRef }}",
                    )
                }

                // Update the selected notes state
                selectedNotes.value = notes
            } catch (e: Exception) {
                Napier.e("Failed to fetch notes for date $date", e)
                selectedNotes.value = emptyList()
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

    private fun TimelineDay.toUiState(overrideNotes: List<JournalNote> = entries): TimelineDayUiState =
        createTimelineDayUiState(
            summary = tldr,
            date = date,
            people = people.map(Person::toUiState),
            events = events,
            placesVisited = placesVisited.map { place -> place.toUiState() },
            notes = overrideNotes.toUiState(),
            isLoadingSummary = tldr.isEmpty(),
            isLoadingPeople = people.isEmpty() && tldr.isEmpty(),
        )

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
}

private fun mergeRecentTimelineDays(
    existing: List<TimelineDay>,
    recent: List<TimelineDay>,
): List<TimelineDay> {
    if (recent.isEmpty()) {
        return emptyList()
    }

    val oldestRecentDate = recent.minOf(TimelineDay::date)
    val retainedExistingDays =
        existing.filter { day ->
            day.date < oldestRecentDate || recent.any { recentDay -> recentDay.date == day.date }
        }

    return mergeTimelineDays(existing = retainedExistingDays, incoming = recent)
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

private data class HomeTimelineInputs(
    val timelineDays: List<TimelineDay>,
    val allNotes: List<JournalNote>,
    val selectedNotes: List<JournalNote>,
    val selection: TimelineDaySelection,
    val selectedDayDate: LocalDate?,
)
