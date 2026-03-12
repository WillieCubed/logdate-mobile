@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.timeline.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import app.logdate.ui.audio.LocalTranscriptionState
import app.logdate.ui.timeline.HomeTimelineUiState
import app.logdate.ui.timeline.TimelinePane
import kotlinx.datetime.LocalDate
import org.koin.compose.viewmodel.koinViewModel
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * Timeline screen showing journal entries for the current and past dates.
 *
 * This screen displays a list of journal entries organized by date, with options to view details,
 * edit entries, delete entries, or open entries in new windows for multi-window editing on supported devices.
 * The screen also handles audio playback and transcription state.
 *
 * @param onOpenTimelineItem Callback invoked when a timeline entry is selected to view/edit details.
 *        Receives the entry's unique identifier.
 * @param onNewEntry Callback invoked when the user initiates creation of a new entry
 * @param onOpenEntryInNewWindow Callback invoked when the user selects "Open in New Window" from an entry's context menu.
 *        Receives the entry ID to open in a separate editor window. Enables multi-window editing on supported devices.
 * @param modifier Optional Compose modifier for customizing the screen's layout and appearance
 * @param viewModel The ViewModel providing timeline state and event handling. Typically injected via Koin.
 */
@Composable
fun TimelineRoute(
    onOpenTimelineItem: (uid: Uuid) -> Unit,
    onNewEntry: () -> Unit,
    onOpenEntryInNewWindow: (entryId: Uuid) -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: TimelineViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val transcriptionState by viewModel.transcriptionState.collectAsState()

    CompositionLocalProvider(
        LocalTranscriptionState provides transcriptionState,
    ) {
        TimelineScreen(
            state = state,
            onNewEntry = onNewEntry,
            onDismissSnackbar = { viewModel.dismissSnackbar() },
            onSetSelectedDay = { date -> viewModel.setSelectedDay(date) },
            birthday = viewModel.birthday.collectAsState().value,
            modifier = modifier,
        )
    }
}

@Composable
internal fun TimelineScreen(
    state: HomeTimelineUiState,
    onNewEntry: () -> Unit,
    onDismissSnackbar: () -> Unit,
    onSetSelectedDay: (LocalDate) -> Unit,
    birthday: Instant?,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.snackbarMessage) {
        state.snackbarMessage?.let {
            snackbarHostState.showSnackbar(message = it)
            onDismissSnackbar()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        containerColor = Color.Transparent,
    ) { paddingValues ->
        TimelinePane(
            uiState =
                app.logdate.ui.timeline.TimelineUiState(
                    items = state.items,
                    loadingState = state.loadingState,
                ),
            onNewEntry = onNewEntry,
            onShareMemory = { /* Handle share memory */ },
            onOpenDay = { date -> onSetSelectedDay(date) },
            birthday = birthday,
            modifier = modifier.padding(paddingValues),
        )
    }
}

@Preview
@Composable
private fun TimelineScreenPreview() {
    TimelineScreen(
        state = HomeTimelineUiState(),
        onNewEntry = {},
        onDismissSnackbar = {},
        onSetSelectedDay = {},
        birthday = null,
    )
}
