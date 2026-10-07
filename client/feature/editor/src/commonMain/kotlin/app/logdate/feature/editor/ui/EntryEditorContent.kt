@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalSharedTransitionApi::class,
)

package app.logdate.feature.editor.ui

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import app.logdate.feature.editor.ui.common.NoteEditorToolbar
import app.logdate.feature.editor.ui.common.PlatformBackHandler
import app.logdate.feature.editor.ui.content.EditorBottomContent
import app.logdate.feature.editor.ui.dialog.DraftsBottomSheet
import app.logdate.feature.editor.ui.dialog.alert.ConfirmEntryExitDialog
import app.logdate.feature.editor.ui.editor.CameraBlockUiState
import app.logdate.feature.editor.ui.editor.EditorBackgroundSaveEffect
import app.logdate.feature.editor.ui.editor.EditorExitReason
import app.logdate.feature.editor.ui.editor.EntryBlockUiState
import app.logdate.feature.editor.ui.editor.EntryEditorViewModel
import app.logdate.feature.editor.ui.editor.delegate.DefaultAudioBlockFinalizer
import app.logdate.feature.editor.ui.editor.rememberEditorAutoSave
import app.logdate.feature.editor.ui.layout.ImmersiveEditorLayout
import app.logdate.feature.editor.ui.state.rememberBlocksUiState
import app.logdate.ui.common.noteDropTarget
import app.logdate.ui.platform.rememberLogDateHaptics
import kotlinx.coroutines.launch
import logdate.client.feature.editor.generated.resources.Res
import logdate.client.feature.editor.generated.resources.draft_deleted
import logdate.client.feature.editor.generated.resources.undo
import org.jetbrains.compose.resources.getString
import org.koin.compose.viewmodel.koinViewModel

val LocalSharedTransitionScope = compositionLocalOf<SharedTransitionScope?> { null }
val LocalAnimatedVisibilityScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

internal fun blockUpdateCallback(
    renderedBlocks: List<EntryBlockUiState>,
    update: (EntryBlockUiState, EntryBlockUiState?) -> Unit,
): (EntryBlockUiState) -> Unit =
    { updated ->
        update(updated, renderedBlocks.firstOrNull { it.id == updated.id })
    }

@Suppress("ktlint:standard:function-naming")
@Composable
fun EntryEditorContent(
    onNavigateBack: () -> Unit,
    onEntrySaved: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: EntryEditorViewModel = koinViewModel(),
) {
    val editorState by viewModel.editorState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val shouldReturnToPickerOnBack = editorState.shouldReturnToPickerOnBack()
    var journalSelectorExpanded by remember { mutableStateOf(false) }
    val haptics = rememberLogDateHaptics()

    val uiState =
        rememberBlocksUiState(
            editorState = editorState,
            onUpdateBlock = blockUpdateCallback(editorState.blocks, viewModel::updateBlock),
            onFocusBlock = { id ->
                journalSelectorExpanded = false
                viewModel.setExpandedBlockId(id)
            },
            onCreateBlock = { type, id ->
                journalSelectorExpanded = false
                viewModel.createNewBlock(type, id)
            },
            onDeleteBlock = viewModel::removeBlock,
            onUpdateJournalSelection = viewModel::setSelectedJournals,
        )

    // Only live camera capture takes over the entry chrome.
    val activeCamera =
        editorState.blocks.any {
            it.id == editorState.expandedBlockId && it is CameraBlockUiState && it.uri == null
        }
    val isImmersiveBlockActive = activeCamera
    LaunchedEffect(editorState.isRecordingAudio) {
        if (editorState.isRecordingAudio) journalSelectorExpanded = false
    }

    // Single float that drives all immersive chrome interpolation (0 = fully immersive, 1 = normal).
    // During a predictive back gesture it's scrubbed in real-time via snapTo; on non-gesture
    // transitions (capture completed, hardware back) it animates with tween(300).
    val scope = rememberCoroutineScope()
    val chromeProgress = remember { Animatable(if (isImmersiveBlockActive) 0f else 1f) }

    LaunchedEffect(isImmersiveBlockActive) {
        if (isImmersiveBlockActive) {
            chromeProgress.snapTo(0f)
        } else {
            chromeProgress.animateTo(1f, tween(300, easing = FastOutSlowInEasing))
        }
    }

    var showExitConfirmation by remember { mutableStateOf(false) }
    var showDraftsDialog by remember { mutableStateOf(false) }
    var isExitDraftSaving by remember { mutableStateOf(false) }

    val handleEditorBack: () -> Unit = {
        when {
            editorState.isSaving || editorState.shouldExit -> Unit
            activeCamera -> {
                viewModel.dismissExpandedBlockOrClearSingleEmpty()
                Unit
            }
            shouldReturnToPickerOnBack -> {
                viewModel.clearSingleEmptyBlock()
                Unit
            }
            !editorState.canExitWithoutSaving -> {
                showExitConfirmation = true
            }
            else -> {
                onNavigateBack()
            }
        }
    }

    // Live camera back is handled by MainEditorContent via PlatformPredictiveBackHandler.
    // This handler only covers cases that must interrupt navigation: unsaved changes and
    // shouldReturnToPicker. Plain exit is left to Nav3 so predictive back can animate.
    PlatformBackHandler(
        enabled =
            !activeCamera &&
                (shouldReturnToPickerOnBack || !editorState.canExitWithoutSaving),
    ) {
        handleEditorBack()
    }

    EditorBackgroundSaveEffect { viewModel.autoSaveLatestEntry() }

    val autoSaveState =
        rememberEditorAutoSave(
            editorState = editorState,
            onAutoSave = { state -> viewModel.persistDraft(state) != null },
            enabled = !editorState.isSaving && !editorState.shouldExit,
        )

    LaunchedEffect(editorState.errorMessage) {
        editorState.errorMessage?.let { error ->
            snackbarHostState.showSnackbar(error)
        }
    }

    LaunchedEffect(editorState.shouldExit, editorState.exitReason) {
        if (editorState.shouldExit) {
            when (editorState.exitReason) {
                EditorExitReason.DISCARDED -> onNavigateBack()
                EditorExitReason.ENTRY_SAVED,
                null,
                -> onEntrySaved()
            }
        }
    }

    if (showExitConfirmation) {
        ConfirmEntryExitDialog(
            onCancel = {
                if (!isExitDraftSaving) showExitConfirmation = false
            },
            onConfirm = {
                if (!isExitDraftSaving) {
                    isExitDraftSaving = true
                    scope.launch {
                        val discarded = viewModel.discardAndExit()
                        isExitDraftSaving = false
                        if (discarded) {
                            showExitConfirmation = false
                        }
                    }
                }
            },
            onSaveAsDraft = {
                if (!isExitDraftSaving) {
                    isExitDraftSaving = true
                    scope.launch {
                        // Read the latest state at click time. The dialog can remain composed
                        // while the text field is dispatching its final edit, so the captured
                        // composition value may otherwise persist stale content.
                        val saved = viewModel.saveAsDraft(viewModel.editorState.value)
                        isExitDraftSaving = false
                        if (saved) {
                            showExitConfirmation = false
                            onNavigateBack()
                        }
                    }
                }
            },
            actionsEnabled = !isExitDraftSaving && !editorState.isSaving,
        )
    }

    if (showDraftsDialog) {
        DraftsBottomSheet(
            drafts = editorState.availableDrafts,
            isLoading = editorState.isLoadingDrafts,
            onDismiss = { showDraftsDialog = false },
            onDraftSelected = { draft ->
                viewModel.loadDraft(draft.id)
                showDraftsDialog = false
            },
            onDraftDeleted = { draftId ->
                viewModel.deleteDraft(draftId)
                scope.launch {
                    val result =
                        snackbarHostState.showSnackbar(
                            message = getString(Res.string.draft_deleted),
                            actionLabel = getString(Res.string.undo),
                            duration = SnackbarDuration.Short,
                        )
                    if (result == SnackbarResult.ActionPerformed) {
                        viewModel.loadDraft(draftId)
                    }
                }
            },
            onDeleteAllDrafts = viewModel::deleteAllDrafts,
        )
    }

    ImmersiveEditorLayout(
        modifier = modifier.noteDropTarget { viewModel.appendTextBlock(it) },
        isImmersiveBlockActive = isImmersiveBlockActive,
        isAudioRecordingActive = editorState.isRecordingAudio,
        immersiveExitProgress = chromeProgress.value,
        topBarContent = {
            NoteEditorToolbar(
                onBack = handleEditorBack,
                onSave = {
                    haptics.saveSucceeded()
                    viewModel.saveEntry(editorState)
                },
                onShowDrafts = { showDraftsDialog = true },
                draftCount = editorState.availableDrafts.size,
                autoSaveStatus = autoSaveState.status,
                actionsVisible = !isImmersiveBlockActive,
                optionsVisible = !editorState.isRecordingAudio,
                actionsEnabled = !editorState.isSaving && !editorState.shouldExit,
            )
        },
        editorContent = {
            Column(Modifier.fillMaxSize()) {
                if (!isImmersiveBlockActive) {
                    editorState.visitContext?.let { VisitMemoryContextBanner(it) }
                }
                MainEditorContent(
                    modifier = Modifier.weight(1f),
                    uiState = uiState,
                    shouldReturnToPickerOnBack = shouldReturnToPickerOnBack,
                    onDismissExpanded = {
                        viewModel.dismissExpandedBlockOrClearSingleEmpty()
                    },
                    onBackProgress = { p ->
                        if (isImmersiveBlockActive) scope.launch { chromeProgress.snapTo(p) }
                    },
                    onBackCommit = {
                        if (isImmersiveBlockActive) scope.launch { chromeProgress.animateTo(1f, tween(300, easing = FastOutSlowInEasing)) }
                    },
                    onBackCancel = {
                        if (isImmersiveBlockActive) scope.launch { chromeProgress.animateTo(0f, tween(300, easing = FastOutSlowInEasing)) }
                    },
                    onAudioResolverReady = { blockId, resolver ->
                        viewModel.bindAudioBlockFinalizer(blockId, DefaultAudioBlockFinalizer(resolver))
                    },
                )
            }
        },
        bottomContent = {
            EditorBottomContent(
                availableJournals = uiState.availableJournals,
                selectedJournalIds = uiState.selectedJournalIds,
                onJournalSelectionChanged = uiState.onJournalSelectionChanged,
                journalSelectorExpanded = journalSelectorExpanded,
                onJournalSelectorExpandedChange = { expanded ->
                    if (!editorState.isSaving && !editorState.shouldExit) {
                        journalSelectorExpanded = expanded
                    }
                },
            )
        },
    )

    Box(modifier = Modifier.fillMaxSize()) {
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}
