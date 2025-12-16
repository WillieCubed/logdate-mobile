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
import app.logdate.client.domain.timeline.GetTimelineUseCase
import app.logdate.client.repository.journals.JournalNote
import app.logdate.feature.journals.ui.JournalClickCallback
import app.logdate.feature.journals.ui.JournalsOverviewScreen
import app.logdate.feature.location.timeline.ui.LocationTimelineScreen
import app.logdate.feature.rewind.ui.RewindOverviewScreen
import app.logdate.shared.model.Person
import app.logdate.ui.common.applyScreenStyles
import app.logdate.ui.profiles.toUiState
import app.logdate.ui.timeline.AudioNoteUiState
import app.logdate.ui.timeline.HomeTimelineUiState
import app.logdate.ui.timeline.ImageNoteUiState
import app.logdate.ui.timeline.TextNoteUiState
import app.logdate.ui.timeline.TimelineDaySelection
import app.logdate.ui.timeline.TimelineDayUiState
import app.logdate.ui.timeline.TimelineLoadingState
import app.logdate.ui.timeline.TimelinePane
import app.logdate.ui.timeline.TimelineUiState
import app.logdate.ui.timeline.VideoNoteUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun HomeScreen(
    onNewEntry: () -> Unit,
    onOpenJournal: JournalClickCallback,
    onCreateJournal: () -> Unit,
    onOpenSettings: () -> Unit = {},
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
        modifier = modifier
    ) { currentDestination ->
        when (currentDestination) {
            HomeRouteDestination.Timeline -> {
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                TimelinePane(
                    uiState = TimelineUiState(items = uiState.items),
                    onNewEntry = onNewEntry,
                    onShareMemory = {},
                    onOpenDay = { date -> viewModel.selectDay(date) },
                    onProfileClick = onOpenSettings,
                    // onHistoryClick handled in TimelinePaneScreen
                    modifier = Modifier
                        .applyScreenStyles()
                        .safeDrawingPadding()
                )
            }

            HomeRouteDestination.Rewind -> {
                RewindOverviewScreen(
                    onOpenRewind = { /* TODO: Handle rewind navigation */ },
                    modifier = Modifier.applyScreenStyles()
                )
            }

            HomeRouteDestination.Journals -> {
                JournalsOverviewScreen(
                    onOpenJournal = onOpenJournal,
                    onBrowseJournals = { /* TODO: Handle browse navigation */ },
                    onCreateJournal = onCreateJournal,
                    modifier = Modifier.applyScreenStyles()
                )
            }

            HomeRouteDestination.LocationHistory -> {
                LocationTimelineScreen(
                    modifier = Modifier
                        .applyScreenStyles()
                        .safeDrawingPadding()
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
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            HomeRouteDestination.ALL.forEach { destination ->
                NavigationBarItem(
                    icon = {
                        Icon(
                            imageVector = if (destination == currentDestination) {
                                destination.selectedIcon
                            } else {
                                destination.unselectedIcon
                            },
                            contentDescription = destination.label
                        )
                    },
                    label = { Text(destination.label) },
                    selected = destination == currentDestination,
                    onClick = { currentDestination = destination }
                )
            }
        }

        if (showFab) {
            FloatingActionButton(
                onClick = { onFabClick(currentDestination) },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .systemBarsPadding()
                    .padding(16.dp)
            ) {
                Icon(
                    Icons.Default.EditNote,
                    contentDescription = "Create new entry",
                )
            }
        }
    }
}


class HomeViewModel(
    getTimelineUseCase: GetTimelineUseCase,
    private val fetchNotesForDayUseCase: app.logdate.client.domain.notes.FetchNotesForDayUseCase,
    private val notesRepository: app.logdate.client.repository.journals.JournalNotesRepository,
) : ViewModel() {
    private val _selectedItemUiState =
        MutableStateFlow<TimelineDaySelection>(TimelineDaySelection.NotSelected)

    private val selectedNotes = MutableStateFlow(emptyList<JournalNote>())
    private val selectedDayFlow = MutableStateFlow<LocalDate?>(null)
    
    val uiState: StateFlow<HomeTimelineUiState> = combine(
        getTimelineUseCase(),
        selectedNotes,
        _selectedItemUiState,
        selectedDayFlow
    ) { timeline, notes, selection, selectedDayDate ->
        val items = timeline.days.map { day ->
            TimelineDayUiState(
                summary = day.tldr,
                date = day.date,
                people = day.people.map(Person::toUiState),
                events = day.events,
                notes = if (day.date == selectedDayDate) {
                    // Only include notes for the selected day
                    notes.map {
                        when (it) {
                            is JournalNote.Text -> TextNoteUiState(
                                noteId = it.uid,
                                text = it.content,
                                timestamp = it.creationTimestamp,
                            )
                            is JournalNote.Image -> ImageNoteUiState(
                                noteId = it.uid,
                                uri = it.mediaRef,
                                timestamp = it.creationTimestamp,
                            )
                            is JournalNote.Audio -> AudioNoteUiState(
                                noteId = it.uid,
                                uri = it.mediaRef,
                                timestamp = it.creationTimestamp,
                            )
                            is JournalNote.Video -> VideoNoteUiState(
                                noteId = it.uid,
                                uri = it.mediaRef,
                                timestamp = it.creationTimestamp,
                            )
                        }
                    }
                } else {
                    emptyList()
                }
            )
        }
        
        // Determine the selected day based on the current selection state
        val selectedDay = when (selection) {
            is TimelineDaySelection.Selected -> {
                items.find { it.date == selection.day }
            }
            is TimelineDaySelection.DateSelected -> {
                items.find { it.date == selection.date }
            }
            TimelineDaySelection.NotSelected -> null
        }
        
        println("Updating HomeViewModel state: selectedDay=$selectedDay, selection=$selection")
        
        HomeTimelineUiState(
            items = items,
            selectedItem = selection,
            selectedDay = selectedDay,
            showEmptyState = items.isEmpty(),
            isLoading = false,
            loadingState = TimelineLoadingState.Loaded
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeTimelineUiState())
        
    /**
     * Selects a timeline day for detailed viewing.
     *
     * @param date The date of the day to select
     */
    fun selectDay(date: LocalDate) {
        println("Selecting day: $date")
        _selectedItemUiState.value = TimelineDaySelection.Selected(
            id = date.toString(),
            day = date
        )
        selectedDayFlow.value = date
        
        // Automatically fetch notes when a day is selected
        fetchNotesForDate(date)
    }
    
    /**
     * Clears the current day selection.
     */
    fun clearSelection() {
        _selectedItemUiState.value = TimelineDaySelection.NotSelected
        selectedDayFlow.value = null
    }
    
    /**
     * Fetches notes for a specific date.
     * 
     * @param date The date to fetch notes for
     */
    fun fetchNotesForDate(date: LocalDate) {
        println("Fetching notes for date: $date")
        
        io.github.aakira.napier.Napier.d(
            tag = "HomeViewModel",
            message = "EXPLICIT FETCH: Getting notes for date $date"
        )
        
        // Create time range for the day
        val tz = TimeZone.currentSystemDefault()
        val start = date.atStartOfDayIn(tz)
        val end = start + kotlin.time.Duration.parse("24h")
        
        // Simply fetch notes for this date range
        viewModelScope.launch {
            try {
                // Direct repository call for simplicity
                val notes = notesRepository.observeNotesInRange(start, end).first()
                
                io.github.aakira.napier.Napier.d(
                    tag = "HomeViewModel", 
                    message = "EXPLICIT FETCH RESULT: Found ${notes.size} notes for $date: " +
                        "${notes.count { it is JournalNote.Text }} text, " +
                        "${notes.count { it is JournalNote.Image }} image, " +
                        "${notes.count { it is JournalNote.Audio }} audio, " +
                        "${notes.count { it is JournalNote.Video }} video notes"
                )
                
                // Log audio notes specifically
                val audioNotes = notes.filterIsInstance<JournalNote.Audio>()
                if (audioNotes.isNotEmpty()) {
                    io.github.aakira.napier.Napier.d(
                        tag = "HomeViewModel",
                        message = "AUDIO NOTES IN FETCH: ${audioNotes.size} audio notes for date $date - " +
                            "UIDs: ${audioNotes.map { it.uid }}, " +
                            "URIs: ${audioNotes.map { it.mediaRef }}"
                    )
                }
                
                // Update the selected notes state
                selectedNotes.value = notes
                
            } catch (e: Exception) {
                io.github.aakira.napier.Napier.e("Failed to fetch notes for date $date", e)
                selectedNotes.value = emptyList()
            }
        }
    }
}
