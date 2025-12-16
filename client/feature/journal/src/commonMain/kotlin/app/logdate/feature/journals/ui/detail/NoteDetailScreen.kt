@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)

package app.logdate.feature.journals.ui.detail

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import app.logdate.client.repository.journals.JournalNote
import app.logdate.feature.editor.ui.layout.ImmersiveEditorLayout
import app.logdate.feature.editor.ui.common.NoteEditorToolbar
import app.logdate.ui.LocalNavAnimatedVisibilityScope
import app.logdate.ui.LocalSharedTransitionScope
import app.logdate.ui.common.transitions.TransitionKeys.EDITOR_TRANSITION
import app.logdate.ui.theme.Spacing
import app.logdate.util.toReadableDateTimeShort
import org.koin.compose.viewmodel.koinViewModel

/**
 * Screen for viewing a journal note in detail, similar to a Stories-like view.
 * Uses the same wrapper layout as the editor for visual consistency.
 */
@Composable
fun NoteDetailScreen(
    onGoBack: () -> Unit,
    viewModel: NoteDetailViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    var showMoreOptionsMenu by remember { mutableStateOf(false) }
    var showDeleteConfirmation by rememberSaveable { mutableStateOf(false) }

    val sharedTransitionScope = LocalSharedTransitionScope.current
        ?: throw IllegalStateException("No SharedElementScope found")
    val animatedVisibilityScope = LocalNavAnimatedVisibilityScope.current
        ?: throw IllegalStateException("No AnimatedVisibility found")

    with(sharedTransitionScope) {
        ImmersiveEditorLayout(
            isEditorFocused = false,
            topBarContent = {
                NoteEditorToolbar(
                    onBack = onGoBack,
                    onSave = {},
                    onShowDrafts = {},
                )
            },
            editorContent = {
                when (val currentState = state) {
                    is NoteDetailUiState.Loading -> {
                        LoadingContent()
                    }
                    is NoteDetailUiState.Error -> {
                        ErrorContent(message = currentState.message)
                    }
                    is NoteDetailUiState.Success -> {
                        NoteContent(note = currentState.note)
                    }
                }
            },
            bottomContent = {
                // Empty bottom content for consistency with editor layout
                Spacer(modifier = Modifier.fillMaxWidth())
            },
            modifier = Modifier.sharedBounds(
                rememberSharedContentState(key = EDITOR_TRANSITION),
                animatedVisibilityScope = animatedVisibilityScope,
            )
        )
    }

    // Show delete confirmation dialog
    if (showDeleteConfirmation) {
        ConfirmEntryDeletionDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            onConfirmation = {
                viewModel.deleteNote(onGoBack)
                showDeleteConfirmation = false
            }
        )
    }
}

@Composable
private fun LoadingContent() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun ErrorContent(message: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            Text(
                text = "Error",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.error
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun NoteContent(note: JournalNote) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        // Timestamp
        Text(
            text = note.creationTimestamp.toReadableDateTimeShort(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        // Note content
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer
            )
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .padding(Spacing.lg),
                color = MaterialTheme.colorScheme.surface
            ) {
                when (note) {
                    is JournalNote.Text -> {
                        Text(
                            text = note.content,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                    is JournalNote.Image -> {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                        ) {
                            Text(
                                text = "Image",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "Media reference: ${note.mediaRef}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    is JournalNote.Video -> {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                        ) {
                            Text(
                                text = "Video",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "Media reference: ${note.mediaRef}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    is JournalNote.Audio -> {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                        ) {
                            Text(
                                text = "Audio",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "Media reference: ${note.mediaRef}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}