@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalFoundationApi::class,
    ExperimentalSharedTransitionApi::class
)

package app.logdate.feature.editor.ui

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import app.logdate.feature.editor.ui.audio.AudioViewModel
import app.logdate.feature.editor.ui.common.NoteEditorToolbar
import app.logdate.feature.editor.ui.common.PlatformBackHandler
import app.logdate.feature.editor.ui.content.EditorBottomContent
import app.logdate.feature.editor.ui.dialog.DraftsListDialog
import app.logdate.feature.editor.ui.dialog.alert.ConfirmEntryExitDialog
import app.logdate.feature.editor.ui.editor.EditorMode
import app.logdate.feature.editor.ui.editor.EntryEditorViewModel
import app.logdate.feature.editor.ui.editor.rememberEditorAutoSave
import app.logdate.feature.editor.ui.layout.ImmersiveEditorLayout
import app.logdate.feature.editor.ui.state.rememberBlocksUiState
import io.github.aakira.napier.Napier
import org.koin.compose.viewmodel.koinViewModel

/**
 * CompositionLocal for accessing SharedTransitionScope within the editor module.
 * This allows the editor to participate in shared element transitions.
 * 
 * Note: This should match the LocalSharedTransitionScope from the navigation module.
 */
val LocalSharedTransitionScope = compositionLocalOf<SharedTransitionScope?> { null }

/**
 * CompositionLocal for accessing AnimatedVisibilityScope within the editor module.
 */
val LocalAnimatedVisibilityScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

/**
 * Shared element key for the FAB to editor transition.
 * This must match the key used in the HomeScene FAB.
 */
private const val FAB_TO_EDITOR_SHARED_ELEMENT_KEY = "fab_to_editor"

/**
 * Main editor screen for creating and editing journal entries.
 * Supports text and audio input modes with a minimal swipe interface.
 * 
 * @param onNavigateBack Callback when the user navigates back
 * @param onEntrySaved Callback when an entry is successfully saved
 * @param modifier Modifier to be applied to the root layout
 */
@Composable
fun EntryEditorContent(
    onNavigateBack: () -> Unit,
    onEntrySaved: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: EntryEditorViewModel = koinViewModel(),
    audioViewModel: AudioViewModel = koinViewModel<AudioViewModel>()
) {
    val editorState by viewModel.editorState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Page selection setup
    val blocks = editorState.blocks

    // For now, we'll focus on text mode only
    val pagerState = rememberPagerState(
        initialPage = EditorMode.TEXT.ordinal,
        pageCount = { 1 } // Only show text mode
    )
    
    // Keep these audio operations for future use but they won't be triggered in the UI
    val startRecording: () -> Unit = { audioViewModel.startRecording() }
    val stopRecording: () -> Unit = { audioViewModel.stopRecording() }
    
    // Create a unified UI state holder for all components using callbacks
    val uiState = rememberBlocksUiState(
        pagerState = pagerState,
        editorState = editorState,
        onAudioRecordingStarted = startRecording,
        onAudioRecordingStopped = stopRecording,
        onUpdateBlock = viewModel::updateBlock,
        onFocusBlock = viewModel::setExpandedBlockId,
        onCreateBlock = viewModel::createNewBlock,
        onUpdateJournalSelection = viewModel::setSelectedJournals,
    )
    
    // Dialog states
    var showExitConfirmation by remember { mutableStateOf(false) }
    var showDraftsDialog by remember { mutableStateOf(false) }
    
    // Handle back button press
    PlatformBackHandler {
        if (!editorState.canExitWithoutSaving) {
            showExitConfirmation = true
        } else {
            onNavigateBack()
        }
    }
    
    // Auto-save handler with improved implementation - returns state for UI indicators
    // The autoSaveState can be used to show UI indicators (e.g., "Saving..." or "Saved")
    val autoSaveState = rememberEditorAutoSave(
        editorState = editorState,
        onAutoSave = { state -> viewModel.autoSaveEntry(state) }
    )

    // Error handling
    LaunchedEffect(editorState.errorMessage) {
        editorState.errorMessage?.let { error ->
            snackbarHostState.showSnackbar(error)
        }
    }
    
    // Navigation after successful save
    LaunchedEffect(editorState.shouldExit) {
        if (editorState.shouldExit) {
            onEntrySaved()
        }
    }
    
    // Show exit confirmation if needed
    if (showExitConfirmation) {
        ConfirmEntryExitDialog(
            onCancel = { showExitConfirmation = false },
            onConfirm = {
                showExitConfirmation = false
                onNavigateBack()
            }
        )
    }
    
    // Show drafts dialog if needed
    if (showDraftsDialog) {
        DraftsListDialog(
            drafts = editorState.availableDrafts,
            isLoading = editorState.isLoadingDrafts,
            onDismiss = { showDraftsDialog = false },
            onDraftSelected = { draft ->
                viewModel.loadDraft(draft.id)
                showDraftsDialog = false
            },
            onDraftDeleted = viewModel::deleteDraft,
            onDeleteAllDrafts = viewModel::deleteAllDrafts,
        )
    }
    
    // Main editor layout with shared element transition
    val sharedTransitionScope = LocalSharedTransitionScope.current
    val animatedVisibilityScope = LocalAnimatedVisibilityScope.current
    
    val editorModifier = when {
        sharedTransitionScope != null && animatedVisibilityScope != null -> {
            // TODO: Fix sharedElement API compatibility
            // with(sharedTransitionScope) {
            //     modifier.sharedElement(
            //         rememberSharedContentState(key = FAB_TO_EDITOR_SHARED_ELEMENT_KEY),
            //         animatedVisibilityScope = animatedVisibilityScope
            //     )
            // }
            modifier
        }
        else -> modifier
    }
    
    ImmersiveEditorLayout(
        modifier = editorModifier,
        isEditorFocused = editorState.expandedBlockId != null,
        topBarContent = {
            NoteEditorToolbar(
                onBack = {
                    if (!editorState.canExitWithoutSaving) {
                        showExitConfirmation = true 
                    } else {
                        onNavigateBack()
                    }
                },
                onSave = {
                    // Just pass the current editor state directly
                    viewModel.saveEntry(editorState)
                },
                onShowDrafts = {
                    showDraftsDialog = true
                },
                // Pass the autoSaveStatus for subtle indicator
                autoSaveStatus = autoSaveState.status,
            )
        },
        editorContent = {
            // Pass the unified UI state to the content
//            EditorContent(uiState = uiState)
            Napier.d("EntryEditorScreen: Rendering MainEditorContent with blocks: ${uiState.blocks.size}")
            MainEditorContent(uiState = uiState)
        },
        bottomContent = {
            EditorBottomContent(journalState = uiState.journalState)
        },
    )
    
    // Show snackbar host for error messages
    Box(modifier = Modifier.fillMaxSize()) {
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}