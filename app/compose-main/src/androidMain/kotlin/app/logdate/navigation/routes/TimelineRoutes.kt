package app.logdate.navigation.routes

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import app.logdate.client.sharing.SharingLauncher
import app.logdate.feature.core.main.HomeViewModel
import app.logdate.feature.timeline.ui.details.TimelineDayDetailPanel
import app.logdate.navigation.MainAppNavigator
import app.logdate.navigation.TimelinePaneScreen
import app.logdate.navigation.routes.core.TimelineDetail
import app.logdate.navigation.routes.core.TimelineListRoute
import app.logdate.navigation.routes.routeEntry
import app.logdate.navigation.scenes.HomeScene
import app.logdate.ui.timeline.TimelineSuggestionBlockUiState
import kotlinx.datetime.LocalDate
import logdate.app.composemain.generated.resources.Res
import logdate.app.composemain.generated.resources.select_an_entry_to_view_details
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

fun MainAppNavigator.openTimeline() {
    backStack.add(TimelineListRoute)
}

fun MainAppNavigator.openTimelineDetail(day: LocalDate) {
    backStack.add(TimelineDetail(day))
}

fun EntryProviderScope<NavKey>.timelineRoutes(
    openEntryEditor: () -> Unit,
    onOpenDraft: (draftId: String) -> Unit,
    sharingLauncher: SharingLauncher,
    onOpenTimelineDetail: (day: LocalDate) -> Unit,
    onCloseTimelineDetail: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenLocationTimeline: () -> Unit,
    onOpenSearch: () -> Unit,
    onImportBackup: () -> Unit,
    onOpenEvent: (eventId: String) -> Unit,
    onDecorate: () -> Unit = {},
    homeViewModel: HomeViewModel,
) {
    // The home screen
    routeEntry<TimelineListRoute>(
        metadata = HomeScene.homeScene(),
    ) { _ ->
        TimelinePaneScreen(
            onNewEntry = openEntryEditor,
            onOpenDay = onOpenTimelineDetail,
            onOpenSettings = onOpenSettings,
            onOpenLocationTimeline = onOpenLocationTimeline,
            onOpenSearch = onOpenSearch,
            onOpenDraft = onOpenDraft,
            onShareMemory = { state: TimelineSuggestionBlockUiState ->
                state.memoryDate?.let { date ->
                    sharingLauncher.shareMemoryDay(
                        date = date,
                        summary = state.message,
                        mediaUris = state.mediaUris.map { it.uri },
                    )
                }
            },
            onImportBackup = onImportBackup,
            viewModel = homeViewModel,
        )
    }
    routeEntry<TimelineDetail>(
        metadata = timelineDetailRouteTransitionMetadata,
    ) { route ->
        LaunchedEffect(route.day) {
            homeViewModel.selectDay(route.day)
        }

        TimelineDetailScreen(
            onClose = onCloseTimelineDetail,
            onOpenLocations = onOpenLocationTimeline,
            onOpenEvent = onOpenEvent,
            onDecorate = onDecorate,
            viewModel = homeViewModel,
        )
    }
}

@Suppress("ktlint:standard:function-naming")
@Composable
fun TimelineDetailScreen(
    onClose: () -> Unit,
    onOpenLocations: (() -> Unit)? = null,
    onOpenEvent: (eventId: String) -> Unit = {},
    onDecorate: (() -> Unit)? = null,
    viewModel: HomeViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Use a safe fallback if selectedDay is null
    uiState.selectedDay?.let { selectedDay ->
        TimelineDayDetailPanel(
            uiState = selectedDay,
            onExit = onClose,
            onOpenEvent = onOpenEvent,
            onOpenLocations = onOpenLocations,
            onDecorate = onDecorate,
        )
    } ?: TimelineDetailPlaceholder()
}

@Suppress("ktlint:standard:function-naming")
@Composable
fun TimelineDetailPlaceholder() {
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(Res.string.select_an_entry_to_view_details),
            style = MaterialTheme.typography.bodyLarge,
            modifier =
                Modifier
                    .fillMaxSize(),
            textAlign = TextAlign.Center,
        )
    }
}
