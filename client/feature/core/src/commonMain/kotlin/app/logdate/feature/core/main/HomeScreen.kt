package app.logdate.feature.core.main

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.logdate.client.domain.timeline.GetTimelineUseCase
import app.logdate.client.repository.journals.JournalNote
import app.logdate.shared.model.Person
import app.logdate.ui.profiles.toUiState
import app.logdate.ui.timeline.HomeTimelineUiState
import app.logdate.ui.timeline.TextNoteUiState
import app.logdate.ui.timeline.TimelineDaySelection
import app.logdate.ui.timeline.TimelineDayUiState
import app.logdate.ui.timeline.TimelinePane
import app.logdate.ui.timeline.TimelineUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun HomeScreen(
    onNewEntry: () -> Unit,
    viewModel: HomeViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainer)) {
        TimelinePane(
            uiState = TimelineUiState(items = uiState.items),
            onNewEntry = onNewEntry,
            onShareMemory = {},
            onOpenDay = {},
            modifier = Modifier.safeDrawingPadding()
        )
        FloatingActionButton(
            onClick = onNewEntry,
            modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.EditNote,
                contentDescription = "Create new entry",
            )
        }
    }
}


class HomeViewModel(
    getTimelineUseCase: GetTimelineUseCase,
) : ViewModel() {
    private val _selectedItemUiState =
        MutableStateFlow<TimelineDaySelection>(TimelineDaySelection.NotSelected)

    private val selectedNotes = MutableStateFlow(emptyList<JournalNote>())
    val uiState: StateFlow<HomeTimelineUiState> = getTimelineUseCase()
        .combine(selectedNotes) { timeline, notes ->
            HomeTimelineUiState(
                items = timeline.days.map { day ->
                    TimelineDayUiState(
                        summary = day.tldr,
                        date = day.date,
                        people = day.people.map(Person::toUiState),
                        events = day.events,
                        notes = notes.map {
                            when (it) {
                                is JournalNote.Text -> TextNoteUiState(
                                    noteId = it.uid,
                                    text = it.content,
                                    timestamp = it.creationTimestamp,
                                )

                                else -> TODO()
                            }
                        }
                    )
                },
                selectedItem = _selectedItemUiState.value
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeTimelineUiState())
}


//@Composable
//internal fun HomeScaffoldWrapper(
//    showFab: Boolean,
//    onFabClick: (HomeRouteDestination) -> Unit,
//    content: @Composable (HomeRouteDestination) -> Unit = {},
//) {
//    var currentDestination: HomeRouteDestination by rememberSaveable {
//        mutableStateOf(
//            HomeRouteDestination.Timeline
//        )
//    }
//    val windowSize = with(LocalDensity.current) {
//        currentWindowSize().toSize().toDpSize()
//    }
//    val layoutType = when {
//        windowSize.width < 600.dp -> NavigationSuiteType.NavigationBar
//        windowSize.width < 1200.dp -> NavigationSuiteType.NavigationRail
//        else -> NavigationSuiteType.NavigationDrawer
//    }/* if (windowSize.width >= 1200.dp) {
//    NavigationSuiteType.NavigationDrawer
//} else {*/
//    NavigationSuiteScaffoldDefaults.calculateFromAdaptiveInfo(
//        currentWindowAdaptiveInfo()
//    )/*}*/
//
//    val fabSize by animateDpAsState(
//        targetValue = if (showFab) 88.dp else 0.dp,
//        label = "fabSize",
//    )
//
//    NavigationSuiteScaffold(
//        containerColor = MaterialTheme.colorScheme.surfaceContainer,
//        layoutType = layoutType,
//        navigationSuiteColors = NavigationSuiteDefaults.colors(
//            navigationBarContainerColor = MaterialTheme.colorScheme.surfaceContainer,
//            navigationRailContainerColor = MaterialTheme.colorScheme.surfaceContainer,
//            navigationDrawerContainerColor = MaterialTheme.colorScheme.surfaceContainer,
//        ),
//        navigationSuiteItems = {
//            if (layoutType == NavigationSuiteType.NavigationRail) {
//                // Put FAB here
//            }
//            HomeRouteDestination.ALL.forEach {
//                item(
//                    selected = it == currentDestination,
//                    onClick = {
//                        currentDestination = it
//                    },
//                    icon = {
//                        Icon(
//                            imageVector = if (it == currentDestination) {
//                                it.selectedIcon
//                            } else {
//                                it.unselectedIcon
//                            },
//                            contentDescription = null,
//                        )
//                    },
//                    label = {
//                        Text(it.label)
//                    },
//                )
//            }
//        },
//    ) {
//        Box(
//            Modifier.fillMaxSize()
//        ) {
//            content(currentDestination)
//            Box(
//                modifier = Modifier
//                    .align(Alignment.BottomCenter)
//                    .systemBarsPadding()
//                    .offset(y = (-16).dp),
////                enter = fadeIn() + expandIn { IntSize(width = 1, height = 1) },
//            ) {
//                LargeFloatingActionButton(
//                    modifier = Modifier.size(fabSize),
//                    onClick = {
//                        onFabClick(currentDestination)
//                    },
//                ) {
//                    Icon(
//                        Icons.Default.EditNote,
//                        contentDescription = "Create new entry",
//                    )
//                }
//            }
//        }
//    }
//}