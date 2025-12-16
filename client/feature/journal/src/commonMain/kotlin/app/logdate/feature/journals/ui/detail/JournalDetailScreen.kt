@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)

package app.logdate.feature.journals.ui.detail

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.logdate.ui.LocalNavAnimatedVisibilityScope
import app.logdate.ui.LocalSharedTransitionScope
import app.logdate.ui.theme.Spacing
import app.logdate.util.toReadableDateTimeShort
import logdate.client.feature.journal.generated.resources.Res
import logdate.client.feature.journal.generated.resources.action_delete
import logdate.client.feature.journal.generated.resources.delete_journal_description
import logdate.client.feature.journal.generated.resources.delete_journal_title
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import kotlin.uuid.Uuid

/**
 * The main screen to view a journal's contents.
 */
@Composable
fun JournalDetailScreen(
    journalId: Uuid,
    onGoBack: () -> Unit,
    onJournalDeleted: () -> Unit,
    onNavigateToNoteDetail: (noteId: Uuid, journalId: Uuid) -> Unit = { _, _ -> },
    onNavigateToSettings: (journalId: Uuid) -> Unit = {},
    onNavigateToShare: (journalId: Uuid) -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: JournalDetailViewModel = koinViewModel(),
) {
    LaunchedEffect(journalId) {
        viewModel.setSelectedJournalId(journalId)
    }
    
    val state by viewModel.uiState.collectAsState()
    val sharedTransitionScope = LocalSharedTransitionScope.current
    val animatedVisibilityScope = LocalNavAnimatedVisibilityScope.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    var openDeleteConfirmation by rememberSaveable { mutableStateOf(false) }
    when (state) {
        is JournalDetailUiState.Loading -> {
            JournalDetailPlaceholder()
            return
        }

        is JournalDetailUiState.Error -> {
            // TODO: Handle error state
            // TODO: Redirect to home if journal not found
            return
        }

        is JournalDetailUiState.Success -> {
            Scaffold(
                modifier = modifier
                    .nestedScroll(scrollBehavior.nestedScrollConnection)
                    .let { baseModifier ->
                        if (sharedTransitionScope != null && animatedVisibilityScope != null) {
                            with(sharedTransitionScope) {
                                baseModifier.sharedElement(
                                    sharedContentState = rememberSharedContentState("journal-container-${(state as JournalDetailUiState.Success).journalId}"),
                                    animatedVisibilityScope = animatedVisibilityScope,
                                )
                            }
                        } else {
                            baseModifier
                        }
                    },
                contentWindowInsets = WindowInsets.navigationBars,
                topBar = {
                    LargeTopAppBar(
                        title = { Text((state as JournalDetailUiState.Success).title) },
                        navigationIcon = {

                            IconButton(onClick = { onGoBack() }) {
                                Icon(
                                    Icons.AutoMirrored.Default.ArrowBack,
                                    contentDescription = "Back"
                                )
                            }
                        },
                        scrollBehavior = scrollBehavior,
                        actions = {
                            // Sort order toggle button
                            IconButton(onClick = { viewModel.toggleSortOrder() }) {
                                val sortIcon = if ((state as JournalDetailUiState.Success).sortOrder == SortOrder.NEWEST_FIRST) {
                                    // Arrow pointing down for newest first
                                    Icons.Default.ArrowDownward
                                } else {
                                    // Arrow pointing up for oldest first
                                    Icons.Default.ArrowUpward
                                }
                                
                                val description = if ((state as JournalDetailUiState.Success).sortOrder == SortOrder.NEWEST_FIRST) {
                                    "Sorted: Newest first (click to show oldest first)"
                                } else {
                                    "Sorted: Oldest first (click to show newest first)"
                                }
                                
                                Icon(
                                    sortIcon,
                                    contentDescription = description
                                )
                            }
                            
                            // Share button
                            IconButton(onClick = { onNavigateToShare((state as JournalDetailUiState.Success).journalId) }) {
                                Icon(
                                    Icons.Default.Share,
                                    contentDescription = "Share journal"
                                )
                            }
                            
                            // Settings button
                            IconButton(onClick = { onNavigateToSettings((state as JournalDetailUiState.Success).journalId) }) {
                                Icon(
                                    Icons.Default.Settings,
                                    contentDescription = "Journal settings"
                                )
                            }
                            
                            // Delete button
                            IconButton(onClick = { openDeleteConfirmation = true }) {
                                Icon(
                                    Icons.Default.DeleteOutline,
                                    contentDescription = "Delete journal"
                                )
                            }
                        },
                    )
                },
            ) {
                val successState = state as JournalDetailUiState.Success
                val listState = rememberLazyListState()
                
                if (successState.entries.isEmpty()) {
                    // Empty state
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(it)
                            .padding(Spacing.lg),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No entries in this journal yet")
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(it)
                    ) {
                        // Sort order indicator at the top
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (successState.sortOrder == SortOrder.NEWEST_FIRST) {
                                    "Newest first"
                                } else {
                                    "Oldest first"
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        
                        // Journal entries list
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(
                                start = Spacing.lg,
                                end = Spacing.lg,
                                bottom = Spacing.xl
                            ),
                            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                        ) {
                            items(successState.entries) { entry ->
                                JournalEntryItem(
                                    content = entry.content,
                                    timestamp = entry.timestamp,
                                    onClick = { 
                                        onNavigateToNoteDetail(entry.id, successState.journalId)
                                    }
                                )
                            }
                        }
                    }
                }
            }
            if (openDeleteConfirmation) {
                DeleteConfirmationDialog(
                    onDismissRequest = { openDeleteConfirmation = false },
                    onConfirmation = {
                        viewModel.deleteJournal(onJournalDeleted)
                        openDeleteConfirmation = false
                    }
                )
            }
        }
    }
}

@Composable
fun DeleteConfirmationDialog(
    onDismissRequest: () -> Unit,
    onConfirmation: () -> Unit,
    icon: ImageVector = Icons.Default.WarningAmber,
) {
    AlertDialog(
        icon = {
            Icon(icon, contentDescription = null)
        },
        title = {
            Text(text = stringResource(Res.string.delete_journal_title))
        },
        text = {
            Text(text = stringResource(Res.string.delete_journal_description))
        },
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(
                onClick = onConfirmation,
            ) {
                Text(stringResource(Res.string.action_delete))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismissRequest,
            ) {
                Text("Cancel")
            }
        }
    )
}

@Composable
internal fun JournalDetailPlaceholder() {
    Row(
        modifier = Modifier
            .padding(Spacing.lg)
            .fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "Loading..."
        )
    }
}

@Composable
private fun JournalEntryItem(
    content: String,
    timestamp: kotlinx.datetime.Instant,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    
    Column(modifier = modifier) {
        // Display date and time above the card
        Text(
            text = timestamp.toReadableDateTimeShort(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        
        Card(
            onClick = { 
                if (expanded) {
                    onClick()
                } else {
                    expanded = !expanded
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = content,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = if (expanded) Int.MAX_VALUE else 4,
                    overflow = if (expanded) TextOverflow.Visible else TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateContentSize()
                        .heightIn(min = 40.dp)
                )
            }
        }
    }
}
