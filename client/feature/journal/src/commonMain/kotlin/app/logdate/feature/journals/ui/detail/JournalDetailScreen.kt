@file:Suppress("ktlint:standard:function-naming", "ktlint:standard:no-wildcard-imports")
@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)

package app.logdate.feature.journals.ui.detail

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import app.logdate.ui.LocalNavAnimatedVisibilityScope
import app.logdate.ui.LocalSharedTransitionScope
import app.logdate.ui.adaptive.FoldableBookLayout
import app.logdate.ui.common.transitions.TransitionKeys
import app.logdate.ui.theme.Spacing
import app.logdate.ui.workspace.LocalWorkspaceEnabled
import app.logdate.ui.workspace.PanelHeader
import app.logdate.ui.workspace.WorkspacePanel
import logdate.client.feature.journal.generated.resources.*
import logdate.client.feature.journal.generated.resources.Res
import logdate.client.ui.generated.resources.common_back
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import kotlin.uuid.Uuid
import logdate.client.ui.generated.resources.Res as UiRes

/**
 * The main screen to view a journal's contents.
 */
@Composable
fun JournalDetailScreen(
    journalId: Uuid,
    onGoBack: () -> Unit,
    onJournalDeleted: () -> Unit,
    onNavigateToNoteDetail: (noteId: Uuid) -> Unit = { _ -> },
    onOpenEditor: (Uuid) -> Unit = {},
    onOpenContentPicker: (Uuid) -> Unit = {},
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
        onOpenEditor = { onOpenEditor(journalId) },
        onOpenContentPicker = { onOpenContentPicker(journalId) },
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
    onOpenEditor: () -> Unit = {},
    onOpenContentPicker: () -> Unit = {},
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
            WorkspacePanel(modifier) {
                Column(Modifier.padding(Spacing.lg)) {
                    Text("This journal couldn't load. Your memories are still saved.")
                    TextButton(onClick = onGoBack) { Text("Back to journals") }
                }
            }
            return
        }

        is JournalDetailUiState.Success -> {
            var showOverflowMenu by remember { mutableStateOf(false) }
            var showAddMenu by remember { mutableStateOf(false) }
            var selectedTab by rememberSaveable { mutableStateOf(0) }
            val mediaEntries =
                remember(uiState.entries) {
                    uiState.entries.filter { it is EntryDisplayData.ImageEntry || it is EntryDisplayData.VideoEntry }
                }
            val hasMedia = mediaEntries.isNotEmpty()

            if (LocalWorkspaceEnabled.current) {
                WorkspacePanel(modifier) {
                    Column(Modifier.fillMaxSize()) {
                        PanelHeader(uiState.title, actions = {
                            IconButton(onClick = onGoBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back to journals") }
                            Box {
                                IconButton(onClick = { showAddMenu = true }) { Icon(Icons.Rounded.Add, "Add to journal") }
                                JournalAddMenu(
                                    expanded = showAddMenu,
                                    onDismiss = { showAddMenu = false },
                                    onCreateEntry = onOpenEditor,
                                    onAddExistingContent = onOpenContentPicker,
                                )
                            }
                            Box {
                                IconButton(onClick = { showOverflowMenu = true }) { Icon(Icons.Rounded.MoreVert, "Journal options") }
                                DropdownMenu(showOverflowMenu, { showOverflowMenu = false }) {
                                    DropdownMenuItem(text = { Text("Change order") }, onClick = {
                                        showOverflowMenu = false
                                        onToggleSortOrder()
                                    })
                                    DropdownMenuItem(text = { Text("Share journal") }, onClick = {
                                        showOverflowMenu = false
                                        onNavigateToShare(uiState.journalId)
                                    })
                                    DropdownMenuItem(text = { Text("Journal settings") }, onClick = {
                                        showOverflowMenu = false
                                        onNavigateToSettings(uiState.journalId)
                                    })
                                    DropdownMenuItem(text = { Text("Delete journal") }, onClick = {
                                        showOverflowMenu = false
                                        onRequestDelete()
                                    })
                                }
                            }
                        })
                        JournalDetailEntriesPane(
                            uiState,
                            mediaEntries,
                            hasMedia,
                            selectedTab,
                            { selectedTab = it },
                            onNavigateToNoteDetail,
                            onRemoveNoteFromJournal,
                            true,
                            Modifier.weight(1f).fillMaxWidth(),
                        )
                    }
                }
            } else {
                Scaffold(
                    modifier =
                        modifier
                            .nestedScroll(scrollBehavior.nestedScrollConnection)
                            .let { baseModifier ->
                                if (sharedTransitionScope != null && animatedVisibilityScope != null) {
                                    with(sharedTransitionScope) {
                                        baseModifier.sharedElement(
                                            rememberSharedContentState(
                                                TransitionKeys.journalContainerTransition(uiState.journalId),
                                            ),
                                            animatedVisibilityScope,
                                        )
                                    }
                                } else {
                                    baseModifier
                                }
                            },
                    contentWindowInsets = WindowInsets.navigationBars,
                    floatingActionButton = {
                        Box {
                            FloatingActionButton(onClick = { showAddMenu = true }) {
                                Icon(Icons.Rounded.Add, contentDescription = "Add to journal")
                            }
                            JournalAddMenu(
                                expanded = showAddMenu,
                                onDismiss = { showAddMenu = false },
                                onCreateEntry = onOpenEditor,
                                onAddExistingContent = onOpenContentPicker,
                            )
                        }
                    },
                    topBar = {
                        LargeTopAppBar(
                            title = { Text(uiState.title) },
                            navigationIcon = {
                                IconButton(onClick = onGoBack) {
                                    Icon(
                                        Icons.AutoMirrored.Rounded.ArrowBack,
                                        contentDescription = stringResource(UiRes.string.common_back),
                                    )
                                }
                            },
                            scrollBehavior = scrollBehavior,
                            actions = {
                                IconButton(onClick = onToggleSortOrder) {
                                    val sortIcon =
                                        if (uiState.sortOrder == SortOrder.NEWEST_FIRST) {
                                            Icons.Rounded.ArrowDownward
                                        } else {
                                            Icons.Rounded.ArrowUpward
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

                                Box {
                                    IconButton(onClick = { showOverflowMenu = true }) {
                                        Icon(
                                            Icons.Rounded.MoreVert,
                                            contentDescription = stringResource(Res.string.journal_settings_label),
                                        )
                                    }
                                    DropdownMenu(
                                        expanded = showOverflowMenu,
                                        onDismissRequest = { showOverflowMenu = false },
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text(stringResource(Res.string.journal_share_label)) },
                                            onClick = {
                                                showOverflowMenu = false
                                                onNavigateToShare(uiState.journalId)
                                            },
                                            leadingIcon = {
                                                Icon(Icons.Rounded.Share, contentDescription = null)
                                            },
                                        )
                                        DropdownMenuItem(
                                            text = { Text(stringResource(Res.string.journal_settings_label)) },
                                            onClick = {
                                                showOverflowMenu = false
                                                onNavigateToSettings(uiState.journalId)
                                            },
                                            leadingIcon = {
                                                Icon(Icons.Rounded.Settings, contentDescription = null)
                                            },
                                        )
                                        DropdownMenuItem(
                                            text = { Text(stringResource(Res.string.journal_delete_label)) },
                                            onClick = {
                                                showOverflowMenu = false
                                                onRequestDelete()
                                            },
                                            leadingIcon = {
                                                Icon(Icons.Rounded.DeleteOutline, contentDescription = null)
                                            },
                                        )
                                    }
                                }
                            },
                        )
                    },
                ) { paddingValues ->
                    FoldableBookLayout(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .padding(paddingValues),
                        startPane = {
                            JournalDetailBookSummaryPane(
                                uiState = uiState,
                                onToggleSortOrder = onToggleSortOrder,
                                onNavigateToShare = onNavigateToShare,
                                onNavigateToSettings = onNavigateToSettings,
                                onRequestDelete = onRequestDelete,
                                modifier = Modifier.fillMaxSize(),
                            )
                        },
                        endPane = {
                            JournalDetailEntriesPane(
                                uiState = uiState,
                                mediaEntries = mediaEntries,
                                hasMedia = hasMedia,
                                selectedTab = selectedTab,
                                onSelectTab = { selectedTab = it },
                                onNavigateToNoteDetail = onNavigateToNoteDetail,
                                onRemoveNoteFromJournal = onRemoveNoteFromJournal,
                                constrainContentWidth = false,
                                modifier = Modifier.fillMaxSize(),
                            )
                        },
                        standardContent = {
                            JournalDetailEntriesPane(
                                uiState = uiState,
                                mediaEntries = mediaEntries,
                                hasMedia = hasMedia,
                                selectedTab = selectedTab,
                                onSelectTab = { selectedTab = it },
                                onNavigateToNoteDetail = onNavigateToNoteDetail,
                                onRemoveNoteFromJournal = onRemoveNoteFromJournal,
                                constrainContentWidth = true,
                                modifier = Modifier.fillMaxSize(),
                            )
                        },
                    )
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
