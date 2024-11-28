package app.logdate.feature.timeline.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import app.logdate.ui.common.PlatformBackHandler
import kotlinx.coroutines.launch
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun TimelineRoute(
    onOpenTimelineItem: (uid: String) -> Unit,
    onNewEntry: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TimelineViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    TimelineScreen(
        state,
        onOpenTimelineItem,
        onDeleteTimelineItem = { viewModel.deleteItem(it) },
        onNewEntry = onNewEntry,
        modifier = modifier,
    )
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
    onOpenTimelineItem: (uid: String) -> Unit,
    onDeleteTimelineItem: (uid: String) -> Unit,
    onNewEntry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showDeletionDialog by rememberSaveable { mutableStateOf(false) }
    var pendingDeletionUid by remember { mutableStateOf<String?>(null) }

    val navigator = rememberListDetailPaneScaffoldNavigator<TimelineSelectionItem>()
    val coroutineScope = rememberCoroutineScope()

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

//    ListDetailPaneScaffold(
//        directive = navigator.scaffoldDirective,
//        detailPane = {
//
//        },
//        listPane = {
//            AnimatedPane {
//                TimelinePane(
//                        state = state,
//                        onItemSelected = onOpenTimelineItem,
//                        onItemDeleted = {
//                            pendingDeletionUid = it;
//                            showDeletionDialog = true
//                        },
//                        onNewEntry = onNewEntry,
//                        modifier = modifier,
//                    )
//            }
//        },
//        value = navigator.scaffoldValue,
//        modifier = modifier,
//    )
}

@Preview
@Composable
private fun TimelineScreenPreview() {
    TimelineScreen(
        HomeTimelineUiState(),
        onOpenTimelineItem = {},
        onDeleteTimelineItem = {},
        onNewEntry = {},
    )
}

