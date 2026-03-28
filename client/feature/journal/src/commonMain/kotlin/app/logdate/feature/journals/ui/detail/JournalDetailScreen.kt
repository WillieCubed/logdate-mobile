@file:Suppress("ktlint:standard:function-naming", "ktlint:standard:no-wildcard-imports")
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import logdate.client.feature.journal.generated.resources.*
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
    onNavigateToNoteDetail: (noteId: Uuid) -> Unit = { _ -> },
    onNavigateToSettings: (journalId: Uuid) -> Unit = {},
    onNavigateToShare: (journalId: Uuid) -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: JournalDetailViewModel = koinViewModel(),
) {
    LaunchedEffect(journalId) {
        viewModel.setSelectedJournalId(journalId)
    }

    val state by viewModel.uiState.collectAsState()
    var openDeleteConfirmation by rememberSaveable { mutableStateOf(false) }
    var noteToRemove by rememberSaveable { mutableStateOf<String?>(null) }
    JournalDetailScreenContent(
        uiState = state,
        onGoBack = onGoBack,
        onNavigateToNoteDetail = onNavigateToNoteDetail,
        onNavigateToSettings = onNavigateToSettings,
        onNavigateToShare = onNavigateToShare,
        onToggleSortOrder = viewModel::toggleSortOrder,
        onRequestDelete = { openDeleteConfirmation = true },
        showDeleteConfirmation = openDeleteConfirmation,
        onDismissDeleteConfirmation = { openDeleteConfirmation = false },
        onConfirmDelete = {
            viewModel.deleteJournal(onJournalDeleted)
            openDeleteConfirmation = false
        },
        onRemoveNoteFromJournal = { noteId -> noteToRemove = noteId.toString() },
        showRemoveNoteConfirmation = noteToRemove != null,
        onDismissRemoveNoteConfirmation = { noteToRemove = null },
        onConfirmRemoveNote = {
            noteToRemove?.let { viewModel.removeNoteFromJournal(Uuid.parse(it)) }
            noteToRemove = null
        },
        modifier = modifier,
    )
}

@Composable
fun JournalDetailScreenContent(
    uiState: JournalDetailUiState,
    onGoBack: () -> Unit,
    onNavigateToNoteDetail: (noteId: Uuid) -> Unit = { _ -> },
    onNavigateToSettings: (journalId: Uuid) -> Unit = {},
    onNavigateToShare: (journalId: Uuid) -> Unit = {},
    onToggleSortOrder: () -> Unit = {},
    onRequestDelete: () -> Unit = {},
    showDeleteConfirmation: Boolean = false,
    onDismissDeleteConfirmation: () -> Unit = {},
    onConfirmDelete: () -> Unit = {},
    onRemoveNoteFromJournal: (noteId: Uuid) -> Unit = {},
    showRemoveNoteConfirmation: Boolean = false,
    onDismissRemoveNoteConfirmation: () -> Unit = {},
    onConfirmRemoveNote: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val sharedTransitionScope = LocalSharedTransitionScope.current
    val animatedVisibilityScope = LocalNavAnimatedVisibilityScope.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    when (uiState) {
        is JournalDetailUiState.Loading -> {
            JournalDetailPlaceholder()
            return
        }

        is JournalDetailUiState.Error -> {
            // Preserve the current empty error branch so previews match the route's real behavior.
            return
        }

        is JournalDetailUiState.Success -> {
            Scaffold(
                modifier =
                    modifier
                        .nestedScroll(scrollBehavior.nestedScrollConnection)
                        .let { baseModifier ->
                            if (sharedTransitionScope != null && animatedVisibilityScope != null) {
                                with(sharedTransitionScope) {
                                    baseModifier.sharedElement(
                                        rememberSharedContentState("journal-container-${uiState.journalId}"),
                                        animatedVisibilityScope,
                                    )
                                }
                            } else {
                                baseModifier
                            }
                        },
                contentWindowInsets = WindowInsets.navigationBars,
                topBar = {
                    LargeTopAppBar(
                        title = { Text(uiState.title) },
                        navigationIcon = {
                            IconButton(onClick = onGoBack) {
                                Icon(
                                    Icons.AutoMirrored.Default.ArrowBack,
                                    contentDescription = stringResource(Res.string.back),
                                )
                            }
                        },
                        scrollBehavior = scrollBehavior,
                        actions = {
                            IconButton(onClick = onToggleSortOrder) {
                                val sortIcon =
                                    if (uiState.sortOrder == SortOrder.NEWEST_FIRST) {
                                        Icons.Default.ArrowDownward
                                    } else {
                                        Icons.Default.ArrowUpward
                                    }

                                val description =
                                    if (uiState.sortOrder == SortOrder.NEWEST_FIRST) {
                                        "Sorted: Newest first (click to show oldest first)"
                                    } else {
                                        "Sorted: Oldest first (click to show newest first)"
                                    }

                                Icon(
                                    sortIcon,
                                    contentDescription = description,
                                )
                            }

                            IconButton(onClick = { onNavigateToShare(uiState.journalId) }) {
                                Icon(
                                    Icons.Default.Share,
                                    contentDescription = stringResource(Res.string.share_journal_2),
                                )
                            }

                            IconButton(onClick = { onNavigateToSettings(uiState.journalId) }) {
                                Icon(
                                    Icons.Default.Settings,
                                    contentDescription = stringResource(Res.string.journal_settings_2),
                                )
                            }

                            IconButton(onClick = onRequestDelete) {
                                Icon(
                                    Icons.Default.DeleteOutline,
                                    contentDescription = stringResource(Res.string.delete_journal_2),
                                )
                            }
                        },
                    )
                },
            ) { paddingValues ->
                val listState = rememberLazyListState()

                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(paddingValues),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .padding(horizontal = Spacing.lg, vertical = Spacing.sm)
                                .widthIn(max = 760.dp),
                    ) {
                        if (uiState.entries.isEmpty()) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(stringResource(Res.string.no_entries_in_this_journal_yet))
                            }
                        } else {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                            ) {
                                Row(
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = Spacing.sm),
                                    horizontalArrangement = Arrangement.End,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text =
                                            if (uiState.sortOrder == SortOrder.NEWEST_FIRST) {
                                                "Newest first"
                                            } else {
                                                "Oldest first"
                                            },
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }

                                LazyColumn(
                                    state = listState,
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding =
                                        PaddingValues(
                                            bottom = Spacing.xl,
                                        ),
                                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                                ) {
                                    items(uiState.entries) { entry ->
                                        JournalEntryItem(
                                            content = entry.content,
                                            timestamp = entry.timestamp,
                                            onClick = { onNavigateToNoteDetail(entry.id) },
                                            onRemoveFromJournal = { onRemoveNoteFromJournal(entry.id) },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (showDeleteConfirmation) {
                DeleteConfirmationDialog(
                    onDismissRequest = onDismissDeleteConfirmation,
                    onConfirmation = onConfirmDelete,
                )
            }

            if (showRemoveNoteConfirmation) {
                RemoveNoteFromJournalDialog(
                    onDismissRequest = onDismissRemoveNoteConfirmation,
                    onConfirmation = onConfirmRemoveNote,
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
                Text(stringResource(Res.string.cancel))
            }
        },
    )
}

@Composable
internal fun JournalDetailPlaceholder() {
    Row(
        modifier =
            Modifier
                .padding(Spacing.lg)
                .fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "Loading...",
        )
    }
}

@Composable
private fun RemoveNoteFromJournalDialog(
    onDismissRequest: () -> Unit,
    onConfirmation: () -> Unit,
) {
    AlertDialog(
        icon = {
            Icon(Icons.Default.WarningAmber, contentDescription = null)
        },
        title = {
            Text(text = stringResource(Res.string.remove_from_journal_title))
        },
        text = {
            Text(text = stringResource(Res.string.remove_from_journal_description))
        },
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(onClick = onConfirmation) {
                Text(stringResource(Res.string.action_remove))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(Res.string.cancel))
            }
        },
    )
}

@Composable
private fun JournalEntryItem(
    content: String,
    timestamp: kotlin.time.Instant,
    onClick: () -> Unit,
    onRemoveFromJournal: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }

    Column(modifier = modifier) {
        // Display date and time above the card
        Text(
            text = timestamp.toReadableDateTimeShort(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 4.dp),
        )

        Card(
            onClick = {
                if (expanded) {
                    onClick()
                } else {
                    expanded = !expanded
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 16.dp, end = 4.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    text = content,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = if (expanded) Int.MAX_VALUE else 4,
                    overflow = if (expanded) TextOverflow.Visible else TextOverflow.Ellipsis,
                    modifier =
                        Modifier
                            .weight(1f)
                            .animateContentSize()
                            .heightIn(min = 40.dp),
                )
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = stringResource(Res.string.journal_settings_2),
                        )
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(Res.string.remove_from_journal)) },
                            onClick = {
                                showMenu = false
                                onRemoveFromJournal()
                            },
                            leadingIcon = {
                                Icon(Icons.Default.RemoveCircleOutline, contentDescription = null)
                            },
                        )
                    }
                }
            }
        }
    }
}
