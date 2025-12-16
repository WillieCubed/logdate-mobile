package app.logdate.feature.timeline.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import app.logdate.ui.audio.LocalAudioPlaybackState
import app.logdate.ui.audio.LocalTranscriptionState
import app.logdate.ui.audio.TranscriptionState
import app.logdate.ui.common.PlatformBackHandler
import app.logdate.ui.timeline.HomeTimelineUiState
import app.logdate.ui.timeline.TimelineDaySelection
import app.logdate.ui.timeline.TimelinePane
import app.logdate.feature.timeline.ui.details.TimelineDayDetailPanel
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.viewmodel.koinViewModel
import kotlin.uuid.Uuid

@Composable
fun TimelineRoute(
    onOpenTimelineItem: (uid: Uuid) -> Unit,
    onNewEntry: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TimelineViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    
    // Get the audio playback state from the ViewModel
    val audioPlaybackState by viewModel.audioPlaybackState.collectAsState()
    
    // Get transcription state from the ViewModel
    val transcriptionState by viewModel.transcriptionState.collectAsState()
    
    // Provide both audio playback and transcription state to all descendants
    CompositionLocalProvider(
        LocalAudioPlaybackState provides audioPlaybackState,
        LocalTranscriptionState provides transcriptionState
    ) {
        TimelineScreen(
            state = state,
            onOpenTimelineItem = onOpenTimelineItem,
            onDeleteTimelineItem = { viewModel.deleteItem(it) },
            onNewEntry = onNewEntry,
            onAddToMemory = { memoryId -> viewModel.showAddToMemoriesSnackbar(memoryId) },
            onDismissSnackbar = { viewModel.dismissSnackbar() },
            onSetSelectedDay = { date -> viewModel.setSelectedDay(date) },
            birthday = viewModel.birthday.collectAsState().value,
            modifier = modifier,
        )
    }
}

data class TimelineSelectionItem(
    val timelineItemId: String,
)

/**
 * A dialog that appears when the user attempts to delete a timeline entry.
 *
 * @param onConfirmDelete The action to take when the user confirms deletion.
 * @param onDismissRequest The action to take when the user dismisses the dialog.
 */
@Composable
internal fun DeleteEntryDialog(
    onDismissRequest: () -> Unit,
    onConfirmDelete: () -> Unit,
) {
    AlertDialog(
        title = { Text("Delete entry?") },
        text = { Text("Are you sure you want to delete this entry?") },
        onDismissRequest = onDismissRequest,
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text("Cancel")
            }
        },
        confirmButton = {
            Button(onClick = onConfirmDelete) {
                Text("Delete")
            }
        },
    )
}


@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
internal fun TimelineScreen(
    state: HomeTimelineUiState,
    onOpenTimelineItem: (uid: Uuid) -> Unit,
    onDeleteTimelineItem: (uid: Uuid) -> Unit,
    onNewEntry: () -> Unit,
    onAddToMemory: (memoryId: String) -> Unit,
    onDismissSnackbar: () -> Unit,
    onSetSelectedDay: (LocalDate) -> Unit,
    birthday: Instant?,
    modifier: Modifier = Modifier,
) {
    var showDeletionDialog by rememberSaveable { mutableStateOf(false) }
    var pendingDeletionUid by remember { mutableStateOf<Uuid?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    
    // Birthday is now passed as a parameter
    
    // Handle showing snackbar
    LaunchedEffect(state.snackbarMessage) {
        state.snackbarMessage?.let {
            snackbarHostState.showSnackbar(message = it)
            onDismissSnackbar()
        }
    }

    val navigator = rememberListDetailPaneScaffoldNavigator<TimelineSelectionItem>()
    val coroutineScope = rememberCoroutineScope()

    // Only handle back navigation for the navigator
    PlatformBackHandler(navigator.canNavigateBack()) {
        coroutineScope.launch {
            navigator.navigateBack()
        }
    }

    if (showDeletionDialog) {
        DeleteEntryDialog(
            onDismissRequest = {
                showDeletionDialog = false
                pendingDeletionUid = null
            },
            onConfirmDelete = {
                showDeletionDialog = false
                pendingDeletionUid?.let(onDeleteTimelineItem)
            },
        )
    }

    // Add snackbar host for showing messages
    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    ) { paddingValues ->
        // Show TimelineDayDetailPanel when a day is selected
        when (state.selectedItem) {
            is TimelineDaySelection.DateSelected -> {
                // A day is selected, show its details
                state.selectedDay?.let { selectedDay ->
                    TimelineDayDetailPanel(
                        uiState = selectedDay,
                        onExit = { onSetSelectedDay(LocalDate.fromEpochDays(0)) }, // Reset selection when the user clicks back
                        modifier = modifier.padding(paddingValues),
                    )
                }
            }
            else -> {
                // No day selected, show the regular timeline
                TimelinePane(
                    uiState = app.logdate.ui.timeline.TimelineUiState(
                        items = state.items
                    ),
                    onNewEntry = onNewEntry,
                    onShareMemory = { /* Handle share memory */ },
                    onOpenDay = { date -> 
                        onSetSelectedDay(date) 
                    },
                    onAddToMemory = onAddToMemory,
                    birthday = birthday,
                    modifier = modifier.padding(paddingValues),
                )
            }
        }
    }

    // Commented out adaptive layout for future implementation
    // ListDetailPaneScaffold(
    //     directive = navigator.scaffoldDirective,
    //     detailPane = {
    //     },
    //     listPane = {
    //         AnimatedPane {
    //             TimelinePane(
    //                 state = state,
    //                 onItemSelected = onOpenTimelineItem,
    //                 onItemDeleted = {
    //                     pendingDeletionUid = it;
    //                     showDeletionDialog = true
    //                 },
    //                 onNewEntry = onNewEntry,
    //                 onAddToMemory = onAddToMemory,
    //                 birthday = birthday,
    //                 modifier = modifier,
    //             )
    //         }
    //     },
    //     value = navigator.scaffoldValue,
    //     modifier = modifier,
    // )
}

@Preview
@Composable
private fun TimelineScreenPreview() {
    // This won't show the actual TimelinePane in the preview
    // because we need a real ViewModel, but it will validate the composable structure
    TimelineScreen(
        state = HomeTimelineUiState(),
        onOpenTimelineItem = {},
        onDeleteTimelineItem = {},
        onNewEntry = {},
        onAddToMemory = {},
        onDismissSnackbar = {},
        onSetSelectedDay = {},
        birthday = null,
    )
}

